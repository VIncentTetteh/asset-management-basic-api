package com.assetiq.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Join entity between {@link Role} and a permission name string.
 *
 * Replaces the denormalised JSON {@code permissions} column that existed on
 * {@link Role} prior to Phase 2 / B-1.  Each row represents a single
 * permission granted to a role.
 *
 * <p>Intentionally lightweight — no audit columns are needed on this table
 * because the effective permission set is audited at the {@link Role} level.
 */
@Entity
@Table(
        name = "role_permission",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_rp_role_permission",
                columnNames = {"role_id", "permission"}
        )
)
@Getter
@Setter
public class RolePermission {

    @Id
    private UUID id = UUID.randomUUID();

    /**
     * The role this permission belongs to.
     * Mapped by {@link Role#rolePermissions} — cascade and orphan-removal are
     * managed from the {@link Role} side.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /**
     * Permission name — matches a value in the {@link com.assetiq.enums.Permission} enum,
     * e.g. "VIEW_ASSETS", "MANAGE_ROLES".
     */
    @Column(nullable = false, length = 100)
    private String permission;
}
