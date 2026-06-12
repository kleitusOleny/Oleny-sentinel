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

@Component
public class MonitoringTask {
    
    private final SystemService systemService;
    private final DiscordService discordService;
    private final DockerService dockerService;
    private final AutoHealService autoHealService;
    private final SystemStatsHistoryService historyService;
    private final SettingsService settingsService;
    
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
    }
    
    @Scheduled(fixedRate = 30000) // Chạy mỗi 30 giây
    public void checkSystemHealth() {
        // Cập nhật các thông số mạng và GPU dạng định kỳ tránh nghẽn luồng API
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
        
        // Đẩy chỉ số hiệu năng mở rộng vào lịch sử
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
        
        double cpuLimit = settingsService.getCpuThreshold();
        if (cpuLoad > cpuLimit) {
            discordService.sendAlert("[CANH BAO] CPU dang hoat dong o muc " + String.format("%.2f", cpuLoad) + "% tren server (Vuot nguong " + String.format("%.1f", cpuLimit) + "%).");
        }
        
        long ramLimit = settingsService.getRamThresholdMB();
        if (freeMemory < ramLimit) {
            discordService.sendAlert("[CANH BAO] RAM trong dang o muc thap (" + freeMemory + " MB) tren server (Duoi nguong " + ramLimit + " MB).");
        }
        
        try {
            List<Container> crashedContainers = dockerService.getExitedContainers();
            if (!crashedContainers.isEmpty()) {
                StringBuilder alertMsg = new StringBuilder("[BAO DONG] Phat hien container bi sap:\n");
                
                for (Container c : crashedContainers) {
                    String containerName = c.getNames()[0].replace("/", "");
                    String containerId = c.getId();
                    alertMsg.append("- Ten: ").append(containerName).append("\n");
                    
                    // Kiem tra xem container co nam trong danh sach duoc phep cuu khong
                    if (autoHealService.isAllowed(containerName)) {
                        try {
                            dockerService.startContainer(containerId);
                            alertMsg.append("  -> [THANH CONG] Da tu dong cuu song!\n");
                        } catch (Exception e) {
                            alertMsg.append("  -> [THAT BAI] Khong the khoi dong lai: ").append(e.getMessage()).append("\n");
                        }
                    } else {
                        alertMsg.append("  -> [BO QUA] Container khong nam trong danh sach Auto-Heal.\n");
                    }
                }
                
                discordService.sendAlert(alertMsg.toString());
            }
        } catch (Exception e) {
            System.err.println("Loi khi kiem tra Docker: " + e.getMessage());
        }
    }
}