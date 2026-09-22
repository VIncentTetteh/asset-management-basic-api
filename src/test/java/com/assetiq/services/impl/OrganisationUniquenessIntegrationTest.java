package com.assetiq.services.impl;

import com.assetiq.BaseIntegrationTest;
import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.OrganisationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V52: names unique case-insensitively among live tenants; contact email not unique; blanks stored as NULL. */
@DisplayName("Organisation uniqueness (V52)")
class OrganisationUniquenessIntegrationTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired OrganisationRepository organisationRepository;

    @Test
    void twoTenantsMayShareAContactEmail_andBlankKeysAreStoredAsNull() throws Exception {
        String suffix = suffix();
        register("Shared A " + suffix, "ops@shared-" + suffix + ".com", "").andExpect(status().isCreated());
        register("Shared B " + suffix, "ops@shared-" + suffix + ".com", "").andExpect(status().isCreated());

        Organisation a = organisationRepository.findAll().stream()
                .filter(o -> o.getName().equals("Shared A " + suffix)).findFirst().orElseThrow();
        assertThat(a.getRegistrationNumber()).isNull();
        assertThat(a.getTaxId()).isNull();
    }

    @Test
    void aNameDifferingOnlyInCase_isADuplicateFieldError() throws Exception {
        String suffix = suffix();
        register("Casey " + suffix, null, null).andExpect(status().isCreated());
        register("CASEY " + suffix.toUpperCase(), null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.organisationName").exists());
    }

    @Test
    void aClosedTenantsNameCanBeReused() throws Exception {
        String suffix = suffix();
        Organisation closed = new Organisation();
        closed.setName("Closed " + suffix);
        closed.setDeletedAt(Instant.now());
        organisationRepository.save(closed);

        register("Closed " + suffix, null, null).andExpect(status().isCreated());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private ResultActions register(String orgName, String contactEmail, String registrationNumber) throws Exception {
        TenantRegisterRequest req = new TenantRegisterRequest();
        req.setOrganisationName(orgName);
        req.setOrganisationContactEmail(contactEmail);
        req.setRegistrationNumber(registrationNumber);
        req.setTaxId(registrationNumber);
        req.setAdminFirstName("Ama");
        req.setAdminLastName("Mensah");
        req.setAdminEmail("admin+" + suffix() + "@example.com");
        req.setPassword("Password123!");
        req.setCountry("GH");
        req.setTimezone("Africa/Accra");
        return mockMvc.perform(post("/api/v1/tenant/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));
    }
}
