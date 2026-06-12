package com.server.sentinel.service;

import org.springframework.stereotype.Service;

@Service
public class DiscordService {
    private final DiscordBotService discordBotService;
    
    public DiscordService(DiscordBotService discordBotService) {
        this.discordBotService = discordBotService;
    }
    
    public void sendAlert(String message) {
        discordBotService.sendAlert(message);
    }
}