package com.personal.task.project.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sync_operations")
public class SyncOps {

    public enum OperationType {
        CREATE, UPDATE, DELETE
    }

    public enum Status {
        PENDING, SUCCESS, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OperationType operationType;

    @Column(nullable = false)
    private Long taskId;


    @Lob
    @Column(columnDefinition = "TEXT")
    private String taskData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
    	return id; 
    	}
    public OperationType getOperationType() {
    	return operationType; 
    	}
    public void setOperationType(OperationType operationType) { 
    	this.operationType = operationType;
    	}
    public Long getTaskId() {
    	return taskId;
    	}
    public void setTaskId(Long taskId) { 
    	this.taskId = taskId; 
    	}
    public String getTaskData() { 
    	return taskData;
    	}
    public void setTaskData(String taskData) {
    	this.taskData = taskData;
    	}
    public Status getStatus() {
    	return status; 
    	}
    public void setStatus(Status status) {
    	this.status = status; 
    	}
    public int getRetryCount() { 
    	return retryCount; 
    	}
    public void setRetryCount(int retryCount) {
    	this.retryCount = retryCount; 
    	}
    public LocalDateTime getCreatedAt() {
    	return createdAt;
    	}
}

