package com.touchgrass.service.ai;

import com.touchgrass.dto.MissionGenerateRequestDto;
import com.touchgrass.dto.MissionResponseDto;

public interface MissionGenerator {
    /**
     * Generates a structured outdoor mission based on user preferences.
     *
     * @param request User criteria (availableMinutes, mood, goal, environment, difficulty, participants)
     * @return Generated and parsed mission DTO
     */
    MissionResponseDto generateMission(MissionGenerateRequestDto request);
}
