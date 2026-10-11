package com.touchgrass.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.touchgrass.domain.enums.Difficulty;
import com.touchgrass.domain.enums.EnvironmentType;
import com.touchgrass.domain.enums.MissionStatus;
import com.touchgrass.domain.enums.Mood;
import com.touchgrass.domain.enums.Participants;
import com.touchgrass.dto.MissionGenerateRequestDto;
import com.touchgrass.dto.MissionResponseDto;
import com.touchgrass.dto.MissionTaskResponseDto;
import com.touchgrass.dto.TaskCompletionRequestDto;
import com.touchgrass.exception.ConflictException;
import com.touchgrass.exception.MissionValidationException;
import com.touchgrass.exception.OllamaUnavailableException;
import com.touchgrass.exception.ResourceNotFoundException;
import com.touchgrass.service.MissionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MissionController.class)
class MissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MissionService missionService;

    @Test
    @DisplayName("POST /api/missions/generate - Should return 201 Created and mission when valid")
    void generateMission_Success() throws Exception {
        MissionGenerateRequestDto requestDto = new MissionGenerateRequestDto(
                20, Mood.STRESSED, "Relax", EnvironmentType.CAMPUS, Difficulty.EASY, Participants.SOLO
        );

        MissionResponseDto responseDto = new MissionResponseDto();
        responseDto.setId(1L);
        responseDto.setTitle("20-Minute Campus Reset");
        responseDto.setDescription("A short relaxing outdoor walk.");
        responseDto.setAvailableMinutes(20);
        responseDto.setMood(Mood.STRESSED);
        responseDto.setGoal("Relax");
        responseDto.setEnvironmentType(EnvironmentType.CAMPUS);
        responseDto.setDifficulty(Difficulty.EASY);
        responseDto.setParticipants(Participants.SOLO);
        responseDto.setStatus(MissionStatus.CREATED);
        responseDto.setTasks(List.of(
                new MissionTaskResponseDto(10L, "Walk around lawn", 8, 1, false),
                new MissionTaskResponseDto(11L, "Sit on bench", 7, 2, false)
        ));

        Mockito.when(missionService.generateMission(any(MissionGenerateRequestDto.class))).thenReturn(responseDto);

        mockMvc.perform(post("/missions/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("20-Minute Campus Reset"))
                .andExpect(jsonPath("$.data.availableMinutes").value(20))
                .andExpect(jsonPath("$.data.tasks[0].id").value(10));
    }

    @Test
    @DisplayName("POST /api/missions/generate - Should return 400 Bad Request on validation error")
    void generateMission_BadRequest() throws Exception {
        MissionGenerateRequestDto invalidRequest = new MissionGenerateRequestDto(
                2, Mood.STRESSED, "", null, null, null
        );

        mockMvc.perform(post("/missions/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/missions/generate - Should return 503 Service Unavailable when Ollama is offline")
    void generateMission_ServiceUnavailable() throws Exception {
        MissionGenerateRequestDto requestDto = new MissionGenerateRequestDto(
                20, Mood.STRESSED, "Relax", EnvironmentType.CAMPUS, Difficulty.EASY, Participants.SOLO
        );

        Mockito.when(missionService.generateMission(any(MissionGenerateRequestDto.class)))
                .thenThrow(new OllamaUnavailableException("Ollama service is unavailable"));

        mockMvc.perform(post("/missions/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("AI Inference Service Unavailable"))
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    @DisplayName("POST /api/missions/generate - Should return 422 Unprocessable Entity when mission validation fails")
    void generateMission_UnprocessableEntity() throws Exception {
        MissionGenerateRequestDto requestDto = new MissionGenerateRequestDto(
                20, Mood.STRESSED, "Relax", EnvironmentType.CAMPUS, Difficulty.EASY, Participants.SOLO
        );

        Mockito.when(missionService.generateMission(any(MissionGenerateRequestDto.class)))
                .thenThrow(new MissionValidationException("Mission rejected due to safety guardrail violation"));

        mockMvc.perform(post("/missions/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Mission Validation Failed"))
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    @DisplayName("GET /api/missions/{missionId} - Should return 200 OK and mission metadata when mission exists")
    void getMissionById_Success() throws Exception {
        MissionResponseDto responseDto = new MissionResponseDto();
        responseDto.setId(5L);
        responseDto.setTitle("Sunset Park Walk");
        responseDto.setStatus(MissionStatus.CREATED);
        responseDto.setPointsAwarded(0);
        responseDto.setTasks(List.of(
                new MissionTaskResponseDto(20L, "Walk 500 steps", 5, 1, false)
        ));

        Mockito.when(missionService.getMissionById(5L)).thenReturn(responseDto);

        mockMvc.perform(get("/missions/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.title").value("Sunset Park Walk"))
                .andExpect(jsonPath("$.data.tasks[0].id").value(20));
    }

    @Test
    @DisplayName("GET /api/missions/{missionId} - Should return 404 Not Found for nonexistent mission")
    void getMissionById_NotFound() throws Exception {
        Mockito.when(missionService.getMissionById(999L))
                .thenThrow(new ResourceNotFoundException("Mission not found with id: 999"));

        mockMvc.perform(get("/missions/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("PATCH /api/missions/{missionId}/tasks/{taskId}/complete - Should return 200 OK on valid task completion")
    void completeTask_Success() throws Exception {
        TaskCompletionRequestDto requestDto = new TaskCompletionRequestDto(15);

        MissionResponseDto responseDto = new MissionResponseDto();
        responseDto.setId(5L);
        responseDto.setStatus(MissionStatus.IN_PROGRESS);
        responseDto.setPointsAwarded(0);
        responseDto.setTasks(List.of(
                new MissionTaskResponseDto(20L, "Walk 500 steps", 5, 1, true),
                new MissionTaskResponseDto(21L, "Stretch legs", 5, 2, false)
        ));

        Mockito.when(missionService.completeTask(eq(5L), eq(20L), eq(15))).thenReturn(responseDto);

        mockMvc.perform(patch("/missions/5/tasks/20/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.tasks[0].completed").value(true));
    }

    @Test
    @DisplayName("PATCH /api/missions/{missionId}/tasks/{taskId}/complete - Should support completion without body")
    void completeTask_WithoutBody_Success() throws Exception {
        MissionResponseDto responseDto = new MissionResponseDto();
        responseDto.setId(5L);
        responseDto.setStatus(MissionStatus.COMPLETED);
        responseDto.setPointsAwarded(80);

        Mockito.when(missionService.completeTask(eq(5L), eq(20L), Mockito.isNull())).thenReturn(responseDto);

        mockMvc.perform(patch("/missions/5/tasks/20/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.pointsAwarded").value(80));
    }

    @Test
    @DisplayName("PATCH /api/missions/{missionId}/tasks/{taskId}/complete - Should return 404 when mission or task not found")
    void completeTask_NotFound() throws Exception {
        Mockito.when(missionService.completeTask(eq(999L), eq(20L), any()))
                .thenThrow(new ResourceNotFoundException("Mission not found with id: 999"));

        mockMvc.perform(patch("/missions/999/tasks/20/complete"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("PATCH /api/missions/{missionId}/tasks/{taskId}/complete - Should return 409 Conflict when task belongs to another mission")
    void completeTask_TaskBelongsToAnotherMission_Conflict() throws Exception {
        Mockito.when(missionService.completeTask(eq(5L), eq(99L), any()))
                .thenThrow(new ConflictException("Task 99 does not belong to mission 5"));

        mockMvc.perform(patch("/missions/5/tasks/99/complete"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Resource Conflict"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("PATCH /api/missions/{missionId}/tasks/{taskId}/complete - Should return 409 Conflict when mission is cancelled")
    void completeTask_CancelledMission_Conflict() throws Exception {
        Mockito.when(missionService.completeTask(eq(5L), eq(20L), any()))
                .thenThrow(new ConflictException("Cannot complete tasks for a cancelled mission."));

        mockMvc.perform(patch("/missions/5/tasks/20/complete"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Resource Conflict"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("PATCH /api/missions/{missionId}/tasks/{taskId}/complete - Should return 400 Bad Request when negative duration provided in body")
    void completeTask_NegativeDurationInBody_BadRequest() throws Exception {
        TaskCompletionRequestDto invalidDto = new TaskCompletionRequestDto(-10);

        mockMvc.perform(patch("/missions/5/tasks/20/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.status").value(400));
    }
}
