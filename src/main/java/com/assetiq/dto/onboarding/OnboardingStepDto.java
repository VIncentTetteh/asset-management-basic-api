package com.assetiq.dto.onboarding;

/**
 * One thing a new organisation still needs, derived from what is actually in
 * the database rather than from a flag someone remembered to set.
 *
 * @param key         stable identifier, e.g. {@code add_locations}
 * @param title       short imperative — "Add your sites"
 * @param description why it matters, in one sentence
 * @param done        true only when the underlying data exists
 * @param count       how many of the thing exist now
 * @param scope       {@code ORGANISATION} for company setup, {@code YOU} for
 *                    something only the signed-in person can do
 * @param resource    the thing being counted, e.g. {@code locations}
 * @param apiPath     where the web app creates one
 * @param optional    true when the product is usable without it
 */
public record OnboardingStepDto(String key, String title, String description,
                                boolean done, long count, String scope,
                                String resource, String apiPath, boolean optional) {
}
