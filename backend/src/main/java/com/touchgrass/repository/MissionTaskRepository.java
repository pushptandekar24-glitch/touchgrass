package com.touchgrass.repository;

import com.touchgrass.domain.MissionTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MissionTaskRepository extends JpaRepository<MissionTask, Long> {
    List<MissionTask> findByMissionIdOrderByTaskOrderAsc(Long missionId);
}
