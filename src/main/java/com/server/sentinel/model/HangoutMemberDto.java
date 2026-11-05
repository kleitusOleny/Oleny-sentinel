package com.server.sentinel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HangoutMemberDto {
    private String id;
    private String name;
    // Danh sach cac buoi ranh dinh dang "YYYY-MM-DD_morning", "YYYY-MM-DD_afternoon", "YYYY-MM-DD_evening"
    private List<String> availableSlots;
    // Deprecated tuong thich nguoc
    private List<String> availableDates;
    private String color;
    private Long updatedAt;
    // Mat khau / ma PIN luu truc tiep (khong hash)
    private String pin;

    public HangoutMemberDto() {
    }

    public HangoutMemberDto(String id, String name, List<String> availableSlots, String color, Long updatedAt, String pin) {
        this.id = id;
        this.name = name;
        this.availableSlots = availableSlots;
        this.color = color;
        this.updatedAt = updatedAt;
        this.pin = pin;
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

    public List<String> getAvailableSlots() {
        return availableSlots;
    }

    public void setAvailableSlots(List<String> availableSlots) {
        this.availableSlots = availableSlots;
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

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }
}
