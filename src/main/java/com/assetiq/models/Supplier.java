package com.assetiq.models;

import com.assetiq.enums.SupplierStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Set;

@Entity
@Table(name = "supplier", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "registration_number",
                "organisation_id" }, name = "uk_supplier_regnum_per_org"),
        @UniqueConstraint(columnNames = { "email", "organisation_id" }, name = "uk_supplier_email_per_org"),
        @UniqueConstraint(columnNames = { "tax_id", "organisation_id" }, name = "uk_supplier_taxid_per_org")
})
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
    @EqualsAndHashCode.Exclude
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
