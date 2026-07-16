"use client";

import { useState, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { ArrowLeft, Loader2, Lock, CreditCard, Smartphone, Banknote, Hash } from "lucide-react";
import { PLANS, PLAN_LIST, isContactSalesPlan, type PlanId } from "@/lib/plans";

/**
 * P1-8: Mobile-money badges. AssetIQ's Ghana customers pay primarily with
 * MTN MoMo / Telecel Cash / AirtelTigo Money, so the checkout surface
 * should broadcast those rails alongside card / bank transfer / USSD.
 */
const CHANNEL_BADGES: { label: string; icon: React.ComponentType<{ className?: string }> }[] = [
  { label: "MTN MoMo", icon: Smartphone },
  { label: "Telecel Cash", icon: Smartphone },
  { label: "AirtelTigo Money", icon: Smartphone },
  { label: "Card", icon: CreditCard },
  { label: "Bank Transfer", icon: Banknote },
  { label: "USSD", icon: Hash },
];

// Business is the highlighted paid tier and the checkout default when ?plan= is missing.
const DEFAULT_PLAN: PlanId = "BUSINESS";

function CheckoutForm() {
  const searchParams = useSearchParams();
  const router = useRouter();

  const initialPlanParam = searchParams.get("plan") as PlanId | null;
  const initialPlan: PlanId =
    initialPlanParam && PLANS[initialPlanParam] ? initialPlanParam : DEFAULT_PLAN;

  const [selectedPlan, setSelectedPlan] = useState<PlanId>(initialPlan);
  const [email, setEmail] = useState("");
  const [orgName, setOrgName] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const plan = PLANS[selectedPlan];
  const salesOnly = isContactSalesPlan(plan);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (salesOnly) {
      window.location.href = `mailto:sales@assetiq.app?subject=Enterprise plan enquiry&body=Org: ${encodeURIComponent(
        orgName,
      )}%0AEmail: ${encodeURIComponent(email)}`;
      return;
    }

    setError(null);
    setLoading(true);

    try {
      const res = await fetch("/api/checkout", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, orgName, planId: selectedPlan }),
      });

      const data = await res.json();
      if (!res.ok) throw new Error(data.error ?? "Checkout failed");

      window.location.href = data.authorizationUrl;
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Something went wrong");
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto max-w-5xl px-6 py-12">
      <button
        onClick={() => router.push("/")}
        className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-800 mb-8 transition-colors"
      >
        <ArrowLeft className="h-4 w-4" /> Back to pricing
      </button>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-8">
        {/* Form — 3 cols */}
        <div className="lg:col-span-3 space-y-6">
          <div>
            <h1 className="text-2xl font-bold text-slate-900">Complete your order</h1>
            <p className="text-sm text-slate-500 mt-1">
              Pay in Ghana cedis with MTN MoMo, Telecel Cash, AirtelTigo Money, card, bank, or USSD.
              Your license key is emailed immediately after payment.
            </p>
          </div>

          {/* Plan selector */}
          <div>
            <label className="block text-sm font-semibold text-slate-700 mb-2">Plan</label>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
              {PLAN_LIST.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => setSelectedPlan(p.id)}
                  className={`rounded-xl border p-3 text-left transition-all ${
                    selectedPlan === p.id
                      ? "border-brand-500 bg-brand-50 ring-1 ring-brand-500"
                      : "border-slate-200 bg-white hover:border-slate-300"
                  }`}
                >
                  <p className="text-xs font-bold text-slate-900">{p.name}</p>
                  <p className="text-xs text-slate-500 mt-0.5">
                    {isContactSalesPlan(p) ? "Custom quote" : `${p.priceDisplay}/mo`}
                  </p>
                </button>
              ))}
            </div>
          </div>

          {/* Contact form */}
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="orgName" className="block text-sm font-semibold text-slate-700 mb-1.5">
                Organisation name
              </label>
              <input
                id="orgName"
                type="text"
                required
                placeholder="Kwabenya Depot Ltd"
                value={orgName}
                onChange={(e) => setOrgName(e.target.value)}
                className="w-full rounded-xl border border-slate-200 px-4 py-2.5 text-sm text-slate-900
                           placeholder:text-slate-400 focus:border-brand-500 focus:outline-none
                           focus:ring-2 focus:ring-brand-500/20"
              />
            </div>

            <div>
              <label htmlFor="email" className="block text-sm font-semibold text-slate-700 mb-1.5">
                Work email
              </label>
              <input
                id="email"
                type="email"
                required
                placeholder="admin@kwabenya.com.gh"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full rounded-xl border border-slate-200 px-4 py-2.5 text-sm text-slate-900
                           placeholder:text-slate-400 focus:border-brand-500 focus:outline-none
                           focus:ring-2 focus:ring-brand-500/20"
              />
              <p className="text-xs text-slate-400 mt-1.5">
                Your license key will be sent to this address.
              </p>
            </div>

            {error && (
              <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full flex items-center justify-center gap-2 rounded-xl bg-brand-600
                         py-3 text-sm font-semibold text-white hover:bg-brand-700 transition-colors
                         disabled:opacity-60"
            >
              {loading ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" /> Redirecting to Paystack…
                </>
              ) : salesOnly ? (
                <>Email sales@assetiq.app</>
              ) : (
                <>
                  <CreditCard className="h-4 w-4" /> Pay {plan.priceDisplay} / month
                </>
              )}
            </button>

            {/* MoMo + channel badges */}
            {!salesOnly && (
              <div className="flex flex-wrap items-center justify-center gap-2">
                {CHANNEL_BADGES.map(({ label, icon: Icon }) => (
                  <span
                    key={label}
                    className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[11px] font-medium text-slate-600"
                  >
                    <Icon className="h-3 w-3" /> {label}
                  </span>
                ))}
              </div>
            )}

            <div className="flex items-center justify-center gap-1.5 text-xs text-slate-400">
              <Lock className="h-3 w-3" /> Secured by Paystack · 256-bit SSL
            </div>
          </form>
        </div>

        {/* Order summary — 2 cols */}
        <div className="lg:col-span-2">
          <div className="sticky top-6 rounded-2xl border border-slate-200 bg-white p-6">
            <h2 className="font-semibold text-slate-900 mb-4">Order summary</h2>

            <div className="rounded-xl bg-brand-50 border border-brand-100 p-4 mb-4">
              <p className="font-bold text-brand-800">{plan.name}</p>
              <p className="text-xs text-brand-600 mt-0.5">{plan.tagline}</p>
            </div>

            <div className="space-y-2 text-sm mb-4">
              {[
                { label: "Assets", value: plan.limits.assets },
                { label: "Users", value: plan.limits.users },
                { label: "Departments", value: plan.limits.departments },
                {
                  label: "Billing",
                  value: salesOnly ? "Custom" : `${plan.interval} · ${plan.currency}`,
                },
              ].map(({ label, value }) => (
                <div key={label} className="flex justify-between">
                  <span className="text-slate-500">{label}</span>
                  <span className="font-medium text-slate-800">
                    {typeof value === "number" ? value.toLocaleString() : value}
                  </span>
                </div>
              ))}
            </div>

            <div className="border-t border-slate-100 pt-4 flex justify-between">
              <span className="font-semibold text-slate-900">
                {salesOnly ? "Total" : "Total (monthly)"}
              </span>
              <span className="font-bold text-slate-900">{plan.priceDisplay}</span>
            </div>

            <p className="text-xs text-slate-400 mt-4 leading-relaxed">
              14-day money-back guarantee. License key delivered instantly after payment.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function CheckoutPage() {
  return (
    <Suspense fallback={<div className="py-20 text-center text-slate-400">Loading…</div>}>
      <CheckoutForm />
    </Suspense>
  );
}
