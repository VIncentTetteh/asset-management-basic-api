package com.assetiq.dto.onboarding;

import java.time.Instant;
import java.util.List;

/**
 * The first-run checklist for one organisation.
 *
 * <p>{@code dismissed} and {@code complete} are independent on purpose: putting
 * the checklist away does not pretend the work is done, and the API keeps
 * reporting the truth about each step so a settings page can still show what is
 * outstanding after the dashboard prompt has been hidden.
 *
 * @param dismissed        whether this tenant has hidden the prompt
 * @param complete         whether every non-optional step is actually done
 * @param completedSteps   how many non-optional steps are done
 * @param totalSteps       how many non-optional steps there are
 * @param nextStepKey      the first outstanding step, or null when there is none
 */
public record OnboardingStatusDto(boolean dismissed, Instant dismissedAt,
                                  boolean complete, int completedSteps, int totalSteps,
                                  String nextStepKey, List<OnboardingStepDto> steps) {
}
