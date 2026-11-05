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
     * Lay toan bo danh sach thanh vien va lich ranh (an pinHash)
     * GET /api/hangout/members
     */
    @GetMapping("/members")
    public ResponseEntity<List<HangoutMemberDto>> getMembers() {
        return ResponseEntity.ok(hangoutService.getAllMembersSafe());
    }

    /**
     * Them thanh vien moi kem ma PIN 4 so
     * POST /api/hangout/members
     * Body: { "name": "Vinh", "pin": "1234" }
     */
    @PostMapping("/members")
    public ResponseEntity<?> createMember(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String pin = body.get("pin");

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Tên thành viên không được để trống."));
        }

        if (pin == null || pin.trim().length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Vui lòng nhập mã PIN bảo vệ 4 số."));
        }

        HangoutMemberDto saved = hangoutService.addMemberWithPin(name.trim(), pin.trim());
        // Tra ve thong tin an toan
        saved.setPinHash("PROTECTED");
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Đã tạo thành viên với mã PIN bảo vệ thành công.",
                "member", saved
        ));
    }

    /**
     * Kiem tra ma PIN (Xac thuc danh tinh)
     * POST /api/hangout/members/{id}/verify-pin
     * Body: { "pin": "1234" }
     */
    @PostMapping("/members/{id}/verify-pin")
    public ResponseEntity<?> verifyPin(@PathVariable String id, @RequestBody Map<String, String> body) {
        String pin = body.get("pin");
        if (pin == null || pin.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Mã PIN không được để trống."));
        }
        boolean ok = hangoutService.verifyPin(id, pin.trim());
        if (ok) {
            return ResponseEntity.ok(Map.of("status", "success", "message", "Mã PIN chính xác!"));
        } else {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", "Mã PIN 4 số không đúng!"));
        }
    }

    /**
     * Bat/tat trang thai ranh cua thanh vien theo ngay co kiem tra PIN
     * POST /api/hangout/members/{id}/toggle-date
     * Body: { "date": "YYYY-MM-DD", "pin": "1234" }
     */
    @PostMapping("/members/{id}/toggle-date")
    public ResponseEntity<?> toggleDate(@PathVariable String id, @RequestBody Map<String, String> body) {
        String date = body.get("date");
        String pin = body.get("pin");

        if (date == null || date.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Ngày không hợp lệ."));
        }

        try {
            HangoutMemberDto updated = hangoutService.toggleDateWithPin(id, date.trim(), pin != null ? pin.trim() : "");
            updated.setPinHash("PROTECTED");
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "member", updated
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Xoa thanh vien co kiem tra PIN
     * DELETE /api/hangout/members/{id}
     * Header hoac param: ?pin=1234
     */
    @DeleteMapping("/members/{id}")
    public ResponseEntity<?> deleteMember(@PathVariable String id, @RequestParam(required = false) String pin) {
        try {
            boolean deleted = hangoutService.deleteMemberWithPin(id, pin != null ? pin.trim() : "");
            if (deleted) {
                return ResponseEntity.ok(Map.of("status", "success", "message", "Đã xóa thành viên."));
            } else {
                return ResponseEntity.status(404).body(Map.of("status", "error", "message", "Không tìm thấy thành viên."));
            }
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
