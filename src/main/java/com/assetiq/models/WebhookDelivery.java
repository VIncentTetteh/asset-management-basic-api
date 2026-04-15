package com.assetiq.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "webhook_delivery")
@Getter
@Setter
public class WebhookDelivery extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_id", nullable = false)
    private Webhook webhook;

    @Column(name = "event_name", nullable = false, length = 100)
    private String eventName;

    /** JSON payload that was sent. */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    /** Round-trip time in milliseconds. */
    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "attempts")
    private int attempts = 1;

    /** "success" | "failed" */
    @Column(name = "status", length = 20)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;
}
