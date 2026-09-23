package com.assetiq.services.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AiDataSection - the assistant sees exactly what the caller's authorities allow")
class AiDataSectionTest {

    private static Authentication authenticated(String... authorities) {
        return new UsernamePasswordAuthenticationToken("someone@example.com", null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    @DisplayName("an org admin sees every section")
    void orgAdminSeesEverything() {
        Set<AiDataSection> granted = AiDataSection.grantedTo(authenticated("ROLE_ORG_ADMIN"));
        assertThat(granted).containsExactlyInAnyOrder(AiDataSection.values());
    }

    @Test
    @DisplayName("a custom role with only VIEW_ASSETS sees assets and nothing else")
    void restrictedRoleSeesOnlyWhatItHolds() {
        Set<AiDataSection> granted = AiDataSection.grantedTo(authenticated("ROLE_VIEWER", "VIEW_ASSETS"));

        assertThat(granted).contains(AiDataSection.ASSETS, AiDataSection.LOCATIONS,
                AiDataSection.CONTRACTS, AiDataSection.LICENCES, AiDataSection.SUPPLIERS);
        // VIEW_ASSETS is the read authority those sections already accept on their
        // own controllers; what it must NOT reach is the governance data.
        assertThat(granted).doesNotContain(AiDataSection.COMPLIANCE, AiDataSection.RISKS,
                AiDataSection.INSIGHTS, AiDataSection.BUDGETS, AiDataSection.USERS,
                AiDataSection.DISPOSALS, AiDataSection.MAINTENANCE);
    }

    @Test
    @DisplayName("a user who cannot list disposals gets no disposal section")
    void noDisposeAuthorityNoDisposals() {
        assertThat(AiDataSection.grantedTo(authenticated("ROLE_VIEWER", "VIEW_ASSETS")))
                .doesNotContain(AiDataSection.DISPOSALS);
        assertThat(AiDataSection.grantedTo(authenticated("ROLE_VIEWER", "DISPOSE_ASSET")))
                .contains(AiDataSection.DISPOSALS);
    }

    @Test
    @DisplayName("no authentication, and an authority-less account, get nothing")
    void defaultIsClosed() {
        assertThat(AiDataSection.grantedTo(null)).isEmpty();
        assertThat(AiDataSection.grantedTo(authenticated())).isEmpty();
        assertThat(AiDataSection.grantedTo(authenticated("SOME_UNRELATED_AUTHORITY"))).isEmpty();
    }
}
