package com.assetiq.security;

import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.dto.TenantRegisterResponse;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.AccountLifecycleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Account data export and closure.
 *
 * <p>Covers the two obligations a tenant has a right to exercise — leaving with their
 * data, and leaving — plus the properties that make them safe: exports must not carry
 * credentials, closure must not be one stray request away, and a closed account must
 * lose access at once rather than when the purge eventually runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Account lifecycle")
class AccountLifecycleTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private AccountLifecycleService accountLifecycleService;

    private final AtomicInteger clientCounter = new AtomicInteger(1);

    @Test
    @DisplayName("an admin can export every record the tenant owns")
    void exportReturnsTenantData() throws Exception {
        Tenant tenant = register();
        createAsset(tenant, "Exported Laptop " + tenant.suffix());

        MvcResult result = mockMvc.perform(auth(get("/api/v1/account/export"), tenant))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("assetiq.tenant-export.v1");
        assertThat(body).contains("Exported Laptop " + tenant.suffix());
        assertThat(body).contains(tenant.email());
    }

    @Test
    @DisplayName("an export never carries credentials")
    void exportOmitsSecrets() throws Exception {
        Tenant tenant = register();

        String body = mockMvc.perform(auth(get("/api/v1/account/export"), tenant))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // An export lands in inboxes, download folders and ticket attachments. Password
        // hashes and MFA secrets are account-security material, not customer data.
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain("mfaSecret");
        assertThat(body).doesNotContain("emailVerificationToken");
        assertThat(body).doesNotContain("resetPasswordToken");
        assertThat(body).doesNotContain("$2a$"); // bcrypt prefix, in case a hash leaks unlabelled
    }

    @Test
    @DisplayName("one tenant cannot export another tenant's data")
    void exportIsTenantScoped() throws Exception {
        Tenant alpha = register();
        Tenant beta = register();
        createAsset(alpha, "Alpha Only " + alpha.suffix());

        String body = mockMvc.perform(auth(get("/api/v1/account/export"), beta))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Alpha Only " + alpha.suffix());
        assertThat(body).doesNotContain(alpha.email());
    }

    @Test
    @DisplayName("closure requires an explicit confirmation phrase")
    void closureRequiresConfirmation() throws Exception {
        Tenant tenant = register();

        mockMvc.perform(auth(post("/api/v1/account/delete"), tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(auth(post("/api/v1/account/delete"), tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("confirm", "yes"))))
                .andExpect(status().isBadRequest());

        // Still live after both rejected attempts.
        assertThat(organisationRepository.findByIdAndDeletedAtIsNull(tenant.organisationId()))
                .isPresent();
    }

    @Test
    @DisplayName("closing an account revokes access immediately and schedules the purge")
    void closureRevokesAccessAndSchedulesPurge() throws Exception {
        Tenant tenant = register();

        mockMvc.perform(auth(post("/api/v1/account/delete"), tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("confirm", "DELETE"))))
                .andExpect(status().isOk());

        Organisation org = organisationRepository.findById(tenant.organisationId()).orElseThrow();
        assertThat(org.getDeletedAt()).as("closure is a soft delete").isNotNull();
        assertThat(org.getPurgeAfter()).as("the purge must be scheduled, not immediate").isNotNull();
        assertThat(org.getPurgeAfter()).isAfter(Instant.now());

        // The token is still cryptographically valid, so this proves the tenant gate —
        // not token expiry — is what stops a closed account.
        mockMvc.perform(auth(get("/api/v1/assets"), tenant))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("an account inside its retention window is not purged")
    void purgeSparesAccountsInsideTheWindow() throws Exception {
        Tenant tenant = register();
        closeAccount(tenant);

        accountLifecycleService.purgeExpiredAccounts();

        assertThat(organisationRepository.findById(tenant.organisationId()))
                .as("closure must stay reversible for the whole retention window")
                .isPresent();
    }

    // The destructive half of the purge — that deleting the organisation actually
    // cascades away every dependent row — is asserted in AccountPurgeCascadeTest
    // against real PostgreSQL, not here. This suite runs on H2 with Flyway disabled
    // and ddl-auto=create-drop, so its foreign keys are Hibernate-generated and carry
    // no ON DELETE CASCADE. Asserting the cascade here would be asserting against a
    // schema that does not exist in production.

    @Test
    @DisplayName("closure can be undone while the account is still recoverable")
    void closureCanBeCancelled() throws Exception {
        Tenant tenant = register();
        closeAccount(tenant);

        assertThat(accountLifecycleService.cancelClosure(tenant.organisationId())).isTrue();

        Organisation org = organisationRepository.findById(tenant.organisationId()).orElseThrow();
        assertThat(org.getDeletedAt()).isNull();
        assertThat(org.getPurgeAfter()).isNull();

        mockMvc.perform(auth(get("/api/v1/assets"), tenant)).andExpect(status().isOk());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private record Tenant(UUID organisationId, String token, String suffix, String email) {}

    private Tenant register() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "owner+" + suffix + "@example.com";

        TenantRegisterRequest req = new TenantRegisterRequest();
        req.setOrganisationName("Closure Org " + suffix);
        req.setOrganisationContactEmail("ops+" + suffix + "@example.com");
        req.setAdminFirstName("Kwame");
        req.setAdminLastName("Owner");
        req.setAdminEmail(email);
        req.setPassword("Password123");
        req.setCountry("GH");
        req.setTimezone("UTC");
        req.setIndustry("IT");

        MvcResult result = mockMvc.perform(post("/api/v1/tenant/register")
                        .header("X-Forwarded-For", nextClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRegisterResponse resp = objectMapper.readValue(
                result.getResponse().getContentAsString(), TenantRegisterResponse.class);

        return new Tenant(resp.getOrganisationId(), resp.getToken(), suffix, email);
    }

    /**
     * Close through the HTTP endpoint rather than calling the service directly —
     * the service reads the tenant from a ThreadLocal that only TenantFilter populates,
     * so a direct call has no organisation context.
     */
    private void closeAccount(Tenant tenant) throws Exception {
        mockMvc.perform(auth(post("/api/v1/account/delete"), tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("confirm", "DELETE"))))
                .andExpect(status().isOk());
    }

    private void createAsset(Tenant tenant, String name) throws Exception {
        mockMvc.perform(auth(post("/api/v1/assets"), tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().is2xxSuccessful());
    }

    private String nextClient() {
        return "10.2.0." + clientCounter.getAndIncrement();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, Tenant tenant) {
        String client = nextClient();
        return builder.header("Authorization", "Bearer " + tenant.token())
                .header("X-Client-ID", client)
                .header("X-Forwarded-For", client);
    }
}
