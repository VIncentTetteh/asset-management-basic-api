package com.assetiq.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A saved column mapping, so the second monthly export from the same system is one
 * click rather than thirty dropdowns.
 *
 * <p>Per organisation and per entity type, named by the user. Names are unique within
 * that triple among live rows, enforced by a partial unique index.</p>
 */
@Entity
@Table(name = "import_mapping_preset", indexes = {
        @Index(name = "idx_import_mapping_preset_org_type", columnList = "organisation_id,entity_type")
})
@Getter
@Setter
public class ImportMappingPreset extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    /** {@link com.assetiq.imports.ImportEntityType} name. */
    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(nullable = false, length = 200)
    private String name;

    /**
     * The mapping as JSON: field name to column header text, not column index. A preset
     * that stored indices would silently misalign the moment the source system adds a
     * column, which is precisely the failure this feature exists to avoid.
     */
    @Column(name = "mapping_json", nullable = false, columnDefinition = "text")
    private String mappingJson;
}
