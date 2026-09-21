package com.assetiq.security;

import com.assetiq.multitenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlatformAdminGuard")
class PlatformAdminGuardTest {

    private static final UUID OPS_ORG = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void signIn(String email, UUID org) {
        TenantContext.setOrganisationId(org);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, "n/a", List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN"))));
    }

    @Test
    void operatorInTheOperatorOrganisationIsAdmitted() {
        var guard = new PlatformAdminGuard(OPS_ORG.toString(), List.of("Ops@AssetIQ.example"));
        signIn("ops@assetiq.example", OPS_ORG);
        assertThat(guard.isPlatformAdmin()).isTrue();
    }

    @Test
    void sameEmailRegisteredInAnotherTenantIsRefused() {
        var guard = new PlatformAdminGuard(OPS_ORG.toString(), List.of("ops@assetiq.example"));
        signIn("ops@assetiq.example", UUID.randomUUID());
        assertThat(guard.isPlatformAdmin()).isFalse();
    }

    @Test
    void otherUsersInTheOperatorOrganisationAreRefused() {
        var guard = new PlatformAdminGuard(OPS_ORG.toString(), List.of("ops@assetiq.example"));
        signIn("someone@assetiq.example", OPS_ORG);
        assertThat(guard.isPlatformAdmin()).isFalse();
    }

    @Test
    void unconfiguredMeansNobody() {
        signIn("ops@assetiq.example", OPS_ORG);
        assertThat(new PlatformAdminGuard("", List.of("ops@assetiq.example")).isPlatformAdmin()).isFalse();
        assertThat(new PlatformAdminGuard(OPS_ORG.toString(), List.of()).isPlatformAdmin()).isFalse();
    }
}
