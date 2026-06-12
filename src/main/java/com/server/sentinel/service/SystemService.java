package com.server.sentinel.service;

import org.springframework.stereotype.Service;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

@Service
public class SystemService {
    private final OperatingSystemMXBean osBean =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    
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
}