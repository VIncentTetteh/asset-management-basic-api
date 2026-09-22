package com.assetiq.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
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
@Table(name = "asset_tag_sequence")
@Getter
@Setter
public class AssetTagSequence {

    @EmbeddedId
    private Key id;

    @Column(name = "next_number", nullable = false)
    private long nextNumber;

    /** Organisation plus tag prefix: the natural key of a counter. */
    @Embeddable
    @Getter
    @Setter
    public static class Key implements Serializable {

        @Column(name = "organisation_id", nullable = false)
        private UUID organisationId;

        @Column(name = "prefix", nullable = false, length = 64)
        private String prefix;

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return Objects.equals(organisationId, key.organisationId) && Objects.equals(prefix, key.prefix);
        }

        @Override
        public int hashCode() {
            return Objects.hash(organisationId, prefix);
        }
    }
}
