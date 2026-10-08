package com.server.sentinel.service;

import com.server.sentinel.model.FileItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path rootStoragePath;

    public FileStorageService(@Value("${sentinel.storage.path:data/storage}") String storageDir) {
        this.rootStoragePath = Paths.get(storageDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            if (!Files.exists(rootStoragePath)) {
                Files.createDirectories(rootStoragePath);
                log.info("Da khoi tao thu muc luu tru tep tin tai: {}", rootStoragePath);
            }
        } catch (IOException e) {
            log.error("Khong the khoi tao thu muc luu tru: {}", e.getMessage());
        }
    }

    /**
     * Chặn triệt để Directory Traversal (../ hoặc ..\)
     */
    public Path resolveAndVerify(String relativePath) {
        String cleanPath = (relativePath == null || relativePath.trim().isEmpty()) ? "" : relativePath.trim();
        // Loại bỏ slash dẫn đầu
        if (cleanPath.startsWith("/") || cleanPath.startsWith("\\")) {
            cleanPath = cleanPath.substring(1);
        }

        Path resolved = rootStoragePath.resolve(cleanPath).normalize();
        if (!resolved.startsWith(rootStoragePath)) {
            throw new SecurityException("Truy cap khong hop le: Canh bao tan cong vuot thu muc (Path Traversal Attempt)!");
        }
        return resolved;
    }

    /**
     * Liệt kê tệp tin và thư mục con
     */
    public List<FileItemDto> listFiles(String subPath) throws IOException {
        Path targetDir = resolveAndVerify(subPath);
        if (!Files.exists(targetDir)) {
            throw new NoSuchFileException("Thu muc khong ton tai: " + subPath);
        }
        if (!Files.isDirectory(targetDir)) {
            throw new IllegalArgumentException("Duong dan khong phai la thu muc: " + subPath);
        }

        List<FileItemDto> items = new ArrayList<>();
        try (Stream<Path> stream = Files.list(targetDir)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                String name = path.getFileName().toString();
                String relPath = rootStoragePath.relativize(path).toString().replace("\\", "/");
                boolean isDir = attrs.isDirectory();
                long size = isDir ? 0 : attrs.size();
                long lastModified = attrs.lastModifiedTime().toMillis();
                String ext = isDir ? "" : getFileExtension(name);
                String mimeType = isDir ? "directory" : guessMimeType(name);

                items.add(new FileItemDto(name, relPath, size, isDir, lastModified, ext, mimeType));
            }
        }

        // Sắp xếp: Thư mục lên đầu, sau đó sắp xếp theo tên A-Z
        items.sort((a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        return items;
    }

    /**
     * Tải lên một hoặc nhiều tệp tin
     */
    public FileItemDto storeFile(String targetSubDir, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Tep tin rong.");
        }

        Path targetDir = resolveAndVerify(targetSubDir);
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        String originalFilename = Paths.get(file.getOriginalFilename()).getFileName().toString();
        // Tránh ghi đè nếu trùng tên bằng cách thêm suffix thời gian
        Path destination = targetDir.resolve(originalFilename);
        if (Files.exists(destination)) {
            String nameWithoutExt = originalFilename;
            String ext = "";
            int dotIdx = originalFilename.lastIndexOf('.');
            if (dotIdx > 0) {
                nameWithoutExt = originalFilename.substring(0, dotIdx);
                ext = originalFilename.substring(dotIdx);
            }
            destination = targetDir.resolve(nameWithoutExt + "_" + System.currentTimeMillis() + ext);
        }

        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        log.info("Da luu file thanh cong tai: {}", destination);

        String relPath = rootStoragePath.relativize(destination).toString().replace("\\", "/");
        return new FileItemDto(
                destination.getFileName().toString(),
                relPath,
                Files.size(destination),
                false,
                Files.getLastModifiedTime(destination).toMillis(),
                getFileExtension(destination.getFileName().toString()),
                guessMimeType(destination.getFileName().toString())
        );
    }

    /**
     * Tạo thư mục mới
     */
    public boolean createDirectory(String parentSubDir, String dirName) throws IOException {
        if (dirName == null || dirName.trim().isEmpty() || dirName.contains("/") || dirName.contains("\\")) {
            throw new IllegalArgumentException("Ten thu muc khong hop le.");
        }
        Path parent = resolveAndVerify(parentSubDir);
        Path newDir = parent.resolve(dirName.trim()).normalize();
        if (!newDir.startsWith(rootStoragePath)) {
            throw new SecurityException("Truy cap khong hop le.");
        }
        if (Files.exists(newDir)) {
            return false;
        }
        Files.createDirectories(newDir);
        return true;
    }

    /**
     * Tải tài nguyên file để Download
     */
    public Resource loadFileAsResource(String relativePath) throws MalformedURLException, NoSuchFileException {
        Path file = resolveAndVerify(relativePath);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            throw new NoSuchFileException("Tep tin khong ton tai hoac la thu muc: " + relativePath);
        }
        Resource resource = new UrlResource(file.toUri());
        if (resource.exists() && resource.isReadable()) {
            return resource;
        } else {
            throw new RuntimeException("Khong the doc tep tin: " + relativePath);
        }
    }

    /**
     * Đọc nội dung tệp text (giới hạn 1MB) để xem trước / logs
     */
    public String readTextFile(String relativePath) throws IOException {
        Path file = resolveAndVerify(relativePath);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            throw new NoSuchFileException("Khong tim thay tep tin.");
        }
        long size = Files.size(file);
        if (size > 2 * 1024 * 1024) { // 2MB
            throw new IllegalArgumentException("Tep tin qua lon de xem truoc truc tiep (Dung luong: " + (size / 1024) + " KB). Vui long tai xuong.");
        }
        return Files.readString(file);
    }

    /**
     * Xóa file hoặc thư mục (xóa đệ quy an toàn)
     */
    public boolean deleteItem(String relativePath) throws IOException {
        if (relativePath == null || relativePath.trim().isEmpty() || relativePath.equals("/")) {
            throw new IllegalArgumentException("Khong the xoa thu muc goc.");
        }
        Path target = resolveAndVerify(relativePath);
        if (!Files.exists(target)) {
            return false;
        }

        if (Files.isDirectory(target)) {
            // Xóa đệ quy toàn bộ thư mục con
            try (Stream<Path> walk = Files.walk(target)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } else {
            Files.delete(target);
        }
        return true;
    }

    private String getFileExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return (idx > 0 && idx < filename.length() - 1) ? filename.substring(idx + 1).toLowerCase() : "";
    }

    public String guessMimeType(String filename) {
        String ext = getFileExtension(filename);
        switch (ext) {
            case "txt": case "log": return "text/plain";
            case "json": return "application/json";
            case "xml": return "application/xml";
            case "yml": case "yaml": return "text/yaml";
            case "csv": return "text/csv";
            case "html": return "text/html";
            case "png": return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "svg": return "image/svg+xml";
            case "pdf": return "application/pdf";
            case "zip": return "application/zip";
            case "tar": case "gz": return "application/gzip";
            case "mp4": case "m4v": return "video/mp4";
            case "webm": return "video/webm";
            case "mkv": return "video/x-matroska";
            case "mov": return "video/quicktime";
            case "avi": return "video/x-msvideo";
            case "mp3": return "audio/mpeg";
            case "wav": return "audio/wav";
            case "ogg": return "audio/ogg";
            default: return "application/octet-stream";
        }
    }

    /**
     * Giải nén file ZIP hoặc RAR vào thư mục cùng cấp (hoặc thư mục con mang tên file)
     */
    public String extractArchive(String relativePath) throws IOException {
        Path archiveFile = resolveAndVerify(relativePath);
        if (!Files.exists(archiveFile) || Files.isDirectory(archiveFile)) {
            throw new NoSuchFileException("Tệp nén không tồn tại: " + relativePath);
        }

        String filename = archiveFile.getFileName().toString();
        String ext = getFileExtension(filename);
        if (!"zip".equalsIgnoreCase(ext) && !"rar".equalsIgnoreCase(ext)) {
            throw new IllegalArgumentException("Định dạng không được hỗ trợ. Chỉ hỗ trợ .zip và .rar");
        }

        // Tạo thư mục đích có tên là tên file (bỏ đuôi .zip/.rar) trong cùng thư mục cha
        String folderName = filename;
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            folderName = filename.substring(0, dot);
        }

        Path targetDir = archiveFile.getParent().resolve(folderName);
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        if ("zip".equalsIgnoreCase(ext)) {
            extractZip(archiveFile, targetDir);
        } else {
            extractRar(archiveFile, targetDir);
        }

        return rootStoragePath.relativize(targetDir).toString().replace("\\", "/");
    }

    /**
     * Lấy danh sách các chunk đã tải lên thành công của một uploadId
     */
    public List<Integer> getUploadedChunks(String uploadId) {
        String cleanId = uploadId.replaceAll("[^a-zA-Z0-9_-]", "");
        Path chunkDir = Paths.get(System.getProperty("java.io.tmpdir"), "sentinel_chunks", cleanId);
        if (!Files.exists(chunkDir)) {
            return Collections.emptyList();
        }

        List<Integer> chunks = new ArrayList<>();
        try (Stream<Path> stream = Files.list(chunkDir)) {
            stream.forEach(p -> {
                String name = p.getFileName().toString();
                if (name.endsWith(".part")) {
                    try {
                        int idx = Integer.parseInt(name.replace(".part", ""));
                        chunks.add(idx);
                    } catch (NumberFormatException ignored) {}
                }
            });
        } catch (IOException e) {
            log.error("Lỗi khi đọc danh sách chunk {}: {}", uploadId, e.getMessage());
        }
        Collections.sort(chunks);
        return chunks;
    }

    /**
     * Lưu 1 chunk của file vào thư mục tạm
     */
    public void saveChunk(String uploadId, int chunkIndex, MultipartFile file) throws IOException {
        String cleanId = uploadId.replaceAll("[^a-zA-Z0-9_-]", "");
        Path chunkDir = Paths.get(System.getProperty("java.io.tmpdir"), "sentinel_chunks", cleanId);
        if (!Files.exists(chunkDir)) {
            Files.createDirectories(chunkDir);
        }

        Path chunkFile = chunkDir.resolve(chunkIndex + ".part");
        file.transferTo(chunkFile.toFile());
    }

    /**
     * Ghép toàn bộ chunk lại thành file hoàn chỉnh
     */
    public void mergeChunks(String uploadId, int totalChunks, String targetPath, String fileName) throws IOException {
        String cleanId = uploadId.replaceAll("[^a-zA-Z0-9_-]", "");
        Path chunkDir = Paths.get(System.getProperty("java.io.tmpdir"), "sentinel_chunks", cleanId);
        if (!Files.exists(chunkDir)) {
            throw new NoSuchFileException("Không tìm thấy dữ liệu chunk cho uploadId: " + uploadId);
        }

        Path targetDir = resolveAndVerify(targetPath);
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        Path finalFile = targetDir.resolve(fileName);
        try (java.io.OutputStream out = new java.io.BufferedOutputStream(Files.newOutputStream(finalFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {
            for (int i = 0; i < totalChunks; i++) {
                Path partFile = chunkDir.resolve(i + ".part");
                if (!Files.exists(partFile)) {
                    throw new IOException("Thiếu chunk số " + i + " để ghép file.");
                }
                Files.copy(partFile, out);
            }
        }

        // Xóa dọn dẹp thư mục chunk tạm
        try (Stream<Path> stream = Files.walk(chunkDir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {}
            });
        }
    }

    /**
     * Hủy phiên upload chunk và xóa file tạm
     */
    public void cancelChunkUpload(String uploadId) {
        String cleanId = uploadId.replaceAll("[^a-zA-Z0-9_-]", "");
        Path chunkDir = Paths.get(System.getProperty("java.io.tmpdir"), "sentinel_chunks", cleanId);
        if (Files.exists(chunkDir)) {
            try (Stream<Path> stream = Files.walk(chunkDir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {}
                });
            } catch (IOException e) {
                log.error("Lỗi khi xóa chunk tạm {}: {}", uploadId, e.getMessage());
            }
        }
    }

    /**
     * Chuyển đổi và chuẩn bị HLS streaming cho video nếu chưa có
     */
    public Path prepareHlsStream(String relativeVideoPath) throws IOException, InterruptedException {
        Path videoFile = resolveAndVerify(relativeVideoPath);
        if (!Files.exists(videoFile) || Files.isDirectory(videoFile)) {
            throw new NoSuchFileException("Không tìm thấy tệp video: " + relativeVideoPath);
        }

        String videoHash = Integer.toHexString((relativeVideoPath + "_" + Files.size(videoFile)).hashCode());
        Path hlsDir = Paths.get(System.getProperty("java.io.tmpdir"), "sentinel_hls", videoHash);
        Path playlist = hlsDir.resolve("playlist.m3u8");

        if (Files.exists(playlist)) {
            return playlist;
        }

        Files.createDirectories(hlsDir);

        // Đường dẫn tương đối cho các segment để trình duyệt gọi đúng endpoint API
        String segmentBaseUrl = "/api/storage/hls/segment?path=" + java.net.URLEncoder.encode(relativeVideoPath, java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\+", "%20") + "&seg=";

        // Chạy ffmpeg cắt nhỏ thành các segment 4 giây và nhúng base url
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-i", videoFile.toAbsolutePath().toString(),
                "-c:v", "copy", "-c:a", "aac", "-b:a", "128k",
                "-hls_time", "4",
                "-hls_list_size", "0",
                "-hls_base_url", segmentBaseUrl,
                "-hls_segment_filename", hlsDir.resolve("segment_%03d.ts").toAbsolutePath().toString(),
                playlist.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0 || !Files.exists(playlist)) {
            // Thử encode lại nếu codec video copy không khớp
            ProcessBuilder fallbackPb = new ProcessBuilder(
                    "ffmpeg", "-i", videoFile.toAbsolutePath().toString(),
                    "-c:v", "libx264", "-preset", "ultrafast", "-crf", "23",
                    "-c:a", "aac", "-b:a", "128k",
                    "-hls_time", "4",
                    "-hls_list_size", "0",
                    "-hls_base_url", segmentBaseUrl,
                    "-hls_segment_filename", hlsDir.resolve("segment_%03d.ts").toAbsolutePath().toString(),
                    playlist.toAbsolutePath().toString()
            );
            Process fallbackProcess = fallbackPb.start();
            int fallbackExit = fallbackProcess.waitFor();
            if (fallbackExit != 0 || !Files.exists(playlist)) {
                throw new IOException("Lỗi khi xử lý HLS bằng ffmpeg (mã thoát: " + fallbackExit + ")");
            }
        }

        return playlist;
    }

    public Resource loadHlsSegment(String relativeVideoPath, String segmentName) throws MalformedURLException {
        Path videoFile = resolveAndVerify(relativeVideoPath);
        long size = 0;
        try { size = Files.size(videoFile); } catch (Exception ignored) {}
        String videoHash = Integer.toHexString((relativeVideoPath + "_" + size).hashCode());
        Path segmentPath = Paths.get(System.getProperty("java.io.tmpdir"), "sentinel_hls", videoHash, segmentName);
        return new UrlResource(segmentPath.toUri());
    }

    private void extractZip(Path zipFile, Path targetDir) throws IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Chống tấn công Zip Slip (Path Traversal bên trong file zip)
                Path newPath = targetDir.resolve(entry.getName()).normalize();
                if (!newPath.startsWith(targetDir)) {
                    throw new SecurityException("Cảnh báo Zip Slip: File zip chứa đường dẫn vượt ngoài thư mục đích: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    if (newPath.getParent() != null && !Files.exists(newPath.getParent())) {
                        Files.createDirectories(newPath.getParent());
                    }
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private void extractRar(Path rarFile, Path targetDir) throws IOException {
        try {
            com.github.junrar.Junrar.extract(rarFile.toFile(), targetDir.toFile());
        } catch (com.github.junrar.exception.RarException e) {
            throw new IOException("Lỗi khi giải nén tệp RAR: " + e.getMessage(), e);
        }
    }
}
