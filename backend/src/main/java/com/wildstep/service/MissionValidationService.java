package com.wildstep.service;

import com.wildstep.dto.MissionGenerateRequestDto;
import com.wildstep.dto.MissionResponseDto;
import com.wildstep.dto.MissionTaskResponseDto;
import com.wildstep.exception.MissionValidationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class MissionValidationService {

    private static final List<String> FORBIDDEN_SAFETY_KEYWORDS = List.of(
            "trespass", "restricted area", "private property", "climb building",
            "climb fence", "climb tree", "climb roof", "railway", "train tracks",
            "traffic road", "highway", "approach stranger", "talk to stranger",
            "swim in river", "dangerous animal", "illegal"
    );

    public void validateGeneratedMission(MissionGenerateRequestDto request, MissionResponseDto mission) {
        if (mission == null) {
            throw new MissionValidationException("AI model returned an empty mission payload.");
        }

        if (mission.getTitle() == null || mission.getTitle().isBlank()) {
            throw new MissionValidationException("Generated mission title cannot be empty.");
        }

        if (mission.getDescription() == null || mission.getDescription().isBlank()) {
            throw new MissionValidationException("Generated mission description cannot be empty.");
        }

        List<MissionTaskResponseDto> tasks = mission.getTasks();
        if (tasks == null || tasks.size() < 2 || tasks.size() > 5) {
            throw new MissionValidationException("Generated mission must contain between 2 and 5 tasks.");
        }

        int totalTaskMinutes = 0;
        for (int i = 0; i < tasks.size(); i++) {
            MissionTaskResponseDto task = tasks.get(i);
            if (task.getDescription() == null || task.getDescription().isBlank()) {
                throw new MissionValidationException("Task " + (i + 1) + " description cannot be empty.");
            }
            if (task.getEstimatedMinutes() == null || task.getEstimatedMinutes() <= 0) {
                throw new MissionValidationException("Task " + (i + 1) + " estimated duration must be greater than 0 minutes.");
            }
            totalTaskMinutes += task.getEstimatedMinutes();
        }

        if (totalTaskMinutes > request.getAvailableMinutes()) {
            throw new MissionValidationException(String.format(
                    "Total task duration (%d mins) exceeds user available time (%d mins).",
                    totalTaskMinutes, request.getAvailableMinutes()
            ));
        }

        // Safety Guardrail Keyword Scan across Title, Description, Tasks, and Bonus Task
        String combinedText = (mission.getTitle() + " " + mission.getDescription() + " " +
                tasks.stream().map(MissionTaskResponseDto::getDescription).reduce("", (a, b) -> a + " " + b) + " " +
                (mission.getBonusTask() != null ? mission.getBonusTask() : "")).toLowerCase(Locale.ROOT);

        for (String keyword : FORBIDDEN_SAFETY_KEYWORDS) {
            if (combinedText.contains(keyword)) {
                throw new MissionValidationException("Mission rejected due to safety guardrail violation: contains forbidden term '" + keyword + "'.");
            }
        }

        // Set contextual properties from request
        mission.setAvailableMinutes(request.getAvailableMinutes());
        mission.setMood(request.getMood());
        mission.setGoal(request.getGoal());
        mission.setEnvironmentType(request.getEnvironmentType());
        mission.setDifficulty(request.getDifficulty());
        mission.setParticipants(request.getParticipants());
    }
}
