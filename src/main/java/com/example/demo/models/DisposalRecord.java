package com.example.demo.models;

import com.example.demo.enums.DisposalMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "disposal_record")
public class DisposalRecord extends BaseEntity {

    @ManyToOne
    @JoinColumn(nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisposalMethod disposalMethod;

    @Column(nullable = false)
    private LocalDate disposalDate;

    @Column(precision = 15, scale = 2)
    private BigDecimal saleValue;

    @ManyToOne
    @JoinColumn(nullable = false)
    private User approvedBy;

    @Column(columnDefinition = "TEXT")
    private String reason;

    private String complianceDocumentUrl;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Organisation organisation;

}

