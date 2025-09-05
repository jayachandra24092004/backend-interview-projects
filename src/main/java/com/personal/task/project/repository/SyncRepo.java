package com.personal.task.project.repository;

import com.personal.task.project.entity.SyncOps;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyncRepo extends JpaRepository<SyncOps, Long> {
    @Query("SELECT s FROM SyncOps s WHERE s.status = :status ORDER BY s.createdAt ASC")
    List<SyncOps> findByStatusOrderByCreatedAtAsc(@Param("status") SyncOps.Status status);

    List<SyncOps> findByStatus(SyncOps.Status status);

    @Query("SELECT s FROM SyncOps s WHERE s.taskId = :taskId ORDER BY s.createdAt ASC")
    List<SyncOps> findByTaskIdOrderByCreatedAtAsc(@Param("taskId") Long taskId);
}