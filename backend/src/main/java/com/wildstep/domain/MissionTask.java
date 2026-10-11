package com.wildstep.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "mission_tasks")
public class MissionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    @JsonIgnore
    private Mission mission;

    @NotBlank
    @Column(nullable = false)
    private String description;

    @NotNull
    @Column(name = "estimated_minutes", nullable = false)
    private Integer estimatedMinutes;

    @Column(nullable = false)
    private Boolean completed = false;

    @Column(name = "task_order", nullable = false)
    private Integer taskOrder = 0;

    public MissionTask() {
    }

    public MissionTask(String description, Integer estimatedMinutes, Integer taskOrder) {
        this.description = description;
        this.estimatedMinutes = estimatedMinutes;
        this.taskOrder = taskOrder;
        this.completed = false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Mission getMission() {
        return mission;
    }

    public void setMission(Mission mission) {
        this.mission = mission;
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

    public Boolean getCompleted() {
        return completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    public Integer getTaskOrder() {
        return taskOrder;
    }

    public void setTaskOrder(Integer taskOrder) {
        this.taskOrder = taskOrder;
    }
}
