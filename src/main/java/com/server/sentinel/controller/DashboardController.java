package com.server.sentinel.controller;

import com.github.dockerjava.api.model.Container;
import com.server.sentinel.service.AutoHealService;
import com.server.sentinel.service.DockerService;
import com.server.sentinel.service.SystemService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Cho phép Svelte gọi API từ server khác
public class DashboardController {
    
    private final DockerService dockerService;
    private final SystemService systemService;
    private final AutoHealService autoHealService;
    
    public DashboardController(DockerService dockerService, SystemService systemService, AutoHealService autoHealService) {
        this.dockerService = dockerService;
        this.systemService = systemService;
        this.autoHealService = autoHealService;
    }
    
    @GetMapping("/containers")
    public List<Container> getContainers() {
        return dockerService.getAllContainers();
    }
    
    @GetMapping("/system/stats")
    public Map<String, Object> getSystemStats() {
        return Map.of(
            "cpuLoad", systemService.getCpuLoad(),
            "freeMemoryMB", systemService.getFreeMemoryMB(),
            "totalMemoryMB", systemService.getTotalMemoryMB()
        );
    }
    
    @PostMapping("/containers/{id}/start")
    public Map<String, String> startContainer(@PathVariable String id) {
        try {
            dockerService.startContainer(id);
            return Map.of("status", "success", "message", "Container started successfully");
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
    
    @PostMapping("/containers/{id}/stop")
    public Map<String, String> stopContainer(@PathVariable String id) {
        try {
            dockerService.stopContainer(id);
            return Map.of("status", "success", "message", "Container stopped successfully");
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
    
    @PostMapping("/containers/{id}/restart")
    public Map<String, String> restartContainer(@PathVariable String id) {
        try {
            dockerService.restartContainer(id);
            return Map.of("status", "success", "message", "Container restarted successfully");
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
    
    @GetMapping("/auto-heal/whitelist")
    public List<String> getWhitelist() {
        return autoHealService.getWhitelist();
    }
    
    @PostMapping("/auto-heal/whitelist/toggle")
    public Map<String, Object> toggleWhitelist(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        if (name == null || name.trim().isEmpty()) {
            return Map.of("status", "error", "message", "Container name is required");
        }
        autoHealService.toggleWhitelist(name);
        return Map.of(
            "status", "success",
            "whitelist", autoHealService.getWhitelist(),
            "isAllowed", autoHealService.isAllowed(name)
        );
    }
}