package com.server.sentinel.controller;

import com.server.sentinel.model.CalendarEventDto;
import com.server.sentinel.service.CalendarService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/calendar")
@CrossOrigin(origins = "*")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    /**
     * Lay danh sach tat ca su kien
     * GET /api/calendar/events
     */
    @GetMapping("/events")
    public ResponseEntity<List<CalendarEventDto>> getEvents() {
        return ResponseEntity.ok(calendarService.getAllEvents());
    }

    /**
     * Tao su kien moi
     * POST /api/calendar/events
     */
    @PostMapping("/events")
    public ResponseEntity<?> createEvent(@RequestBody CalendarEventDto dto) {
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Tiêu đề không được để trống."));
        }
        if (dto.getStartDate() == null || dto.getStartDate().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Thời gian bắt đầu không được để trống."));
        }

        CalendarEventDto created = calendarService.createEvent(dto);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Đã tạo sự kiện thành công.",
                "event", created
        ));
    }

    /**
     * Cap nhat su kien
     * PUT /api/calendar/events/{id}
     */
    @PutMapping("/events/{id}")
    public ResponseEntity<?> updateEvent(@PathVariable String id, @RequestBody CalendarEventDto dto) {
        try {
            CalendarEventDto updated = calendarService.updateEvent(id, dto);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Đã cập nhật sự kiện.",
                    "event", updated
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Xoa su kien
     * DELETE /api/calendar/events/{id}
     */
    @DeleteMapping("/events/{id}")
    public ResponseEntity<?> deleteEvent(@PathVariable String id) {
        boolean deleted = calendarService.deleteEvent(id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("status", "success", "message", "Đã xóa sự kiện thành công."));
        } else {
            return ResponseEntity.status(404).body(Map.of("status", "error", "message", "Không tìm thấy sự kiện cần xóa."));
        }
    }
}
