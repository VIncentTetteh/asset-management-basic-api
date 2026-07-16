/**
 * P1-11: Currency formatting helpers for the customer portal.
 *
 * All AssetIQ pricing is stored and billed in minor units (pesewa for GHS,
 * kobo for NGN, cents for USD). These helpers render a consistent Ghana-first
 * display layer while staying flexible enough for the occasional USD-priced
 * legacy plan or international customer.
 *
 * Keep this file in lock-step with:
 *   - mobile: src/shared/utils/currency.ts
 *   - desktop: renderer/src/lib/currency.ts
 */

export type SupportedCurrency = "GHS" | "USD" | "NGN";

/**
 * Symbol map. We use the "GH\u20b5" escape so the cedi glyph renders
 * correctly on every browser regardless of font-fallback quirks.
 */
const SYMBOLS: Record<SupportedCurrency, string> = {
  GHS: "GH\u20b5",
  USD: "$",
  NGN: "\u20a6", // ₦
};

/**
 * Format a raw minor-unit integer (pesewa / kobo / cents) to a display string.
 *
 *   formatMoney(9900, "GHS")  -> "GH₵99"
 *   formatMoney(9950, "GHS")  -> "GH₵99.50"
 *   formatMoney(9900, "USD")  -> "$99.00"
 *
 * For GHS we suppress the decimals when the amount is a whole cedi value
 * because that matches how MoMo wallets and Paystack display prices.
 */
export function formatMoney(
  amountMinor: number | null | undefined,
  currency: string = "GHS",
): string {
  const code = (currency || "GHS").toUpperCase();
  const safe = Number.isFinite(amountMinor as number) ? (amountMinor as number) : 0;
  const symbol = SYMBOLS[code as SupportedCurrency] ?? `${code} `;

  if (code === "GHS") {
    const major = safe / 100;
    const hasPesewa = safe % 100 !== 0;
    return `${symbol}${major.toLocaleString("en-GH", {
      minimumFractionDigits: hasPesewa ? 2 : 0,
      maximumFractionDigits: 2,
    })}`;
  }

  // USD / NGN / anything else: show cents/kobo always — standard currency UX.
  return `${symbol}${(safe / 100).toLocaleString("en-US", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

/**
 * Convenience shorthand for cedi pricing — the path 90% of the portal uses.
 */
export function formatGhs(amountMinor: number | null | undefined): string {
  return formatMoney(amountMinor, "GHS");
}

/**
 * Parse a user-typed cedi amount ("GH₵ 1,299.50", "1299.5", "₵99") into
 * minor units. Returns null if the input can't be coerced, so the caller
 * can surface a validation error rather than silently defaulting to 0.
 */
export function parseCedis(input: string | number | null | undefined): number | null {
  if (input == null) return null;
  if (typeof input === "number") {
    return Number.isFinite(input) ? Math.round(input * 100) : null;
  }

  const stripped = input
    .replace(/GH[\u20b5\u00a2C]?/gi, "")
    .replace(/[\u20b5\u00a2]/g, "")
    .replace(/[,\s]/g, "")
    .trim();

  if (!stripped) return null;
  const parsed = Number(stripped);
  if (!Number.isFinite(parsed)) return null;
  return Math.round(parsed * 100);
}

/**
 * Return just the currency symbol for a given code. Useful for input
 * prefixes where formatMoney would be overkill.
 */
export function symbolFor(currency: string): string {
  const code = (currency || "GHS").toUpperCase() as SupportedCurrency;
  return SYMBOLS[code] ?? `${code} `;
}
