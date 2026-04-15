/**
 * Plan definitions for the Customer Portal.
 * Prices are in kobo (NGN × 100). Adjust for your primary currency.
 */

export type PlanId = "STARTER" | "PROFESSIONAL" | "ENTERPRISE";

export interface PortalPlan {
  id: PlanId;
  name: string;
  tagline: string;
  priceKobo: number;       // annual price in smallest currency unit
  priceDisplay: string;    // e.g. "₦120,000"
  currency: string;        // ISO 4217
  durationDays: number;
  highlighted: boolean;
  limits: {
    assets: number | "Unlimited";
    users: number | "Unlimited";
    departments: number | "Unlimited";
  };
  features: string[];
}

export const PLANS: Record<PlanId, PortalPlan> = {
  STARTER: {
    id: "STARTER",
    name: "Starter",
    tagline: "Perfect for small teams",
    priceKobo: 12_000_000,         // ₦120,000 / year
    priceDisplay: "₦120,000",
    currency: "NGN",
    durationDays: 365,
    highlighted: false,
    limits: { assets: 500, users: 10, departments: 5 },
    features: [
      "Up to 500 assets",
      "Up to 10 users",
      "Basic analytics",
      "QR code scanning",
      "Email support",
    ],
  },
  PROFESSIONAL: {
    id: "PROFESSIONAL",
    name: "Professional",
    tagline: "For growing organisations",
    priceKobo: 36_000_000,         // ₦360,000 / year
    priceDisplay: "₦360,000",
    currency: "NGN",
    durationDays: 365,
    highlighted: true,
    limits: { assets: 5000, users: 100, departments: 50 },
    features: [
      "Up to 5,000 assets",
      "Up to 100 users",
      "Full analytics & reports",
      "Custom fields",
      "API access",
      "14-day grace period",
      "Priority email support",
    ],
  },
  ENTERPRISE: {
    id: "ENTERPRISE",
    name: "Enterprise",
    tagline: "Unlimited, SSO, dedicated support",
    priceKobo: 120_000_000,        // ₦1,200,000 / year
    priceDisplay: "₦1,200,000",
    currency: "NGN",
    durationDays: 365,
    highlighted: false,
    limits: { assets: "Unlimited", users: "Unlimited", departments: "Unlimited" },
    features: [
      "Unlimited assets & users",
      "Single Sign-On (SSO)",
      "Full analytics & reports",
      "Custom fields",
      "API access",
      "30-day grace period",
      "Dedicated account manager",
      "SLA guarantee",
    ],
  },
};

export const PLAN_LIST = Object.values(PLANS);
