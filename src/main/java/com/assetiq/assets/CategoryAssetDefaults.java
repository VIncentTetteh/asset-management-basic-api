package com.assetiq.assets;

import com.assetiq.models.Category;

import java.time.LocalDate;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Defaults a category promises for new assets: its asset prefix code builds the
 * next asset tag ({@code PREFIX-0001}, {@code PREFIX-0002}, ...) and its default
 * warranty sets the warranty expiry from the purchase date. Both apply only when
 * the caller did not supply a value.
 */
public final class CategoryAssetDefaults {

    /** Tag numbers are zero-padded to this width; longer numbers simply grow. */
    static final int TAG_NUMBER_WIDTH = 4;

    private CategoryAssetDefaults() {
    }

    /** The trimmed prefix code, or null when the category has none. */
    public static String prefixOf(Category category) {
        if (category == null || category.getAssetPrefixCode() == null) return null;
        String prefix = category.getAssetPrefixCode().trim();
        return prefix.isEmpty() ? null : prefix;
    }

    /**
     * The next tag after the highest {@code PREFIX-<number>} among {@code existingTags}.
     * Tags that only share the leading characters (e.g. {@code LAPTOP-7} for prefix
     * {@code LAP}) or have a non-numeric suffix are ignored.
     *
     * <p>Reading the tags and adding one is not safe against a concurrent save:
     * {@link AssetTagAllocator} claims the number instead, and this is only used
     * to seed its counter. Kept for callers that generate tags outside a request.
     */
    public static String nextTag(String prefix, Collection<String> existingTags) {
        return formatTag(prefix, highestNumber(prefix, existingTags) + 1);
    }

    /** The highest {@code PREFIX-<number>} among {@code existingTags}, or 0 for none. */
    public static long highestNumber(String prefix, Collection<String> existingTags) {
        Pattern own = Pattern.compile("^" + Pattern.quote(prefix) + "-(\\d+)$");
        long max = 0;
        for (String tag : existingTags) {
            if (tag == null) continue;
            Matcher m = own.matcher(tag.trim());
            if (m.matches()) {
                try {
                    max = Math.max(max, Long.parseLong(m.group(1)));
                } catch (NumberFormatException ignored) {
                    // more digits than a long holds: not one of ours
                }
            }
        }
        return max;
    }

    /** {@code PREFIX-0007}: the number zero-padded to {@link #TAG_NUMBER_WIDTH}. */
    public static String formatTag(String prefix, long number) {
        return prefix + "-" + String.format("%0" + TAG_NUMBER_WIDTH + "d", number);
    }

    /**
     * Warranty expiry from the category's default warranty, or null when there is
     * no purchase date or no positive default.
     */
    public static LocalDate warrantyExpiry(Category category, LocalDate purchaseDate) {
        if (category == null || purchaseDate == null) return null;
        Integer months = category.getDefaultWarrantyPeriodMonths();
        if (months == null || months <= 0) return null;
        return purchaseDate.plusMonths(months);
    }
}
