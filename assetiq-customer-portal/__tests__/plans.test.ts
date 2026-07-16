/**
 * Unit tests — plans.ts
 * Validates that plan definitions are internally consistent.
 *
 * Re-anchored to the public 4-plan pricing story:
 *   Freemium → Basic → Business → Enterprise.
 */

import { describe, it, expect } from "vitest";
import { PLANS, PLAN_LIST, isContactSalesPlan, formatGhsMinor } from "@/lib/plans";

describe("Plan definitions", () => {
  it("exports exactly four public packages", () => {
    expect(PLAN_LIST.length).toBe(4);
  });

  it("paid plans have non-zero priceMinor; enterprise is contact-sales", () => {
    expect(PLANS.FREEMIUM.priceMinor).toBe(0);
    expect(PLANS.BASIC.priceMinor).toBeGreaterThan(0);
    expect(PLANS.BUSINESS.priceMinor).toBeGreaterThan(0);
    expect(PLANS.ENTERPRISE.priceMinor).toBe(0);
    expect(isContactSalesPlan(PLANS.ENTERPRISE)).toBe(true);
    expect(isContactSalesPlan(PLANS.FREEMIUM)).toBe(false);
    expect(isContactSalesPlan(PLANS.BASIC)).toBe(false);
    expect(isContactSalesPlan(PLANS.BUSINESS)).toBe(false);
  });

  it("monotonically increasing paid-plan ladder: basic < business", () => {
    expect(PLANS.BUSINESS.priceMinor).toBeGreaterThan(PLANS.BASIC.priceMinor);
  });

  it("every plan prices in GHS", () => {
    for (const plan of PLAN_LIST) {
      expect(plan.currency).toBe("GHS");
    }
  });

  it("enterprise plan has unlimited assets", () => {
    expect(PLANS.ENTERPRISE.limits.assets).toBe("Unlimited");
  });

  it("every plan has at least one feature listed", () => {
    for (const plan of PLAN_LIST) {
      expect(plan.features.length).toBeGreaterThan(0);
    }
  });

  it("paid plans bill monthly; enterprise has null durationDays", () => {
    expect(PLANS.BASIC.durationDays).toBe(30);
    expect(PLANS.BUSINESS.durationDays).toBe(30);
    expect(PLANS.ENTERPRISE.durationDays).toBeNull();
  });

  it("exactly one plan is highlighted (BUSINESS)", () => {
    const highlighted = PLAN_LIST.filter((p) => p.highlighted);
    expect(highlighted.length).toBe(1);
    expect(highlighted[0].id).toBe("BUSINESS");
  });

  it("every plan advertises mobile-money channels", () => {
    for (const plan of PLAN_LIST) {
      expect(plan.momoChannels).toContain("MTN MoMo");
      expect(plan.momoChannels).toContain("Telecel Cash");
      expect(plan.momoChannels).toContain("AirtelTigo Money");
    }
  });
});

describe("formatGhsMinor", () => {
  it("formats whole cedis without decimals", () => {
    expect(formatGhsMinor(9900)).toBe("GH₵99");
    expect(formatGhsMinor(29900)).toBe("GH₵299");
  });

  it("formats pesewa with two decimals when non-zero", () => {
    // Any non-whole-cedi amount is rendered with exactly two decimals so the
    // pesewa portion is always unambiguous on receipts & invoices.
    expect(formatGhsMinor(9950)).toBe("GH₵99.50");
    expect(formatGhsMinor(101)).toBe("GH₵1.01");
  });

  it("handles zero and non-finite input", () => {
    expect(formatGhsMinor(0)).toBe("GH₵0");
    expect(formatGhsMinor(Number.NaN)).toBe("GH₵0");
  });
});
