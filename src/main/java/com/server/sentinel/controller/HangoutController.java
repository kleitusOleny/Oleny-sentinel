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
     * Lay toan bo danh sach thanh vien va cac buoi ranh (an pin)
     * GET /api/hangout/members
     */
    @GetMapping("/members")
    public ResponseEntity<List<HangoutMemberDto>> getMembers() {
        return ResponseEntity.ok(hangoutService.getAllMembersSafe());
    }

    /**
     * Them thanh vien moi kem ma PIN/mat khau
     * POST /api/hangout/members
     */
    @PostMapping("/members")
    public ResponseEntity<?> createMember(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String pin = body.get("pin");

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Tên thành viên không được để trống."));
        }

        if (pin == null || pin.trim().length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Vui lòng nhập mật khẩu/mã PIN tối thiểu 4 ký tự."));
        }

        HangoutMemberDto saved = hangoutService.addMemberWithPin(name.trim(), pin.trim());
        saved.setPin("PROTECTED");
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Đã tạo thành viên thành công.",
                "member", saved
        ));
    }

    /**
     * Kiem tra ma PIN (Xac thuc danh tinh)
     * POST /api/hangout/members/{id}/verify-pin
     */
    @PostMapping("/members/{id}/verify-pin")
    public ResponseEntity<?> verifyPin(@PathVariable String id, @RequestBody Map<String, String> body) {
        String pin = body.get("pin");
        if (pin == null || pin.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Mật khẩu không được để trống."));
        }
        boolean ok = hangoutService.verifyPin(id, pin.trim());
        if (ok) {
            return ResponseEntity.ok(Map.of("status", "success", "message", "Mật khẩu chính xác!"));
        } else {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", "Mật khẩu/mã PIN không đúng!"));
        }
    }

    /**
     * Bat/tat trang thai ranh cua thanh vien theo tung buoi
     * POST /api/hangout/members/{id}/toggle-slot
     */
    @PostMapping("/members/{id}/toggle-slot")
    public ResponseEntity<?> toggleSlot(@PathVariable String id, @RequestBody Map<String, String> body) {
        String slot = body.get("slot");
        String pin = body.get("pin");

        if (slot == null || slot.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Buổi rảnh không hợp lệ."));
        }

        try {
            HangoutMemberDto updated = hangoutService.toggleSlotWithPin(id, slot.trim(), pin != null ? pin.trim() : "");
            updated.setPin("PROTECTED");
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "member", updated
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Bat/tat ca ngay cho thanh vien
     * POST /api/hangout/members/{id}/toggle-day
     */
    @PostMapping("/members/{id}/toggle-day")
    public ResponseEntity<?> toggleDay(@PathVariable String id, @RequestBody Map<String, String> body) {
        String date = body.get("date");
        String pin = body.get("pin");

        if (date == null || date.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Ngày không hợp lệ."));
        }

        try {
            HangoutMemberDto updated = hangoutService.toggleFullDayWithPin(id, date.trim(), pin != null ? pin.trim() : "");
            updated.setPin("PROTECTED");
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "member", updated
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Xoa thanh vien
     * DELETE /api/hangout/members/{id}?pin=1234
     */
    @DeleteMapping("/members/{id}")
    public ResponseEntity<?> deleteMember(@PathVariable String id, @RequestParam(required = false) String pin) {
        try {
            boolean deleted = hangoutService.deleteMemberWithPin(id, pin != null ? pin.trim() : "");
            if (deleted) {
                return ResponseEntity.ok(Map.of("status", "success", "message", "Đã xóa thành viên thành công."));
            } else {
                return ResponseEntity.status(404).body(Map.of("status", "error", "message", "Không tìm thấy thành viên."));
            }
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Truncate toan bo du lieu hangout
     * POST /api/hangout/truncate
     */
    @PostMapping("/truncate")
    public ResponseEntity<?> truncate() {
        hangoutService.truncateAll();
        return ResponseEntity.ok(Map.of("status", "success", "message", "Đã xóa sạch toàn bộ dữ liệu lịch hẹn!"));
    }
}
