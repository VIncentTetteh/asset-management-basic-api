/**
 * Server-side client for the AssetIQ License Server.
 * Called exclusively from Next.js API routes — the admin key never reaches
 * the browser.
 */

const BASE     = process.env.LICENSE_SERVER_URL  ?? "http://localhost:8090";
const ADMIN_KEY = process.env.LICENSE_ADMIN_API_KEY ?? "";

function adminHeaders() {
  if (!ADMIN_KEY) throw new Error("LICENSE_ADMIN_API_KEY is not set");
  return {
    "Content-Type": "application/json",
    "X-Admin-Key": ADMIN_KEY,
  };
}

export type Plan = "STARTER" | "PROFESSIONAL" | "ENTERPRISE";

export interface IssuedKey {
  id: string;
  keyToken: string;
  plan: Plan;
  orgId: string;
  issuedAt: string;
  expiresAt: string;
}

/** Issue a new license key after a successful payment. */
export async function issueKey(params: {
  orgId: string;
  orgName: string;
  orgEmail: string;
  plan: Plan;
  durationDays?: number;
}): Promise<IssuedKey> {
  const res = await fetch(`${BASE}/v1/admin/keys/issue`, {
    method: "POST",
    headers: adminHeaders(),
    body: JSON.stringify({
      orgId: params.orgId,
      orgName: params.orgName,
      orgEmail: params.orgEmail,
      plan: params.plan,
      durationDays: params.durationDays ?? 365,
    }),
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(`License server error ${res.status}: ${text}`);
  }

  return res.json() as Promise<IssuedKey>;
}

export interface KeySummary {
  id: string;
  plan: Plan;
  orgId: string;
  orgName: string;
  orgEmail: string;
  status: "ACTIVE" | "EXPIRED" | "REVOKED";
  issuedAt: string;
  expiresAt: string;
  abuseFlagged: boolean;
}

/** List all keys for an organisation (matched by email). */
export async function listKeysByOrg(orgId: string): Promise<KeySummary[]> {
  const res = await fetch(`${BASE}/v1/admin/keys/org/${encodeURIComponent(orgId)}`, {
    headers: adminHeaders(),
    cache: "no-store",
  });

  if (!res.ok) throw new Error(`License server error ${res.status}`);
  return res.json() as Promise<KeySummary[]>;
}

export interface RenewedKey {
  id: string;
  keyToken: string;
  expiresAt: string;
  status: string;
}

/** Extend the expiry of an existing key by durationDays days. */
export async function renewKey(keyId: string, durationDays: number): Promise<RenewedKey> {
  const res = await fetch(`${BASE}/v1/admin/keys/${encodeURIComponent(keyId)}/renew`, {
    method: "POST",
    headers: adminHeaders(),
    body: JSON.stringify({ durationDays }),
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(`License server error ${res.status}: ${text}`);
  }

  return res.json() as Promise<RenewedKey>;
}
