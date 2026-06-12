package com.server.sentinel.service;

import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class SystemService {
    private final OperatingSystemMXBean osBean =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            
    // Network cache
    private long lastNetRx = 0;
    private long lastNetTx = 0;
    private long lastNetTime = 0;
    private double rxSpeedKBps = 0.0;
    private double txSpeedKBps = 0.0;

    // GPU cache
    private String gpuName = "N/A";
    private double gpuLoad = 0.0;
    private double gpuMemoryUsagePercent = 0.0;
    private long gpuMemoryTotalMB = 0;
    private long gpuMemoryUsedMB = 0;
    private boolean gpuAvailable = false;

    public SystemService() {
        // Run initial metrics gather
        updateMetrics();
    }

    public synchronized void updateMetrics() {
        updateNetworkSpeed();
        checkGpuStats();
    }

    public double getCpuLoad() {
        double load = osBean.getCpuLoad();
        if (load < 0.0) {
            return 0.0;
        }
        return load * 100.0;
    }
    
    public long getFreeMemoryMB() {
        return osBean.getFreeMemorySize() / (1024 * 1024);
    }
    
    public long getTotalMemoryMB() {
        return osBean.getTotalMemorySize() / (1024 * 1024);
    }

    // Disk space
    public double getDiskTotalGB() {
        File file = new File(System.getProperty("user.dir"));
        return file.getTotalSpace() / (1024.0 * 1024.0 * 1024.0);
    }

    public double getDiskUsedGB() {
        File file = new File(System.getProperty("user.dir"));
        long total = file.getTotalSpace();
        long free = file.getFreeSpace();
        return (total - free) / (1024.0 * 1024.0 * 1024.0);
    }

    public double getDiskUsagePercent() {
        double total = getDiskTotalGB();
        if (total <= 0) return 0.0;
        return (getDiskUsedGB() / total) * 100.0;
    }

    // Network Speeds
    public synchronized double getRxSpeedKBps() {
        return rxSpeedKBps;
    }

    public synchronized double getTxSpeedKBps() {
        return txSpeedKBps;
    }

    private void updateNetworkSpeed() {
        try {
            Path path = Paths.get("/proc/net/dev");
            if (Files.exists(path)) {
                List<String> lines = Files.readAllLines(path);
                long currentRx = 0;
                long currentTx = 0;
                for (int i = 2; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.isEmpty() || line.startsWith("lo:")) continue;
                    
                    String[] parts = line.split(":\\s*");
                    if (parts.length < 2) continue;
                    
                    String[] metrics = parts[1].trim().split("\\s+");
                    if (metrics.length > 8) {
                        currentRx += Long.parseLong(metrics[0]);
                        currentTx += Long.parseLong(metrics[8]);
                    }
                }
                
                long now = System.currentTimeMillis();
                if (lastNetTime > 0 && now > lastNetTime) {
                    double seconds = (now - lastNetTime) / 1000.0;
                    rxSpeedKBps = ((currentRx - lastNetRx) / 1024.0) / seconds;
                    txSpeedKBps = ((currentTx - lastNetTx) / 1024.0) / seconds;
                    if (rxSpeedKBps < 0) rxSpeedKBps = 0;
                    if (txSpeedKBps < 0) txSpeedKBps = 0;
                }
                lastNetRx = currentRx;
                lastNetTx = currentTx;
                lastNetTime = now;
            }
        } catch (Exception e) {
            System.err.println("Loi khi doc network stats: " + e.getMessage());
        }
    }

    // GPU Statistics
    public synchronized boolean isGpuAvailable() {
        return gpuAvailable;
    }

    public synchronized String getGpuName() {
        return gpuName;
    }

    public synchronized double getGpuLoad() {
        return gpuLoad;
    }

    public synchronized double getGpuMemoryUsagePercent() {
        return gpuMemoryUsagePercent;
    }

    public synchronized long getGpuMemoryTotalMB() {
        return gpuMemoryTotalMB;
    }

    public synchronized long getGpuMemoryUsedMB() {
        return gpuMemoryUsedMB;
    }

    private void checkGpuStats() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c", "nvidia-smi --query-gpu=name,utilization.gpu,utilization.memory,memory.total,memory.used --format=csv,noheader,nounits");
            } else {
                pb = new ProcessBuilder("sh", "-c", "nvidia-smi --query-gpu=name,utilization.gpu,utilization.memory,memory.total,memory.used --format=csv,noheader,nounits");
            }
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    String[] parts = line.split(",\\s*");
                    if (parts.length >= 5) {
                        this.gpuName = parts[0].trim();
                        this.gpuLoad = Double.parseDouble(parts[1].trim());
                        this.gpuMemoryUsagePercent = Double.parseDouble(parts[2].trim());
                        this.gpuMemoryTotalMB = Long.parseLong(parts[3].trim());
                        this.gpuMemoryUsedMB = Long.parseLong(parts[4].trim());
                        this.gpuAvailable = true;
                        return;
                    }
                }
            }

            // Fallback for Windows local development (check if basic VideoController exists)
            if (os.contains("win")) {
                ProcessBuilder winPb = new ProcessBuilder("cmd.exe", "/c", "wmic path win32_VideoController get name");
                Process winProc = winPb.start();
                try (BufferedReader winReader = new BufferedReader(new InputStreamReader(winProc.getInputStream()))) {
                    String l;
                    while ((l = winReader.readLine()) != null) {
                        l = l.trim();
                        if (!l.isEmpty() && !l.equalsIgnoreCase("Name")) {
                            this.gpuName = l;
                            this.gpuLoad = 0.0;
                            this.gpuMemoryUsagePercent = 0.0;
                            this.gpuMemoryTotalMB = 0;
                            this.gpuMemoryUsedMB = 0;
                            this.gpuAvailable = true;
                            return;
                        }
                    }
                }
            }
            this.gpuAvailable = false;
        } catch (Exception e) {
            this.gpuAvailable = false;
        }
    }
}