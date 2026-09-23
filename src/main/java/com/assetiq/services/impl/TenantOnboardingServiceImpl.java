package com.assetiq.services.impl;

import com.assetiq.dto.onboarding.OnboardingStatusDto;
import com.assetiq.dto.onboarding.OnboardingStepDto;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.CategoryRepository;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.repositories.LocationRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.UserInvitationRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.services.TenantAwareService;
import com.assetiq.services.TenantOnboardingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Derives the first-run checklist from counts, never from stored progress.
 *
 * <p>The distinction matters: a checklist that remembers "you added a location"
 * keeps saying so after the location is deleted, and a new tenant that imported
 * and rolled back is told it is finished when it has nothing. Each step below
 * asks the database the same question the step describes, every time.
 */
@Service
@Transactional(readOnly = true)
public class TenantOnboardingServiceImpl extends TenantAwareService implements TenantOnboardingService {

    private final UserRepository userRepository;
    private final UserInvitationRepository invitationRepository;
    private final LocationRepository locationRepository;
    private final DepartmentRepository departmentRepository;
    private final CategoryRepository categoryRepository;
    private final AssetRepository assetRepository;

    private Clock clock = Clock.systemUTC();

    public TenantOnboardingServiceImpl(OrganisationRepository organisationRepository,
                                       UserRepository userRepository,
                                       UserInvitationRepository invitationRepository,
                                       LocationRepository locationRepository,
                                       DepartmentRepository departmentRepository,
                                       CategoryRepository categoryRepository,
                                       AssetRepository assetRepository) {
        super(organisationRepository);
        this.userRepository = userRepository;
        this.invitationRepository = invitationRepository;
        this.locationRepository = locationRepository;
        this.departmentRepository = departmentRepository;
        this.categoryRepository = categoryRepository;
        this.assetRepository = assetRepository;
    }

    @Override
    public OnboardingStatusDto status(String callerEmail) {
        return build(requireTenantOrg(), callerEmail);
    }

    @Override
    @Transactional
    public OnboardingStatusDto dismiss(String callerEmail) {
        Organisation org = requireTenantOrg();
        if (org.getOnboardingDismissedAt() == null) {
            org.setOnboardingDismissedAt(clock.instant());
            organisationRepository.save(org);
        }
        return build(org, callerEmail);
    }

    @Override
    @Transactional
    public OnboardingStatusDto restore(String callerEmail) {
        Organisation org = requireTenantOrg();
        org.setOnboardingDismissedAt(null);
        organisationRepository.save(org);
        return build(org, callerEmail);
    }

    private OnboardingStatusDto build(Organisation org, String callerEmail) {
        Instant now = clock.instant();

        long people = userRepository.countByOrganisationAndDeletedAtIsNull(org);
        long pendingInvites = invitationRepository.countLivePending(org, now);
        long locations = locationRepository.countByOrganisationAndDeletedAtIsNull(org);
        long departments = departmentRepository.countByOrganisationAndDeletedAtIsNull(org);
        long categories = categoryRepository.countByOrganisationAndDeletedAtIsNull(org);
        long assets = assetRepository.countByOrganisationAndDeletedAtIsNull(org);

        boolean callerVerified = callerEmail != null
                && userRepository.findByEmailAndOrganisationId(callerEmail, org.getId())
                        .map(u -> u.getEmailVerifiedAt() != null)
                        .orElse(true);

        List<OnboardingStepDto> steps = new ArrayList<>();

        // Ordered as a company would actually do it: prove the mailbox, describe
        // where things live and what they are, then put assets in, then bring
        // colleagues in to work with them.
        steps.add(new OnboardingStepDto("verify_email",
                "Confirm your email address",
                "We sent you a link when you registered. Confirming it keeps your account recoverable.",
                callerVerified, callerVerified ? 1 : 0, "YOU",
                "users", "/api/v1/auth/resend-verification", false));

        steps.add(new OnboardingStepDto("add_locations",
                "Add your sites and locations",
                "Assets live somewhere. Adding your offices, floors or stores is what makes an audit possible later.",
                locations > 0, locations, "ORGANISATION",
                "locations", "/api/v1/locations", false));

        steps.add(new OnboardingStepDto("add_categories",
                "Set up asset categories",
                "Categories drive depreciation, reporting and the maintenance schedules you will want later.",
                categories > 0, categories, "ORGANISATION",
                "categories", "/api/v1/categories", false));

        steps.add(new OnboardingStepDto("add_departments",
                "Add your departments",
                "Departments are how costs and custody are attributed once assets are in use.",
                departments > 0, departments, "ORGANISATION",
                "departments", "/api/v1/departments", false));

        steps.add(new OnboardingStepDto("add_assets",
                "Register your first assets",
                "Add one by hand to see the flow, or import a spreadsheet to bring the whole register in at once.",
                assets > 0, assets, "ORGANISATION",
                "assets", "/api/v1/assets", false));

        // Counted as done by an outstanding invitation as well as a second
        // colleague: the administrator has done their part, and reopening the
        // step because nobody has accepted yet would be nagging them about
        // someone else's inbox.
        steps.add(new OnboardingStepDto("invite_team",
                "Invite your colleagues",
                "Invite the people who will use AssetIQ with you and choose what each of them can do.",
                people > 1 || pendingInvites > 0, Math.max(people - 1, 0) + pendingInvites, "ORGANISATION",
                "invitations", "/api/v1/invitations", false));

        List<OnboardingStepDto> required = steps.stream().filter(s -> !s.optional()).toList();
        int completed = (int) required.stream().filter(OnboardingStepDto::done).count();
        String next = required.stream().filter(s -> !s.done())
                .map(OnboardingStepDto::key).findFirst().orElse(null);

        return new OnboardingStatusDto(
                org.getOnboardingDismissedAt() != null,
                org.getOnboardingDismissedAt(),
                completed == required.size(),
                completed, required.size(), next, steps);
    }

    /** Test seam. */
    void setClock(Clock clock) {
        this.clock = clock;
    }
}
