package com.assetiq.security;

import com.assetiq.enums.OrganisationStatus;
import com.assetiq.enums.UserStatus;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtSessionVersionRevocationTest {

    @Test
    void staleAccessTokenIsRejectedWithoutDependingOnRedisBlacklist() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserRepository users = mock(UserRepository.class);
        JwtBlacklist blacklist = mock(JwtBlacklist.class);
        PermissionCacheService permissions = mock(PermissionCacheService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtUtil, users, blacklist, permissions, false);

        UUID orgId = UUID.randomUUID();
        Organisation org = new Organisation();
        org.setId(orgId);
        org.setStatus(OrganisationStatus.ACTIVE);
        User liveUser = new User();
        liveUser.setEmail("security@example.com");
        liveUser.setStatus(UserStatus.ACTIVE);
        liveUser.setOrganisation(org);
        liveUser.setSessionVersion(9);

        Claims claims = mock(Claims.class);
        when(jwtUtil.parseToken("stale-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(liveUser.getEmail());
        when(claims.get("organisationId", String.class)).thenReturn(orgId.toString());
        when(claims.get("sessionVersion", Number.class)).thenReturn(8L);
        when(users.findByEmailAndOrganisationId(liveUser.getEmail(), orgId))
                .thenReturn(Optional.of(liveUser));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/assets");
        request.addHeader("Authorization", "Bearer stale-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        verifyNoInteractions(permissions);
    }
}
