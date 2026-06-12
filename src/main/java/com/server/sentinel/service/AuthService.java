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
}
