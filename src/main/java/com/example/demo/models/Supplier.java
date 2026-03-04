package com.example.demo.models;

import com.example.demo.enums.SupplierStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "supplier", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "registration_number",
                "organisation_id" }, name = "uk_supplier_regnum_per_org"),
        @UniqueConstraint(columnNames = { "email", "organisation_id" }, name = "uk_supplier_email_per_org"),
        @UniqueConstraint(columnNames = { "tax_id", "organisation_id" }, name = "uk_supplier_taxid_per_org")
})
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
    private Organisation organisation;

    @OneToMany(mappedBy = "supplier")
    private Set<Asset> assets;

    @OneToMany(mappedBy = "supplier")
    private Set<PurchaseOrder> purchaseOrders;

}
