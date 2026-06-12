package com.server.sentinel.controller;

import com.github.dockerjava.api.model.Container;
import com.server.sentinel.service.AutoHealService;
import com.server.sentinel.service.DockerService;
import com.server.sentinel.service.SystemService;
import com.server.sentinel.service.SystemStatsHistoryService;
import com.server.sentinel.service.SettingsService;
import com.server.sentinel.service.DiscordBotService;
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
    private final SystemStatsHistoryService statsHistoryService;
    private final SettingsService settingsService;
    private final DiscordBotService discordBotService;
    
    public DashboardController(
            DockerService dockerService, 
            SystemService systemService, 
            AutoHealService autoHealService,
            SystemStatsHistoryService statsHistoryService,
            SettingsService settingsService,
            DiscordBotService discordBotService) {
        this.dockerService = dockerService;
        this.systemService = systemService;
        this.autoHealService = autoHealService;
        this.statsHistoryService = statsHistoryService;
        this.settingsService = settingsService;
        this.discordBotService = discordBotService;
    }
    
    @GetMapping("/containers")
    public List<Container> getContainers() {
        return dockerService.getAllContainers();
    }
    
    @GetMapping("/system/stats")
    public Map<String, Object> getSystemStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("cpuLoad", systemService.getCpuLoad());
        stats.put("freeMemoryMB", systemService.getFreeMemoryMB());
        stats.put("totalMemoryMB", systemService.getTotalMemoryMB());
        stats.put("diskTotalGB", systemService.getDiskTotalGB());
        stats.put("diskUsedGB", systemService.getDiskUsedGB());
        stats.put("diskUsagePercent", systemService.getDiskUsagePercent());
        stats.put("rxSpeedKBps", systemService.getRxSpeedKBps());
        stats.put("txSpeedKBps", systemService.getTxSpeedKBps());
        stats.put("gpuAvailable", systemService.isGpuAvailable());
        stats.put("gpuName", systemService.getGpuName());
        stats.put("gpuLoad", systemService.getGpuLoad());
        stats.put("gpuMemoryTotalMB", systemService.getGpuMemoryTotalMB());
        stats.put("gpuMemoryUsedMB", systemService.getGpuMemoryUsedMB());
        stats.put("gpuMemoryUsagePercent", systemService.getGpuMemoryUsagePercent());
        return stats;
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
    
    @GetMapping("/containers/{id}/logs")
    public Map<String, String> getContainerLogs(
            @PathVariable String id, 
            @RequestParam(defaultValue = "100") int lines) {
        String logs = dockerService.getContainerLogs(id, lines);
        return Map.of("logs", logs);
    }
    
    @GetMapping("/system/history")
    public List<Map<String, Object>> getSystemHistory() {
        return statsHistoryService.getHistory();
    }
    
    @GetMapping("/settings")
    public Map<String, Object> getSettings() {
        return settingsService.getSettings();
    }
    
    @PostMapping("/settings")
    public Map<String, Object> updateSettings(@RequestBody Map<String, Object> payload) {
        try {
            settingsService.updateSettings(payload);
            discordBotService.restartBot(); // Tự động khởi động lại bot khi cấu hình thay đổi
            return Map.of("status", "success", "settings", settingsService.getSettings());
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
}