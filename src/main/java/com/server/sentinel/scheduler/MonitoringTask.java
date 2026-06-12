package com.server.sentinel.scheduler;

import com.github.dockerjava.api.model.Container;
import com.server.sentinel.service.DiscordService;
import com.server.sentinel.service.DockerService;
import com.server.sentinel.service.SystemService;
import com.server.sentinel.service.AutoHealService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MonitoringTask {
    
    private final SystemService systemService;
    private final DiscordService discordService;
    private final DockerService dockerService;
    private final AutoHealService autoHealService;
    
    public MonitoringTask(
            SystemService systemService,
            DiscordService discordService,
            DockerService dockerService,
            AutoHealService autoHealService) {
        this.systemService = systemService;
        this.discordService = discordService;
        this.dockerService = dockerService;
        this.autoHealService = autoHealService;
    }
    
    @Scheduled(fixedRate = 60000)
    public void checkSystemHealth() {
        double cpuLoad = systemService.getCpuLoad();
        long freeMemory = systemService.getFreeMemoryMB();
        
        System.out.println("Kiem tra he thong: CPU = " + String.format("%.2f", cpuLoad) + "%, RAM trong = " + freeMemory + " MB");
        
        if (cpuLoad > 90.0) {
            discordService.sendAlert("[CANH BAO] CPU dang hoat dong o muc " + String.format("%.2f", cpuLoad) + "% tren server.");
        }
        
        if (freeMemory < 500) {
            discordService.sendAlert("[CANH BAO] RAM trong dang o muc thap (" + freeMemory + " MB) tren server.");
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