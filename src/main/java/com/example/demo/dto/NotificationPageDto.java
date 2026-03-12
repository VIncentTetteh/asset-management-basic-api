package com.example.demo.dto;

import lombok.Data;

import java.util.List;

@Data
public class NotificationPageDto {
    private long totalNotifications;
    private long unreadCount;
    private int limit;
    private List<NotificationDto> notifications;
}
