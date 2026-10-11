package com.wildstep.service;

import com.wildstep.domain.enums.Difficulty;
import com.wildstep.domain.enums.EnvironmentType;
import com.wildstep.domain.enums.Mood;
import com.wildstep.domain.enums.Participants;
import com.wildstep.dto.MissionGenerateRequestDto;
import com.wildstep.dto.MissionResponseDto;
import com.wildstep.dto.MissionTaskResponseDto;
import com.wildstep.exception.MissionValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MissionValidationServiceTest {

    private MissionValidationService validationService;
    private MissionGenerateRequestDto requestDto;

    @BeforeEach
    void setUp() {
        validationService = new MissionValidationService();
        requestDto = new MissionGenerateRequestDto(
                20, Mood.STRESSED, "Relax", EnvironmentType.CAMPUS, Difficulty.EASY, Participants.SOLO
        );
    }

    @Test
    @DisplayName("Should pass validation when generated mission meets all bounds and safety rules")
    void validate_Success() {
        MissionResponseDto mission = new MissionResponseDto();
        mission.setTitle("20-Minute Campus Reset");
        mission.setDescription("A short relaxing walk around public quad areas.");
        mission.setBonusTask("Notice one new natural detail.");

        List<MissionTaskResponseDto> tasks = new ArrayList<>();
        tasks.add(new MissionTaskResponseDto("Walk near the main library lawn", 8, 1));
        tasks.add(new MissionTaskResponseDto("Sit quietly on a public bench", 7, 2));
        mission.setTasks(tasks);

        assertDoesNotThrow(() -> validationService.validateGeneratedMission(requestDto, mission));
        assertEquals(20, mission.getAvailableMinutes());
        assertEquals(Mood.STRESSED, mission.getMood());
    }

    @Test
    @DisplayName("Should throw MissionValidationException when task count is less than 2")
    void validate_TaskCountTooLow() {
        MissionResponseDto mission = new MissionResponseDto();
        mission.setTitle("Short Reset");
        mission.setDescription("A brief reset.");

        List<MissionTaskResponseDto> tasks = List.of(
                new MissionTaskResponseDto("Walk around", 10, 1)
        );
        mission.setTasks(tasks);

        MissionValidationException ex = assertThrows(
                MissionValidationException.class,
                () -> validationService.validateGeneratedMission(requestDto, mission)
        );
        assertTrue(ex.getMessage().contains("must contain between 2 and 5 tasks"));
    }

    @Test
    @DisplayName("Should throw MissionValidationException when total task duration exceeds available time")
    void validate_DurationExceeded() {
        MissionResponseDto mission = new MissionResponseDto();
        mission.setTitle("Long Campus Exploration");
        mission.setDescription("An extended exploration.");

        List<MissionTaskResponseDto> tasks = List.of(
                new MissionTaskResponseDto("Walk to park", 15, 1),
                new MissionTaskResponseDto("Observe nature", 15, 2)
        );
        mission.setTasks(tasks);

        MissionValidationException ex = assertThrows(
                MissionValidationException.class,
                () -> validationService.validateGeneratedMission(requestDto, mission)
        );
        assertTrue(ex.getMessage().contains("exceeds user available time"));
    }

    @Test
    @DisplayName("Should throw MissionValidationException when safety guardrail keyword is detected")
    void validate_UnsafeKeyword() {
        MissionResponseDto mission = new MissionResponseDto();
        mission.setTitle("Campus Exploration");
        mission.setDescription("Explore campus.");

        List<MissionTaskResponseDto> tasks = List.of(
                new MissionTaskResponseDto("Walk near quad", 8, 1),
                new MissionTaskResponseDto("Climb building rooftop for a view", 7, 2)
        );
        mission.setTasks(tasks);

        MissionValidationException ex = assertThrows(
                MissionValidationException.class,
                () -> validationService.validateGeneratedMission(requestDto, mission)
        );
        assertTrue(ex.getMessage().contains("safety guardrail violation"));
    }
}
