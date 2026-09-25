package com.assetiq.services.mobile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MobileHomeSection - the Home payload shows exactly what the caller's authorities allow")
class MobileHomeSectionTest {

    private static Authentication authenticated(String... authorities) {
        return new UsernamePasswordAuthenticationToken("someone@example.com", null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    @DisplayName("org and platform admins see every section")
    void adminsSeeEverything() {
        assertThat(MobileHomeSection.grantedTo(authenticated("ROLE_ORG_ADMIN")))
                .containsExactlyInAnyOrder(MobileHomeSection.values());
        assertThat(MobileHomeSection.grantedTo(authenticated("ROLE_ADMIN")))
                .containsExactlyInAnyOrder(MobileHomeSection.values());
    }

    @Test
    @DisplayName("no authentication, and an authority-less account, get nothing")
    void defaultIsClosed() {
        assertThat(MobileHomeSection.grantedTo(null)).isEmpty();
        assertThat(MobileHomeSection.grantedTo(authenticated())).isEmpty();
        assertThat(MobileHomeSection.grantedTo(authenticated("SOME_UNRELATED_AUTHORITY"))).isEmpty();
    }

    @Test
    @DisplayName("ROLE_USER may not approve transfers or read the budget summary")
    void roleUserIsNotAnApprover() {
        assertThat(MobileHomeSection.grantedTo(authenticated("ROLE_USER")))
                .containsExactlyInAnyOrder(MobileHomeSection.ASSETS, MobileHomeSection.MAINTENANCE,
                        MobileHomeSection.CHECKOUTS);
    }

    @Test
    @DisplayName("VIEW_ASSETS alone unlocks assets and checkouts, nothing else")
    void viewAssetsAlone() {
        assertThat(MobileHomeSection.grantedTo(authenticated("ROLE_VIEWER", "VIEW_ASSETS")))
                .containsExactlyInAnyOrder(MobileHomeSection.ASSETS, MobileHomeSection.CHECKOUTS);
    }

    @Test
    @DisplayName("every authority set is copied from its owning controller's mapping")
    void authoritiesMirrorTheOwningControllers() {
        // Copied from the @PreAuthorize of each owning endpoint. If one changes,
        // this fails and the enum has to be brought back into line.
        assertThat(MobileHomeSection.ASSETS.authorities())            // AssetController#list
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_ASSETS");
        assertThat(MobileHomeSection.MAINTENANCE.authorities())       // MaintenanceController#list
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_MAINTENANCE");
        assertThat(MobileHomeSection.TRANSFER_APPROVALS.authorities()) // AssetTransferController#approve
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_ORG_ADMIN", "TRANSFER_ASSET");
        assertThat(MobileHomeSection.CHECKOUTS.authorities())         // CheckoutController#listOverdue
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "CHECKOUT_ASSET",
                        "VIEW_ASSETS");
        assertThat(MobileHomeSection.BUDGETS.authorities())           // BudgetController#getSummary
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_ORG_ADMIN", "VIEW_BUDGETS");
    }
}
