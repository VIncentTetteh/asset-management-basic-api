package com.assetiq.models;

import com.assetiq.enums.OrganisationStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;



import java.util.Set;

@Entity
@Table(name = "organisation")
@Data
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Organisation extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(unique = true)
    private String registrationNumber;

    @Column(unique = true)
    private String taxId;

    private String industry;

    private String country;

    private String address;

    @Column(unique = true)
    private String contactEmail;

    private String contactPhone;

    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrganisationStatus status = OrganisationStatus.ACTIVE;

    @OneToMany(mappedBy = "organisation", cascade = CascadeType.ALL)
    private Set<Department> departments;

    @OneToMany(mappedBy = "organisation", cascade = CascadeType.ALL)
    private Set<User> users;

    @OneToMany(mappedBy = "organisation", cascade = CascadeType.ALL)
    private Set<Asset> assets;

    @OneToMany(mappedBy = "organisation", cascade = CascadeType.ALL)
    private Set<Role> roles;

}
