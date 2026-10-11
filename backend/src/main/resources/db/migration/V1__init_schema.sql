-- TouchGrass AI Initial Database Migration
-- Phase 1a Foundation Schema

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    total_points INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS missions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    available_minutes INT NOT NULL,
    mood VARCHAR(30) NOT NULL,
    goal VARCHAR(100) NOT NULL,
    environment_type VARCHAR(50) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    participants VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    points_awarded INT NOT NULL DEFAULT 0,
    bonus_task VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_missions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mission_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mission_id BIGINT NOT NULL,
    description VARCHAR(255) NOT NULL,
    estimated_minutes INT NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    task_order INT NOT NULL,
    CONSTRAINT fk_mission_tasks_mission FOREIGN KEY (mission_id) REFERENCES missions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
