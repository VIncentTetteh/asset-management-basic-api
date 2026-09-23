package com.assetiq.services.insights;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InsightSection - a dashboard shows exactly what the caller's authorities allow")
class InsightSectionTest {

    private static Authentication authenticated(String... authorities) {
        return new UsernamePasswordAuthenticationToken("someone@example.com", null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    @DisplayName("an org admin sees every section")
    void orgAdminSeesEverything() {
        assertThat(InsightSection.grantedTo(authenticated("ROLE_ORG_ADMIN")))
                .containsExactlyInAnyOrder(InsightSection.values());
    }

    @Test
    @DisplayName("no authentication, and an authority-less account, get nothing")
    void defaultIsClosed() {
        assertThat(InsightSection.grantedTo(null)).isEmpty();
        assertThat(InsightSection.grantedTo(authenticated())).isEmpty();
        assertThat(InsightSection.grantedTo(authenticated("SOME_UNRELATED_AUTHORITY"))).isEmpty();
    }

    @Test
    @DisplayName("a user who cannot list disposals gets no disposal section")
    void noDisposeAuthorityNoDisposals() {
        assertThat(InsightSection.grantedTo(authenticated("ROLE_VIEWER", "VIEW_ASSETS")))
                .doesNotContain(InsightSection.DISPOSALS);
        assertThat(InsightSection.grantedTo(authenticated("ROLE_VIEWER", "DISPOSE_ASSET")))
                .contains(InsightSection.DISPOSALS);
    }

    @Test
    @DisplayName("a viewer with only VIEW_ASSETS gets no money, no budgets and no maintenance")
    void viewAssetsDoesNotUnlockMoney() {
        Set<InsightSection> granted = InsightSection.grantedTo(authenticated("ROLE_VIEWER", "VIEW_ASSETS"));

        assertThat(granted).contains(InsightSection.ASSETS, InsightSection.LICENCES,
                InsightSection.CONTRACTS, InsightSection.LOCATIONS);
        assertThat(granted).doesNotContain(InsightSection.VALUATION, InsightSection.BUDGETS,
                InsightSection.MAINTENANCE, InsightSection.DISPOSALS, InsightSection.DEPARTMENTS,
                InsightSection.LEASES);
    }

    @Test
    @DisplayName("every authority set is a subset of its owning controller's read mapping")
    void authoritiesMirrorTheOwningControllers() {
        // Copied from the @PreAuthorize of each controller's list endpoint. If a
        // controller's read authorities change, this test fails and the enum has
        // to be brought back into line — which is the point.
        assertThat(InsightSection.ASSETS.authorities())
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_ASSETS");
        assertThat(InsightSection.MAINTENANCE.authorities())
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_MAINTENANCE");
        assertThat(InsightSection.LICENCES.authorities())
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_ASSETS",
                        "VIEW_SOFTWARE_LICENSES", "MANAGE_SOFTWARE_LICENSES");
        assertThat(InsightSection.CONTRACTS.authorities())
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_ASSETS",
                        "VIEW_CONTRACTS", "MANAGE_CONTRACTS");
        assertThat(InsightSection.LEASES.authorities())
                .containsExactlyInAnyOrder("ROLE_ORG_ADMIN", "ROLE_ADMIN", "ROLE_USER", "MANAGE_LEASES",
                        "VIEW_CONTRACTS");
        assertThat(InsightSection.BUDGETS.authorities())
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_BUDGETS");
        assertThat(InsightSection.DISPOSALS.authorities())
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "DISPOSE_ASSET");
        assertThat(InsightSection.DEPARTMENTS.authorities())
                .containsExactlyInAnyOrder("ROLE_ORG_ADMIN", "ROLE_USER", "ROLE_ADMIN", "VIEW_DEPARTMENTS",
                        "MANAGE_DEPARTMENTS");
        assertThat(InsightSection.LOCATIONS.authorities())
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_ASSETS",
                        "VIEW_LOCATIONS", "MANAGE_LOCATIONS");
    }
}
