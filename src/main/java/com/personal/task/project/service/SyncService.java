package com.personal.task.project.service;

import com.personal.task.project.entity.SyncOps;
import com.personal.task.project.entity.Task;
import com.personal.task.project.repository.SyncRepo;
import com.personal.task.project.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class SyncService {

    @Autowired
    private SyncRepo syncRepo;
    
    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Value("${app.sync.batch-size:50}")
    private int batchSize;
    
    @Value("${app.sync.max-retries:3}")
    private int maxRetries;
    
    @Value("${app.sync.server-url:http://localhost:8081/api}")
    private String serverUrl;

    @Transactional
    public void syncWithServer() {
        System.out.println("Starting sync process...");

        List<SyncOps> pendingOps = syncRepo.findByStatus(SyncOps.Status.PENDING);
        
        if (pendingOps.isEmpty()) {
            System.out.println("No pending sync operations");
            return;
        }
        
        System.out.println("Found " + pendingOps.size() + " pending operations");

        for (int i = 0; i < pendingOps.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, pendingOps.size());
            List<SyncOps> batch = pendingOps.subList(i, endIndex);
            processBatch(batch);
        }
    }

    private void processBatch(List<SyncOps> batch) {
        System.out.println("Processing batch of " + batch.size() + " operations");
        
        for (SyncOps syncOp : batch) {
            try {
                boolean success = processSingleOperation(syncOp);
                
                if (success) {
                    syncOp.setStatus(SyncOps.Status.SUCCESS);
                    updateTaskSyncStatus(syncOp.getTaskId(), Task.SyncStatus.SYNCED);
                    System.out.println("Successfully done: " + syncOp.getId());
                } else {
                    handleSyncFailure(syncOp);
                }
                
            } catch (Exception e) {
                System.err.println("Error occured " + syncOp.getId() + ": " + e.getMessage());
                handleSyncFailure(syncOp);
            }
            
            syncRepo.save(syncOp);
        }
    }

    private boolean processSingleOperation(SyncOps syncOp) throws Exception {
        String endpoint = serverUrl + "/tasks";
        
        switch (syncOp.getOperationType()) {
            case CREATE:
                return syncCreate(syncOp, endpoint);
            case UPDATE:
                return syncUpdate(syncOp, endpoint);
            case DELETE:
                return syncDelete(syncOp, endpoint);
            default:
                System.err.println("Error operation type: " + syncOp.getOperationType());
                return false;
        }
    }

    private boolean syncCreate(SyncOps syncOp, String endpoint) throws Exception {
        Task task = objectMapper.readValue(syncOp.getTaskData(), Task.class);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Task> request = new HttpEntity<>(task, headers);
        
        try {
            ResponseEntity<Task> response = restTemplate.postForEntity(endpoint, request, Task.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Optional<Task> localTask = taskRepository.findById(syncOp.getTaskId());
                if (localTask.isPresent()) {
                    Task updatedTask = localTask.get();
                    updatedTask.setServerId(response.getBody().getId().toString());
                    updatedTask.setLastSyncedAt(LocalDateTime.now());
                    taskRepository.save(updatedTask);
                }
                return true;
            }
        } catch (Exception e) {
            System.err.println("Failed to create task on server: " + e.getMessage());
        }
        
        return false;
    }

    private boolean syncUpdate(SyncOps syncOp, String endpoint) throws Exception {
        Task localTask = objectMapper.readValue(syncOp.getTaskData(), Task.class);
        
        // Get current local task for conflict detection
        Optional<Task> currentLocalTask = taskRepository.findById(syncOp.getTaskId());
        if (!currentLocalTask.isPresent()) return false;
        
        String taskId = currentLocalTask.get().getServerId() != null ? 
                        currentLocalTask.get().getServerId() : 
                        currentLocalTask.get().getId().toString();
        
        try {
            ResponseEntity<Task> getResponse = restTemplate.getForEntity(endpoint + "/" + taskId, Task.class);
            
            if (getResponse.getStatusCode().is2xxSuccessful() && getResponse.getBody() != null) {
                Task serverTask = getResponse.getBody();
                
                // Check for conflict - EXACTLY 2 parameters
                if (hasConflict(currentLocalTask.get(), serverTask)) {
                    // Log the conflict - EXACTLY 3 parameters
                    logConflict(currentLocalTask.get(), serverTask, "UPDATE");
                }
            }
        } catch (Exception e) {
            System.out.println("Could not fetch server version for conflict detection: " + e.getMessage());
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Task> request = new HttpEntity<>(localTask, headers);
        
        try {
            ResponseEntity<Task> response = restTemplate.exchange(
                endpoint + "/" + taskId, 
                HttpMethod.PUT, 
                request, 
                Task.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Task updatedTask = currentLocalTask.get();
                updatedTask.setLastSyncedAt(LocalDateTime.now());
                taskRepository.save(updatedTask);
                return true;
            }
        } catch (Exception e) {
            System.err.println("Failed to update task on server: " + e.getMessage());
        }
        
        return false;
    }

    private boolean syncDelete(SyncOps syncOp, String endpoint) throws Exception {
        Optional<Task> localTask = taskRepository.findById(syncOp.getTaskId());
        if (!localTask.isPresent()) return true; // Already deleted, consider success
        
        String taskId = localTask.get().getServerId() != null ? 
                        localTask.get().getServerId() : 
                        localTask.get().getId().toString();
        
        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                endpoint + "/" + taskId,
                HttpMethod.DELETE,
                null,
                Void.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                // Update last synced timestamp
                Task updatedTask = localTask.get();
                updatedTask.setLastSyncedAt(LocalDateTime.now());
                taskRepository.save(updatedTask);
                return true;
            }
        } catch (Exception e) {
            System.err.println("Failed to delete task on server: " + e.getMessage());
        }
        
        return false;
    }

    private void handleSyncFailure(SyncOps syncOp) {
        syncOp.setRetryCount(syncOp.getRetryCount() + 1);
        
        if (syncOp.getRetryCount() >= maxRetries) {
            syncOp.setStatus(SyncOps.Status.FAILED);
            updateTaskSyncStatus(syncOp.getTaskId(), Task.SyncStatus.ERROR);
            System.err.println("Max retries reached for sync operation: " + syncOp.getId());
        } else {
            System.out.println("Will retry sync operation: " + syncOp.getId() + " (attempt " + (syncOp.getRetryCount() + 1) + ")");
        }
    }

    private void updateTaskSyncStatus(Long taskId, Task.SyncStatus status) {
        Optional<Task> task = taskRepository.findById(taskId);
        if (task.isPresent()) {
            Task updatedTask = task.get();
            updatedTask.setSyncStatus(status);
            if (status == Task.SyncStatus.SYNCED) {
                updatedTask.setLastSyncedAt(LocalDateTime.now());
            }
            taskRepository.save(updatedTask);
        }
    }

    @Scheduled(fixedDelay = 30000) 
    public void scheduledSync() {
        if (isOnline()) {
            syncWithServer();
        }
    }

    private boolean isOnline() {
        try {
            restTemplate.getForEntity(serverUrl + "/health", String.class);
            return true;
        } catch (Exception e) {
            System.out.println("Server not reachable, skipping sync");
            return false;
        }
    }

    private boolean hasConflict(Task localTask, Task serverTask) {
        if (localTask.getLastSyncedAt() == null) {
            return false;
        }

        if (serverTask.getUpdatedAt().isAfter(localTask.getLastSyncedAt())) {
            return true;
        }
        
        return false;
    }

    private void logConflict(Task localTask, Task serverTask, String operation) {
        System.out.println(" Conflict Occured");
        System.out.println("Operation: " + operation);
        System.out.println("Task ID: " + localTask.getId());
        System.out.println("Local last updated: " + localTask.getUpdatedAt());
        System.out.println("Local last synced: " + localTask.getLastSyncedAt());
        System.out.println("Server last updated: " + serverTask.getUpdatedAt());
        System.out.println("Resolution: Using last-write-wins (local version wins)");
        System.out.println("Local title: '" + localTask.getTitle() + "'");
        System.out.println("Server title: '" + serverTask.getTitle() + "'");
        System.out.println("----------------------------------------");
        
    }


    public String getSyncStatus() {
        List<SyncOps> pending = syncRepo.findByStatus(SyncOps.Status.PENDING);
        List<SyncOps> failed = syncRepo.findByStatus(SyncOps.Status.FAILED);
        
        long conflictCount = 0;
        
        return String.format("Sync Status - Pending: %d, Failed: %d, Conflicts Detected: %d", 
                           pending.size(), failed.size(), conflictCount);
    }
}