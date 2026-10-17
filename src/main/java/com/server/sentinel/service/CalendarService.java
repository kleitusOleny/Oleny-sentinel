package com.server.sentinel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.sentinel.model.CalendarEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CalendarService {

    private static final Logger log = LoggerFactory.getLogger(CalendarService.class);

    private final String storagePath;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, CalendarEventDto> eventsMap = new ConcurrentHashMap<>();

    public CalendarService() {
        String os = System.getProperty("os.name").toLowerCase();
        this.storagePath = os.contains("win")
                ? "./config/calendar_events.json"
                : "/app/config/calendar_events.json";

        loadEvents();
    }

    private synchronized void loadEvents() {
        File file = new File(storagePath);
        if (file.exists()) {
            try {
                List<CalendarEventDto> list = objectMapper.readValue(file, new TypeReference<List<CalendarEventDto>>() {});
                eventsMap.clear();
                if (list != null) {
                    for (CalendarEventDto ev : list) {
                        if (ev.getId() != null) {
                            eventsMap.put(ev.getId(), ev);
                        }
                    }
                }
                log.info("Da nap {} su kien lich tu: {}", eventsMap.size(), storagePath);
                return;
            } catch (IOException e) {
                log.error("Loi khi doc file calendar_events.json: {}", e.getMessage());
            }
        }
        saveEventsToFile();
    }

    public synchronized void saveEventsToFile() {
        try {
            File file = new File(storagePath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            List<CalendarEventDto> list = new ArrayList<>(eventsMap.values());
            // Sap xep theo thoi gian bat dau
            list.sort(Comparator.comparing(CalendarEventDto::getStartDate, Comparator.nullsLast(String::compareTo)));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, list);
        } catch (IOException e) {
            log.error("Loi khi ghi file calendar_events.json: {}", e.getMessage());
        }
    }

    public List<CalendarEventDto> getAllEvents() {
        List<CalendarEventDto> list = new ArrayList<>(eventsMap.values());
        list.sort(Comparator.comparing(CalendarEventDto::getStartDate, Comparator.nullsLast(String::compareTo)));
        return list;
    }

    public Optional<CalendarEventDto> getEventById(String id) {
        return Optional.ofNullable(eventsMap.get(id));
    }

    public CalendarEventDto createEvent(CalendarEventDto dto) {
        if (dto.getId() == null || dto.getId().trim().isEmpty()) {
            dto.setId(UUID.randomUUID().toString());
        }
        if (dto.getCreatedAt() <= 0) {
            dto.setCreatedAt(System.currentTimeMillis());
        }
        if (dto.getColor() == null || dto.getColor().trim().isEmpty()) {
            dto.setColor("indigo");
        }
        if (dto.getCategory() == null || dto.getCategory().trim().isEmpty()) {
            dto.setCategory("Chung");
        }
        dto.setNotified(false);

        eventsMap.put(dto.getId(), dto);
        saveEventsToFile();
        log.info("Da tao su kien moi: {} ({})", dto.getTitle(), dto.getId());
        return dto;
    }

    public CalendarEventDto updateEvent(String id, CalendarEventDto dto) {
        CalendarEventDto existing = eventsMap.get(id);
        if (existing == null) {
            throw new NoSuchElementException("Khong tim thay su kien voi ID: " + id);
        }

        existing.setTitle(dto.getTitle());
        existing.setDescription(dto.getDescription());
        existing.setStartDate(dto.getStartDate());
        existing.setEndDate(dto.getEndDate());
        existing.setAllDay(dto.isAllDay());
        existing.setColor(dto.getColor() != null ? dto.getColor() : existing.getColor());
        existing.setCategory(dto.getCategory() != null ? dto.getCategory() : existing.getCategory());
        existing.setReminderMinutes(dto.getReminderMinutes());
        existing.setRecurrence(dto.getRecurrence() != null ? dto.getRecurrence() : "none");
        existing.setNotified(false); // Reset de co the nhac nho lai neu doi gio

        saveEventsToFile();
        log.info("Da cap nhat su kien: {} ({})", existing.getTitle(), id);
        return existing;
    }

    public boolean deleteEvent(String id) {
        CalendarEventDto removed = eventsMap.remove(id);
        if (removed != null) {
            saveEventsToFile();
            log.info("Da xoa su kien: {} ({})", removed.getTitle(), id);
            return true;
        }
        return false;
    }

    /**
     * Kiem tra va tra ve danh sach cac su kien can nhac nho (reminder)
     */
    public List<CalendarEventDto> checkDueReminders() {
        List<CalendarEventDto> dueList = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (CalendarEventDto ev : eventsMap.values()) {
            if (ev.isNotified() || ev.getReminderMinutes() <= 0 || ev.getStartDate() == null) {
                continue;
            }

            try {
                LocalDateTime start;
                if (ev.getStartDate().contains("T")) {
                    start = LocalDateTime.parse(ev.getStartDate().substring(0, 19));
                } else {
                    // Truong hop all day YYYY-MM-DD
                    start = LocalDateTime.parse(ev.getStartDate() + "T09:00:00");
                }

                LocalDateTime reminderTime = start.minusMinutes(ev.getReminderMinutes());

                // Neu hien tai da den hoac vuot qua thoi diem can nhac, va chua qua gio bat dau qua 120 phut
                if (!now.isBefore(reminderTime) && now.isBefore(start.plusMinutes(120))) {
                    dueList.add(ev);
                    ev.setNotified(true);
                }
            } catch (Exception e) {
                // Bo qua format loi
            }
        }

        if (!dueList.isEmpty()) {
            saveEventsToFile();
        }

        return dueList;
    }
}
