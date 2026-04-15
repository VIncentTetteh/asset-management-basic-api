package com.assetiq.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class WebhookDeliveryDto {

    private UUID deliveryId;
    private UUID webhookId;
    private String eventName;
    private String payload;
    private Integer statusCode;
    private String responseBody;
    private Long responseTimeMs;
    private int attempts;
    private String status;
    private Instant triggeredAt;
}
