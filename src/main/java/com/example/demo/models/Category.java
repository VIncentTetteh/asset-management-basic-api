package com.example.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "category")
public class Category extends BaseEntity {

    @Column(nullable = false)
    private String name;

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
