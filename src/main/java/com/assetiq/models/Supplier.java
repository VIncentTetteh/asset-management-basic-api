package com.assetiq.models;

import com.assetiq.enums.SupplierStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Set;

// Natural key is unique among live rows only: partial unique index uq_supplier_org_email_live / _tax_id_live / _regnum_live
// (V46, WHERE deleted_at IS NULL). JPA cannot declare a partial index, so no
// @UniqueConstraint here; declaring the full one would claim soft-deleted rows count.
@Entity
@Table(name = "supplier")
@Data
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Supplier extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column
    private String registrationNumber;

    private String contactPerson;

    @Column
    private String email;

    private String phone;

    // Stored as TEXT: the column is free text with no meaningful upper bound, and
    // the migrated schema declares it TEXT. Pinning a varchar length here would
    // both fail ddl-auto=validate and invite a truncating migration.
    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(columnDefinition = "TEXT")
    private String bankDetails; // JSON format

    @Column
    private String taxId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SupplierStatus status = SupplierStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    @ToString.Exclude
    private Organisation organisation;

    @OneToMany(mappedBy = "supplier")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<Asset> assets;

    @OneToMany(mappedBy = "supplier")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<PurchaseOrder> purchaseOrders;

}
