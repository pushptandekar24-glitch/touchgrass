package com.touchgrass.service;

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
import com.touchgrass.repository.UserRepository;
import com.touchgrass.service.ai.MissionGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MissionLifecycleIntegrationTest {

    @Autowired
    private MissionService missionService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private MissionGenerator missionGenerator;

    @Test
    @DisplayName("End-to-End: Generate, retrieve, complete tasks individually, award Grass Points idempotently")
    void fullLifecycle_IntegrationTest() {
        // 1. Create a persistent user
        User user = new User("gardener_alex", "alex@example.com");
        user.setTotalPoints(10);
        user = userRepository.save(user);

        // 2. Mock AI generation output
        MissionResponseDto aiOutput = new MissionResponseDto();
        aiOutput.setTitle("Green Sanctuary Reset");
        aiOutput.setDescription("Find a quiet tree and reflect for 15 minutes");
        aiOutput.setAvailableMinutes(15);
        aiOutput.setMood(Mood.STRESSED);
        aiOutput.setGoal("De-stress");
        aiOutput.setEnvironmentType(EnvironmentType.PARK);
        aiOutput.setDifficulty(Difficulty.EASY);
        aiOutput.setParticipants(Participants.SOLO);
        aiOutput.setBonusTask("Spot a singing bird");
        aiOutput.setTasks(List.of(
                new MissionTaskResponseDto("Walk to the old oak tree", 7, 1),
                new MissionTaskResponseDto("Sit quietly and breathe deeply", 8, 2)
        ));

        when(missionGenerator.generateMission(any(MissionGenerateRequestDto.class))).thenReturn(aiOutput);

        // 3. Generate and persist mission
        MissionGenerateRequestDto request = new MissionGenerateRequestDto(
                15, Mood.STRESSED, "De-stress", EnvironmentType.PARK, Difficulty.EASY, Participants.SOLO, user.getId()
        );
        MissionResponseDto persistedMission = missionService.generateMission(request);

        assertNotNull(persistedMission.getId());
        assertEquals(user.getId(), persistedMission.getUserId());
        assertEquals(MissionStatus.CREATED, persistedMission.getStatus());
        assertEquals(0, persistedMission.getPointsAwarded());
        assertEquals(2, persistedMission.getTasks().size());

        Long missionId = persistedMission.getId();
        Long task1Id = persistedMission.getTasks().get(0).getId();
        Long task2Id = persistedMission.getTasks().get(1).getId();

        assertNotNull(task1Id);
        assertNotNull(task2Id);

        // 4. Retrieve mission and verify persistence
        MissionResponseDto retrieved = missionService.getMissionById(missionId);
        assertEquals(missionId, retrieved.getId());
        assertEquals("Green Sanctuary Reset", retrieved.getTitle());
        assertEquals(2, retrieved.getTasks().size());

        // 5. Complete task 1 -> Transitions to IN_PROGRESS, points remain 0
        MissionResponseDto progressState = missionService.completeTask(missionId, task1Id, null);
        assertEquals(MissionStatus.IN_PROGRESS, progressState.getStatus());
        assertEquals(0, progressState.getPointsAwarded());
        assertTrue(progressState.getTasks().stream().anyMatch(t -> t.getId().equals(task1Id) && t.getCompleted()));
        assertFalse(progressState.getTasks().stream().anyMatch(t -> t.getId().equals(task2Id) && t.getCompleted()));

        // User points still at initial 10
        User userMidway = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(10, userMidway.getTotalPoints());

        // 6. Complete task 2 with verified actual duration 20 minutes -> Transitions to COMPLETED
        // Formula: min(150, 50 + (2 tasks * 10) + floor(20 / 5) * 5) = 50 + 20 + 20 = 90 points
        MissionResponseDto completedState = missionService.completeTask(missionId, task2Id, 20);
        assertEquals(MissionStatus.COMPLETED, completedState.getStatus());
        assertEquals(90, completedState.getPointsAwarded());
        assertNotNull(completedState.getCompletedAt());
        assertTrue(completedState.getTasks().stream().allMatch(MissionTaskResponseDto::getCompleted));

        // User points updated: initial 10 + 90 awarded = 100
        User userFinal = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(100, userFinal.getTotalPoints());

        // 7. Duplicate completion on task 2 -> Idempotent, points are NOT re-awarded!
        MissionResponseDto repeatState = missionService.completeTask(missionId, task2Id, 20);
        assertEquals(MissionStatus.COMPLETED, repeatState.getStatus());
        assertEquals(90, repeatState.getPointsAwarded());

        User userAfterRepeat = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(100, userAfterRepeat.getTotalPoints()); // Still 100, never 190!

        // 8. Duplicate completion on task 1 -> Also idempotent
        MissionResponseDto repeatTask1State = missionService.completeTask(missionId, task1Id, null);
        assertEquals(MissionStatus.COMPLETED, repeatTask1State.getStatus());
        assertEquals(90, repeatTask1State.getPointsAwarded());

        User userAfterTask1Repeat = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(100, userAfterTask1Repeat.getTotalPoints());
    }

    @Test
    @DisplayName("Cross-Mission Task Validation: Reject task completion if task belongs to a different mission")
    void completeTask_BelongsToDifferentMission_ThrowsConflict() {
        MissionResponseDto aiOutput1 = new MissionResponseDto();
        aiOutput1.setTitle("Mission A");
        aiOutput1.setDescription("Walk");
        aiOutput1.setAvailableMinutes(10);
        aiOutput1.setMood(Mood.TIRED);
        aiOutput1.setGoal("Energize");
        aiOutput1.setEnvironmentType(EnvironmentType.CAMPUS);
        aiOutput1.setDifficulty(Difficulty.EASY);
        aiOutput1.setParticipants(Participants.SOLO);
        aiOutput1.setTasks(List.of(
                new MissionTaskResponseDto("Task A1", 5, 1),
                new MissionTaskResponseDto("Task A2", 5, 2)
        ));

        when(missionGenerator.generateMission(any(MissionGenerateRequestDto.class))).thenReturn(aiOutput1);

        MissionResponseDto missionA = missionService.generateMission(new MissionGenerateRequestDto(
                10, Mood.TIRED, "Energize", EnvironmentType.CAMPUS, Difficulty.EASY, Participants.SOLO
        ));

        MissionResponseDto aiOutput2 = new MissionResponseDto();
        aiOutput2.setTitle("Mission B");
        aiOutput2.setDescription("Breathe");
        aiOutput2.setAvailableMinutes(10);
        aiOutput2.setMood(Mood.TIRED);
        aiOutput2.setGoal("Energize");
        aiOutput2.setEnvironmentType(EnvironmentType.CAMPUS);
        aiOutput2.setDifficulty(Difficulty.EASY);
        aiOutput2.setParticipants(Participants.SOLO);
        aiOutput2.setTasks(List.of(
                new MissionTaskResponseDto("Task B1", 5, 1),
                new MissionTaskResponseDto("Task B2", 5, 2)
        ));

        when(missionGenerator.generateMission(any(MissionGenerateRequestDto.class))).thenReturn(aiOutput2);

        MissionResponseDto missionB = missionService.generateMission(new MissionGenerateRequestDto(
                10, Mood.TIRED, "Energize", EnvironmentType.CAMPUS, Difficulty.EASY, Participants.SOLO
        ));

        Long missionAId = missionA.getId();
        Long taskBId = missionB.getTasks().get(0).getId();

        // Attempting to complete Task B under Mission A must fail
        assertThrows(ConflictException.class, () -> missionService.completeTask(missionAId, taskBId, 5));
    }
}
