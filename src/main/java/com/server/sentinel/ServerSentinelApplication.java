package com.server.sentinel;

import com.server.sentinel.service.DiscordService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@PropertySource("classpath:.env")
public class ServerSentinelApplication implements CommandLineRunner {
    
    private final DiscordService discordService;
    
    public ServerSentinelApplication(DiscordService discordService) {
        this.discordService = discordService;
    }
    
    public static void main(String[] args) {
        SpringApplication.run(ServerSentinelApplication.class, args);
    }
    
    @Override
    public void run(String... args) throws Exception {
        System.out.println("Dang kiem tra ket noi Discord Webhook...");
        try {
            discordService.sendAlert("[INFO] Server Sentinel da khoi dong va ket noi thanh cong voi Discord!");
            System.out.println("Da gui tin nhan test den Discord!");
        } catch (Exception e) {
            System.err.println("Loi khi gui tin nhan test: " + e.getMessage());
        }
    }
}