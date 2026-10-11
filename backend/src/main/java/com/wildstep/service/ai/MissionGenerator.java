package com.wildstep.service.ai;

import com.wildstep.dto.MissionGenerateRequestDto;
import com.wildstep.dto.MissionResponseDto;

public interface MissionGenerator {
    /**
     * Generates a structured outdoor mission based on user preferences.
     *
     * @param request User criteria (availableMinutes, mood, goal, environment, difficulty, participants)
     * @return Generated and parsed mission DTO
     */
    MissionResponseDto generateMission(MissionGenerateRequestDto request);
}
