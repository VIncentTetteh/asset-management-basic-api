package com.example.demo.services.impl;

import com.example.demo.dto.OrganisationDto;
import com.example.demo.models.Organisation;
import com.example.demo.multitenancy.TenantContext;
import com.example.demo.repositories.OrganisationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganisationServiceImplTest {

    @Mock
    private OrganisationRepository organisationRepository;

    @InjectMocks
    private OrganisationServiceImpl organisationService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void patch_allowsTenantOrganisationForAdminEvenWhenCreatedByDiffers() {
        UUID organisationId = UUID.randomUUID();
        Organisation organisation = new Organisation();
        organisation.setId(organisationId);
        organisation.setName("Old Name");
        organisation.setCreatedBy("another-admin@example.com");

        OrganisationDto patch = new OrganisationDto();
        patch.setName("New Name");

        TenantContext.setOrganisationId(organisationId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@example.com", null, List.of(() -> "ROLE_ADMIN")));

        when(organisationRepository.findByIdAndDeletedAtIsNull(organisationId)).thenReturn(Optional.of(organisation));
        when(organisationRepository.save(any(Organisation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganisationDto result = organisationService.patch(organisationId, patch);

        assertEquals("New Name", result.getName());
        verify(organisationRepository).save(organisation);
    }

    @Test
    void patch_deniesUpdatingAnotherTenantOrganisation() {
        UUID organisationId = UUID.randomUUID();
        Organisation organisation = new Organisation();
        organisation.setId(organisationId);

        TenantContext.setOrganisationId(UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@example.com", null, List.of(() -> "ROLE_ADMIN")));

        when(organisationRepository.findByIdAndDeletedAtIsNull(organisationId)).thenReturn(Optional.of(organisation));

        AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                () -> organisationService.patch(organisationId, new OrganisationDto()));

        assertEquals("You do not have permission to update this organisation", exception.getMessage());
        verify(organisationRepository, never()).save(any(Organisation.class));
    }

    @Test
    void patch_throwsNotFoundWhenOrganisationDoesNotExist() {
        UUID organisationId = UUID.randomUUID();

        when(organisationRepository.findByIdAndDeletedAtIsNull(organisationId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> organisationService.patch(organisationId, new OrganisationDto()));

        assertEquals("Organisation not found", exception.getMessage());
    }
}
