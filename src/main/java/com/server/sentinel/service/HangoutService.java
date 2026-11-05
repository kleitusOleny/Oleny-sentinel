package com.server.sentinel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.sentinel.model.HangoutMemberDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
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

    public List<HangoutMemberDto> getAllMembers() {
        List<HangoutMemberDto> list = new ArrayList<>(membersMap.values());
        list.sort(Comparator.comparing(m -> m.getUpdatedAt() != null ? m.getUpdatedAt() : 0L));
        return list;
    }

    public HangoutMemberDto addOrUpdateMember(HangoutMemberDto dto) {
        String id = dto.getId();
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
            dto.setId(id);
        }

        if (dto.getColor() == null || dto.getColor().trim().isEmpty()) {
            int idx = Math.abs(id.hashCode()) % DEFAULT_COLORS.length;
            dto.setColor(DEFAULT_COLORS[idx]);
        }

        if (dto.getAvailableDates() == null) {
            dto.setAvailableDates(new ArrayList<>());
        }

        dto.setUpdatedAt(System.currentTimeMillis());
        membersMap.put(id, dto);
        saveMembersToFile();
        return dto;
    }

    public HangoutMemberDto toggleDate(String memberId, String date) {
        HangoutMemberDto member = membersMap.get(memberId);
        if (member == null) {
            throw new NoSuchElementException("Không tìm thấy thành viên với ID: " + memberId);
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

    public boolean deleteMember(String id) {
        if (membersMap.containsKey(id)) {
            membersMap.remove(id);
            saveMembersToFile();
            return true;
        }
        return false;
    }
}
