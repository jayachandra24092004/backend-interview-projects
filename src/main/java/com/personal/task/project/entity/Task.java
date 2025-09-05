package com.personal.task.project.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Task {

    public enum SyncStatus {
        PENDING, SYNCED, ERROR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", unique = true, nullable = false, updatable = false)
    private UUID clientId = UUID.randomUUID();

    @NotBlank(message = "Title is required")
    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 20)
    private SyncStatus syncStatus = SyncStatus.PENDING;

    @Column(name = "server_id")
    private String serverId;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (syncStatus == SyncStatus.SYNCED) {
            syncStatus = SyncStatus.PENDING;
        }
    }
    public Long getId() {
    	return id;
    	}
    public UUID getClientId() {
    	return clientId; 
    	}
    public String getTitle() { 
    	return title; 
    	}
    public void setTitle(String title) { 
    	this.title = title;
    	}
    public String getDescription() {
    	return description;
    	}
    public void setDescription(String description) {
    	this.description = description;
    	}
    public boolean isCompleted() {
    	return completed; 
    	}
    public void setCompleted(boolean completed) {
    	this.completed = completed;
    	}
    public LocalDateTime getCreatedAt() {
    	return createdAt;
    	}
    public LocalDateTime getUpdatedAt() { 
    	return updatedAt; 
    	}
    public boolean isDeleted() {
    	return isDeleted; 
    	}
    public void setDeleted(boolean deleted) { 
    	isDeleted = deleted;
    	}
    public SyncStatus getSyncStatus() {
    	return syncStatus;
    	}
    public void setSyncStatus(SyncStatus syncStatus) { 
    	this.syncStatus = syncStatus; 
    	}
    public String getServerId() {
    	return serverId; 
    	}
    public void setServerId(String serverId) {
    	this.serverId = serverId;
    	}
    public LocalDateTime getLastSyncedAt() { 
    	return lastSyncedAt; 
    	}
    public void setLastSyncedAt(LocalDateTime lastSyncedAt) {
    	this.lastSyncedAt = lastSyncedAt;
    	}
}

