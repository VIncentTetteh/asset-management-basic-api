package com.example.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "audit_item")
public class AuditItem {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne
    @JoinColumn(nullable = false)
    private AssetAudit audit;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Asset asset;

    private String expectedLocation;

    private String actualLocation;

    private String condition;

    private Boolean discrepancyFlag = false;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(nullable = false, updatable = false)
    private java.time.Instant createdAt = java.time.Instant.now();

}
