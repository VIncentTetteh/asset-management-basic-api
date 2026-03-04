package com.example.demo.models;

import com.example.demo.enums.DepreciationMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "depreciation_policy")
public class DepreciationPolicy extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepreciationMethod method;

    private Integer usefulLifeMonths;

    @Column(precision = 5, scale = 2)
    private BigDecimal salvageValuePercent; // Percentage of original cost

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Organisation organisation;

    @OneToMany(mappedBy = "depreciationPolicy")
    private Set<Category> categories;

}

