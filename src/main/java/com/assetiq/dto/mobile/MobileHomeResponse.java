package com.assetiq.dto.mobile;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Everything the mobile Home screen shows, in one round trip.
 *
 * <p>A section the caller has no permission for is {@code null}, never zero:
 * a zero reads as good news ("nothing overdue") when the truth is "you may
 * not see this". {@link #needsYou} is always present because its unread
 * notification count is the caller's own data.
 *
 * @param needsYou              counts of things waiting on the caller; never null
 * @param queue                 up to five items to act on, most urgent first: overdue
 *                              maintenance and checkouts by due date, then transfers
 *                              awaiting the caller's approval by request date. Built
 *                              only from sections the caller may read; never null
 * @param portfolio             asset totals by status; null without asset read access
 * @param recentlyUpdated       the ten most recently updated assets, newest first;
 *                              null without asset read access
 * @param budgetUtilisationPct  spent / allocated across in-force budgets, in the
 *                              tenant base currency; null without budget read access,
 *                              when there is nothing allocated, or when a budget's
 *                              currency has no exchange rate (a partial figure would
 *                              be a wrong one)
 * @param generatedAt           server time the figures were read
 */
// ALWAYS overrides the app-wide non_null inclusion: a withheld section must
// reach the client as an explicit null, not an absent key.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MobileHomeResponse(
        NeedsYou needsYou,
        List<QueueItem> queue,
        Portfolio portfolio,
        List<RecentAsset> recentlyUpdated,
        Integer budgetUtilisationPct,
        Instant generatedAt) {
}
