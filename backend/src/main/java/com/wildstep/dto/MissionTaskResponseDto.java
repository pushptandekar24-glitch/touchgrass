package com.wildstep.dto;

public class MissionTaskResponseDto {

    private Long id;
    private String description;
    private Integer estimatedMinutes;
    private Integer taskOrder;
    private Boolean completed = false;

    public MissionTaskResponseDto() {
    }

    public MissionTaskResponseDto(String description, Integer estimatedMinutes, Integer taskOrder) {
        this.description = description;
        this.estimatedMinutes = estimatedMinutes;
        this.taskOrder = taskOrder;
        this.completed = false;
    }

    public MissionTaskResponseDto(Long id, String description, Integer estimatedMinutes, Integer taskOrder, Boolean completed) {
        this.id = id;
        this.description = description;
        this.estimatedMinutes = estimatedMinutes;
        this.taskOrder = taskOrder;
        this.completed = completed != null ? completed : false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(Integer estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }

    public Integer getTaskOrder() {
        return taskOrder;
    }

    public void setTaskOrder(Integer taskOrder) {
        this.taskOrder = taskOrder;
    }

    public Boolean getCompleted() {
        return completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }
}
