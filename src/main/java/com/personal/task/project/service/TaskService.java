package com.personal.task.project.service;

import com.personal.task.project.entity.SyncOps;
import com.personal.task.project.entity.Task;
import com.personal.task.project.repository.SyncRepo;
import com.personal.task.project.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TaskService {

    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private SyncRepo syncRepo;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Transactional
    public Task saveTask(Task task) {
        boolean isNew = task.getId() == null;
        Task savedTask = taskRepository.save(task);

        SyncOps.OperationType operationType = isNew ? SyncOps.OperationType.CREATE : SyncOps.OperationType.UPDATE;
        queueSyncOperation(savedTask, operationType);
        
        return savedTask;
    }

    public List<Task> getAllTasks() {
        return taskRepository.findByIsDeletedFalse();
    }

    public Optional<Task> getTaskById(Long id) {
        return taskRepository.findById(id).filter(task -> !task.isDeleted());
    }

    public Task updateTask(Long id, Task taskDetails) {
        Optional<Task> optionalTask = taskRepository.findById(id);
        if (optionalTask.isPresent()) {
            Task existingTask = optionalTask.get();
            
            // Check if task is deleted
            if (existingTask.isDeleted()) {
                return null; // Can't update deleted task
            }
            
            // Update the fields
            existingTask.setTitle(taskDetails.getTitle());
            existingTask.setDescription(taskDetails.getDescription());
            existingTask.setCompleted(taskDetails.isCompleted());
            
            // Save and queue sync operation
            Task savedTask = taskRepository.save(existingTask);
            queueSyncOperation(savedTask, SyncOps.OperationType.UPDATE);
            
            return savedTask;
        }
        return null; 
    }

    @Transactional
    public boolean deleteTask(Long id) {//del karo
        Optional<Task> optionalTask = taskRepository.findById(id);
        if (optionalTask.isPresent()) {
            Task task = optionalTask.get();
            task.setDeleted(true);
            Task deletedTask = taskRepository.save(task);

            queueSyncOperation(deletedTask, SyncOps.OperationType.DELETE);
            
            return true;
        }
        return false;
    }
    
    private void queueSyncOperation(Task task, SyncOps.OperationType operationType) {
        try {
            SyncOps syncOp = new SyncOps();
            syncOp.setOperationType(operationType);
            syncOp.setTaskId(task.getId());
            
            // jfs
            String taskData = objectMapper.writeValueAsString(task);
            syncOp.setTaskData(taskData);
            
            // tap
            task.setSyncStatus(Task.SyncStatus.PENDING);
            taskRepository.save(task);

            syncRepo.save(syncOp);
            
        } catch (Exception e) {
            System.err.println("Failed to queue sync operation: " + e.getMessage());
            task.setSyncStatus(Task.SyncStatus.ERROR);
            taskRepository.save(task);
        }
    }
}

