package com.example.demo.models;

import com.example.demo.enums.TransferStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "asset_transfer")
public class AssetTransfer extends BaseEntity {

    @ManyToOne
    @JoinColumn(nullable = false)
    private Asset asset;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Department fromDepartment;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Department toDepartment;

    @ManyToOne
    private Location fromLocation;

    @ManyToOne
    private Location toLocation;

    @ManyToOne
    @JoinColumn(nullable = false)
    private User requestedBy;

    @ManyToOne
    private User approvedBy;

    private LocalDate transferDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TransferStatus status = TransferStatus.REQUESTED;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Organisation organisation;

}
