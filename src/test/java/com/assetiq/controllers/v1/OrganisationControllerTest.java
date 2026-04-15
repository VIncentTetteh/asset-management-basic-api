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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganisationControllerTest {

    @Mock
    private OrganisationService organisationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrganisationController(organisationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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
}
