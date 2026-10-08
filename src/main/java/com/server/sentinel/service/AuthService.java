package com.server.sentinel.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AuthService {

    private final String allowAccessPath;
    private final RestTemplate restTemplate = new RestTemplate();

    public AuthService() {
        String os = System.getProperty("os.name").toLowerCase();
        this.allowAccessPath = os.contains("win") 
                ? "./config/allow_accesss.txt" 
                : "/app/config/allow_accesss.txt";
        
        initializeFile();
    }

    private void initializeFile() {
        try {
            File file = new File(allowAccessPath);
            if (!file.exists()) {
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                List<String> defaultLines = new ArrayList<>();
                defaultLines.add("# Danh sach cac tai khoan email duoc phep truy cap Sentinel (dang nhap Google)");
                defaultLines.add("# Them moi moi email tren mot dong");
                defaultLines.add("admin@example.com");
                Files.write(file.toPath(), defaultLines, StandardCharsets.UTF_8);
                System.out.println("Da tao file phan quyen ban dau tai: " + allowAccessPath);
            }
        } catch (IOException e) {
            System.err.println("Loi khi khoi tao file allow_accesss.txt: " + e.getMessage());
        }
    }

    public synchronized Set<String> getAllowedEmails() {
        Set<String> emails = new HashSet<>();
        try {
            File file = new File(allowAccessPath);
            if (file.exists()) {
                List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        emails.add(trimmed.toLowerCase());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Loi khi doc file phan quyen: " + e.getMessage());
        }
        return emails;
    }

    public boolean isEmailAllowed(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return getAllowedEmails().contains(email.trim().toLowerCase());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> verifyGoogleToken(String idToken) throws Exception {
        String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || response.containsKey("error")) {
                String errorDesc = response != null ? (String) response.get("error_description") : "Token khong hop le";
                throw new Exception("Xac thuc token Google that bai: " + errorDesc);
            }
            
            String email = (String) response.get("email");
            String emailVerified = (String) response.get("email_verified");
            
            if (email == null || !"true".equalsIgnoreCase(emailVerified)) {
                throw new Exception("Email Google chua duoc xac thuc.");
            }
            
            return response;
        } catch (Exception e) {
            throw new Exception("Loi khi goi Google API xac thuc Token: " + e.getMessage());
        }
    }

    // QR Code Login Management (Store pending sessions for 5 minutes)
    public static class QrSession {
        public String code;
        public long createdAt;
        public boolean approved;
        public String email;
        public String name;
        public String picture;
        public String token;

        public QrSession(String code) {
            this.code = code;
            this.createdAt = System.currentTimeMillis();
            this.approved = false;
        }
    }

    private final Map<String, QrSession> qrSessions = new java.util.concurrent.ConcurrentHashMap<>();

    public String createQrSession() {
        // Xóa các session cũ quá 5 phút
        long now = System.currentTimeMillis();
        qrSessions.entrySet().removeIf(e -> now - e.getValue().createdAt > 300000);

        String code = "qr-" + java.util.UUID.randomUUID().toString();
        qrSessions.put(code, new QrSession(code));
        return code;
    }

    public QrSession getQrSession(String code) {
        if (code == null) return null;
        QrSession session = qrSessions.get(code);
        if (session != null && System.currentTimeMillis() - session.createdAt > 300000) {
            qrSessions.remove(code);
            return null;
        }
        return session;
    }

    public boolean approveQrSession(String code, String email, String name, String picture, String token) {
        QrSession session = getQrSession(code);
        if (session == null || !isEmailAllowed(email)) {
            return false;
        }
        session.approved = true;
        session.email = email;
        session.name = name;
        session.picture = picture;
        session.token = token;
        return true;
    }
}
