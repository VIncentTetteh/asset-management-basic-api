package com.example.demo.models;

import com.example.demo.enums.SupplierStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "supplier")
public class Supplier extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String registrationNumber;

    private String contactPerson;

    @Column(unique = true)
    private String email;

    private String phone;

    private String address;

    @Column(columnDefinition = "TEXT")
    private String bankDetails; // JSON format

    @Column(unique = true)
    private String taxId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SupplierStatus status = SupplierStatus.ACTIVE;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Organisation organisation;

    @OneToMany(mappedBy = "supplier")
    private Set<Asset> assets;

    @OneToMany(mappedBy = "supplier")
    private Set<PurchaseOrder> purchaseOrders;

}

