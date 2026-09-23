package com.assetiq.integration;

import com.assetiq.dto.TenantRegisterRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The invitation flow over real HTTP, with two tenants present.
 *
 * <p>What only an end-to-end run can prove: that the acceptance endpoints are
 * actually reachable without a token (they are declared public in two separate
 * places — the security chain and the tenant filter — and either one being
 * wrong makes the whole feature unusable), and that one tenant's administrator
 * cannot touch another's invitations through the authenticated surface.
 *
 * <p>Email is off in this profile, so the API hands the link back in the
 * response — which is exactly the behaviour being relied on here, and is itself
 * asserted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.email.enabled=false"})
@DisplayName("Invitations over the API")
class InvitationApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private Tenant acme;
    private Tenant rival;

    private record Tenant(String orgId, String adminEmail, String token) {
    }

    @BeforeEach
    void registerTwoTenants() throws Exception {
        acme = register("Acme");
        rival = register("Rival");
    }

    // ── The happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("invite, accept, then sign in as the new colleague")
    void inviteThenAcceptThenSignIn() throws Exception {
        String roleId = roleId(acme, "VIEWER");
        String invitee = "joiner+" + suffix() + "@example.com";

        JsonNode issued = invite(acme, invitee, roleId);
        assertThat(issued.get("emailSent").asBoolean()).isFalse();
        String acceptUrl = issued.get("acceptUrl").asText();
        assertThat(acceptUrl).contains("/accept-invite?token=");
        String rawToken = acceptUrl.substring(acceptUrl.indexOf("token=") + "token=".length());

        // The unauthenticated preview names the company and the role, and nothing else.
        mockMvc.perform(post("/api/v1/invitations/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", rawToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.roleName").value("VIEWER"))
                .andExpect(jsonPath("$.email").value(invitee));

        // Accepting: no bearer token, no organisation header.
        JsonNode accepted = objectMapper.readTree(mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawToken,
                                "firstName", "Kwame",
                                "lastName", "Mensah",
                                "password", "Password123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(accepted.get("organisationId").asText()).isEqualTo(acme.orgId());
        assertThat(accepted.get("roleName").asText()).isEqualTo("VIEWER");

        // The account really exists, in the inviting organisation, and can sign in
        // straight away — redeeming the link is itself proof of the mailbox.
        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", invitee, "password", "Password123",
                                "organisationId", acme.orgId()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(login.hasNonNull("token")).isTrue();
    }

    @Test
    @DisplayName("a token works exactly once")
    void tokenIsSingleUse() throws Exception {
        String invitee = "once+" + suffix() + "@example.com";
        String rawToken = tokenFor(invite(acme, invitee, roleId(acme, "VIEWER")));

        mockMvc.perform(accept(rawToken)).andExpect(status().isCreated());
        // The second attempt does not find a spent invitation; it finds nothing.
        mockMvc.perform(accept(rawToken)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a revoked invitation stops working immediately")
    void revocationInvalidatesTheLink() throws Exception {
        String invitee = "revoked+" + suffix() + "@example.com";
        JsonNode issued = invite(acme, invitee, roleId(acme, "VIEWER"));
        String invitationId = issued.get("invitation").get("id").asText();
        String rawToken = tokenFor(issued);

        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/revoke")
                        .header("Authorization", "Bearer " + acme.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        mockMvc.perform(accept(rawToken)).andExpect(status().isBadRequest());
    }

    // ── Tenant isolation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("one tenant's administrator cannot see or revoke another tenant's invitation")
    void crossTenantAccessIsDenied() throws Exception {
        String invitee = "target+" + suffix() + "@example.com";
        String invitationId = invite(acme, invitee, roleId(acme, "VIEWER")).get("invitation").get("id").asText();

        // Rival's own listing never contains it.
        String rivalList = mockMvc.perform(get("/api/v1/invitations")
                        .header("Authorization", "Bearer " + rival.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(rivalList).doesNotContain(invitee).doesNotContain(invitationId);

        // And acting on it by id is refused, not merely empty.
        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/revoke")
                        .header("Authorization", "Bearer " + rival.token()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/resend")
                        .header("Authorization", "Bearer " + rival.token()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a role belonging to another tenant cannot be invited into")
    void aForeignRoleCannotBeUsed() throws Exception {
        String rivalRoleId = roleId(rival, "VIEWER");

        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + acme.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "sneaky+" + suffix() + "@example.com",
                                "roleId", rivalRoleId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("inviting an address that belongs to another tenant discloses nothing about it")
    void invitingAnAddressFromAnotherTenantLooksLikeAnyOtherInvitation() throws Exception {
        // rival's administrator is a real user — in a different organisation.
        JsonNode issued = invite(acme, rival.adminEmail(), roleId(acme, "VIEWER"));

        assertThat(issued.get("invitation").get("status").asText()).isEqualTo("PENDING");
        assertThat(issued.get("message").asText()).doesNotContain("another organisation");
        // No hint of the other tenant anywhere in the answer.
        assertThat(issued.toString()).doesNotContain(rival.orgId());
    }

    @Test
    @DisplayName("the acceptance endpoints need no authentication and ignore a tenant header")
    void acceptanceIsPublicAndIgnoresTheTenantHeader() throws Exception {
        String invitee = "public+" + suffix() + "@example.com";
        String rawToken = tokenFor(invite(acme, invitee, roleId(acme, "VIEWER")));

        // A caller with no account sets the header to someone else's organisation.
        // It must change nothing: the token names the organisation.
        JsonNode accepted = objectMapper.readTree(mockMvc.perform(post("/api/v1/invitations/accept")
                        .header("X-Organisation-Id", rival.orgId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawToken,
                                "firstName", "Ama",
                                "lastName", "Owusu",
                                "password", "Password123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(accepted.get("organisationId").asText()).isEqualTo(acme.orgId());
    }

    @Test
    @DisplayName("an unauthenticated caller cannot list or issue invitations")
    void administrativeEndpointsStayClosed() throws Exception {
        mockMvc.perform(get("/api/v1/invitations")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@example.com\",\"roleId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── Roles, described ──────────────────────────────────────────────────────

    @Test
    @DisplayName("a role's effective permissions read as sentences, and grant-all resolves to everything")
    void effectivePermissionsAreReadable() throws Exception {
        mockMvc.perform(get("/api/v1/roles/" + roleId(acme, "VIEWER") + "/effective-permissions")
                        .header("Authorization", "Bearer " + acme.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleName").value("VIEWER"))
                .andExpect(jsonPath("$.grantAllPermissions").value(false))
                .andExpect(jsonPath("$.permissions[?(@.key == 'VIEW_ASSETS')].label")
                        .value("See the asset register"));

        String admin = mockMvc.perform(get("/api/v1/roles/" + roleId(acme, "ADMIN") + "/effective-permissions")
                        .header("Authorization", "Bearer " + acme.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantAllPermissions").value(true))
                .andReturn().getResponse().getContentAsString();
        // A grant-all role stores no permission rows; it must still report them all.
        assertThat(objectMapper.readTree(admin).get("permissionCount").asInt())
                .isEqualTo(com.assetiq.enums.Permission.values().length);
    }

    @Test
    @DisplayName("the onboarding checklist reflects a freshly registered tenant")
    void onboardingStartsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/onboarding").header("Authorization", "Bearer " + acme.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.totalSteps").value(6))
                .andExpect(jsonPath("$.steps[?(@.key == 'add_assets')].done").value(false));

        mockMvc.perform(post("/api/v1/onboarding/dismiss").header("Authorization", "Bearer " + acme.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dismissed").value(true))
                // Dismissing hides the prompt; it does not claim the work is done.
                .andExpect(jsonPath("$.complete").value(false));
    }

    @Test
    @DisplayName("an invitation appears in its tenant's listing with a usable status")
    void listingShowsStatus() throws Exception {
        String invitee = "listed+" + suffix() + "@example.com";
        invite(acme, invitee, roleId(acme, "VIEWER"));

        mockMvc.perform(get("/api/v1/invitations").param("status", "PENDING")
                        .header("Authorization", "Bearer " + acme.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.email == '" + invitee + "')].status").value("PENDING"))
                .andExpect(jsonPath("$.items[?(@.email == '" + invitee + "')].roleName").value("VIEWER"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JsonNode invite(Tenant tenant, String email, String roleId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + tenant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "roleId", roleId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private static String tokenFor(JsonNode issued) {
        String url = issued.get("acceptUrl").asText();
        return url.substring(url.indexOf("token=") + "token=".length());
    }

    private org.springframework.test.web.servlet.RequestBuilder accept(String rawToken) throws Exception {
        return post("/api/v1/invitations/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "token", rawToken,
                        "firstName", "Yaw",
                        "lastName", "Asante",
                        "password", "Password123")));
    }

    private String roleId(Tenant tenant, String name) throws Exception {
        String body = mockMvc.perform(get("/api/v1/roles/by-name")
                        .param("name", name)
                        .param("organisationId", tenant.orgId())
                        .header("Authorization", "Bearer " + tenant.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private Tenant register(String label) throws Exception {
        String suffix = suffix();
        String email = label.toLowerCase() + "+" + suffix + "@example.com";
        String password = "Password123";

        TenantRegisterRequest request = new TenantRegisterRequest();
        request.setOrganisationName(label + " Org " + suffix);
        request.setOrganisationContactEmail("ops+" + suffix + "@example.com");
        request.setAdminFirstName("Abena");
        request.setAdminLastName("Darko");
        request.setAdminEmail(email);
        request.setPassword(password);
        request.setCountry("GH");
        request.setTimezone("UTC");
        request.setIndustry("IT");

        String orgId = objectMapper.readTree(mockMvc.perform(post("/api/v1/tenant/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("organisationId").asText();

        String token = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        return new Tenant(orgId, email, token);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
