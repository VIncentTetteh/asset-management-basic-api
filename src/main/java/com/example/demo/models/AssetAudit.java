package com.example.demo.models;

import com.example.demo.enums.AuditStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "asset_audit")
public class AssetAudit extends BaseEntity {

    @ManyToOne
    @JoinColumn(nullable = false)
    private Organisation organisation;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Department department;

    @Column(nullable = false, updatable = false)
    private java.time.LocalDate auditDate;

    @ManyToOne
    @JoinColumn(nullable = false)
    private User conductedBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AuditStatus status = AuditStatus.PLANNED;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @OneToMany(mappedBy = "audit", cascade = CascadeType.ALL)
    private Set<AuditItem> auditItems;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (this.auditDate == null) {
            this.auditDate = java.time.LocalDate.now();
        }
    }

}
