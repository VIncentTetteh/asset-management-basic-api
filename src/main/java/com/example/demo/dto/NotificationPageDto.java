package com.example.demo.dto;

import lombok.Data;

@Data
public class NotificationPageDto extends PagedResponseDto<NotificationDto> {
    private long unreadCount;
}
