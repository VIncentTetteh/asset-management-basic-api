/**
 * Plan definitions for the Customer Portal.
 *
 * P1-4 / P1-17a: Re-anchored to Ghana as AssetIQ's primary launch market,
 * then consolidated to a 4-plan ladder (Freemium sign-up + 2 paid + custom)
 * for pricing-page clarity. Prices are expressed in minor units
 * (GHS × 100 = pesewa), and every plan ID maps one-to-one to a backend
 * subscription plan code seeded by {@code BillingPlanSeeder}. Keep them in
 * sync — the portal sends the plan code verbatim to
 * {@code POST /api/v1/billing/checkout}.
 *
 * Pricing ladder:
 *   FREEMIUM      — free entry workspace
 *   BASIC         — GHS 99/month   (small teams, single site)
 *   BUSINESS      — GHS 799/month  (full platform for mid-market)
 *   ENTERPRISE    — custom quote (unlimited + SSO + dedicated support)
 */

import { formatGhs } from "@/lib/currency";

export type PlanId =
  | "FREEMIUM"
  | "BASIC"
  | "BUSINESS"
  | "ENTERPRISE";

export type PlanInterval = "monthly" | "yearly";

export interface PortalPlan {
  id: PlanId;
  name: string;
  tagline: string;
  /** Monthly price expressed in the smallest currency unit (pesewa for GHS). */
  priceMinor: number;
  /** Ready-to-render localised price label. */
  priceDisplay: string;
  /** ISO-4217 alpha-3 currency code. */
  currency: string;
  interval: PlanInterval;
  /** `null` when the plan is a custom-quote (ENTERPRISE). */
  durationDays: number | null;
  /** Whether the UI should highlight this plan as "most popular". */
  highlighted: boolean;
  /** Rendered on the checkout CTA — for example "Get Basic" or "Contact sales". */
  ctaLabel: string;
  /** Mobile money / channel badges to show on the plan card. */
  momoChannels: readonly (
    | "MTN MoMo"
    | "Telecel Cash"
    | "AirtelTigo Money"
    | "Card"
    | "Bank Transfer"
    | "USSD"
  )[];
  limits: {
    assets: number | "Unlimited";
    users: number | "Unlimited";
    departments: number | "Unlimited";
  };
  features: string[];
}

const ALL_CHANNELS = [
  "MTN MoMo",
  "Telecel Cash",
  "AirtelTigo Money",
  "Card",
  "Bank Transfer",
  "USSD",
] as const;

export const PLANS: Record<PlanId, PortalPlan> = {
  FREEMIUM: {
    id: "FREEMIUM",
    name: "Freemium",
    tagline: "Start tracking core assets with no payment required",
    priceMinor: 0,
    priceDisplay: "Free",
    currency: "GHS",
    interval: "monthly",
    durationDays: null,
    highlighted: false,
    ctaLabel: "Included by default",
    momoChannels: ALL_CHANNELS,
    limits: { assets: 50, users: 5, departments: 2 },
    features: [
      "Up to 50 assets",
      "Up to 5 users",
      "Basic asset tracking",
      "Freemium workspace access",
    ],
  },
  BASIC: {
    id: "BASIC",
    name: "Basic",
    tagline: "Perfect for small teams and single-site operations",
    priceMinor: 9_900, // GHS 99.00
    priceDisplay: "GH₵99",
    currency: "GHS",
    interval: "monthly",
    durationDays: 30,
    highlighted: false,
    ctaLabel: "Get Basic",
    momoChannels: ALL_CHANNELS,
    limits: { assets: 250, users: 10, departments: 5 },
    features: [
      "Up to 250 assets",
      "Up to 10 users",
      "QR / barcode scanning",
      "Mobile money checkout (MTN, Telecel, AirtelTigo)",
      "Email support — 1 business day",
    ],
  },
  // Business is the primary paid upgrade path for mid-market organisations.
  BUSINESS: {
    id: "BUSINESS",
    name: "Business",
    tagline: "Full-platform asset management for mid-market businesses",
    priceMinor: 79_900, // GHS 799.00
    priceDisplay: "GH₵799",
    currency: "GHS",
    interval: "monthly",
    durationDays: 30,
    highlighted: true,
    ctaLabel: "Get Business",
    momoChannels: ALL_CHANNELS,
    limits: { assets: 10_000, users: 250, departments: "Unlimited" },
    features: [
      "Up to 10,000 assets",
      "Up to 250 users",
      "5-year audit retention",
      "Advanced workflows & approvals",
      "Department & cost-centre budgets",
      "Priority support — 1 business hour",
    ],
  },
  ENTERPRISE: {
    id: "ENTERPRISE",
    name: "Enterprise",
    tagline: "Unlimited scale, SSO, and dedicated Ghana-based success manager",
    priceMinor: 0,
    priceDisplay: "Contact sales",
    currency: "GHS",
    interval: "monthly",
    durationDays: null,
    highlighted: false,
    ctaLabel: "Talk to sales",
    momoChannels: ALL_CHANNELS,
    limits: {
      assets: "Unlimited",
      users: "Unlimited",
      departments: "Unlimited",
    },
    features: [
      "Unlimited assets & users",
      "Single Sign-On (SAML / OIDC)",
      "Custom onboarding & migration",
      "10-year audit retention",
      "Dedicated account manager",
      "99.9% uptime SLA",
    ],
  },
};

export const PLAN_LIST: readonly PortalPlan[] = Object.values(PLANS);

/**
 * Convenience helper — format a {@link PortalPlan#priceMinor} into a GHS
 * display string. Useful when the caller has an arbitrary amount (e.g. a
 * discount calculation) rather than a raw plan.
 *
 * P1-11: delegates to the shared {@link formatGhs} helper so there is a
 * single source of truth for cedi formatting across the portal.
 */
export function formatGhsMinor(amountMinor: number): string {
  return formatGhs(amountMinor);
}

/** True when the plan is a custom-quote tier (no self-serve checkout). */
export function isContactSalesPlan(plan: PortalPlan): boolean {
  return plan.id === "ENTERPRISE";
}
