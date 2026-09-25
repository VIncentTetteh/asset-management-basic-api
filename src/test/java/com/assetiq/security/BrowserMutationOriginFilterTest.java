package com.assetiq.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserMutationOriginFilterTest {

    private final BrowserMutationOriginFilter filter = new BrowserMutationOriginFilter(
            "https://portal.assetiq.example,http://localhost:3100");

    @Test
    void rejectsCrossSiteCookieAuthenticatedMutation() throws Exception {
        var request = mutation("https://attacker.example");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void acceptsApprovedOriginAndBearerOnlyClients() throws Exception {
        var approved = mutation("https://portal.assetiq.example");
        var approvedChain = new MockFilterChain();
        filter.doFilter(approved, new MockHttpServletResponse(), approvedChain);
        assertThat(approvedChain.getRequest()).isNotNull();

        var bearer = new MockHttpServletRequest("POST", "/api/v1/assets");
        bearer.addHeader("Authorization", "Bearer api-token");
        var bearerChain = new MockFilterChain();
        filter.doFilter(bearer, new MockHttpServletResponse(), bearerChain);
        assertThat(bearerChain.getRequest()).isNotNull();
    }

    @Test
    void missingOriginFailsClosedForRefreshCookie() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        request.setCookies(new Cookie("refresh_token", "opaque"));
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    private MockHttpServletRequest mutation(String origin) {
        var request = new MockHttpServletRequest("POST", "/api/v1/assets");
        request.setCookies(new Cookie("access_token", "jwt"));
        request.addHeader("Origin", origin);
        return request;
    }
}
