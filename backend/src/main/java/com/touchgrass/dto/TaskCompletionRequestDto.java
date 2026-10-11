package com.touchgrass.dto;

import jakarta.validation.constraints.Min;

public class TaskCompletionRequestDto {

    @Min(value = 0, message = "Actual duration minutes cannot be negative")
    private Integer actualDurationMinutes;

    public TaskCompletionRequestDto() {
    }

    public TaskCompletionRequestDto(Integer actualDurationMinutes) {
        this.actualDurationMinutes = actualDurationMinutes;
    }

    public Integer getActualDurationMinutes() {
        return actualDurationMinutes;
    }

    public void setActualDurationMinutes(Integer actualDurationMinutes) {
        this.actualDurationMinutes = actualDurationMinutes;
    }
}
