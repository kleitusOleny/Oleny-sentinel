package com.server.sentinel.controller;

import com.server.sentinel.model.HangoutMemberDto;
import com.server.sentinel.service.HangoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/hangout")
@CrossOrigin(origins = "*")
public class HangoutController {

    private final HangoutService hangoutService;

    public HangoutController(HangoutService hangoutService) {
        this.hangoutService = hangoutService;
    }

    /**
     * Lay toan bo danh sach thanh vien va lich ranh
     * GET /api/hangout/members
     */
    @GetMapping("/members")
    public ResponseEntity<List<HangoutMemberDto>> getMembers() {
        return ResponseEntity.ok(hangoutService.getAllMembers());
    }

    /**
     * Them hoac cap nhat thong tin thanh vien
     * POST /api/hangout/members
     */
    @PostMapping("/members")
    public ResponseEntity<?> saveMember(@RequestBody HangoutMemberDto dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Tên thành viên không được để trống."));
        }
        HangoutMemberDto saved = hangoutService.addOrUpdateMember(dto);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Đã lưu thành viên thành công.",
                "member", saved
        ));
    }

    /**
     * Bat/tat trang thai ranh cua thanh vien theo ngay
     * POST /api/hangout/members/{id}/toggle-date
     * Body: { "date": "YYYY-MM-DD" }
     */
    @PostMapping("/members/{id}/toggle-date")
    public ResponseEntity<?> toggleDate(@PathVariable String id, @RequestBody Map<String, String> body) {
        String date = body.get("date");
        if (date == null || date.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Ngày không hợp lệ."));
        }
        try {
            HangoutMemberDto updated = hangoutService.toggleDate(id, date.trim());
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "member", updated
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Xoa thanh vien
     * DELETE /api/hangout/members/{id}
     */
    @DeleteMapping("/members/{id}")
    public ResponseEntity<?> deleteMember(@PathVariable String id) {
        boolean deleted = hangoutService.deleteMember(id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("status", "success", "message", "Đã xóa thành viên."));
        } else {
            return ResponseEntity.status(404).body(Map.of("status", "error", "message", "Không tìm thấy thành viên."));
        }
    }
}
