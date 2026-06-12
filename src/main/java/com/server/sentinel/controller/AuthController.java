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
}
