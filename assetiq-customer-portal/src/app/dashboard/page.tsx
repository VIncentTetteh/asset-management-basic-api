"use client";

import { useState } from "react";
import { Search, Loader2, Key, RefreshCw, AlertCircle, CheckCircle, Clock, Ban } from "lucide-react";

interface KeySummary {
  id: string;
  plan: string;
  status: "ACTIVE" | "EXPIRED" | "REVOKED";
  issuedAt: string;
  expiresAt: string;
  orgName: string;
}

const STATUS_CONFIG = {
  ACTIVE:  { label: "Active",  icon: CheckCircle, color: "text-emerald-500", bg: "bg-emerald-50", border: "border-emerald-200" },
  EXPIRED: { label: "Expired", icon: Clock,        color: "text-amber-500",   bg: "bg-amber-50",   border: "border-amber-200"  },
  REVOKED: { label: "Revoked", icon: Ban,           color: "text-red-500",    bg: "bg-red-50",     border: "border-red-200"    },
};

export default function DashboardPage() {
  const [email, setEmail]     = useState("");
  const [keys, setKeys]       = useState<KeySummary[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState<string | null>(null);
  const [renewingId, setRenewingId] = useState<string | null>(null);

  const handleLookup = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email) return;
    setError(null);
    setLoading(true);
    setKeys(null);

    try {
      const res = await fetch(`/api/keys/lookup?email=${encodeURIComponent(email)}`);
      const data = await res.json();
      if (!res.ok) throw new Error(data.error ?? "Lookup failed");
      setKeys(data.keys ?? []);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Something went wrong");
    } finally {
      setLoading(false);
    }
  };

  const handleRenew = async (key: KeySummary) => {
    setRenewingId(key.id);
    try {
      const res = await fetch("/api/keys/renew", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email,
          orgName: key.orgName,
          keyId: key.id,
          planId: key.plan.toUpperCase(),
        }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error ?? "Renewal init failed");
      window.location.href = data.authorizationUrl;
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Renewal failed");
    } finally {
      setRenewingId(null);
    }
  };

  const formatDate = (iso: string) =>
    new Date(iso).toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric" });

  const daysRemaining = (iso: string) => {
    const diff = new Date(iso).getTime() - Date.now();
    return Math.ceil(diff / (1000 * 60 * 60 * 24));
  };

  return (
    <div className="mx-auto max-w-3xl px-6 py-12">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-slate-900">My licenses</h1>
        <p className="text-sm text-slate-500 mt-1">
          Enter the email you used at checkout to retrieve your license keys.
        </p>
      </div>

      {/* Lookup form */}
      <form onSubmit={handleLookup} className="flex gap-3 mb-8">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="admin@acme.com"
            className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-200 text-sm
                       focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20"
          />
        </div>
        <button
          type="submit"
          disabled={loading}
          className="flex items-center gap-2 rounded-xl bg-brand-600 px-5 py-2.5 text-sm
                     font-semibold text-white hover:bg-brand-700 disabled:opacity-60 transition-colors"
        >
          {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}
          Look up
        </button>
      </form>

      {error && (
        <div className="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 mb-6">
          <AlertCircle className="h-4 w-4 text-red-500 flex-shrink-0" />
          <p className="text-sm text-red-700">{error}</p>
        </div>
      )}

      {/* Results */}
      {keys !== null && (
        keys.length === 0 ? (
          <div className="text-center py-16 border border-dashed border-slate-200 rounded-2xl">
            <Key className="h-10 w-10 text-slate-300 mx-auto mb-3" />
            <p className="text-slate-500 text-sm">No license keys found for this email.</p>
            <a href="/" className="text-brand-600 text-sm hover:underline mt-1 inline-block">
              Purchase a plan →
            </a>
          </div>
        ) : (
          <div className="space-y-4">
            {keys.map((key) => {
              const sc = STATUS_CONFIG[key.status];
              const StatusIcon = sc.icon;
              const days = daysRemaining(key.expiresAt);
              const canRenew = key.status !== "REVOKED";

              return (
                <div
                  key={key.id}
                  className={`rounded-2xl border bg-white p-5 ${sc.border}`}
                >
                  <div className="flex items-start justify-between gap-4 mb-3">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-slate-900 capitalize">{key.plan}</span>
                        <span className={`inline-flex items-center gap-1 text-xs font-semibold
                                         rounded-full px-2 py-0.5 ${sc.bg} ${sc.color}`}>
                          <StatusIcon className="h-3 w-3" />
                          {sc.label}
                        </span>
                      </div>
                      <p className="text-xs text-slate-400 mt-0.5 font-mono">{key.id}</p>
                    </div>
                    {canRenew && (
                      <button
                        onClick={() => handleRenew(key)}
                        disabled={renewingId === key.id}
                        className="flex items-center gap-1.5 rounded-lg border border-brand-200
                                   bg-brand-50 px-3 py-1.5 text-xs font-semibold text-brand-700
                                   hover:bg-brand-100 disabled:opacity-60 transition-colors"
                      >
                        {renewingId === key.id ? (
                          <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        ) : (
                          <RefreshCw className="h-3.5 w-3.5" />
                        )}
                        Renew
                      </button>
                    )}
                  </div>

                  <div className="grid grid-cols-3 gap-3 text-xs">
                    <div>
                      <p className="text-slate-400 mb-0.5">Issued</p>
                      <p className="font-medium text-slate-700">{formatDate(key.issuedAt)}</p>
                    </div>
                    <div>
                      <p className="text-slate-400 mb-0.5">Expires</p>
                      <p className="font-medium text-slate-700">{formatDate(key.expiresAt)}</p>
                    </div>
                    <div>
                      <p className="text-slate-400 mb-0.5">Days left</p>
                      <p className={`font-bold ${
                        days > 30 ? "text-emerald-600" : days > 0 ? "text-amber-600" : "text-red-600"
                      }`}>
                        {days > 0 ? days : "Expired"}
                      </p>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )
      )}
    </div>
  );
}
