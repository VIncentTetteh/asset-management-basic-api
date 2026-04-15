/**
 * Unit tests — plans.ts
 * Validates that plan definitions are internally consistent.
 */

import { describe, it, expect } from "vitest";
import { PLANS, PLAN_LIST } from "@/lib/plans";

describe("Plan definitions", () => {
  it("exports exactly three plans", () => {
    expect(PLAN_LIST.length).toBe(3);
  });

  it("every plan has a non-zero price", () => {
    for (const plan of PLAN_LIST) {
      expect(plan.priceKobo).toBeGreaterThan(0);
    }
  });

  it("ENTERPRISE has higher price than PROFESSIONAL, which has higher price than STARTER", () => {
    expect(PLANS.ENTERPRISE.priceKobo).toBeGreaterThan(PLANS.PROFESSIONAL.priceKobo);
    expect(PLANS.PROFESSIONAL.priceKobo).toBeGreaterThan(PLANS.STARTER.priceKobo);
  });

  it("ENTERPRISE plan has unlimited assets", () => {
    expect(PLANS.ENTERPRISE.limits.assets).toBe("Unlimited");
  });

  it("every plan has at least one feature listed", () => {
    for (const plan of PLAN_LIST) {
      expect(plan.features.length).toBeGreaterThan(0);
    }
  });

  it("durationDays is 365 for all plans", () => {
    for (const plan of PLAN_LIST) {
      expect(plan.durationDays).toBe(365);
    }
  });

  it("exactly one plan is highlighted", () => {
    const highlighted = PLAN_LIST.filter((p) => p.highlighted);
    expect(highlighted.length).toBe(1);
    expect(highlighted[0].id).toBe("PROFESSIONAL");
  });
});
