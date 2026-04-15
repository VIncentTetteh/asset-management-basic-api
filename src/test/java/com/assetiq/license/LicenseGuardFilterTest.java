package com.assetiq.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LicenseGuardFilterTest {

    LicenseService licenseService;
    LicenseGuardFilter filter;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        licenseService = mock(LicenseService.class);
        filter = new LicenseGuardFilter(licenseService, objectMapper);
    }

    // ── Cloud / valid license — all methods pass ──────────────────────────────

    @Test
    @DisplayName("GET request always passes through regardless of license state")
    void get_alwaysPasses() throws Exception {
        when(licenseService.getCurrentState()).thenReturn(LicenseState.expired("expired"));

        var req   = new MockHttpServletRequest("GET", "/api/v1/assets");
        var res   = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull(); // chain was called
    }

    @Test
    @DisplayName("POST passes when license is valid")
    void post_passesWhenValid() throws Exception {
        when(licenseService.getCurrentState()).thenReturn(
            LicenseState.valid("professional", null, 300, 14, null, null, null));

        var req   = new MockHttpServletRequest("POST", "/api/v1/assets");
        var res   = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    // ── Expired / read-only — write methods blocked ───────────────────────────

    @Test
    @DisplayName("POST blocked with 402 when license is expired")
    void post_blockedWhenExpired() throws Exception {
        when(licenseService.getCurrentState()).thenReturn(LicenseState.expired("License expired."));

        var req   = new MockHttpServletRequest("POST", "/api/v1/assets");
        var res   = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(402);
        assertThat(res.getContentAsString()).contains("LICENSE_READ_ONLY");
        assertThat(chain.getRequest()).isNull(); // chain NOT called
    }

    @Test
    @DisplayName("DELETE blocked with 402 when license is in grace period")
    void delete_blockedInGracePeriod() throws Exception {
        when(licenseService.getCurrentState()).thenReturn(
            LicenseState.gracePeriod("professional", null, 0, 7, null, null, null, "3 days left"));

        var req   = new MockHttpServletRequest("DELETE", "/api/v1/assets/123");
        var res   = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(402);
    }

    // ── Exempt paths — always writable even in read-only mode ─────────────────

    @Test
    @DisplayName("POST /api/v1/license/activate is exempt from read-only block")
    void licenseActivate_alwaysWritable() throws Exception {
        when(licenseService.getCurrentState()).thenReturn(LicenseState.expired("expired"));

        var req   = new MockHttpServletRequest("POST", "/api/v1/license/activate");
        var res   = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("POST /api/v1/auth/login is exempt from read-only block")
    void login_alwaysWritable() throws Exception {
        when(licenseService.getCurrentState()).thenReturn(LicenseState.expired("expired"));

        var req   = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        var res   = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("LicenseService is NOT called for GET requests (performance guard)")
    void get_doesNotCallLicenseService() throws Exception {
        var req   = new MockHttpServletRequest("GET", "/api/v1/assets");
        var res   = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(req, res, chain);

        verify(licenseService, never()).getCurrentState();
    }
}
