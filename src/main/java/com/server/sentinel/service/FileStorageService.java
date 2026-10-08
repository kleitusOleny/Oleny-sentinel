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
