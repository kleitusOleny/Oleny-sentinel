package com.server.sentinel.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AutoHealService {
    
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();
    private final String whitelistPath;

    public AutoHealService(@Value("${sentinel.auto-heal.allowed:}") List<String> allowedContainers) {
        String os = System.getProperty("os.name").toLowerCase();
        this.whitelistPath = os.contains("win") 
                ? "./config/whitelist.txt" 
                : "/app/config/whitelist.txt";
        
        loadWhitelist(allowedContainers);
    }
    
    private void loadWhitelist(List<String> allowedContainers) {
        File file = new File(whitelistPath);
        if (file.exists()) {
            try {
                List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line != null && !line.trim().isEmpty()) {
                        whitelist.add(line.trim());
                    }
                }
                System.out.println("Da nap whitelist tu file: " + whitelistPath + ", So luong: " + whitelist.size());
                return;
            } catch (IOException e) {
                System.err.println("Loi khi doc file whitelist: " + e.getMessage());
            }
        }
        
        // Neu file chua ton tai, nap tu config properties (env) va luu vao file
        if (allowedContainers != null) {
            for (String name : allowedContainers) {
                if (name != null && !name.trim().isEmpty()) {
                    whitelist.add(name.trim());
                }
            }
        }
        saveWhitelistToFile();
    }
    
    private void saveWhitelistToFile() {
        try {
            File file = new File(whitelistPath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            List<String> list = new ArrayList<>(whitelist);
            Files.write(file.toPath(), list, StandardCharsets.UTF_8);
            System.out.println("Da luu whitelist vao file: " + whitelistPath);
        } catch (IOException e) {
            System.err.println("Loi khi ghi file whitelist: " + e.getMessage());
        }
    }
    
    public List<String> getWhitelist() {
        List<String> list = new ArrayList<>(whitelist);
        Collections.sort(list);
        return list;
    }
    
    public boolean isAllowed(String containerName) {
        if (containerName == null) {
            return false;
        }
        return whitelist.contains(containerName.trim());
    }
    
    public void toggleWhitelist(String containerName) {
        if (containerName == null || containerName.trim().isEmpty()) {
            return;
        }
        String cleaned = containerName.trim();
        if (whitelist.contains(cleaned)) {
            whitelist.remove(cleaned);
        } else {
            whitelist.add(cleaned);
        }
        saveWhitelistToFile();
    }
    
    public void addToWhitelist(String containerName) {
        if (containerName != null && !containerName.trim().isEmpty()) {
            whitelist.add(containerName.trim());
            saveWhitelistToFile();
        }
    }
    
    public void removeFromWhitelist(String containerName) {
        if (containerName != null) {
            whitelist.remove(containerName.trim());
            saveWhitelistToFile();
        }
    }
}
