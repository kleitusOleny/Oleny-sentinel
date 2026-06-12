package com.server.sentinel.service;

import com.github.dockerjava.api.model.Container;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DiscordBotService extends ListenerAdapter {

    private final SettingsService settingsService;
    private final DockerService dockerService;
    private final SystemService systemService;
    
    private JDA jda;
    private boolean isBotRunning = false;

    public DiscordBotService(
            SettingsService settingsService,
            DockerService dockerService,
            SystemService systemService) {
        this.settingsService = settingsService;
        this.dockerService = dockerService;
        this.systemService = systemService;
    }

    @PostConstruct
    public void init() {
        startBot();
    }

    public synchronized void startBot() {
        String token = settingsService.getDiscordBotToken();
        if (token == null || token.trim().isEmpty() || token.equalsIgnoreCase("your-bot-token-here")) {
            System.out.println("[Discord Bot] Token chua duoc cau hinh hoac la token mau. Bo qua khoi chay JDA.");
            return;
        }

        try {
            System.out.println("[Discord Bot] Dang khoi chay JDA Discord Bot...");
            
            jda = JDABuilder.createDefault(token.trim())
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(this)
                    .build();
            
            jda.awaitReady(); // Cho bot load xong
            
            // Dang ky cac Slash Commands toan cau
            jda.updateCommands().addCommands(
                Commands.slash("stats", "Lay thong so hieu nang phan cung thoi gian thuc"),
                Commands.slash("containers", "Liet ke danh sach cac Docker container"),
                Commands.slash("action", "Dieu khien trang thai hoat dong cua Docker container")
                    .addOption(OptionType.STRING, "id", "ID hoac Ten cua container", true)
                    .addOption(OptionType.STRING, "action", "Hanh dong (start/stop/restart)", true),
                Commands.slash("logs", "Doc nhat ky logs cua container")
                    .addOption(OptionType.STRING, "id", "ID hoac Ten cua container", true)
                    .addOption(OptionType.INTEGER, "lines", "So dong logs (mac dinh 50)", false)
            ).queue();
            
            isBotRunning = true;
            System.out.println("[Discord Bot] Khoi dong thanh cong va da dang ky cac lenh Slash!");
            
            sendAlert("🛡️ **Server Sentinel Online!** Discord Bot da ket noi thanh cong.");
        } catch (Exception e) {
            System.err.println("[Discord Bot] Loi khi khoi dong JDA: " + e.getMessage());
            isBotRunning = false;
        }
    }

    public synchronized void restartBot() {
        shutdownBot();
        startBot();
    }

    @PreDestroy
    public synchronized void shutdownBot() {
        if (jda != null) {
            try {
                System.out.println("[Discord Bot] Dang tat Discord Bot...");
                jda.shutdownNow();
            } catch (Exception e) {
                System.err.println("Loi khi tat JDA: " + e.getMessage());
            }
            jda = null;
        }
        isBotRunning = false;
    }

    public void sendAlert(String message) {
        if (jda == null || !isBotRunning) {
            return;
        }
        String channelId = settingsService.getDiscordChannelId();
        if (channelId == null || channelId.trim().isEmpty()) {
            System.out.println("[Discord Bot] Chua cau hinh Channel ID. Bo qua gui thong bao.");
            return;
        }
        try {
            TextChannel channel = jda.getTextChannelById(channelId.trim());
            if (channel != null) {
                channel.sendMessage(message).queue();
            } else {
                System.err.println("[Discord Bot] Khong tim thay Text Channel voi ID: " + channelId);
            }
        } catch (Exception e) {
            System.err.println("[Discord Bot] Loi khi gui thong bao: " + e.getMessage());
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();

        // 1. STATS COMMAND
        if ("stats".equals(commandName)) {
            event.deferReply().queue();
            try {
                // Cập nhật thông số mới nhất
                systemService.updateMetrics();

                double cpu = systemService.getCpuLoad();
                long totalMem = systemService.getTotalMemoryMB();
                long freeMem = systemService.getFreeMemoryMB();
                long usedMem = totalMem - freeMem;
                double memPercent = totalMem > 0 ? ((double) usedMem / totalMem) * 100 : 0.0;

                double diskTotal = systemService.getDiskTotalGB();
                double diskUsed = systemService.getDiskUsedGB();
                double diskPercent = systemService.getDiskUsagePercent();

                double rx = systemService.getRxSpeedKBps();
                double tx = systemService.getTxSpeedKBps();

                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("🛡️ Server Sentinel - Thong so He thong");
                embed.setColor(Color.decode("#5865F2")); // Blurple

                embed.addField("🖥️ CPU Load", String.format("%.2f", cpu) + "%", true);
                embed.addField("💾 RAM Memory", String.format("%.1f", memPercent) + "% (" + usedMem + "/" + totalMem + " MB)", true);
                embed.addField("💽 Disk Storage", String.format("%.1f", diskPercent) + "% (" + String.format("%.1f", diskUsed) + "/" + String.format("%.1f", diskTotal) + " GB)", true);
                
                String netSpeed = String.format("Rx: %.2f KB/s\nTx: %.2f KB/s", rx, tx);
                embed.addField("🌐 Network IO", netSpeed, true);

                if (systemService.isGpuAvailable()) {
                    String gpuInfo = String.format("Model: %s\nLoad: %.0f%%\nVRAM: %d MB", 
                            systemService.getGpuName(), systemService.getGpuLoad(), systemService.getGpuMemoryUsedMB());
                    embed.addField("🎮 Graphics GPU", gpuInfo, false);
                }

                embed.setFooter("Server Sentinel • Giam sat he thong");
                event.getHook().sendMessageEmbeds(embed.build()).queue();
            } catch (Exception e) {
                event.getHook().sendMessage("Loi khi lay thong so: " + e.getMessage()).queue();
            }
        }

        // 2. CONTAINERS COMMAND
        else if ("containers".equals(commandName)) {
            event.deferReply().queue();
            try {
                List<Container> list = dockerService.getAllContainers();
                if (list.isEmpty()) {
                    event.getHook().sendMessage("Khong co container nao tren he thong.").queue();
                    return;
                }

                StringBuilder sb = new StringBuilder("**Danh sach Docker Container:**\n```\n");
                sb.append(String.format("%-25s %-12s %-25s\n", "TEN", "TRANG THAI", "STATUS"));
                sb.append("----------------------------------------------------------------\n");
                for (Container c : list) {
                    String name = c.getNames().length > 0 ? c.getNames()[0].replace("/", "") : "unknown";
                    if (name.length() > 24) name = name.substring(0, 21) + "...";
                    String state = c.getState() != null ? c.getState() : "";
                    String status = c.getStatus() != null ? c.getStatus() : "";
                    if (status.length() > 24) status = status.substring(0, 24);
                    sb.append(String.format("%-25s %-12s %-25s\n", name, state, status));
                }
                sb.append("```");
                event.getHook().sendMessage(sb.toString()).queue();
            } catch (Exception e) {
                event.getHook().sendMessage("Loi khi lay danh sach: " + e.getMessage()).queue();
            }
        }

        // 3. ACTION COMMAND
        else if ("action".equals(commandName)) {
            event.deferReply().queue();
            try {
                String containerId = event.getOption("id", OptionMapping::getAsString);
                String action = event.getOption("action", OptionMapping::getAsString);

                if (containerId == null || action == null) {
                    event.getHook().sendMessage("Thieu tham so id hoac action.").queue();
                    return;
                }

                action = action.toLowerCase().trim();
                if (!action.equals("start") && !action.equals("stop") && !action.equals("restart")) {
                    event.getHook().sendMessage("Hanh dong '" + action + "' khong hop le. Chi chap nhan start, stop, restart.").queue();
                    return;
                }

                if (action.equals("start")) {
                    dockerService.startContainer(containerId);
                } else if (action.equals("stop")) {
                    dockerService.stopContainer(containerId);
                } else {
                    dockerService.restartContainer(containerId);
                }

                event.getHook().sendMessage("Thuc thi hanh dong `" + action + "` cho container `" + containerId + "` thanh cong!").queue();
            } catch (Exception e) {
                event.getHook().sendMessage("Loi khi dieu khien container: " + e.getMessage()).queue();
            }
        }

        // 4. LOGS COMMAND
        else if ("logs".equals(commandName)) {
            event.deferReply().queue();
            try {
                String containerId = event.getOption("id", OptionMapping::getAsString);
                int lines = event.getOption("lines", 50, OptionMapping::getAsInt);

                if (containerId == null) {
                    event.getHook().sendMessage("Thieu tham so container ID.").queue();
                    return;
                }

                String logs = dockerService.getContainerLogs(containerId, lines);
                if (logs.length() > 1900) {
                    logs = logs.substring(logs.length() - 1900);
                }

                event.getHook().sendMessage("Logs cho container `" + containerId + "`:\n```text\n" + logs + "\n```").queue();
            } catch (Exception e) {
                event.getHook().sendMessage("Loi khi doc logs: " + e.getMessage()).queue();
            }
        }
    }
}
