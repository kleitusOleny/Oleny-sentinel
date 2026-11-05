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
                            if (m.getAvailableSlots() == null) {
                                m.setAvailableSlots(new ArrayList<>());
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
     * Tra ve danh sach thanh vien cho client (an mat khau de khong bi lo)
     */
    public List<HangoutMemberDto> getAllMembersSafe() {
        List<HangoutMemberDto> list = new ArrayList<>();
        for (HangoutMemberDto original : membersMap.values()) {
            HangoutMemberDto safe = new HangoutMemberDto();
            safe.setId(original.getId());
            safe.setName(original.getName());
            safe.setAvailableSlots(original.getAvailableSlots() != null ? new ArrayList<>(original.getAvailableSlots()) : new ArrayList<>());
            safe.setColor(original.getColor());
            safe.setUpdatedAt(original.getUpdatedAt());
            // An pin khi gui ve danh sach chung
            safe.setPin(original.getPin() != null && !original.getPin().isEmpty() ? "PROTECTED" : "");
            list.add(safe);
        }
        list.sort(Comparator.comparing(m -> m.getUpdatedAt() != null ? m.getUpdatedAt() : 0L));
        return list;
    }

    public boolean verifyPin(String memberId, String rawPin) {
        HangoutMemberDto member = membersMap.get(memberId);
        if (member == null) return false;
        // Neu thanh vien chua co PIN, cho phep
        if (member.getPin() == null || member.getPin().trim().isEmpty()) {
            return true;
        }
        String storedPin = member.getPin().trim();
        String inputPin = rawPin != null ? rawPin.trim() : "";
        return storedPin.equals(inputPin);
    }

    public HangoutMemberDto addMemberWithPin(String name, String rawPin) {
        String id = UUID.randomUUID().toString();
        HangoutMemberDto dto = new HangoutMemberDto();
        dto.setId(id);
        dto.setName(name.trim());
        int idx = Math.abs(id.hashCode()) % DEFAULT_COLORS.length;
        dto.setColor(DEFAULT_COLORS[idx]);
        dto.setAvailableSlots(new ArrayList<>());
        dto.setUpdatedAt(System.currentTimeMillis());
        // Luu truc tiep khong hash
        dto.setPin(rawPin != null ? rawPin.trim() : "");

        membersMap.put(id, dto);
        saveMembersToFile();
        return dto;
    }

    public HangoutMemberDto toggleSlotWithPin(String memberId, String slot, String rawPin) {
        HangoutMemberDto member = membersMap.get(memberId);
        if (member == null) {
            throw new NoSuchElementException("Không tìm thấy thành viên với ID: " + memberId);
        }

        if (!verifyPin(memberId, rawPin)) {
            throw new SecurityException("Mật khẩu/mã PIN không chính xác. Không thể sửa lịch của thành viên này!");
        }

        List<String> slots = new ArrayList<>(member.getAvailableSlots() != null ? member.getAvailableSlots() : Collections.emptyList());
        if (slots.contains(slot)) {
            slots.remove(slot);
        } else {
            slots.add(slot);
        }
        member.setAvailableSlots(slots);
        member.setUpdatedAt(System.currentTimeMillis());
        membersMap.put(memberId, member);
        saveMembersToFile();
        return member;
    }

    public HangoutMemberDto toggleFullDayWithPin(String memberId, String date, String rawPin) {
        HangoutMemberDto member = membersMap.get(memberId);
        if (member == null) {
            throw new NoSuchElementException("Không tìm thấy thành viên với ID: " + memberId);
        }

        if (!verifyPin(memberId, rawPin)) {
            throw new SecurityException("Mật khẩu/mã PIN không chính xác. Không thể sửa lịch của thành viên này!");
        }

        String mSlot = date + "_morning";
        String aSlot = date + "_afternoon";
        String eSlot = date + "_evening";

        List<String> slots = new ArrayList<>(member.getAvailableSlots() != null ? member.getAvailableSlots() : Collections.emptyList());
        boolean hasAll = slots.contains(mSlot) && slots.contains(aSlot) && slots.contains(eSlot);

        if (hasAll) {
            slots.remove(mSlot);
            slots.remove(aSlot);
            slots.remove(eSlot);
        } else {
            if (!slots.contains(mSlot)) slots.add(mSlot);
            if (!slots.contains(aSlot)) slots.add(aSlot);
            if (!slots.contains(eSlot)) slots.add(eSlot);
        }

        member.setAvailableSlots(slots);
        member.setUpdatedAt(System.currentTimeMillis());
        membersMap.put(memberId, member);
        saveMembersToFile();
        return member;
    }

    public boolean deleteMemberWithPin(String id, String rawPin) {
        HangoutMemberDto member = membersMap.get(id);
        if (member == null) return false;

        if (!verifyPin(id, rawPin)) {
            throw new SecurityException("Mật khẩu/mã PIN không chính xác. Không thể xóa thành viên này!");
        }

        membersMap.remove(id);
        saveMembersToFile();
        return true;
    }

    /**
     * Truncate toan bo thanh vien va lich hen
     */
    public synchronized void truncateAll() {
        membersMap.clear();
        saveMembersToFile();
        log.info("Da truncate toan bo danh sach hangout!");
    }
}
