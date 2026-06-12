package com.server.sentinel.scheduler;

import com.github.dockerjava.api.model.Container;
import com.server.sentinel.service.DiscordService;
import com.server.sentinel.service.DockerService;
import com.server.sentinel.service.SystemService;
import com.server.sentinel.service.AutoHealService;
import com.server.sentinel.service.SystemStatsHistoryService;
import com.server.sentinel.service.SettingsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class MonitoringTask {
    
    private final SystemService systemService;
    private final DiscordService discordService;
    private final DockerService dockerService;
    private final AutoHealService autoHealService;
    private final SystemStatsHistoryService historyService;
    private final SettingsService settingsService;

    // Quan ly trang thai de tranh spam thong bao
    private final Set<String> alertedContainers = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> runningContainersStartTimes = new ConcurrentHashMap<>();
    private long lastCpuAlertTime = 0;
    private long lastRamAlertTime = 0;
    private static final long COOLDOWN_MS = 10 * 60 * 1000; // Cooldown 10 phut cho canh bao he thong
    
    public MonitoringTask(
            SystemService systemService,
            DiscordService discordService,
            DockerService dockerService,
            AutoHealService autoHealService,
            SystemStatsHistoryService historyService,
            SettingsService settingsService) {
        this.systemService = systemService;
        this.discordService = discordService;
        this.dockerService = dockerService;
        this.autoHealService = autoHealService;
        this.historyService = historyService;
        this.settingsService = settingsService;

        // Khoi tao danh sach container da sap san tu truoc khi Sentinel chay de tranh spam
        try {
            List<Container> allContainers = dockerService.getAllContainers();
            for (Container c : allContainers) {
                String state = c.getState();
                if (state != null && state.equalsIgnoreCase("exited")) {
                    alertedContainers.add(c.getId());
                }
            }
        } catch (Exception e) {
            System.err.println("Khong the khoi tao danh sach container da sap: " + e.getMessage());
        }
    }
    
    @Scheduled(fixedRate = 30000) // Chay moi 30 giay
    public void checkSystemHealth() {
        // Cap nhat cac thong so mang va GPU dang dinh ky tranh nghen luong API
        systemService.updateMetrics();

        double cpuLoad = systemService.getCpuLoad();
        long freeMemory = systemService.getFreeMemoryMB();
        long totalMemory = systemService.getTotalMemoryMB();
        double ramUsagePercent = totalMemory > 0
                ? ((double) (totalMemory - freeMemory) / totalMemory) * 100
                : 0.0;
                
        double diskUsagePercent = systemService.getDiskUsagePercent();
        double rxSpeed = systemService.getRxSpeedKBps();
        double txSpeed = systemService.getTxSpeedKBps();
        boolean gpuAvailable = systemService.isGpuAvailable();
        double gpuLoad = systemService.getGpuLoad();
        double gpuMemoryUsagePercent = systemService.getGpuMemoryUsagePercent();

        historyService.addRecord(
            cpuLoad,
            ramUsagePercent,
            diskUsagePercent,
            rxSpeed,
            txSpeed,
            gpuAvailable,
            gpuLoad,
            gpuMemoryUsagePercent
        );
        
        System.out.println("Kiem tra he thong: CPU = " + String.format("%.2f", cpuLoad)
                + "%, RAM trong = " + freeMemory + " MB, Disk = " + String.format("%.1f", diskUsagePercent) + "%");
        
        // Canh bao CPU (cooldown 10 phut)
        double cpuLimit = settingsService.getCpuThreshold();
        if (cpuLoad > cpuLimit) {
            long now = System.currentTimeMillis();
            if (now - lastCpuAlertTime > COOLDOWN_MS) {
                discordService.sendAlert("[WARNING] CPU dang hoat dong o muc " + String.format("%.2f", cpuLoad) + "% tren server (Vuot nguong " + String.format("%.1f", cpuLimit) + "%).");
                lastCpuAlertTime = now;
            }
        } else {
            lastCpuAlertTime = 0; // Reset khi tro lai binh thuong
        }
        
        // Canh bao RAM (cooldown 10 phut)
        long ramLimit = settingsService.getRamThresholdMB();
        if (freeMemory < ramLimit) {
            long now = System.currentTimeMillis();
            if (now - lastRamAlertTime > COOLDOWN_MS) {
                discordService.sendAlert("[WARNING] RAM trong dang o muc thap (" + freeMemory + " MB) tren server (Duoi nguong " + ramLimit + " MB).");
                lastRamAlertTime = now;
            }
        } else {
            lastRamAlertTime = 0; // Reset khi tro lai binh thuong
        }
        
        // Kiem tra va khoi phuc Docker Container (tranh spam)
        try {
            List<Container> allContainers = dockerService.getAllContainers();
            
            // Cap nhat trang thai cac container dang chay va ngan ngua spam canh bao tu container khong thuoc Auto-Heal
            Set<String> currentRunningIds = new java.util.HashSet<>();
            for (Container c : allContainers) {
                String state = c.getState();
                if (state != null && state.equalsIgnoreCase("running")) {
                    String containerId = c.getId();
                    currentRunningIds.add(containerId);
                    
                    // Ghi nhan thoi diem bat dau chay
                    runningContainersStartTimes.putIfAbsent(containerId, System.currentTimeMillis());
                    
                    long runningTime = System.currentTimeMillis() - runningContainersStartTimes.get(containerId);
                    String containerName = c.getNames()[0].replace("/", "");
                    
                    // Neu container nam trong danh sach Auto-Heal, reset canh bao ngay de san sang cuu tiep neu sap lai
                    // Neu khong trong Auto-Heal, chi reset khi da chay on dinh lien tuc tren 5 phut (tranh spam crash loop)
                    if (autoHealService.isAllowed(containerName) || runningTime > 5 * 60 * 1000) {
                        alertedContainers.remove(containerId);
                    }
                }
            }
            // Don dep thong tin thoi gian cua cac container da tat
            runningContainersStartTimes.keySet().removeIf(id -> !currentRunningIds.contains(id));

            // Lay danh sach cac container bi sap (exited)
            List<Container> crashedContainers = allContainers.stream()
                    .filter(c -> {
                        String state = c.getState();
                        return state != null && state.equalsIgnoreCase("exited");
                    })
                    .collect(Collectors.toList());

            if (!crashedContainers.isEmpty()) {
                StringBuilder alertMsg = new StringBuilder();
                boolean hasNewAlert = false;
                
                for (Container c : crashedContainers) {
                    String containerId = c.getId();
                    
                    // Neu container nay da duoc canh bao va van o trang thai sap, bo qua khong gui lai
                    if (alertedContainers.contains(containerId)) {
                        continue;
                    }
                    
                    String containerName = c.getNames()[0].replace("/", "");
                    
                    if (!hasNewAlert) {
                        alertMsg.append("[ALERT] Phat hien container bi sap:\n");
                        hasNewAlert = true;
                    }
                    
                    alertMsg.append("- Ten: ").append(containerName).append("\n");
                    
                    // Kiem tra xem container co nam trong danh sach duoc phep cuu khong
                    if (autoHealService.isAllowed(containerName)) {
                        try {
                            dockerService.startContainer(containerId);
                            alertMsg.append("  -> [SUCCESS] Da tu dong cuu song!\n");
                            // Khong dua vao alertedContainers de neu sap tiep o chu ky sau van se canh bao & tu dong cuu tiep
                        } catch (Exception e) {
                            alertMsg.append("  -> [FAILED] Khong the khoi dong lai: ").append(e.getMessage()).append("\n");
                            alertedContainers.add(containerId); // Luu de tranh lap lai hanh dong loi lien tuc
                        }
                    } else {
                        alertMsg.append("  -> [SKIPPED] Container khong nam trong danh sach Auto-Heal.\n");
                        alertedContainers.add(containerId); // Luu lai de tranh spam thong bao cho container tat chu dong
                    }
                }
                
                if (hasNewAlert) {
                    discordService.sendAlert(alertMsg.toString());
                }
            }
        } catch (Exception e) {
            System.err.println("Loi khi kiem tra Docker: " + e.getMessage());
        }
    }
}