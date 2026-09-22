package com.assetiq.config;

import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.dto.TenantRegisterResponse;
import com.assetiq.models.Organisation;
import com.assetiq.models.Role;
import com.assetiq.models.RolePermission;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.RoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Self-registered tenants get the standard roles; the backfill is idempotent")
class DefaultRoleSeedingTest {

    private static final List<String> STANDARD = List.of("ADMIN", "USER", "ASSET_MANAGER", "PROCUREMENT_OFFICER",
            "COMPLIANCE_OFFICER", "IT_MANAGER", "FINANCE_MANAGER", "HR_MANAGER", "VIEWER");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RoleRepository roleRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private DefaultRoleBackfill backfill;
    @Autowired private TransactionTemplate tx;

    @Test
    void registrationSeedsTheStandardRoleSet() throws Exception {
        Organisation org = register();
        List<String> names = tx.execute(s -> roleRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .map(Role::getName).toList());
        assertThat(names).containsExactlyInAnyOrderElementsOf(STANDARD);
        Role admin = roleRepository.findByNameAndOrganisationAndDeletedAtIsNull("ADMIN", org).orElseThrow();
        assertThat(admin.isGrantAllPermissions()).isTrue();
    }

    @Test
    void backfillAddsOnlyMissingRolesAndLeavesExistingOnesUntouched() throws Exception {
        Organisation org = register();
        // Simulate a tenant registered before seeding: drop the standard extras and
        // customise one that stays, plus a custom role of its own.
        tx.executeWithoutResult(s -> {
            for (String name : List.of("ASSET_MANAGER", "PROCUREMENT_OFFICER", "COMPLIANCE_OFFICER",
                    "IT_MANAGER", "FINANCE_MANAGER", "VIEWER")) {
                roleRepository.findByNameAndOrganisationAndDeletedAtIsNull(name, org).ifPresent(roleRepository::delete);
            }
            Role hr = roleRepository.findByNameAndOrganisationAndDeletedAtIsNull("HR_MANAGER", org).orElseThrow();
            hr.setDescription("Customised by the tenant");
            hr.getRolePermissions().clear();
            RolePermission only = new RolePermission();
            only.setRole(hr);
            only.setPermission("VIEW_TCO");
            hr.getRolePermissions().add(only);
            roleRepository.save(hr);
            Role custom = new Role();
            custom.setName("FIELD_TECH");
            custom.setOrganisation(org);
            roleRepository.save(custom);
        });

        backfill.backfill();
        int secondRun = backfill.backfill();

        tx.executeWithoutResult(s -> {
            List<Role> roles = roleRepository.findByOrganisationAndDeletedAtIsNull(org).stream().toList();
            assertThat(roles).extracting(Role::getName)
                    .containsExactlyInAnyOrderElementsOf(concat(STANDARD, "FIELD_TECH"))
                    .doesNotHaveDuplicates();
            Role hr = roles.stream().filter(r -> r.getName().equals("HR_MANAGER")).findFirst().orElseThrow();
            assertThat(hr.getDescription()).isEqualTo("Customised by the tenant");
            assertThat(hr.getRolePermissions()).extracting(RolePermission::getPermission).containsExactly("VIEW_TCO");
        });
        assertThat(secondRun).isZero();
    }

    private static List<String> concat(List<String> base, String extra) {
        return java.util.stream.Stream.concat(base.stream(), java.util.stream.Stream.of(extra)).toList();
    }

    private Organisation register() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        TenantRegisterRequest req = new TenantRegisterRequest();
        req.setOrganisationName("Roles Org " + suffix);
        req.setOrganisationContactEmail("ops+" + suffix + "@example.com");
        req.setAdminFirstName("Kwame");
        req.setAdminLastName("Owner");
        req.setAdminEmail("owner+" + suffix + "@example.com");
        req.setPassword("Password123");
        req.setCountry("GH");
        req.setTimezone("UTC");
        req.setIndustry("IT");
        MvcResult result = mockMvc.perform(post("/api/v1/tenant/register")
                        .header("X-Forwarded-For", "10.9." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        TenantRegisterResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(),
                TenantRegisterResponse.class);
        return organisationRepository.findById(resp.getOrganisationId()).orElseThrow();
    }
}
