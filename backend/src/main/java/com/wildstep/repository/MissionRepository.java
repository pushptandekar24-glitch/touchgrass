package com.wildstep.repository;

import com.wildstep.domain.Mission;
import com.wildstep.domain.enums.MissionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MissionRepository extends JpaRepository<Mission, Long> {
    List<Mission> findByUserId(Long userId);
    List<Mission> findByUserIdAndStatus(Long userId, MissionStatus status);

    @Query("SELECT m FROM Mission m LEFT JOIN FETCH m.tasks WHERE m.id = :id")
    Optional<Mission> findByIdWithTasks(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Mission m WHERE m.id = :id")
    Optional<Mission> findByIdWithLock(@Param("id") Long id);
}
