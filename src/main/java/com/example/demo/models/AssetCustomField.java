package com.example.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Arbitrary key-value metadata attached to an Asset.
 * Allows organisations to extend asset records without schema changes.
 */
@Entity
@Getter
@Setter
@Table(name = "asset_custom_field", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"asset_id", "field_name"}, name = "uk_asset_custom_field_name")
})
public class AssetCustomField extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "field_value", columnDefinition = "TEXT")
    private String fieldValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;
}
