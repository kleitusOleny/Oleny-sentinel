package com.server.sentinel.scheduler;

import com.github.dockerjava.api.model.Container;
import com.server.sentinel.service.DiscordService;
import com.server.sentinel.service.DockerService;
import com.server.sentinel.service.SystemService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MonitoringTask {
    
    private final SystemService systemService;
    private final DiscordService discordService;
    private final DockerService dockerService;
    
    public MonitoringTask(SystemService systemService, DiscordService discordService, DockerService dockerService) {
        this.systemService = systemService;
        this.discordService = discordService;
        this.dockerService = dockerService;
    }
    
    // Chay moi 60 giay
    @Scheduled(fixedRate = 60000)
    public void checkSystemHealth() {
        // 1. Kiem tra CPU va RAM
        double cpuLoad = systemService.getCpuLoad();
        long freeMemory = systemService.getFreeMemoryMB();
        
        System.out.println("Kiem tra he thong: CPU = " + String.format("%.2f", cpuLoad) + "%, RAM trong = " + freeMemory + " MB");
        
        if (cpuLoad > 90.0) {
            discordService.sendAlert("[CANH BAO] CPU dang hoat dong o muc " + String.format("%.2f", cpuLoad) + "% tren server.");
        }
        
        if (freeMemory < 500) {
            discordService.sendAlert("[CANH BAO] RAM trong dang o muc thap (" + freeMemory + " MB) tren server.");
        }
        
        // 2. Kiem tra trang thai Container
        try {
            List<Container> crashedContainers = dockerService.getExitedContainers();
            if (!crashedContainers.isEmpty()) {
                StringBuilder alertMsg = new StringBuilder("[BAO DONG] Phat hien cac container sau dang bi dung hoat dong:\n");
                
                for (Container c : crashedContainers) {
                    // Docker API tra ve ten container co dau "/" o dau, can cat bo
                    String containerName = c.getNames()[0].replace("/", "");
                    alertMsg.append("- ").append(containerName).append("\n");
                }
                
                discordService.sendAlert(alertMsg.toString());
            }
        } catch (Exception e) {
            System.err.println("Loi khi kiem tra Docker: " + e.getMessage());
        }
    }
}