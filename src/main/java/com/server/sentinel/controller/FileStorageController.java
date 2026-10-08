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
     * Tải xuống hoặc xem trực tiếp (inline) tệp tin (ảnh, video, audio, pdf)
     * GET /api/storage/raw?path=filename.ext hoặc GET /api/storage/download?path=filename.ext&inline=true
     */
    @GetMapping({"/download", "/raw"})
    public ResponseEntity<?> downloadFile(
            @RequestParam("path") String path,
            @RequestParam(value = "inline", defaultValue = "false") boolean inline) {
        try {
            Resource resource = fileStorageService.loadFileAsResource(path);
            String filename = resource.getFilename();
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            String mimeType = fileStorageService.guessMimeType(filename);
            String dispositionType = inline ? "inline" : "attachment";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mimeType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, dispositionType + "; filename*=UTF-8''" + encodedFilename)
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

    /**
     * Giải nén file ZIP hoặc RAR
     * POST /api/storage/extract
     */
    @PostMapping("/extract")
    public ResponseEntity<?> extractFile(@RequestBody Map<String, String> payload) {
        String path = payload.get("path");
        if (path == null || path.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Đường dẫn tệp nén không được để trống."));
        }
        try {
            String extractedTo = fileStorageService.extractArchive(path);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Đã giải nén tệp thành công.",
                    "extractedTo", extractedTo
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Lỗi giải nén: " + e.getMessage()));
        }
    }

    /**
     * Lấy danh sách chunk đã tải lên thành công để hỗ trợ Resume
     * GET /api/storage/upload/chunk/status?uploadId=...
     */
    @GetMapping("/upload/chunk/status")
    public ResponseEntity<?> getChunkUploadStatus(@RequestParam("uploadId") String uploadId) {
        try {
            List<Integer> uploadedChunks = fileStorageService.getUploadedChunks(uploadId);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "uploadId", uploadId,
                    "uploadedChunks", uploadedChunks
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Tải lên 1 chunk phân đoạn
     * POST /api/storage/upload/chunk
     */
    @PostMapping("/upload/chunk")
    public ResponseEntity<?> uploadChunk(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("file") MultipartFile file) {
        try {
            fileStorageService.saveChunk(uploadId, chunkIndex, file);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "uploadId", uploadId,
                    "chunkIndex", chunkIndex
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Lỗi lưu chunk: " + e.getMessage()));
        }
    }

    /**
     * Hợp nhất toàn bộ chunk thành file hoàn chỉnh
     * POST /api/storage/upload/chunk/merge
     */
    @PostMapping("/upload/chunk/merge")
    public ResponseEntity<?> mergeChunks(@RequestBody Map<String, Object> payload) {
        String uploadId = (String) payload.get("uploadId");
        String targetPath = (String) payload.getOrDefault("targetPath", "");
        String fileName = (String) payload.get("fileName");
        Number totalChunksNum = (Number) payload.get("totalChunks");

        if (uploadId == null || fileName == null || totalChunksNum == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Thiếu thông số uploadId, fileName hoặc totalChunks"));
        }

        try {
            fileStorageService.mergeChunks(uploadId, totalChunksNum.intValue(), targetPath, fileName);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Ghép tệp tin hoàn tất.",
                    "fileName", fileName
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Lỗi ghép file: " + e.getMessage()));
        }
    }

    /**
     * Hủy phiên tải chunk
     * POST /api/storage/upload/chunk/cancel
     */
    @PostMapping("/upload/chunk/cancel")
    public ResponseEntity<?> cancelChunkUpload(@RequestBody Map<String, String> payload) {
        String uploadId = payload.get("uploadId");
        if (uploadId != null) {
            fileStorageService.cancelChunkUpload(uploadId);
        }
        return ResponseEntity.ok(Map.of("status", "success", "message", "Đã hủy phiên upload chunk."));
    }

    /**
     * Lấy playlist HLS (.m3u8) cho video
     * GET /api/storage/hls/playlist?path=video.mp4
     */
    @GetMapping("/hls/playlist")
    public ResponseEntity<?> getHlsPlaylist(@RequestParam("path") String path) {
        try {
            java.nio.file.Path playlist = fileStorageService.prepareHlsStream(path);
            Resource resource = new org.springframework.core.io.UrlResource(playlist.toUri());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Không thể chuẩn bị luồng HLS: " + e.getMessage()));
        }
    }

    /**
     * Tải phân đoạn video HLS (.ts)
     * GET /api/storage/hls/segment?path=video.mp4&seg=segment_001.ts
     */
    @GetMapping("/hls/segment")
    public ResponseEntity<?> getHlsSegment(
            @RequestParam("path") String path,
            @RequestParam("seg") String segmentName) {
        try {
            Resource resource = fileStorageService.loadHlsSegment(path, segmentName);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/mp2t"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
