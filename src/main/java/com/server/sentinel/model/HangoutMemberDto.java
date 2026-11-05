package com.server.sentinel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HangoutMemberDto {
    private String id;
    private String name;
    // Danh sach cac ngay ranh dinh dang "YYYY-MM-DD"
    private List<String> availableDates;
    private String color;
    private Long updatedAt;
    // Ma PIN 4 so ca nhan (lưu hash SHA-256)
    private String pinHash;

    public HangoutMemberDto() {
    }

    public HangoutMemberDto(String id, String name, List<String> availableDates, String color, Long updatedAt, String pinHash) {
        this.id = id;
        this.name = name;
        this.availableDates = availableDates;
        this.color = color;
        this.updatedAt = updatedAt;
        this.pinHash = pinHash;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAvailableDates() {
        return availableDates;
    }

    public void setAvailableDates(List<String> availableDates) {
        this.availableDates = availableDates;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPinHash() {
        return pinHash;
    }

    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }
}
