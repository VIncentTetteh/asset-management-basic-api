package com.assetiq.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@Entity
@Table(name = "category")
@Data
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Category extends BaseEntity {

    @Column(nullable = false)
    private String name;

    // Stored as TEXT: the column is free text with no meaningful upper bound, and
    // the migrated schema declares it TEXT. Pinning a varchar length here would
    // both fail ddl-auto=validate and invite a truncating migration.
    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    private Category parentCategory;

    @OneToMany(mappedBy = "parentCategory")
    private Set<Category> subCategories;

    @ManyToOne(fetch = FetchType.LAZY)
    private DepreciationPolicy depreciationPolicy;

    private Integer defaultWarrantyPeriodMonths;

    private String assetPrefixCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Organisation organisation;

    @OneToMany(mappedBy = "category")
    private Set<Asset> assets;

}
