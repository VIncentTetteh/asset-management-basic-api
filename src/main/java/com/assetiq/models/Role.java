package com.assetiq.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "role")
public class Role extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * The individual permissions granted to this role.
     * Replaces the old JSON {@code permissions} column (removed in V4 migration).
     * Use {@link com.assetiq.services.impl.RoleServiceImpl} to modify permissions;
     * do not mutate this set directly outside a transaction.
     */
    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<RolePermission> rolePermissions = new HashSet<>();

    /**
     * When TRUE this role is a built-in system role that cannot be modified or
     * deleted by organisation admins. Set at org-creation time or via migration.
     */
    @Column(name = "system_role", nullable = false)
    private boolean systemRole = false;

    /**
     * When TRUE the bearer of this role receives every permission defined in the
     * {@link com.assetiq.enums.Permission} enum — no need to enumerate them.
     * Replaces the fragile name-based admin detection in RolePermissionDefaults.
     */
    @Column(name = "grant_all_permissions", nullable = false)
    private boolean grantAllPermissions = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Organisation organisation;

    @OneToMany(mappedBy = "role")
    private Set<User> users;

}

