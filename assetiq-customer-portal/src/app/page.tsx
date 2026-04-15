"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Check, Shield, Server, Zap, ArrowRight } from "lucide-react";
import { PLAN_LIST, type PlanId } from "@/lib/plans";

export default function PricingPage() {
  const router = useRouter();
  const [loading, setLoading] = useState<PlanId | null>(null);

  const handleSelect = (planId: PlanId) => {
    setLoading(planId);
    router.push(`/checkout?plan=${planId}`);
  };

  return (
    <div>
      {/* Hero */}
      <section className="bg-gradient-to-b from-brand-600 to-brand-700 text-white py-20 px-6">
        <div className="mx-auto max-w-3xl text-center">
          <div className="inline-flex items-center gap-2 bg-white/10 rounded-full px-4 py-1.5 text-sm font-medium mb-6">
            <Server className="h-3.5 w-3.5" />
            Self-hosted · Your data stays on your servers
          </div>
          <h1 className="text-4xl font-bold tracking-tight sm:text-5xl mb-4">
            AssetIQ Standalone
          </h1>
          <p className="text-lg text-brand-100 max-w-xl mx-auto">
            Deploy AssetIQ on your own infrastructure. One annual payment,
            full control, zero per-user pricing.
          </p>
        </div>
      </section>

      {/* Value props */}
      <section className="py-12 border-b border-slate-100 bg-white">
        <div className="mx-auto max-w-5xl px-6 grid grid-cols-1 sm:grid-cols-3 gap-8 text-center">
          {[
            { icon: <Shield className="h-6 w-6 text-brand-600" />, title: "Data sovereignty", body: "All data lives on your servers. Nothing leaves your network." },
            { icon: <Server className="h-6 w-6 text-brand-600" />, title: "Docker Compose deploy", body: "Single command brings up the entire stack — API, frontend, database." },
            { icon: <Zap className="h-6 w-6 text-brand-600" />, title: "30-day grace period", body: "Expired keys enter read-only mode — no hard lockout for Enterprise." },
          ].map(({ icon, title, body }) => (
            <div key={title} className="flex flex-col items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-brand-50">
                {icon}
              </div>
              <h3 className="font-semibold text-slate-900">{title}</h3>
              <p className="text-sm text-slate-500 leading-relaxed">{body}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Pricing cards */}
      <section className="py-16 px-6">
        <div className="mx-auto max-w-5xl">
          <h2 className="text-2xl font-bold text-center text-slate-900 mb-2">
            Annual plans
          </h2>
          <p className="text-center text-slate-500 text-sm mb-10">
            All plans include a 7-day free trial period. Renew any time before expiry.
          </p>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {PLAN_LIST.map((plan) => (
              <div
                key={plan.id}
                className={`relative rounded-2xl border bg-white p-6 flex flex-col ${
                  plan.highlighted
                    ? "border-brand-500 shadow-lg shadow-brand-500/10 ring-1 ring-brand-500"
                    : "border-slate-200"
                }`}
              >
                {plan.highlighted && (
                  <div className="absolute -top-3 left-1/2 -translate-x-1/2">
                    <span className="bg-brand-600 text-white text-xs font-bold px-3 py-1 rounded-full">
                      Most popular
                    </span>
                  </div>
                )}

                <div className="mb-6">
                  <h3 className="text-lg font-bold text-slate-900">{plan.name}</h3>
                  <p className="text-sm text-slate-500 mt-1">{plan.tagline}</p>
                  <div className="mt-4">
                    <span className="text-3xl font-bold text-slate-900">{plan.priceDisplay}</span>
                    <span className="text-sm text-slate-500 ml-1">/ year</span>
                  </div>
                </div>

                {/* Limits */}
                <div className="mb-5 rounded-xl bg-slate-50 p-3 space-y-1.5">
                  {[
                    { label: "Assets", value: plan.limits.assets },
                    { label: "Users", value: plan.limits.users },
                    { label: "Departments", value: plan.limits.departments },
                  ].map(({ label, value }) => (
                    <div key={label} className="flex justify-between text-sm">
                      <span className="text-slate-500">{label}</span>
                      <span className="font-semibold text-slate-800">
                        {value === "Unlimited" ? "Unlimited" : value.toLocaleString()}
                      </span>
                    </div>
                  ))}
                </div>

                {/* Features */}
                <ul className="space-y-2 flex-1 mb-6">
                  {plan.features.map((f) => (
                    <li key={f} className="flex items-start gap-2 text-sm text-slate-600">
                      <Check className="h-4 w-4 text-brand-500 flex-shrink-0 mt-0.5" />
                      {f}
                    </li>
                  ))}
                </ul>

                <button
                  onClick={() => handleSelect(plan.id)}
                  disabled={loading === plan.id}
                  className={`w-full rounded-xl py-2.5 text-sm font-semibold transition-all flex items-center justify-center gap-1.5 ${
                    plan.highlighted
                      ? "bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-60"
                      : "bg-slate-100 text-slate-900 hover:bg-slate-200 disabled:opacity-60"
                  }`}
                >
                  {loading === plan.id ? "Redirecting…" : (
                    <>Get started <ArrowRight className="h-4 w-4" /></>
                  )}
                </button>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* FAQ */}
      <section className="py-12 px-6 border-t border-slate-100 bg-white">
        <div className="mx-auto max-w-2xl">
          <h2 className="text-xl font-bold text-slate-900 mb-8 text-center">
            Frequently asked questions
          </h2>
          <div className="space-y-6">
            {[
              { q: "How does the license key work?", a: "After payment you receive a signed JWT license key. Paste it into Settings → License in your AssetIQ installation. The app verifies it locally using RSA-2048 — no internet required day-to-day." },
              { q: "What happens when the license expires?", a: "Your installation enters read-only mode — all data is visible and exportable, but creating or editing records is paused. Renew any time to restore full access." },
              { q: "Can I run multiple servers with one key?", a: "Each key is bound to one server fingerprint. Enterprise customers can request multi-site licensing by emailing support." },
              { q: "Do you offer a refund?", a: "We offer a 14-day money-back guarantee if AssetIQ Standalone doesn't meet your needs. Contact support@assetiq.io." },
            ].map(({ q, a }) => (
              <div key={q} className="border-b border-slate-100 pb-6">
                <h3 className="font-semibold text-slate-800 mb-2">{q}</h3>
                <p className="text-sm text-slate-500 leading-relaxed">{a}</p>
              </div>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
