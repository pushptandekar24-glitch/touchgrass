package com.wildstep.dto;

import com.wildstep.domain.enums.Difficulty;
import com.wildstep.domain.enums.EnvironmentType;
import com.wildstep.domain.enums.Mood;
import com.wildstep.domain.enums.Participants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class MissionGenerateRequestDto {

    @NotNull(message = "Available minutes is required")
    @Min(value = 5, message = "Available time must be at least 5 minutes")
    @Max(value = 180, message = "Available time cannot exceed 180 minutes")
    private Integer availableMinutes;

    @NotNull(message = "Mood is required")
    private Mood mood;

    @NotBlank(message = "Personal goal is required")
    @Size(max = 100, message = "Goal cannot exceed 100 characters")
    private String goal;

    @NotNull(message = "Environment type is required")
    private EnvironmentType environmentType;

    @NotNull(message = "Difficulty is required")
    private Difficulty difficulty;

    @NotNull(message = "Participants option is required")
    private Participants participants;

    @Min(value = 1, message = "User ID must be positive")
    private Long userId;

    public MissionGenerateRequestDto() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public MissionGenerateRequestDto(Integer availableMinutes, Mood mood, String goal,
                                    EnvironmentType environmentType, Difficulty difficulty,
                                    Participants participants) {
        this.availableMinutes = availableMinutes;
        this.mood = mood;
        this.goal = goal;
        this.environmentType = environmentType;
        this.difficulty = difficulty;
        this.participants = participants;
    }

    public MissionGenerateRequestDto(Integer availableMinutes, Mood mood, String goal,
                                    EnvironmentType environmentType, Difficulty difficulty,
                                    Participants participants, Long userId) {
        this(availableMinutes, mood, goal, environmentType, difficulty, participants);
        this.userId = userId;
    }

    public Integer getAvailableMinutes() {
        return availableMinutes;
    }

    public void setAvailableMinutes(Integer availableMinutes) {
        this.availableMinutes = availableMinutes;
    }

    public Mood getMood() {
        return mood;
    }

    public void setMood(Mood mood) {
        this.mood = mood;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public EnvironmentType getEnvironmentType() {
        return environmentType;
    }

    public void setEnvironmentType(EnvironmentType environmentType) {
        this.environmentType = environmentType;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public Participants getParticipants() {
        return participants;
    }

    public void setParticipants(Participants participants) {
        this.participants = participants;
    }
}
