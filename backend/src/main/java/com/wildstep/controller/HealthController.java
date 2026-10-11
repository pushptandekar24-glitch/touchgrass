package com.wildstep.controller;

import com.wildstep.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHealth() {
        Map<String, Object> healthInfo = Map.of(
                "status", "UP",
                "application", "WildStep",
                "phase", "Phase 1a Foundation"
        );
        return ResponseEntity.ok(ApiResponse.success("Backend service operational", healthInfo));
    }
}
