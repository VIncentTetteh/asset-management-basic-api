/**
 * Integration tests — /api/checkout
 *
 * Uses jest + node-fetch mocks.  Run with: npm test
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";
import { POST } from "@/app/api/checkout/route";

// ── Mock Paystack ──────────────────────────────────────────────────────────────
vi.mock("@/lib/paystack", () => ({
  initTransaction: vi.fn().mockResolvedValue({
    authorizationUrl: "https://checkout.paystack.com/test-access-code",
    accessCode: "test-access-code",
    reference: "test-ref-abc",
  }),
}));

// ── Mock rate limiter so tests never get throttled ─────────────────────────────
vi.mock("@/lib/rate-limit", () => ({
  checkoutLimiter: { check: () => ({ success: true, remaining: 4, resetAt: Date.now() + 60000 }) },
  verifyLimiter:   { check: () => ({ success: true, remaining: 9, resetAt: Date.now() + 60000 }) },
  lookupLimiter:   { check: () => ({ success: true, remaining: 19, resetAt: Date.now() + 60000 }) },
}));

function makeRequest(body: object): NextRequest {
  return new NextRequest("http://localhost/api/checkout", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

describe("POST /api/checkout", () => {
  it("returns 400 when required fields are missing", async () => {
    const req = makeRequest({ email: "test@example.com" }); // missing orgName + planId
    const res = await POST(req);
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.error).toBeTruthy();
  });

  it("returns 400 for an unknown plan ID", async () => {
    const req = makeRequest({ email: "test@example.com", orgName: "Acme", planId: "UNKNOWN" });
    const res = await POST(req);
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.error).toMatch(/invalid plan/i);
  });

  it("returns the Paystack authorizationUrl for a valid PROFESSIONAL checkout", async () => {
    const req = makeRequest({ email: "cfo@acme.com", orgName: "Acme Ltd", planId: "PROFESSIONAL" });
    const res = await POST(req);
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.authorizationUrl).toBe("https://checkout.paystack.com/test-access-code");
  });

  it("returns the Paystack authorizationUrl for STARTER plan", async () => {
    const req = makeRequest({ email: "admin@startup.io", orgName: "Startup Inc", planId: "STARTER" });
    const res = await POST(req);
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.authorizationUrl).toContain("paystack");
  });

  it("returns the Paystack authorizationUrl for ENTERPRISE plan", async () => {
    const req = makeRequest({ email: "cto@corp.com", orgName: "Corp", planId: "ENTERPRISE" });
    const res = await POST(req);
    expect(res.status).toBe(200);
  });
});
