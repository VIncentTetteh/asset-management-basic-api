package com.assetiq.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;
import java.time.Instant;
@Entity
@Table(
        name = "idempotency_record",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_idempotency_org_op_key",
                        columnNames = {"organisation_id", "operation", "idempotency_key"}
                )
        },
        indexes = {
                @Index(name = "idx_idempotency_org_op", columnList = "organisation_id,operation")
        }
)
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
public class IdempotencyRecord extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;
    @Column(nullable = false, length = 120)
    private String operation;
    @Column(name = "idempotency_key", nullable = false, length = 220)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @Column(name = "response_job_id", nullable = true)
    private UUID responseJobId;
    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "response_json", columnDefinition = "text")
    private String responseJson;
}
