package com.server.sentinel.controller;

import com.server.sentinel.model.FileItemDto;
import com.server.sentinel.service.FileStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * Upload file
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File tải lên không được rỗng"));
        }
        try {
            FileItemDto fileDto = fileStorageService.uploadFile(file);
            return ResponseEntity.ok(fileDto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Lỗi upload file: " + e.getMessage()));
        }
    }

    /**
     * Lấy danh sách files
     */
    @GetMapping
    public List<FileItemDto> getFiles() {
        return fileStorageService.listFiles();
    }

    /**
     * Streaming file (hỗ trợ HTTP Range requests cho video/audio/file)
     */
    @GetMapping("/stream/{filename}")
    public ResponseEntity<InputStreamResource> streamFile(
            @PathVariable("filename") String filename,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        return fileStorageService.streamFile(filename, rangeHeader);
    }

    /**
     * Lấy Pre-signed URL truy cập trực tiếp
     */
    @GetMapping("/url/{filename}")
    public ResponseEntity<?> getPresignedUrl(
            @PathVariable("filename") String filename,
            @RequestParam(value = "expiryMinutes", defaultValue = "1440") int expiryMinutes) {
        String url = fileStorageService.generatePresignedUrl(filename, expiryMinutes);
        if (url != null) {
            return ResponseEntity.ok(Map.of("filename", filename, "url", url));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Không thể tạo liên kết cho file"));
        }
    }

    /**
     * Xóa file
     */
    @DeleteMapping("/{filename}")
    public ResponseEntity<?> deleteFile(@PathVariable("filename") String filename) {
        boolean success = fileStorageService.deleteFile(filename);
        if (success) {
            return ResponseEntity.ok(Map.of("message", "Đã xóa file thành công"));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Xóa file thất bại"));
        }
    }
}
