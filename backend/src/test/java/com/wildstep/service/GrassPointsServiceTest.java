package com.wildstep.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GrassPointsServiceTest {

    private GrassPointsService grassPointsService;

    @BeforeEach
    void setUp() {
        grassPointsService = new GrassPointsService();
    }

    @Test
    @DisplayName("Should return 0 points when 0 tasks are completed")
    void calculatePoints_ZeroTasks_ReturnsZero() {
        int points = grassPointsService.calculatePoints(0, 30);
        assertEquals(0, points);
    }

    @Test
    @DisplayName("Should return 0 points when negative tasks provided")
    void calculatePoints_NegativeTasks_ReturnsZero() {
        int points = grassPointsService.calculatePoints(-1, 30);
        assertEquals(0, points);
    }

    @ParameterizedTest(name = "tasks={0}, duration={1}min -> expected {2} points")
    @CsvSource({
            "1, 0, 60",    // 50 + 10 + 0 = 60
            "1, 4, 60",    // 50 + 10 + floor(4/5)*5 = 60
            "1, 5, 65",    // 50 + 10 + 5 = 65
            "2, 0, 70",    // 50 + 20 + 0 = 70
            "2, 9, 75",    // 50 + 20 + 5 = 75
            "2, 10, 80",   // 50 + 20 + 10 = 80
            "3, 20, 100",  // 50 + 30 + 20 = 100
            "3, 25, 105",  // 50 + 30 + 25 = 105
            "4, 43, 130",  // 50 + 40 + floor(43/5)*5 = 50 + 40 + 40 = 130
            "5, 45, 145",  // 50 + 50 + 45 = 145
            "5, 50, 150",  // 50 + 50 + 50 = 150 (cap reached)
            "5, 90, 150",  // 50 + 50 + 90 = 190 -> capped at 150
            "10, 120, 150" // Extreme values capped at 150
    })
    @DisplayName("Should calculate points matching formula: min(150, 50 + tasks*10 + floor(duration/5)*5)")
    void calculatePoints_FormulaCompliance(int tasks, int duration, int expectedPoints) {
        int points = grassPointsService.calculatePoints(tasks, duration);
        assertEquals(expectedPoints, points);
    }

    @Test
    @DisplayName("Should treat negative duration as 0 minutes duration bonus")
    void calculatePoints_NegativeDuration_TreatedAsZero() {
        int points = grassPointsService.calculatePoints(2, -15);
        // 50 + 20 + 0 = 70
        assertEquals(70, points);
    }
}
