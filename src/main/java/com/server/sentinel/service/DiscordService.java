package com.server.sentinel.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service
public class DiscordService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final SettingsService settingsService;
    
    public DiscordService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }
    
    public void sendAlert(String message) {
        String discordWebhookUrl = settingsService.getDiscordWebhookUrl();
        if (discordWebhookUrl == null || discordWebhookUrl.trim().isEmpty() || !discordWebhookUrl.startsWith("https")) {
            System.out.println("[Bo qua] Khong the gui Discord vi Webhook URL chua duoc cau hinh hoac khong hop le.");
            return;
        }
        
        try {
            Map<String, String> payload = Map.of("content", message);
            restTemplate.postForObject(discordWebhookUrl, payload, String.class);
        } catch (Exception e) {
            System.err.println("Loi khi gui thong bao Discord: " + e.getMessage());
        }
    }
}