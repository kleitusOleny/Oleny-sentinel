package com.server.sentinel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.sentinel.model.HangoutMemberDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HangoutService {

    private static final Logger log = LoggerFactory.getLogger(HangoutService.class);

    private final String storagePath;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, HangoutMemberDto> membersMap = new ConcurrentHashMap<>();

    private final String[] DEFAULT_COLORS = {
            "#f43f86", "#6366f1", "#06b6d4", "#10b981", "#f59e0b",
            "#8b5cf6", "#ec4899", "#14b8a6", "#f97316", "#3b82f6"
    };

    public HangoutService() {
        String os = System.getProperty("os.name").toLowerCase();
        this.storagePath = os.contains("win")
                ? "./config/hangout_schedule.json"
                : "/app/config/hangout_schedule.json";

        loadMembers();
    }

    public static String hashPin(String rawPin) {
        if (rawPin == null || rawPin.trim().isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(rawPin.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return rawPin.trim();
        }
    }

    private synchronized void loadMembers() {
        File file = new File(storagePath);
        if (file.exists()) {
            try {
                List<HangoutMemberDto> list = objectMapper.readValue(file, new TypeReference<List<HangoutMemberDto>>() {});
                membersMap.clear();
                if (list != null) {
                    for (HangoutMemberDto m : list) {
                        if (m.getId() != null) {
                            if (m.getAvailableDates() == null) {
                                m.setAvailableDates(new ArrayList<>());
                            }
                            membersMap.put(m.getId(), m);
                        }
                    }
                }
                log.info("Da nap {} thanh vien hangout tu: {}", membersMap.size(), storagePath);
                return;
            } catch (IOException e) {
                log.error("Loi khi doc file hangout_schedule.json: {}", e.getMessage());
            }
        }
        saveMembersToFile();
    }

    public synchronized void saveMembersToFile() {
        try {
            File file = new File(storagePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            List<HangoutMemberDto> list = new ArrayList<>(membersMap.values());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, list);
        } catch (IOException e) {
            log.error("Loi khi luu file hangout_schedule.json: {}", e.getMessage());
        }
    }

    /**
     * Tra ve danh sach thanh vien cho client (giau pinHash de bao mat)
     */
    public List<HangoutMemberDto> getAllMembersSafe() {
        List<HangoutMemberDto> list = new ArrayList<>();
        for (HangoutMemberDto original : membersMap.values()) {
            HangoutMemberDto safe = new HangoutMemberDto();
            safe.setId(original.getId());
            safe.setName(original.getName());
            safe.setAvailableDates(original.getAvailableDates() != null ? new ArrayList<>(original.getAvailableDates()) : new ArrayList<>());
            safe.setColor(original.getColor());
            safe.setUpdatedAt(original.getUpdatedAt());
            // An pinHash khi gui ve client
            safe.setPinHash(original.getPinHash() != null && !original.getPinHash().isEmpty() ? "PROTECTED" : "");
            list.add(safe);
        }
        list.sort(Comparator.comparing(m -> m.getUpdatedAt() != null ? m.getUpdatedAt() : 0L));
        return list;
    }

    public boolean verifyPin(String memberId, String rawPin) {
        HangoutMemberDto member = membersMap.get(memberId);
        if (member == null) return false;
        // Neu thanh vien chua co PIN (thanh vien cu), chap nhan
        if (member.getPinHash() == null || member.getPinHash().isEmpty()) {
            return true;
        }
        String inputHash = hashPin(rawPin);
        return inputHash.equals(member.getPinHash());
    }

    public HangoutMemberDto addMemberWithPin(String name, String rawPin) {
        String id = UUID.randomUUID().toString();
        HangoutMemberDto dto = new HangoutMemberDto();
        dto.setId(id);
        dto.setName(name.trim());
        int idx = Math.abs(id.hashCode()) % DEFAULT_COLORS.length;
        dto.setColor(DEFAULT_COLORS[idx]);
        dto.setAvailableDates(new ArrayList<>());
        dto.setUpdatedAt(System.currentTimeMillis());
        dto.setPinHash(hashPin(rawPin));

        membersMap.put(id, dto);
        saveMembersToFile();
        return dto;
    }

    public HangoutMemberDto toggleDateWithPin(String memberId, String date, String rawPin) {
        HangoutMemberDto member = membersMap.get(memberId);
        if (member == null) {
            throw new NoSuchElementException("Không tìm thấy thành viên với ID: " + memberId);
        }

        if (!verifyPin(memberId, rawPin)) {
            throw new SecurityException("Mã PIN 4 số không chính xác. Không thể sửa lịch của thành viên này!");
        }

        List<String> dates = new ArrayList<>(member.getAvailableDates() != null ? member.getAvailableDates() : Collections.emptyList());
        if (dates.contains(date)) {
            dates.remove(date);
        } else {
            dates.add(date);
        }
        member.setAvailableDates(dates);
        member.setUpdatedAt(System.currentTimeMillis());
        membersMap.put(memberId, member);
        saveMembersToFile();
        return member;
    }

    public boolean deleteMemberWithPin(String id, String rawPin) {
        HangoutMemberDto member = membersMap.get(id);
        if (member == null) return false;

        if (!verifyPin(id, rawPin)) {
            throw new SecurityException("Mã PIN 4 số không chính xác. Không thể xóa thành viên này!");
        }

        membersMap.remove(id);
        saveMembersToFile();
        return true;
    }
}
