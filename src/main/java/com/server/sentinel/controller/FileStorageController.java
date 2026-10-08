package com.server.sentinel.controller;

import com.server.sentinel.model.FileItemDto;
import com.server.sentinel.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/storage")
@CrossOrigin(origins = "*")
public class FileStorageController {

    private final FileStorageService fileStorageService;

    public FileStorageController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * Liệt kê danh sách file & thư mục
     * GET /api/storage/list?path=subfolder
     */
    @GetMapping("/list")
    public ResponseEntity<?> listFiles(@RequestParam(value = "path", defaultValue = "") String path) {
        try {
            List<FileItemDto> files = fileStorageService.listFiles(path);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "currentPath", path,
                    "items", files
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Upload 1 hoặc nhiều files vào thư mục
     * POST /api/storage/upload
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFiles(
            @RequestParam(value = "path", defaultValue = "") String path,
            @RequestParam("file") MultipartFile[] files) {
        try {
            List<FileItemDto> uploaded = new java.util.ArrayList<>();
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    FileItemDto item = fileStorageService.storeFile(path, file);
                    uploaded.add(item);
                }
            }
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Đã tải lên thành công " + uploaded.size() + " tệp tin.",
                    "items", uploaded
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Tạo thư mục mới
     * POST /api/storage/mkdir
     */
    @PostMapping("/mkdir")
    public ResponseEntity<?> createFolder(@RequestBody Map<String, String> payload) {
        String parentPath = payload.getOrDefault("path", "");
        String folderName = payload.get("folderName");
        if (folderName == null || folderName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Tên thư mục không được rỗng."));
        }

        try {
            boolean created = fileStorageService.createDirectory(parentPath, folderName);
            if (!created) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Thư mục đã tồn tại."));
            }
            return ResponseEntity.ok(Map.of("status", "success", "message", "Đã tạo thư mục thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Tải xuống tệp tin
     * GET /api/storage/download?path=filename.ext
     */
    @GetMapping("/download")
    public ResponseEntity<?> downloadFile(@RequestParam("path") String path) {
        try {
            Resource resource = fileStorageService.loadFileAsResource(path);
            String filename = resource.getFilename();
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            String mimeType = fileStorageService.guessMimeType(filename);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mimeType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                    .body(resource);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Đọc nội dung tệp tin văn bản (preview)
     * GET /api/storage/preview?path=logfile.log
     */
    @GetMapping("/preview")
    public ResponseEntity<?> previewFile(@RequestParam("path") String path) {
        try {
            String content = fileStorageService.readTextFile(path);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "path", path,
                    "content", content
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Xóa tệp tin hoặc thư mục
     * DELETE /api/storage/delete?path=folder/file
     */
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteFile(@RequestParam("path") String path) {
        try {
            boolean deleted = fileStorageService.deleteItem(path);
            if (!deleted) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Không tìm thấy tệp hoặc thư mục để xóa."));
            }
            return ResponseEntity.ok(Map.of("status", "success", "message", "Đã xóa thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
