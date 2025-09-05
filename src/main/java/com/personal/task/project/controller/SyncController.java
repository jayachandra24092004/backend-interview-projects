package com.personal.task.project.controller;

import com.personal.task.project.service.SyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    @Autowired
    private SyncService syncService;

    @PostMapping("/trigger")
    public ResponseEntity<Map<String, String>> triggerSync() {
        Map<String, String> response = new HashMap<>();
        
        try {
            syncService.syncWithServer();
            response.put("status", "success");
            response.put("message", "Sync process completed");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Sync failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String status = syncService.getSyncStatus();
            response.put("status", "success");
            response.put("syncStatus", status);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to get sync status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "sync");
        return ResponseEntity.ok(response);
    }
}