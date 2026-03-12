package com.example.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "webhook")
@Getter
@Setter
public class Webhook extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    /** Comma-separated event names e.g. "asset.created,asset.updated" */
    @Column(name = "events", length = 1000)
    private String events;

    @Column(nullable = false)
    private boolean active = true;

    /** HMAC-SHA256 signing secret (stored plain; encrypt at rest in prod). */
    @Column(name = "secret", length = 200)
    private String secret;

    @Column(name = "delivery_count")
    private long deliveryCount = 0;

    @Column(name = "failure_count")
    private long failureCount = 0;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;
}
