package com.assetiq.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * One asset-tag counter per organisation and prefix (V54).
 *
 * <p>Nothing reads or writes this through JPA — {@code AssetTagAllocator} claims
 * numbers with plain SQL so the row lock is held for as little as possible. The
 * mapping exists so the table is part of the schema Hibernate validates (and
 * creates for the integration tests, which build the schema from the entities).
 */
@Entity
@Table(name = "asset_tag_sequence",
        uniqueConstraints = @UniqueConstraint(name = "uq_asset_tag_sequence_org_prefix",
                columnNames = {"organisation_id", "prefix"}))
@Getter
@Setter
public class AssetTagSequence {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Column(name = "prefix", nullable = false, length = 64)
    private String prefix;

    @Column(name = "next_number", nullable = false)
    private long nextNumber;
}
