package com.server.sentinel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class SettingsService {

    private final String settingsPath;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private double cpuThreshold = 90.0;
    private long ramThresholdMB = 500;
    private String discordBotToken = "your-bot-token-here";
    private String discordChannelId = "";

    public SettingsService(@Value("${discord.webhook.url:}") String defaultDiscordUrl) {
        String os = System.getProperty("os.name").toLowerCase();
        this.settingsPath = os.contains("win") 
                ? "./config/settings.json" 
                : "/app/config/settings.json";
        
        loadSettings();
    }

    private synchronized void loadSettings() {
        File file = new File(settingsPath);
        if (file.exists()) {
            try {
                Map<String, Object> data = objectMapper.readValue(file, new TypeReference<Map<String, Object>>() {});
                if (data.containsKey("cpuThreshold")) {
                    this.cpuThreshold = ((Number) data.get("cpuThreshold")).doubleValue();
                }
                if (data.containsKey("ramThresholdMB")) {
                    this.ramThresholdMB = ((Number) data.get("ramThresholdMB")).longValue();
                }
                if (data.containsKey("discordBotToken")) {
                    this.discordBotToken = (String) data.get("discordBotToken");
                }
                if (data.containsKey("discordChannelId")) {
                    this.discordChannelId = (String) data.get("discordChannelId");
                }
                System.out.println("Da nap cau hinh settings tu file: " + settingsPath);
                return;
            } catch (IOException e) {
                System.err.println("Loi khi doc file cau hinh settings.json: " + e.getMessage());
            }
        }

        // Neu file chua ton tai, luu cac gia tri mac dinh vao file
        saveSettingsToFile();
    }

    private synchronized void saveSettingsToFile() {
        try {
            File file = new File(settingsPath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            Map<String, Object> data = new HashMap<>();
            data.put("cpuThreshold", this.cpuThreshold);
            data.put("ramThresholdMB", this.ramThresholdMB);
            data.put("discordBotToken", this.discordBotToken);
            data.put("discordChannelId", this.discordChannelId);
            
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
            System.out.println("Da luu cau hinh settings vao file: " + settingsPath);
        } catch (IOException e) {
            System.err.println("Loi khi ghi file settings.json: " + e.getMessage());
        }
    }

    public synchronized double getCpuThreshold() {
        return cpuThreshold;
    }

    public synchronized long getRamThresholdMB() {
        return ramThresholdMB;
    }

    public synchronized String getDiscordBotToken() {
        return discordBotToken;
    }

    public synchronized String getDiscordChannelId() {
        return discordChannelId;
    }

    public synchronized Map<String, Object> getSettings() {
        Map<String, Object> data = new HashMap<>();
        data.put("cpuThreshold", this.cpuThreshold);
        data.put("ramThresholdMB", this.ramThresholdMB);
        data.put("discordBotToken", this.discordBotToken);
        data.put("discordChannelId", this.discordChannelId);
        return data;
    }

    public synchronized void updateSettings(Map<String, Object> newSettings) {
        if (newSettings.containsKey("cpuThreshold")) {
            this.cpuThreshold = ((Number) newSettings.get("cpuThreshold")).doubleValue();
        }
        if (newSettings.containsKey("ramThresholdMB")) {
            this.ramThresholdMB = ((Number) newSettings.get("ramThresholdMB")).longValue();
        }
        if (newSettings.containsKey("discordBotToken")) {
            this.discordBotToken = (String) newSettings.get("discordBotToken");
        }
        if (newSettings.containsKey("discordChannelId")) {
            this.discordChannelId = (String) newSettings.get("discordChannelId");
        }
        saveSettingsToFile();
    }
}
