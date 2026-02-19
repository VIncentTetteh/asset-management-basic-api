package com.example.demo.models;

import com.example.demo.enums.AssetState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "organisation_id"},
                      name = "uk_asset_name_per_organisation")
})
public class Asset extends BaseEntity {

    private String name;

    private String category;

    private BigDecimal purchaseCost;

    private Integer usefulLifeInYears;

    @Enumerated(EnumType.STRING)
    private AssetState state = AssetState.REGISTERED;

    @ManyToOne
    private Department department;

    @ManyToOne
    private Organisation organisation;

}
