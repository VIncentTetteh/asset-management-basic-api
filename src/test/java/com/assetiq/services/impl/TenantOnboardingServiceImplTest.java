package com.assetiq.services.impl;

import com.assetiq.dto.onboarding.OnboardingStatusDto;
import com.assetiq.dto.onboarding.OnboardingStepDto;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.CategoryRepository;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.repositories.LocationRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.UserInvitationRepository;
import com.assetiq.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The first-run checklist must describe what the tenant actually has. A step
 * that reports itself done on a tenant with nothing in it is worse than no
 * checklist: it sends a new customer looking for data that was never imported.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("The first-run checklist")
class TenantOnboardingServiceImplTest {

    @Mock OrganisationRepository organisationRepository;
    @Mock UserRepository userRepository;
    @Mock UserInvitationRepository invitationRepository;
    @Mock LocationRepository locationRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock AssetRepository assetRepository;

    private TenantOnboardingServiceImpl service;
    private Organisation org;
    private User caller;
    private final Instant now = Instant.parse("2026-03-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        service = new TenantOnboardingServiceImpl(organisationRepository, userRepository, invitationRepository,
                locationRepository, departmentRepository, categoryRepository, assetRepository);
        service.setClock(Clock.fixed(now, ZoneOffset.UTC));

        org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setName("Acme Ghana");
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        when(organisationRepository.save(any(Organisation.class))).thenAnswer(i -> i.getArgument(0));

        caller = new User();
        caller.setEmail("owner@acme.test");
        when(userRepository.findByEmailAndOrganisationId(anyString(), any())).thenReturn(Optional.of(caller));

        // A brand-new tenant: one person, nothing else.
        when(userRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(1L);
        when(invitationRepository.countLivePending(any(), any())).thenReturn(0L);
        when(locationRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(0L);
        when(departmentRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(0L);
        when(categoryRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(0L);
        when(assetRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("an empty tenant has nothing done and is pointed at the first step")
    void emptyTenantHasNothingDone() {
        OnboardingStatusDto status = service.status("owner@acme.test");

        assertThat(status.complete()).isFalse();
        assertThat(status.completedSteps()).isZero();
        assertThat(status.totalSteps()).isEqualTo(6);
        assertThat(status.nextStepKey()).isEqualTo("verify_email");
        assertThat(status.steps()).extracting(OnboardingStepDto::key).containsExactly(
                "verify_email", "add_locations", "add_categories", "add_departments", "add_assets", "invite_team");
        assertThat(status.steps()).allMatch(step -> !step.done());
    }

    @Test
    @DisplayName("a step is done only when the data behind it exists, and counts what is there")
    void stepsFollowTheData() {
        caller.setEmailVerifiedAt(now);
        when(locationRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(3L);
        when(assetRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(42L);

        OnboardingStatusDto status = service.status("owner@acme.test");

        assertThat(step(status, "verify_email").done()).isTrue();
        assertThat(step(status, "add_locations").done()).isTrue();
        assertThat(step(status, "add_locations").count()).isEqualTo(3L);
        assertThat(step(status, "add_assets").count()).isEqualTo(42L);
        assertThat(step(status, "add_categories").done()).isFalse();
        // Verified, located and stocked — three of the six.
        assertThat(status.completedSteps()).isEqualTo(3);
        assertThat(status.nextStepKey()).isEqualTo("add_categories");
    }

    @Test
    @DisplayName("deleting the last location reopens the step it had completed")
    void stepsReopenWhenTheDataGoesAway() {
        when(locationRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(1L);
        assertThat(step(service.status("owner@acme.test"), "add_locations").done()).isTrue();

        when(locationRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(0L);
        assertThat(step(service.status("owner@acme.test"), "add_locations").done()).isFalse();
    }

    @Test
    @DisplayName("an outstanding invitation counts as having invited the team")
    void anOutstandingInvitationCompletesTheInviteStep() {
        when(invitationRepository.countLivePending(any(), any())).thenReturn(2L);

        assertThat(step(service.status("owner@acme.test"), "invite_team").done()).isTrue();
        assertThat(step(service.status("owner@acme.test"), "invite_team").count()).isEqualTo(2L);
    }

    @Test
    @DisplayName("dismissing hides the prompt without claiming the work is done")
    void dismissingDoesNotFakeCompletion() {
        OnboardingStatusDto dismissed = service.dismiss("owner@acme.test");

        assertThat(dismissed.dismissed()).isTrue();
        assertThat(dismissed.dismissedAt()).isEqualTo(now);
        // The steps keep telling the truth, so a settings page can still show them.
        assertThat(dismissed.complete()).isFalse();
        assertThat(dismissed.completedSteps()).isZero();

        assertThat(service.restore("owner@acme.test").dismissed()).isFalse();
    }

    private static OnboardingStepDto step(OnboardingStatusDto status, String key) {
        return status.steps().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
    }
}
