package com.server.sentinel.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class SystemStatsHistoryService {
    
    private final int maxRecords = 60; // Lưu tối đa 60 bản ghi (30 phút)
    private final Queue<Map<String, Object>> history = new ConcurrentLinkedQueue<>();
    
    public void addRecord(
            double cpuLoad, 
            double ramUsagePercent, 
            double diskUsagePercent, 
            double rxSpeedKBps, 
            double txSpeedKBps,
            boolean gpuAvailable,
            double gpuLoad,
            double gpuMemoryUsagePercent) {
            
        Map<String, Object> record = new HashMap<>();
        record.put("timestamp", Instant.now().toString());
        record.put("cpuLoad", cpuLoad);
        record.put("ramUsagePercent", ramUsagePercent);
        record.put("diskUsagePercent", diskUsagePercent);
        record.put("rxSpeedKBps", rxSpeedKBps);
        record.put("txSpeedKBps", txSpeedKBps);
        record.put("gpuAvailable", gpuAvailable);
        record.put("gpuLoad", gpuLoad);
        record.put("gpuMemoryUsagePercent", gpuMemoryUsagePercent);
        
        history.add(record);
        
        // Giữ kích thước hàng đợi tối đa là maxRecords
        while (history.size() > maxRecords) {
            history.poll();
        }
    }
    
    public List<Map<String, Object>> getHistory() {
        return new ArrayList<>(history);
    }
}
