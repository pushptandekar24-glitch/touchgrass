package com.wildstep.service;

import com.wildstep.domain.Mission;
import com.wildstep.domain.MissionTask;
import com.wildstep.domain.User;
import com.wildstep.domain.enums.MissionStatus;
import com.wildstep.dto.MissionGenerateRequestDto;
import com.wildstep.dto.MissionResponseDto;
import com.wildstep.dto.MissionTaskResponseDto;
import com.wildstep.exception.ConflictException;
import com.wildstep.exception.ResourceNotFoundException;
import com.wildstep.repository.MissionRepository;
import com.wildstep.repository.MissionTaskRepository;
import com.wildstep.repository.UserRepository;
import com.wildstep.service.ai.MissionGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MissionService {

    private static final Logger log = LoggerFactory.getLogger(MissionService.class);

    private final MissionGenerator missionGenerator;
    private final MissionValidationService validationService;
    private final MissionRepository missionRepository;
    private final MissionTaskRepository missionTaskRepository;
    private final UserRepository userRepository;
    private final GrassPointsService grassPointsService;

    public MissionService(
            MissionGenerator missionGenerator,
            MissionValidationService validationService,
            MissionRepository missionRepository,
            MissionTaskRepository missionTaskRepository,
            UserRepository userRepository,
            GrassPointsService grassPointsService) {
        this.missionGenerator = missionGenerator;
        this.validationService = validationService;
        this.missionRepository = missionRepository;
        this.missionTaskRepository = missionTaskRepository;
        this.userRepository = userRepository;
        this.grassPointsService = grassPointsService;
    }

    /**
     * Generates a mission using AI inference, validates safety and schema constraints,
     * and persists the mission along with its tasks in MySQL within a single transaction.
     *
     * @param request Generation parameters
     * @return Persisted mission details with database IDs
     */
    @Transactional
    public MissionResponseDto generateMission(MissionGenerateRequestDto request) {
        log.info("Generating mission for environment '{}', duration {} mins, mood '{}', userId {}",
                request.getEnvironmentType(), request.getAvailableMinutes(), request.getMood(), request.getUserId());

        MissionResponseDto rawMission = missionGenerator.generateMission(request);
        validationService.validateGeneratedMission(request, rawMission);

        Mission mission = new Mission();
        if (request.getUserId() != null) {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getUserId()));
            mission.setUser(user);
        } else {
            mission.setUser(null);
        }

        mission.setTitle(rawMission.getTitle());
        mission.setDescription(rawMission.getDescription());
        mission.setAvailableMinutes(rawMission.getAvailableMinutes() != null ? rawMission.getAvailableMinutes() : request.getAvailableMinutes());
        mission.setMood(rawMission.getMood() != null ? rawMission.getMood() : request.getMood());
        mission.setGoal(rawMission.getGoal() != null ? rawMission.getGoal() : request.getGoal());
        mission.setEnvironmentType(rawMission.getEnvironmentType() != null ? rawMission.getEnvironmentType() : request.getEnvironmentType());
        mission.setDifficulty(rawMission.getDifficulty() != null ? rawMission.getDifficulty() : request.getDifficulty());
        mission.setParticipants(rawMission.getParticipants() != null ? rawMission.getParticipants() : request.getParticipants());
        mission.setStatus(MissionStatus.CREATED);
        mission.setPointsAwarded(0);
        mission.setBonusTask(rawMission.getBonusTask());
        mission.setCreatedAt(LocalDateTime.now());

        if (rawMission.getTasks() != null) {
            for (int i = 0; i < rawMission.getTasks().size(); i++) {
                MissionTaskResponseDto taskDto = rawMission.getTasks().get(i);
                int order = taskDto.getTaskOrder() != null ? taskDto.getTaskOrder() : (i + 1);
                MissionTask task = new MissionTask(taskDto.getDescription(), taskDto.getEstimatedMinutes(), order);
                mission.addTask(task);
            }
        }

        Mission savedMission = missionRepository.save(mission);
        log.info("Persisted generated mission with id {} and {} tasks", savedMission.getId(), savedMission.getTasks().size());
        return toResponseDto(savedMission);
    }

    /**
     * Retrieves an existing mission and its tasks by ID.
     *
     * @param missionId Target mission ID
     * @return Mission metadata, status, points, and task list
     */
    @Transactional(readOnly = true)
    public MissionResponseDto getMissionById(Long missionId) {
        Mission mission = missionRepository.findByIdWithTasks(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission not found with id: " + missionId));
        return toResponseDto(mission);
    }

    /**
     * Completes a task on a mission.
     * Enforces:
     * - Task must belong to the specified mission.
     * - Cancelled missions reject completions.
     * - Idempotency: repeated requests on already completed tasks do not re-award points.
     * - State transitions: CREATED -> IN_PROGRESS on partial completion;
     *   IN_PROGRESS -> COMPLETED when all tasks are complete.
     * - Concurrency: guarded by pessimistic database write lock.
     * - Reward calculation: Grass Points awarded exactly once upon mission completion.
     * - User totalPoints updated only when a real user is linked.
     *
     * @param missionId             Mission ID
     * @param taskId                Task ID
     * @param actualDurationMinutes Optional client-supplied verified outdoor duration
     * @return Updated mission state
     */
    @Transactional
    public MissionResponseDto completeTask(Long missionId, Long taskId, Integer actualDurationMinutes) {
        if (actualDurationMinutes != null && actualDurationMinutes < 0) {
            throw new IllegalArgumentException("Actual duration minutes cannot be negative.");
        }

        Mission mission = missionRepository.findByIdWithLock(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission not found with id: " + missionId));

        if (mission.getStatus() == MissionStatus.CANCELLED) {
            throw new ConflictException("Cannot complete tasks for a cancelled mission.");
        }

        MissionTask targetTask = mission.getTasks().stream()
                .filter(t -> t.getId() != null && t.getId().equals(taskId))
                .findFirst()
                .orElse(null);

        if (targetTask == null) {
            if (missionTaskRepository.existsById(taskId)) {
                throw new ConflictException("Task with id " + taskId + " does not belong to mission with id " + missionId);
            } else {
                throw new ResourceNotFoundException("Task not found with id: " + taskId);
            }
        }

        // Idempotency: if already completed, return current state without re-awarding points
        if (Boolean.TRUE.equals(targetTask.getCompleted())) {
            log.info("Task {} on mission {} already completed (idempotent request)", taskId, missionId);
            return toResponseDto(mission);
        }

        // If the mission was somehow already completed, prevent modifying tasks
        if (mission.getStatus() == MissionStatus.COMPLETED) {
            throw new ConflictException("Cannot modify tasks on an already completed mission.");
        }

        // Mark task complete
        targetTask.setCompleted(true);

        // Move CREATED -> IN_PROGRESS on first task completion
        if (mission.getStatus() == MissionStatus.CREATED) {
            mission.setStatus(MissionStatus.IN_PROGRESS);
        }

        // Check if all tasks in mission are completed
        boolean allTasksCompleted = !mission.getTasks().isEmpty() &&
                mission.getTasks().stream().allMatch(t -> Boolean.TRUE.equals(t.getCompleted()));

        if (allTasksCompleted) {
            mission.setStatus(MissionStatus.COMPLETED);
            mission.setCompletedAt(LocalDateTime.now());

            // Calculate Grass Points using deterministic formula:
            // min(150, 50 + completedTasks * 10 + floor(actualDurationMinutes / 5) * 5)
            int duration = (actualDurationMinutes != null && actualDurationMinutes > 0) ? actualDurationMinutes : 0;
            int points = grassPointsService.calculatePoints(mission.getTasks().size(), duration);
            mission.setPointsAwarded(points);

            // Update User totalPoints only for missions linked to a real user
            if (mission.getUser() != null) {
                User user = mission.getUser();
                int currentPoints = user.getTotalPoints() != null ? user.getTotalPoints() : 0;
                user.setTotalPoints(currentPoints + points);
                userRepository.save(user);
                log.info("Awarded {} Grass Points to user {} (new total: {})", points, user.getId(), user.getTotalPoints());
            } else {
                log.info("Mission {} completed anonymously. Awarded {} points recorded on mission only.", missionId, points);
            }
        }

        Mission savedMission = missionRepository.save(mission);
        return toResponseDto(savedMission);
    }

    /**
     * Maps a Mission entity to MissionResponseDto.
     *
     * @param mission Domain entity
     * @return DTO representation
     */
    public MissionResponseDto toResponseDto(Mission mission) {
        if (mission == null) {
            return null;
        }

        MissionResponseDto dto = new MissionResponseDto();
        dto.setId(mission.getId());
        dto.setUserId(mission.getUser() != null ? mission.getUser().getId() : null);
        dto.setTitle(mission.getTitle());
        dto.setDescription(mission.getDescription());
        dto.setAvailableMinutes(mission.getAvailableMinutes());
        dto.setMood(mission.getMood());
        dto.setGoal(mission.getGoal());
        dto.setEnvironmentType(mission.getEnvironmentType());
        dto.setDifficulty(mission.getDifficulty());
        dto.setParticipants(mission.getParticipants());
        dto.setStatus(mission.getStatus());
        dto.setPointsAwarded(mission.getPointsAwarded() != null ? mission.getPointsAwarded() : 0);
        dto.setBonusTask(mission.getBonusTask());
        dto.setCreatedAt(mission.getCreatedAt());
        dto.setCompletedAt(mission.getCompletedAt());

        List<MissionTaskResponseDto> taskDtos = new ArrayList<>();
        if (mission.getTasks() != null) {
            taskDtos = mission.getTasks().stream()
                    .map(t -> new MissionTaskResponseDto(
                            t.getId(),
                            t.getDescription(),
                            t.getEstimatedMinutes(),
                            t.getTaskOrder(),
                            t.getCompleted()
                    ))
                    .collect(Collectors.toList());
        }
        dto.setTasks(taskDtos);

        return dto;
    }
}
