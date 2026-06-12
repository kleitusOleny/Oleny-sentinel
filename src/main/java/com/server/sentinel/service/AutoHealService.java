package com.server.sentinel.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AutoHealService {
    
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();
    
    public AutoHealService(@Value("${sentinel.auto-heal.allowed:}") List<String> allowedContainers) {
        if (allowedContainers != null) {
            for (String name : allowedContainers) {
                if (name != null && !name.trim().isEmpty()) {
                    whitelist.add(name.trim());
                }
            }
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
    }
    
    public void addToWhitelist(String containerName) {
        if (containerName != null && !containerName.trim().isEmpty()) {
            whitelist.add(containerName.trim());
        }
    }
    
    public void removeFromWhitelist(String containerName) {
        if (containerName != null) {
            whitelist.remove(containerName.trim());
        }
    }
}
