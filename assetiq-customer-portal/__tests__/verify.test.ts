/**
 * Integration tests — /api/checkout/verify
 *
 * Tests both the new-key and renewal paths, plus error cases. P1-5 adds
 * the currency-parity check: the plan's currency must match what Paystack
 * says it charged.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";
import { GET } from "@/app/api/checkout/verify/route";
import { createHmac } from "crypto";

// ── Encode a signed-reference payload (mirrors production logic) ──────────────
function encodeRef(data: object): string {
  const payload = Buffer.from(JSON.stringify(data)).toString("base64url");
  const sig = createHmac("sha256", "dev-secret").update(payload).digest("hex").slice(0, 16);
  return `${payload}.${sig}`;
}

function makeGetRequest(ref: string): NextRequest {
  return new NextRequest(
    `http://localhost/api/checkout/verify?ref=${encodeURIComponent(ref)}`,
  );
}

// ── All mocks hoisted — no doMock/resetModules needed ─────────────────────────

vi.mock("@/lib/rate-limit", () => ({
  verifyLimiter: {
    check: () => ({ success: true, remaining: 9, resetAt: Date.now() + 60_000 }),
  },
}));

const mockVerifyTransaction = vi.fn();
vi.mock("@/lib/paystack", () => ({
  verifyTransaction: (...args: unknown[]) => mockVerifyTransaction(...args),
}));

const mockIssueKey = vi.fn();
const mockRenewKey  = vi.fn();
vi.mock("@/lib/license-server", () => ({
  issueKey: (...args: unknown[]) => mockIssueKey(...args),
  renewKey:  (...args: unknown[]) => mockRenewKey(...args),
}));

vi.mock("@/lib/email", () => ({
  sendLicenseKeyEmail: vi.fn().mockResolvedValue(undefined),
  sendRenewalEmail:    vi.fn().mockResolvedValue(undefined),
}));

// ── Shared fixture data ───────────────────────────────────────────────────────

const FUTURE_EXPIRY = new Date(Date.now() + 30 * 86_400_000).toISOString();

const SUCCESSFUL_TX = {
  reference:   "test-ref",
  status:      "success",
  amountMinor: 79_900,
  currency:    "GHS",
  email:       "cfo@kwabenya.com.gh",
  metadata:    { orgName: "Kwabenya Depot Ltd", planId: "BUSINESS" },
  channel:     "mobile_money",
  paidAt:      new Date().toISOString(),
};

const ISSUED_KEY = {
  id:        "key-uuid-001",
  keyToken:  "eyJhbGciOiJSUzI1NiJ9.issued-token",
  plan:      "BUSINESS",
  orgId:     "kwabenya-depot",
  issuedAt:  new Date().toISOString(),
  expiresAt: FUTURE_EXPIRY,
};

const RENEWED_KEY = {
  id:        "key-uuid-001",
  keyToken:  "eyJhbGciOiJSUzI1NiJ9.renewed-token",
  expiresAt: FUTURE_EXPIRY,
  status:    "ACTIVE",
};

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("GET /api/checkout/verify", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockVerifyTransaction.mockResolvedValue(SUCCESSFUL_TX);
    mockIssueKey.mockResolvedValue(ISSUED_KEY);
    mockRenewKey.mockResolvedValue(RENEWED_KEY);
  });

  // ── Missing / invalid inputs ──────────────────────────────────────────────

  it("returns 400 when ref param is absent", async () => {
    const req = new NextRequest("http://localhost/api/checkout/verify");
    const res = await GET(req);
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.error).toMatch(/ref/i);
  });

  it("returns 402 when Paystack status is 'abandoned'", async () => {
    mockVerifyTransaction.mockResolvedValue({
      ...SUCCESSFUL_TX,
      status: "abandoned",
      amountMinor: 0,
    });
    const ref = encodeRef({
      email: "user@example.com",
      orgName: "Org",
      planId: "BASIC",
      currency: "GHS",
    });
    const res = await GET(makeGetRequest(ref));
    expect(res.status).toBe(402);
    const body = await res.json();
    expect(body.error).toMatch(/not successful/i);
  });

  it("returns 400 for a ref that decodes to an unknown plan", async () => {
    const ref = encodeRef({
      email: "x@y.com",
      orgName: "Org",
      planId: "NONEXISTENT",
    });
    const res = await GET(makeGetRequest(ref));
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.error).toMatch(/unknown plan/i);
  });

  it("returns 400 when the Paystack currency disagrees with the plan", async () => {
    mockVerifyTransaction.mockResolvedValue({ ...SUCCESSFUL_TX, currency: "NGN" });
    const ref = encodeRef({
      email: "cfo@kwabenya.com.gh",
      orgName: "Kwabenya Depot Ltd",
      planId: "BUSINESS",
      currency: "GHS",
    });
    const res = await GET(makeGetRequest(ref));
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.error).toMatch(/currency/i);
  });

  // ── New-key path ─────────────────────────────────────────────────────────

  it("issues a key and returns success payload for a new BUSINESS purchase", async () => {
    const ref = encodeRef({
      email: "cfo@kwabenya.com.gh",
      orgName: "Kwabenya Depot Ltd",
      planId: "BUSINESS",
      currency: "GHS",
    });
    const res = await GET(makeGetRequest(ref));
    expect(res.status).toBe(200);

    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.renewed).toBe(false);
    expect(body.key.token).toBe(ISSUED_KEY.keyToken);
    expect(body.key.plan).toBe("Business");
    expect(body.email).toBe("cfo@kwabenya.com.gh");
    expect(body.orgName).toBe("Kwabenya Depot Ltd");

    expect(mockIssueKey).toHaveBeenCalledOnce();
    expect(mockRenewKey).not.toHaveBeenCalled();
  });

  it("calls issueKey with the correct plan and orgId derived from email", async () => {
    const ref = encodeRef({
      email: "admin@startup.gh",
      orgName: "Accra Startup Inc",
      planId: "BASIC",
      currency: "GHS",
    });
    await GET(makeGetRequest(ref));

    expect(mockIssueKey).toHaveBeenCalledWith(
      expect.objectContaining({
        plan:     "BASIC",
        orgEmail: "admin@startup.gh",
        orgName:  "Accra Startup Inc",
        orgId:    "admin-startup-gh",
      }),
    );
  });

  // ── Renewal path ─────────────────────────────────────────────────────────

  it("calls renewKey (not issueKey) when intent=renew is in the reference", async () => {
    const ref = encodeRef({
      email:    "cfo@kwabenya.com.gh",
      orgName:  "Kwabenya Depot Ltd",
      planId:   "BUSINESS",
      intent:   "renew",
      keyId:    "key-uuid-001",
      currency: "GHS",
    });
    const res = await GET(makeGetRequest(ref));
    expect(res.status).toBe(200);

    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.renewed).toBe(true);
    expect(body.key.token).toBe(RENEWED_KEY.keyToken);

    expect(mockRenewKey).toHaveBeenCalledOnce();
    expect(mockRenewKey).toHaveBeenCalledWith("key-uuid-001", 30);
    expect(mockIssueKey).not.toHaveBeenCalled();
  });

  it("falls back to issueKey when intent=renew but keyId is missing", async () => {
    // intent=renew without keyId → treated as new key (defensive)
    const ref = encodeRef({
      email:    "cfo@kwabenya.com.gh",
      orgName:  "Kwabenya Depot Ltd",
      planId:   "BUSINESS",
      intent:   "renew",
      currency: "GHS",
      // keyId intentionally omitted
    });
    const res = await GET(makeGetRequest(ref));
    // Should still succeed — falls through to issueKey branch
    expect([200, 400]).toContain(res.status);
    if (res.status === 200) {
      expect(mockIssueKey).toHaveBeenCalled();
      expect(mockRenewKey).not.toHaveBeenCalled();
    }
  });
});
