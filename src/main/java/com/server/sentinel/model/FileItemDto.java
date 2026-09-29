package com.server.sentinel.model;

import java.time.ZonedDateTime;

public class FileItemDto {
    private String filename;
    private long size;
    private String contentType;
    private ZonedDateTime lastModified;
    private String streamUrl;
    private String presignedUrl;

    public FileItemDto() {
    }

    public FileItemDto(String filename, long size, String contentType, ZonedDateTime lastModified, String streamUrl, String presignedUrl) {
        this.filename = filename;
        this.size = size;
        this.contentType = contentType;
        this.lastModified = lastModified;
        this.streamUrl = streamUrl;
        this.presignedUrl = presignedUrl;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public ZonedDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(ZonedDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public void setStreamUrl(String streamUrl) {
        this.streamUrl = streamUrl;
    }

    public String getPresignedUrl() {
        return presignedUrl;
    }

    public void setPresignedUrl(String presignedUrl) {
        this.presignedUrl = presignedUrl;
    }
}
