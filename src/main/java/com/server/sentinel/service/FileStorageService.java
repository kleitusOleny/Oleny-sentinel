package com.server.sentinel.service;

import com.server.sentinel.model.FileItemDto;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.external-endpoint:http://localhost:9000}")
    private String externalEndpoint;

    @Value("${minio.endpoint:http://localhost:9000}")
    private String internalEndpoint;

    public FileStorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * Upload file vào MinIO
     */
    public FileItemDto uploadFile(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            originalFilename = "file_" + System.currentTimeMillis();
        }

        // Làm sạch tên file
        String filename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String contentType = file.getContentType();
        if (contentType == null || contentType.isEmpty()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        long size = file.getSize();

        log.info("Uploading file '{}' (size: {} bytes, type: {}) to bucket '{}'", filename, size, contentType, bucketName);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filename)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );
        }

        String streamUrl = "/api/files/stream/" + filename;
        String presignedUrl = generatePresignedUrl(filename, 7 * 24 * 60); // 7 ngày

        return new FileItemDto(filename, size, contentType, java.time.ZonedDateTime.now(), streamUrl, presignedUrl);
    }

    /**
     * Sinh Pre-signed URL cho phép truy cập trực tiếp
     */
    public String generatePresignedUrl(String filename, int expiryMinutes) {
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(filename)
                            .expiry(expiryMinutes, TimeUnit.MINUTES)
                            .build()
            );

            // Nếu MinIO chạy trong docker network nội bộ (vd: http://minio:9000), 
            // đổi sang endpoint public bên ngoài (vd: http://localhost:9000) để client truy cập được
            if (url != null && !internalEndpoint.equals(externalEndpoint)) {
                url = url.replace(internalEndpoint, externalEndpoint);
            }
            return url;
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for file '{}': {}", filename, e.getMessage());
            return null;
        }
    }

    /**
     * Lấy danh sách tất cả file trong Bucket
     */
    public List<FileItemDto> listFiles() {
        List<FileItemDto> fileList = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.isDir()) continue;

                String filename = item.objectName();
                long size = item.size();
                String streamUrl = "/api/files/stream/" + filename;
                String presignedUrl = generatePresignedUrl(filename, 7 * 24 * 60);

                // Ước lượng mimeType từ extension
                String mimeType = guessContentType(filename);

                fileList.add(new FileItemDto(
                        filename,
                        size,
                        mimeType,
                        item.lastModified(),
                        streamUrl,
                        presignedUrl
                ));
            }
        } catch (Exception e) {
            log.error("Error listing files from bucket '{}': {}", bucketName, e.getMessage());
        }
        return fileList;
    }

    /**
     * Xóa một file khỏi MinIO
     */
    public boolean deleteFile(String filename) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filename)
                            .build()
            );
            log.info("File '{}' deleted from bucket '{}'", filename, bucketName);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete file '{}': {}", filename, e.getMessage());
            return false;
        }
    }

    /**
     * Streaming file hỗ trợ HTTP Range (Status 206 Partial Content)
     */
    public ResponseEntity<InputStreamResource> streamFile(String filename, String rangeHeader) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filename)
                            .build()
            );

            long fileSize = stat.size();
            String contentType = stat.contentType();
            if (contentType == null || contentType.isEmpty() || contentType.equals(MediaType.APPLICATION_OCTET_STREAM_VALUE)) {
                contentType = guessContentType(filename);
            }

            // Xử lý khi có Range Header từ client (trình duyệt xem video/audio)
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
                if (!ranges.isEmpty()) {
                    HttpRange range = ranges.get(0);
                    long start = range.getRangeStart(fileSize);
                    long end = range.getRangeEnd(fileSize);
                    long rangeLength = end - start + 1;

                    InputStream is = minioClient.getObject(
                            GetObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(filename)
                                    .offset(start)
                                    .length(rangeLength)
                                    .build()
                    );

                    HttpHeaders responseHeaders = new HttpHeaders();
                    responseHeaders.add(HttpHeaders.CONTENT_TYPE, contentType);
                    responseHeaders.add(HttpHeaders.ACCEPT_RANGES, "bytes");
                    responseHeaders.add(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, fileSize));
                    responseHeaders.setContentLength(rangeLength);

                    return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                            .headers(responseHeaders)
                            .body(new InputStreamResource(is));
                }
            }

            // Trả về toàn bộ file nếu không có Range Header
            InputStream is = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filename)
                            .build()
            );

            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.add(HttpHeaders.CONTENT_TYPE, contentType);
            responseHeaders.add(HttpHeaders.ACCEPT_RANGES, "bytes");
            responseHeaders.setContentLength(fileSize);

            return ResponseEntity.ok()
                    .headers(responseHeaders)
                    .body(new InputStreamResource(is));

        } catch (Exception e) {
            log.error("Error streaming file '{}': {}", filename, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private String guessContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".txt") || lower.endsWith(".log")) return "text/plain";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".zip")) return "application/zip";
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
