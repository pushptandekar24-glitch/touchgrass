package com.touchgrass.controller;

import com.touchgrass.dto.ApiResponse;
import com.touchgrass.dto.MissionGenerateRequestDto;
import com.touchgrass.dto.MissionResponseDto;
import com.touchgrass.dto.TaskCompletionRequestDto;
import com.touchgrass.service.MissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/missions")
public class MissionController {

    private final MissionService missionService;

    public MissionController(MissionService missionService) {
        this.missionService = missionService;
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<MissionResponseDto>> generateMission(
            @Valid @RequestBody MissionGenerateRequestDto requestDto) {
        MissionResponseDto generatedMission = missionService.generateMission(requestDto);
        return new ResponseEntity<>(
                ApiResponse.success("Outdoor mission generated successfully", generatedMission),
                HttpStatus.CREATED
        );
    }

    @GetMapping("/{missionId}")
    public ResponseEntity<ApiResponse<MissionResponseDto>> getMissionById(@PathVariable Long missionId) {
        MissionResponseDto mission = missionService.getMissionById(missionId);
        return ResponseEntity.ok(ApiResponse.success("Mission retrieved successfully", mission));
    }

    @PatchMapping("/{missionId}/tasks/{taskId}/complete")
    public ResponseEntity<ApiResponse<MissionResponseDto>> completeTask(
            @PathVariable Long missionId,
            @PathVariable Long taskId,
            @RequestParam(required = false) Integer actualDurationMinutes,
            @Valid @RequestBody(required = false) TaskCompletionRequestDto requestDto) {
        Integer duration = (requestDto != null && requestDto.getActualDurationMinutes() != null)
                ? requestDto.getActualDurationMinutes()
                : actualDurationMinutes;
        MissionResponseDto responseDto = missionService.completeTask(missionId, taskId, duration);
        return ResponseEntity.ok(ApiResponse.success("Task completed successfully", responseDto));
    }
}
