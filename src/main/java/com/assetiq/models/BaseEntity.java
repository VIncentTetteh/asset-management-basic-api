package com.assetiq.models;

import com.assetiq.multitenancy.TenantContext;
import jakarta.persistence.*;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @EqualsAndHashCode.Include
    private UUID id = UUID.randomUUID();

//    @Version
//    @Column(name = "version")
//    private Long version;

    @Column(name = "created_at", updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(name = "updated_at")
    @LastModifiedDate
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreatedBy
    @Column(name = "created_by")
    private String createdBy;

    @LastModifiedBy
    @Column(name = "modified_by")
    private String modifiedBy;

    @PrePersist
    protected void onCreate() {
        enforceTenantBoundary();
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        enforceTenantBoundary();
        this.updatedAt = Instant.now();
    }

    /**
     * Defense in depth for every entity declaring an {@code organisation} owner.
     * A missed repository predicate therefore fails at the ORM boundary before a
     * cross-tenant entity can be returned, changed, or deleted.
     */
    @PostLoad
    @PreRemove
    protected void enforceTenantBoundary() {
        UUID currentTenant = TenantContext.getOrganisationId();
        if (currentTenant == null) return; // approved system/background operation

        java.lang.reflect.Field organisationField = findOrganisationField(getClass());
        if (organisationField == null) return; // shared/global reference data
        try {
            organisationField.setAccessible(true);
            Object owner = organisationField.get(this);
            if (!(owner instanceof Organisation organisation)
                || organisation.getId() == null
                || !currentTenant.equals(organisation.getId())) {
                throw new AccessDeniedException("Tenant boundary violation for " + getClass().getSimpleName());
            }
        } catch (IllegalAccessException inaccessible) {
            throw new AccessDeniedException("Unable to verify tenant ownership", inaccessible);
        }
    }

    private static java.lang.reflect.Field findOrganisationField(Class<?> type) {
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            try {
                return cursor.getDeclaredField("organisation");
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
