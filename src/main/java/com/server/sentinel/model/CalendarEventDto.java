package com.server.sentinel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CalendarEventDto {
    private String id;
    private String title;
    private String description;
    private String startDate; // ISO string: YYYY-MM-DD or YYYY-MM-DDTHH:mm:ss
    private String endDate;   // ISO string: YYYY-MM-DD or YYYY-MM-DDTHH:mm:ss
    private Boolean allDay = false;
    private String color = "indigo";     // e.g. "blue", "indigo", "emerald", "amber", "rose", "purple"
    private String category = "Lịch học";  // e.g. "Work", "Server", "Meeting", "Personal", "Reminder", "Lịch học"
    private String location;  // Địa điểm hoặc Phòng học (đối với Lịch học)
    private Integer reminderMinutes = 0; // e.g. 0 (none), 5, 10, 15, 30, 60
    private String recurrence = "none"; // "none", "daily", "weekly", "monthly"
    private Integer repeatWeeks = 1;   // Số tuần lặp lại nếu recurrence là weekly (mặc định 1)
    private Boolean notified = false; // flag to prevent duplicate notification
    private Long createdAt = 0L;

    public CalendarEventDto() {
    }

    public CalendarEventDto(String id, String title, String description, String startDate, String endDate,
                            Boolean allDay, String color, String category, String location, Integer reminderMinutes,
                            String recurrence, Integer repeatWeeks, Boolean notified, Long createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.allDay = allDay != null ? allDay : false;
        this.color = color != null ? color : "indigo";
        this.category = category != null ? category : "Lịch học";
        this.location = location;
        this.reminderMinutes = reminderMinutes != null ? reminderMinutes : 0;
        this.recurrence = recurrence != null ? recurrence : "none";
        this.repeatWeeks = (repeatWeeks != null && repeatWeeks > 0) ? repeatWeeks : 1;
        this.notified = notified != null ? notified : false;
        this.createdAt = createdAt != null ? createdAt : System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public Boolean isAllDay() {
        return allDay != null && allDay;
    }

    public Boolean getAllDay() {
        return allDay != null && allDay;
    }

    public void setAllDay(Boolean allDay) {
        this.allDay = allDay != null ? allDay : false;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getReminderMinutes() {
        return reminderMinutes != null ? reminderMinutes : 0;
    }

    public void setReminderMinutes(Integer reminderMinutes) {
        this.reminderMinutes = reminderMinutes != null ? reminderMinutes : 0;
    }

    public String getRecurrence() {
        return recurrence;
    }

    public void setRecurrence(String recurrence) {
        this.recurrence = recurrence;
    }

    public Integer getRepeatWeeks() {
        return (repeatWeeks != null && repeatWeeks > 0) ? repeatWeeks : 1;
    }

    public void setRepeatWeeks(Integer repeatWeeks) {
        this.repeatWeeks = (repeatWeeks != null && repeatWeeks > 0) ? repeatWeeks : 1;
    }

    public Boolean isNotified() {
        return notified != null && notified;
    }

    public Boolean getNotified() {
        return notified != null && notified;
    }

    public void setNotified(Boolean notified) {
        this.notified = notified != null ? notified : false;
    }

    public Long getCreatedAt() {
        return createdAt != null ? createdAt : 0L;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt != null ? createdAt : 0L;
    }
}
