package com.assetiq.models;

import com.assetiq.multitenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaseEntityTenantBoundaryTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void tenantOwnedEntityFailsClosedWhenRepositoryLoadsAnotherTenant() {
        UUID current = UUID.randomUUID();
        Organisation other = new Organisation();
        other.setId(UUID.randomUUID());
        Asset asset = new Asset();
        asset.setOrganisation(other);
        TenantContext.setOrganisationId(current);

        assertThatThrownBy(asset::enforceTenantBoundary)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Tenant boundary violation");
    }

    @Test
    void matchingTenantAndGlobalReferenceEntitiesRemainAccessible() {
        UUID current = UUID.randomUUID();
        Organisation owner = new Organisation();
        owner.setId(current);
        Asset asset = new Asset();
        asset.setOrganisation(owner);
        SubscriptionPlan globalPlan = new SubscriptionPlan();
        TenantContext.setOrganisationId(current);

        assertThatCode(asset::enforceTenantBoundary).doesNotThrowAnyException();
        assertThatCode(globalPlan::enforceTenantBoundary).doesNotThrowAnyException();
    }
}
