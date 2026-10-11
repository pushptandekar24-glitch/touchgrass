package com.touchgrass.service;

import com.touchgrass.domain.Mission;
import com.touchgrass.domain.MissionTask;
import com.touchgrass.domain.User;
import com.touchgrass.domain.enums.Difficulty;
import com.touchgrass.domain.enums.EnvironmentType;
import com.touchgrass.domain.enums.MissionStatus;
import com.touchgrass.domain.enums.Mood;
import com.touchgrass.domain.enums.Participants;
import com.touchgrass.dto.MissionGenerateRequestDto;
import com.touchgrass.dto.MissionResponseDto;
import com.touchgrass.dto.MissionTaskResponseDto;
import com.touchgrass.exception.ConflictException;
import com.touchgrass.exception.ResourceNotFoundException;
import com.touchgrass.repository.MissionRepository;
import com.touchgrass.repository.MissionTaskRepository;
import com.touchgrass.repository.UserRepository;
import com.touchgrass.service.ai.MissionGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionLifecycleServiceTest {

    @Mock
    private MissionGenerator missionGenerator;

    @Mock
    private MissionValidationService validationService;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MissionTaskRepository missionTaskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GrassPointsService grassPointsService;

    @InjectMocks
    private MissionService missionService;

    private User sampleUser;
    private Mission sampleMission;
    private MissionTask task1;
    private MissionTask task2;

    @BeforeEach
    void setUp() {
        sampleUser = new User("testuser", "test@example.com");
        sampleUser.setId(1L);
        sampleUser.setTotalPoints(50);

        sampleMission = new Mission();
        sampleMission.setId(10L);
        sampleMission.setUser(sampleUser);
        sampleMission.setTitle("Campus Stroll");
        sampleMission.setDescription("Walk outdoors");
        sampleMission.setAvailableMinutes(20);
        sampleMission.setMood(Mood.STRESSED);
        sampleMission.setGoal("Relax");
        sampleMission.setEnvironmentType(EnvironmentType.CAMPUS);
        sampleMission.setDifficulty(Difficulty.EASY);
        sampleMission.setParticipants(Participants.SOLO);
        sampleMission.setStatus(MissionStatus.CREATED);
        sampleMission.setPointsAwarded(0);

        task1 = new MissionTask("Walk by trees", 10, 1);
        task1.setId(101L);
        sampleMission.addTask(task1);

        task2 = new MissionTask("Sit quietly", 10, 2);
        task2.setId(102L);
        sampleMission.addTask(task2);
    }

    @Test
    @DisplayName("generateMission - Should persist mission and tasks with linked user")
    void generateMission_PersistsWithUser() {
        MissionGenerateRequestDto request = new MissionGenerateRequestDto(
                20, Mood.STRESSED, "Relax", EnvironmentType.CAMPUS, Difficulty.EASY, Participants.SOLO, 1L
        );

        MissionResponseDto rawGenerated = new MissionResponseDto();
        rawGenerated.setTitle("Campus Stroll");
        rawGenerated.setDescription("Walk outdoors");
        rawGenerated.setAvailableMinutes(20);
        rawGenerated.setMood(Mood.STRESSED);
        rawGenerated.setGoal("Relax");
        rawGenerated.setEnvironmentType(EnvironmentType.CAMPUS);
        rawGenerated.setDifficulty(Difficulty.EASY);
        rawGenerated.setParticipants(Participants.SOLO);
        rawGenerated.setTasks(List.of(
                new MissionTaskResponseDto("Walk by trees", 10, 1),
                new MissionTaskResponseDto("Sit quietly", 10, 2)
        ));

        when(missionGenerator.generateMission(request)).thenReturn(rawGenerated);
        doNothing().when(validationService).validateGeneratedMission(request, rawGenerated);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> {
            Mission m = invocation.getArgument(0);
            m.setId(10L);
            long taskIdCounter = 101L;
            for (MissionTask t : m.getTasks()) {
                t.setId(taskIdCounter++);
            }
            return m;
        });

        MissionResponseDto response = missionService.generateMission(request);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals(1L, response.getUserId());
        assertEquals(MissionStatus.CREATED, response.getStatus());
        assertEquals(2, response.getTasks().size());
        assertEquals(101L, response.getTasks().get(0).getId());
        assertFalse(response.getTasks().get(0).getCompleted());
        verify(missionRepository, times(1)).save(any(Mission.class));
    }

    @Test
    @DisplayName("generateMission - Should persist anonymous mission when userId is null")
    void generateMission_PersistsAnonymous() {
        MissionGenerateRequestDto request = new MissionGenerateRequestDto(
                20, Mood.STRESSED, "Relax", EnvironmentType.CAMPUS, Difficulty.EASY, Participants.SOLO
        );

        MissionResponseDto rawGenerated = new MissionResponseDto();
        rawGenerated.setTitle("Campus Stroll");
        rawGenerated.setDescription("Walk outdoors");
        rawGenerated.setAvailableMinutes(20);
        rawGenerated.setMood(Mood.STRESSED);
        rawGenerated.setGoal("Relax");
        rawGenerated.setEnvironmentType(EnvironmentType.CAMPUS);
        rawGenerated.setDifficulty(Difficulty.EASY);
        rawGenerated.setParticipants(Participants.SOLO);
        rawGenerated.setTasks(List.of(
                new MissionTaskResponseDto("Walk by trees", 10, 1)
        ));

        when(missionGenerator.generateMission(request)).thenReturn(rawGenerated);
        doNothing().when(validationService).validateGeneratedMission(request, rawGenerated);
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> {
            Mission m = invocation.getArgument(0);
            m.setId(11L);
            return m;
        });

        MissionResponseDto response = missionService.generateMission(request);

        assertNotNull(response);
        assertEquals(11L, response.getId());
        assertNull(response.getUserId());
        verify(userRepository, never()).findById(any());
        verify(missionRepository, times(1)).save(any(Mission.class));
    }

    @Test
    @DisplayName("generateMission - Should throw ResourceNotFoundException when userId does not exist")
    void generateMission_NonexistentUser_ThrowsException() {
        MissionGenerateRequestDto request = new MissionGenerateRequestDto(
                20, Mood.STRESSED, "Relax", EnvironmentType.CAMPUS, Difficulty.EASY, Participants.SOLO, 999L
        );

        MissionResponseDto rawGenerated = new MissionResponseDto();
        rawGenerated.setTitle("Campus Stroll");
        rawGenerated.setTasks(List.of(new MissionTaskResponseDto("Walk", 10, 1)));

        when(missionGenerator.generateMission(request)).thenReturn(rawGenerated);
        doNothing().when(validationService).validateGeneratedMission(request, rawGenerated);
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> missionService.generateMission(request));
        verify(missionRepository, never()).save(any(Mission.class));
    }

    @Test
    @DisplayName("getMissionById - Should return mission when found")
    void getMissionById_Success() {
        when(missionRepository.findByIdWithTasks(10L)).thenReturn(Optional.of(sampleMission));

        MissionResponseDto response = missionService.getMissionById(10L);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("Campus Stroll", response.getTitle());
        assertEquals(2, response.getTasks().size());
    }

    @Test
    @DisplayName("getMissionById - Should throw ResourceNotFoundException when not found")
    void getMissionById_NotFound() {
        when(missionRepository.findByIdWithTasks(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> missionService.getMissionById(999L));
    }

    @Test
    @DisplayName("completeTask - Partial completion moves status to IN_PROGRESS and points remain 0")
    void completeTask_PartialCompletion_TransitionsToInProgress() {
        when(missionRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleMission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MissionResponseDto response = missionService.completeTask(10L, 101L, 10);

        assertNotNull(response);
        assertEquals(MissionStatus.IN_PROGRESS, response.getStatus());
        assertEquals(0, response.getPointsAwarded());
        assertTrue(response.getTasks().get(0).getCompleted());
        assertFalse(response.getTasks().get(1).getCompleted());
        // User points should not be modified
        assertEquals(50, sampleUser.getTotalPoints());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("completeTask - All tasks complete moves to COMPLETED, awards points once, updates user")
    void completeTask_AllTasksCompleted_AwardsPointsToUser() {
        task1.setCompleted(true);
        sampleMission.setStatus(MissionStatus.IN_PROGRESS);

        when(missionRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleMission));
        when(grassPointsService.calculatePoints(2, 20)).thenReturn(90);
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MissionResponseDto response = missionService.completeTask(10L, 102L, 20);

        assertNotNull(response);
        assertEquals(MissionStatus.COMPLETED, response.getStatus());
        assertEquals(90, response.getPointsAwarded());
        assertNotNull(response.getCompletedAt());
        assertTrue(response.getTasks().stream().allMatch(MissionTaskResponseDto::getCompleted));

        // User points: 50 + 90 = 140
        assertEquals(140, sampleUser.getTotalPoints());
        verify(userRepository, times(1)).save(sampleUser);
    }

    @Test
    @DisplayName("completeTask - Anonymous mission records points on mission but does not modify any user")
    void completeTask_AllTasksCompleted_AnonymousMission() {
        sampleMission.setUser(null);
        task1.setCompleted(true);
        sampleMission.setStatus(MissionStatus.IN_PROGRESS);

        when(missionRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleMission));
        when(grassPointsService.calculatePoints(2, 25)).thenReturn(105);
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MissionResponseDto response = missionService.completeTask(10L, 102L, 25);

        assertNotNull(response);
        assertEquals(MissionStatus.COMPLETED, response.getStatus());
        assertEquals(105, response.getPointsAwarded());
        assertNull(response.getUserId());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("completeTask - Repeated completion request on completed task is idempotent")
    void completeTask_IdempotentOnRepeatedRequest() {
        task1.setCompleted(true);
        sampleMission.setStatus(MissionStatus.IN_PROGRESS);

        when(missionRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleMission));

        MissionResponseDto response = missionService.completeTask(10L, 101L, 15);

        assertNotNull(response);
        assertEquals(MissionStatus.IN_PROGRESS, response.getStatus());
        assertEquals(0, response.getPointsAwarded());
        verify(grassPointsService, never()).calculatePoints(anyInt(), anyInt());
        verify(userRepository, never()).save(any());
        verify(missionRepository, never()).save(any());
    }

    @Test
    @DisplayName("completeTask - Task belonging to another mission throws ConflictException")
    void completeTask_TaskBelongsToAnotherMission_ThrowsConflictException() {
        when(missionRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleMission));
        when(missionTaskRepository.existsById(999L)).thenReturn(true); // Exists, but not in sampleMission tasks

        ConflictException ex = assertThrows(ConflictException.class,
                () -> missionService.completeTask(10L, 999L, 10));
        assertTrue(ex.getMessage().contains("does not belong to mission"));
    }

    @Test
    @DisplayName("completeTask - Nonexistent task ID throws ResourceNotFoundException")
    void completeTask_NonexistentTaskId_ThrowsResourceNotFoundException() {
        when(missionRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleMission));
        when(missionTaskRepository.existsById(999L)).thenReturn(false);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> missionService.completeTask(10L, 999L, 10));
        assertTrue(ex.getMessage().contains("Task not found with id"));
    }

    @Test
    @DisplayName("completeTask - Nonexistent mission ID throws ResourceNotFoundException")
    void completeTask_NonexistentMissionId_ThrowsResourceNotFoundException() {
        when(missionRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> missionService.completeTask(999L, 101L, 10));
    }

    @Test
    @DisplayName("completeTask - Cancelled mission rejects completion with ConflictException")
    void completeTask_CancelledMission_ThrowsConflictException() {
        sampleMission.setStatus(MissionStatus.CANCELLED);
        when(missionRepository.findByIdWithLock(10L)).thenReturn(Optional.of(sampleMission));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> missionService.completeTask(10L, 101L, 10));
        assertTrue(ex.getMessage().contains("cancelled"));
    }

    @Test
    @DisplayName("completeTask - Negative actual duration throws IllegalArgumentException")
    void completeTask_NegativeDuration_ThrowsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> missionService.completeTask(10L, 101L, -5));
        assertTrue(ex.getMessage().contains("cannot be negative"));
    }
}
