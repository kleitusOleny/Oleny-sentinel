package com.server.sentinel.model;

public class CalendarEventDto {
    private String id;
    private String title;
    private String description;
    private String startDate; // ISO string: YYYY-MM-DD or YYYY-MM-DDTHH:mm:ss
    private String endDate;   // ISO string: YYYY-MM-DD or YYYY-MM-DDTHH:mm:ss
    private boolean allDay;
    private String color;     // e.g. "blue", "indigo", "emerald", "amber", "rose", "purple"
    private String category;  // e.g. "Work", "Server", "Meeting", "Personal", "Reminder"
    private int reminderMinutes; // e.g. 0 (none), 5, 10, 15, 30, 60
    private String recurrence; // "none", "daily", "weekly", "monthly"
    private int repeatWeeks;   // Số tuần lặp lại nếu recurrence là weekly (mặc định 1)
    private boolean notified; // flag to prevent duplicate notification
    private long createdAt;

    public CalendarEventDto() {
    }

    public CalendarEventDto(String id, String title, String description, String startDate, String endDate,
                            boolean allDay, String color, String category, int reminderMinutes,
                            String recurrence, int repeatWeeks, boolean notified, long createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.allDay = allDay;
        this.color = color;
        this.category = category;
        this.reminderMinutes = reminderMinutes;
        this.recurrence = recurrence;
        this.repeatWeeks = repeatWeeks > 0 ? repeatWeeks : 1;
        this.notified = notified;
        this.createdAt = createdAt;
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

    public boolean isAllDay() {
        return allDay;
    }

    public void setAllDay(boolean allDay) {
        this.allDay = allDay;
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

    public int getReminderMinutes() {
        return reminderMinutes;
    }

    public void setReminderMinutes(int reminderMinutes) {
        this.reminderMinutes = reminderMinutes;
    }

    public String getRecurrence() {
        return recurrence;
    }

    public void setRecurrence(String recurrence) {
        this.recurrence = recurrence;
    }

    public int getRepeatWeeks() {
        return repeatWeeks;
    }

    public void setRepeatWeeks(int repeatWeeks) {
        this.repeatWeeks = repeatWeeks;
    }

    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
