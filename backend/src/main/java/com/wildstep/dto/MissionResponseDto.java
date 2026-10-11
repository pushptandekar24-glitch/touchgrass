package com.wildstep.dto;

import com.wildstep.domain.enums.Difficulty;
import com.wildstep.domain.enums.EnvironmentType;
import com.wildstep.domain.enums.MissionStatus;
import com.wildstep.domain.enums.Mood;
import com.wildstep.domain.enums.Participants;
import java.time.LocalDateTime;
import java.util.List;

public class MissionResponseDto {

    private Long id;
    private Long userId;
    private String title;
    private String description;
    private Integer availableMinutes;
    private Mood mood;
    private String goal;
    private EnvironmentType environmentType;
    private Difficulty difficulty;
    private Participants participants;
    private MissionStatus status = MissionStatus.CREATED;
    private Integer pointsAwarded = 0;
    private String bonusTask;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private List<MissionTaskResponseDto> tasks;

    public MissionResponseDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public MissionStatus getStatus() {
        return status;
    }

    public void setStatus(MissionStatus status) {
        this.status = status;
    }

    public Integer getPointsAwarded() {
        return pointsAwarded;
    }

    public void setPointsAwarded(Integer pointsAwarded) {
        this.pointsAwarded = pointsAwarded;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public String getBonusTask() {
        return bonusTask;
    }

    public void setBonusTask(String bonusTask) {
        this.bonusTask = bonusTask;
    }

    public List<MissionTaskResponseDto> getTasks() {
        return tasks;
    }

    public void setTasks(List<MissionTaskResponseDto> tasks) {
        this.tasks = tasks;
    }
}
