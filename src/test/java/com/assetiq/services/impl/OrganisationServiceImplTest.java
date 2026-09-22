package com.assetiq.services.impl;

import com.assetiq.dto.OrganisationDto;
import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
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

    private Organisation ownTenant(String... authorities) {
        UUID organisationId = UUID.randomUUID();
        Organisation organisation = new Organisation();
        organisation.setId(organisationId);
        organisation.setName("Acme");
        organisation.setStatus(com.assetiq.enums.OrganisationStatus.ACTIVE);
        TenantContext.setOrganisationId(organisationId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@example.com", null,
                java.util.Arrays.stream(authorities).map(a -> (org.springframework.security.core.GrantedAuthority) () -> a).toList()));
        org.mockito.Mockito.lenient().when(organisationRepository.findByIdAndDeletedAtIsNull(organisationId))
                .thenReturn(Optional.of(organisation));
        return organisation;
    }

    @Test
    void tenantCannotSuspendOrDeleteItself() {
        Organisation own = ownTenant("ROLE_ORG_ADMIN");
        OrganisationDto patch = new OrganisationDto();
        patch.setStatus(com.assetiq.enums.OrganisationStatus.SUSPENDED);
        assertThrows(IllegalStateException.class, () -> organisationService.patch(own.getId(), patch));
        assertThrows(IllegalStateException.class, () -> organisationService.delete(own.getId()));
        verify(organisationRepository, never()).save(any());
    }

    @Test
    void nonAdminCannotReadAnotherTenantById() {
        ownTenant("VIEW_ASSETS");
        UUID otherId = UUID.randomUUID();
        Organisation other = new Organisation();
        other.setId(otherId);
        other.setName("Other");
        when(organisationRepository.findByIdAndDeletedAtIsNull(otherId)).thenReturn(Optional.of(other));
        assertEquals(null, organisationService.get(otherId));
    }
}
