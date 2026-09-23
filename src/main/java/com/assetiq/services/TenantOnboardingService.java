package com.assetiq.services;

import com.assetiq.dto.onboarding.OnboardingStatusDto;

/**
 * The first-run checklist a newly registered company sees.
 *
 * <p>Not to be confused with employee onboarding ({@code EmployeeService}),
 * which is an HR checklist for a joiner's asset handover. This one is about the
 * tenant's own setup: an empty product with no locations, no categories and one
 * person in it is a product nobody can evaluate.
 *
 * <p>Every step is derived from a live count. Nothing here is stored progress,
 * so a step cannot report itself complete while the thing it describes does not
 * exist — including after an import is rolled back or the only location is
 * deleted.
 */
public interface TenantOnboardingService {

    /** The checklist for the caller's organisation, with the caller's own steps resolved. */
    OnboardingStatusDto status(String callerEmail);

    /** Hides the prompt for the whole organisation. Changes no step's truth. */
    OnboardingStatusDto dismiss(String callerEmail);

    /** Shows it again. */
    OnboardingStatusDto restore(String callerEmail);
}
