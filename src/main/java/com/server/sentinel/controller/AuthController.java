package com.server.sentinel.controller;

import com.server.sentinel.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*") // Cho phep Svelte goi API tu server khac
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/google")
    public Map<String, Object> verifyGoogleLogin(@RequestBody Map<String, String> payload) {
        String idToken = payload.get("idToken");
        if (idToken == null || idToken.trim().isEmpty()) {
            return Map.of("status", "error", "message", "ID Token is required");
        }

        try {
            // Xác thực token với Google API
            Map<String, Object> tokenInfo = authService.verifyGoogleToken(idToken);
            String email = (String) tokenInfo.get("email");
            String name = (String) tokenInfo.get("name");
            String picture = (String) tokenInfo.get("picture");

            // Phân quyền dựa trên danh sách email trong allow_accesss.txt
            if (!authService.isEmailAllowed(email)) {
                return Map.of(
                    "status", "error", 
                    "message", "Tai khoan email cua ban (" + email + ") khong co quyen truy cap he thong. Vui long lien he quan tri vien."
                );
            }

            // Trả về kết quả xác thực thành công
            return Map.of(
                "status", "success",
                "email", email,
                "name", name != null ? name : email,
                "picture", picture != null ? picture : "",
                "token", "sentinel-session-verified-" + System.currentTimeMillis() // Token phiên đơn giản
            );

        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    /**
     * Tạo mã phiên đăng nhập QR (cho Desktop hiển thị)
     * GET /api/auth/qr/generate
     */
    @GetMapping("/qr/generate")
    public Map<String, Object> generateQr() {
        String code = authService.createQrSession();
        return Map.of("status", "success", "code", code);
    }

    /**
     * Kiểm tra trạng thái mã QR (Desktop định kỳ poll)
     * GET /api/auth/qr/status?code=...
     */
    @GetMapping("/qr/status")
    public Map<String, Object> checkQrStatus(@RequestParam("code") String code) {
        AuthService.QrSession session = authService.getQrSession(code);
        if (session == null) {
            return Map.of("status", "expired", "message", "Mã QR đã hết hạn");
        }
        if (session.approved) {
            return Map.of(
                "status", "approved",
                "email", session.email,
                "name", session.name != null ? session.name : session.email,
                "picture", session.picture != null ? session.picture : "",
                "token", session.token
            );
        }
        return Map.of("status", "pending");
    }

    /**
     * Điện thoại (đã đăng nhập) quét mã QR và phê duyệt đăng nhập cho Desktop
     * POST /api/auth/qr/approve
     */
    @PostMapping("/qr/approve")
    public Map<String, Object> approveQr(@RequestBody Map<String, String> payload) {
        String code = payload.get("code");
        String email = payload.get("email");
        String name = payload.get("name");
        String picture = payload.get("picture");
        String token = payload.get("token");

        if (code == null || email == null) {
            return Map.of("status", "error", "message", "Thiếu thông tin xác thực");
        }

        boolean approved = authService.approveQrSession(code, email, name, picture, token != null ? token : "sentinel-qr-" + System.currentTimeMillis());
        if (!approved) {
            return Map.of("status", "error", "message", "Mã QR không hợp lệ hoặc tài khoản không có quyền.");
        }
        return Map.of("status", "success", "message", "Đã xác nhận đăng nhập thành công cho thiết bị!");
    }
}
