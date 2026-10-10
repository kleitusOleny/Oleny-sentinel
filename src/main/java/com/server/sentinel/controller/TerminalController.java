package com.server.sentinel.controller;

import com.server.sentinel.service.TerminalService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/terminal")
@CrossOrigin(origins = "*")
public class TerminalController {

    private final TerminalService terminalService;

    public TerminalController(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    @PostMapping("/exec")
    public Map<String, Object> executeCommand(@RequestBody Map<String, String> payload) {
        String command = payload.get("command");
        String target = payload.getOrDefault("target", "host");
        String containerId = payload.get("containerId");

        return terminalService.executeCommand(command, target, containerId);
    }
}
