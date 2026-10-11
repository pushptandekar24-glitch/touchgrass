package com.wildstep.service;

import org.springframework.stereotype.Service;

@Service
public class GrassPointsService {

    public static final int BASE_POINTS = 50;
    public static final int POINTS_PER_TASK = 10;
    public static final int POINTS_PER_FIVE_MINUTES = 5;
    public static final int MAX_POINTS = 150;

    /**
     * Calculates Grass Points according to deterministic rules:
     * Points = min(150, 50 + (completedTasks * 10) + floor(actualDurationMinutes / 5) * 5)
     *
     * @param completedTasks        number of checked-off tasks
     * @param actualDurationMinutes actual verified/elapsed minutes outdoors (not estimated duration)
     * @return points bounded between 0 and 150
     */
    public int calculatePoints(int completedTasks, int actualDurationMinutes) {
        if (completedTasks <= 0) {
            return 0;
        }

        int taskPoints = completedTasks * POINTS_PER_TASK;
        int durationIntervals = Math.max(0, actualDurationMinutes) / 5;
        int durationPoints = durationIntervals * POINTS_PER_FIVE_MINUTES;

        int total = BASE_POINTS + taskPoints + durationPoints;
        return Math.min(MAX_POINTS, Math.max(0, total));
    }
}
