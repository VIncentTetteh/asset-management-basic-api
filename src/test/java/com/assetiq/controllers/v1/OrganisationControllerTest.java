package com.assetiq.controllers.v1;

import com.assetiq.services.OrganisationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganisationControllerTest {

    @Mock
    private OrganisationService organisationService;

    @Mock
    private com.assetiq.security.PlatformAdminGuard platformAdminGuard;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrganisationController(organisationService, platformAdminGuard))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void create_isInvisibleToATenantAdmin() throws Exception {
        // Any tenant admin could stand up an unbilled organisation here; customers
        // sign themselves up through /tenant/register instead.
        when(platformAdminGuard.isPlatformAdmin()).thenReturn(false);

        mockMvc.perform(post("/api/v1/organisations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Squatter Ltd\"}"))
                .andExpect(status().isNotFound());

        org.mockito.Mockito.verify(organisationService, org.mockito.Mockito.never()).create(any());
    }

    @Test
    void create_isAllowedForAPlatformOperator() throws Exception {
        when(platformAdminGuard.isPlatformAdmin()).thenReturn(true);
        com.assetiq.dto.OrganisationDto created = new com.assetiq.dto.OrganisationDto();
        created.setName("Acme Ltd");
        when(organisationService.create(any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/organisations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme Ltd\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Ltd"));
    }

    @Test
    void patch_returnsForbiddenForAccessDenied() throws Exception {
        UUID organisationId = UUID.randomUUID();
        when(organisationService.patch(eq(organisationId), any()))
                .thenThrow(new AccessDeniedException("You do not have permission to update this organisation"));

        mockMvc.perform(patch("/api/v1/organisations/{id}", organisationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to update this organisation"));
    }

    @Test
    void patch_returnsNotFoundWhenOrganisationIsMissing() throws Exception {
        UUID organisationId = UUID.randomUUID();
        when(organisationService.patch(eq(organisationId), any()))
                .thenThrow(new EntityNotFoundException("Organisation not found"));

        mockMvc.perform(patch("/api/v1/organisations/{id}", organisationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Organisation not found"));
    }

    @Test
    void patch_refusesAnUnknownTimeZone() throws Exception {
        mockMvc.perform(patch("/api/v1/organisations/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Mars/Olympus\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.timezone").exists());
    }

    @Test
    void patch_acceptsAnIanaTimeZoneOrBlank() throws Exception {
        UUID id = UUID.randomUUID();
        when(organisationService.patch(eq(id), any())).thenReturn(new com.assetiq.dto.OrganisationDto());
        mockMvc.perform(patch("/api/v1/organisations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Africa/Accra\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/organisations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void patch_refusesAnUnknownResidencyOrABadDpoEmail() throws Exception {
        mockMvc.perform(patch("/api/v1/organisations/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataResidencyRegion\":\"MARS\",\"dpoEmail\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dataResidencyRegion").exists())
                .andExpect(jsonPath("$.errors.dpoEmail").exists());
    }
}
