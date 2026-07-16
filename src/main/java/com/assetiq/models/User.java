package com.assetiq.models;

import com.assetiq.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_user", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "email", "organisation_id" }, name = "uk_user_email_per_org"),
        @UniqueConstraint(columnNames = { "employee_id", "organisation_id" }, name = "uk_user_employeeid_per_org")
})
@Data
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class User extends BaseEntity {

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String employeeId;

    private String jobTitle;

    /**
     * Primary role — used for JWT {@code role} claim and backward-compatible
     * single-role queries (e.g. {@code findByRoleId}).  Retained during the
     * Phase 2 / B-5 transition; the {@link #roles} collection is the
     * authoritative source for permission resolution.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    private Role role;

    /**
     * All roles assigned to this user (many-to-many via {@code user_roles}).
     * Permission resolution in {@link com.assetiq.security.PermissionCacheService}
     * unions the permissions from every role in this set, allowing fine-grained
     * access control through role composition.
     *
     * <p>Populated from the {@code user_roles} join table; backfilled from
     * {@link #role} by the V5 Flyway migration.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns        = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "reset_password_token", length = 100)
    private String resetPasswordToken;

    @Column(name = "reset_password_token_expiry")
    private Instant resetPasswordTokenExpiry;

    @Column(name = "reset_password_token_used", columnDefinition = "boolean default false")
    private Boolean resetPasswordTokenUsed = false;

    @ManyToOne(fetch = FetchType.LAZY)
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    private Department department;

    @OneToMany(mappedBy = "assignedUser")
    private Set<Asset> assignedAssets;

    /** Whether TOTP-based MFA is enabled for this user. */
    @Column(name = "mfa_enabled", columnDefinition = "boolean default false")
    private Boolean mfaEnabled = false;

    /** Base32-encoded TOTP secret (stored encrypted in production via column-level encryption). */
    @Column(name = "mfa_secret", length = 100)
    private String mfaSecret;

    // ── Account lockout (brute-force protection) ──────────────────────────────

    /**
     * Number of consecutive failed login attempts since the last successful
     * login or manual admin reset.  Incremented on every failed password check;
     * reset to 0 on a successful login.
     */
    @Column(name = "failed_login_attempts", nullable = false, columnDefinition = "integer default 0")
    private int failedLoginAttempts = 0;

    /**
     * When non-null the account is temporarily locked.  Login attempts before
     * this instant are rejected with HTTP 423.  Set automatically when
     * {@link #failedLoginAttempts} reaches the threshold; cleared on successful
     * login or by an admin reset.
     */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** Returns true if the account is currently within a lockout window. */
    public boolean isLockedOut() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }
}
