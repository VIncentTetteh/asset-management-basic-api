"use client";

import { useEffect, useState, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import {
  CheckCircle, Copy, Check, Loader2, AlertCircle, ExternalLink, RefreshCw,
} from "lucide-react";

interface KeyResult {
  id:        string;
  token:     string;
  plan:      string;
  expiresAt: string;
}

interface VerifyResponse {
  success:  boolean;
  renewed:  boolean;
  key:      KeyResult;
  orgName:  string;
  email:    string;
}

function VerifyContent() {
  const searchParams = useSearchParams();
  const ref = searchParams.get("ref");

  const [phase,  setPhase]  = useState<"loading" | "success" | "error">("loading");
  const [data,   setData]   = useState<VerifyResponse | null>(null);
  const [error,  setError]  = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!ref) { setError("Missing payment reference."); setPhase("error"); return; }

    fetch(`/api/checkout/verify?ref=${encodeURIComponent(ref)}`)
      .then(async (res) => {
        const body = await res.json();
        if (!res.ok) throw new Error(body.error ?? "Verification failed");
        setData(body as VerifyResponse);
        setPhase("success");
      })
      .catch((err: Error) => { setError(err.message); setPhase("error"); });
  }, [ref]);

  const handleCopy = () => {
    if (!data?.key.token) return;
    navigator.clipboard.writeText(data.key.token).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2500);
    });
  };

  // ── Loading ────────────────────────────────────────────────────────────────
  if (phase === "loading") {
    return (
      <div className="flex flex-col items-center justify-center py-32 gap-4">
        <Loader2 className="h-10 w-10 animate-spin text-brand-500" />
        <p className="text-slate-500 text-sm">
          Verifying payment and issuing your license key…
        </p>
      </div>
    );
  }

  // ── Error ──────────────────────────────────────────────────────────────────
  if (phase === "error") {
    return (
      <div className="mx-auto max-w-lg px-6 py-20 text-center">
        <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-red-50">
          <AlertCircle className="h-8 w-8 text-red-500" />
        </div>
        <h1 className="text-xl font-bold text-slate-900 mb-2">Verification failed</h1>
        <p className="text-slate-500 text-sm mb-6">{error}</p>
        <p className="text-xs text-slate-400">
          If you were charged, email{" "}
          <a href="mailto:support@assetiq.io" className="text-brand-600 underline">
            support@assetiq.io
          </a>{" "}
          with your payment reference:{" "}
          <code className="font-mono bg-slate-100 px-1 rounded break-all">{ref}</code>
        </p>
      </div>
    );
  }

  // ── Success ────────────────────────────────────────────────────────────────
  const { key, orgName, email, renewed } = data!;
  const expiryDisplay = new Date(key.expiresAt).toLocaleDateString("en-US", {
    year: "numeric", month: "long", day: "numeric",
  });

  return (
    <div className="mx-auto max-w-xl px-6 py-12">

      {/* Header */}
      <div className="text-center mb-8">
        <div className={`mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full
                         ${renewed ? "bg-blue-50" : "bg-emerald-50"}`}>
          {renewed
            ? <RefreshCw className="h-8 w-8 text-blue-500" />
            : <CheckCircle className="h-8 w-8 text-emerald-500" />}
        </div>
        <h1 className="text-2xl font-bold text-slate-900">
          {renewed ? "License renewed!" : "Payment successful!"}
        </h1>
        <p className="text-slate-500 text-sm mt-2">
          Your <strong>{key.plan}</strong> license key has been{" "}
          {renewed ? "updated" : "issued"} and emailed to <strong>{email}</strong>.
        </p>
      </div>

      {/* Key card */}
      <div className="rounded-2xl border border-brand-200 bg-brand-50 p-6 mb-6">
        <div className="flex items-center justify-between mb-3">
          <p className="text-xs font-bold uppercase tracking-wider text-brand-700">
            {renewed ? "Updated" : ""} License Key — {key.plan}
          </p>
          <button
            onClick={handleCopy}
            className="flex items-center gap-1.5 text-xs font-semibold text-brand-600
                       hover:text-brand-800 transition-colors"
          >
            {copied
              ? <><Check className="h-3.5 w-3.5" /> Copied!</>
              : <><Copy className="h-3.5 w-3.5" /> Copy</>}
          </button>
        </div>
        <code
          onClick={handleCopy}
          className="block font-mono text-[11px] text-slate-700 break-all leading-relaxed
                     cursor-pointer select-all"
        >
          {key.token}
        </code>
      </div>

      {/* Details row */}
      <div className="rounded-xl border border-slate-100 bg-white p-4 space-y-2 mb-6 text-sm">
        {[
          { label: "Organisation", value: orgName },
          { label: "Plan",         value: key.plan },
          { label: "Expires",      value: expiryDisplay },
        ].map(({ label, value }) => (
          <div key={label} className="flex justify-between">
            <span className="text-slate-500">{label}</span>
            <span className="font-semibold text-slate-800">{value}</span>
          </div>
        ))}
      </div>

      {/* Next steps — only shown for new keys */}
      {!renewed && (
        <div className="rounded-xl bg-slate-50 border border-slate-100 p-5 mb-6">
          <p className="text-xs font-semibold text-slate-600 uppercase tracking-wider mb-3">
            Next steps
          </p>
          {[
            "Copy the license key above.",
            "Open the AssetIQ web UI → Settings → License.",
            "Paste the key and click Activate.",
            "The setup wizard will guide you through the rest.",
          ].map((step, i) => (
            <div key={i} className="flex items-start gap-2.5 mb-2">
              <span className="flex h-5 w-5 flex-shrink-0 items-center justify-center
                               rounded-full bg-brand-100 text-[10px] font-bold text-brand-700">
                {i + 1}
              </span>
              <p className="text-sm text-slate-600">{step}</p>
            </div>
          ))}
        </div>
      )}

      {/* Renewal note */}
      {renewed && (
        <div className="rounded-xl bg-blue-50 border border-blue-100 p-4 mb-6 text-sm text-blue-700">
          Paste this updated key into <strong>Settings → License</strong> on your AssetIQ
          installation to restore full access.
        </div>
      )}

      {/* CTAs */}
      <div className="flex flex-col sm:flex-row gap-3">
        <a href="/dashboard"
           className="flex-1 flex items-center justify-center gap-2 rounded-xl bg-brand-600
                      py-2.5 text-sm font-semibold text-white hover:bg-brand-700 transition-colors">
          View my licenses
        </a>
        <a href="https://docs.assetiq.io/standalone/getting-started"
           target="_blank" rel="noopener noreferrer"
           className="flex-1 flex items-center justify-center gap-2 rounded-xl border
                      border-slate-200 bg-white py-2.5 text-sm font-semibold text-slate-700
                      hover:bg-slate-50 transition-colors">
          Installation guide <ExternalLink className="h-3.5 w-3.5" />
        </a>
      </div>
    </div>
  );
}

export default function VerifyPage() {
  return (
    <Suspense fallback={<div className="py-20 text-center text-slate-400">Loading…</div>}>
      <VerifyContent />
    </Suspense>
  );
}
