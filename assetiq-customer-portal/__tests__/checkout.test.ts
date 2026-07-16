/**
 * Integration tests — /api/checkout
 *
 * P1-4 / P1-7: Verifies that the new Ghana-first plan IDs flow through the
 * checkout route, that Paystack receives `currency` + `channels`, and that
 * the contact-sales plan short-circuits to a 400.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";
import { POST } from "@/app/api/checkout/route";
import * as paystack from "@/lib/paystack";

// ── Mock Paystack ──────────────────────────────────────────────────────────────
vi.mock("@/lib/paystack", async () => {
  return {
    initTransaction: vi.fn().mockResolvedValue({
      authorizationUrl: "https://checkout.paystack.com/test-access-code",
      accessCode: "test-access-code",
      reference: "test-ref-abc",
    }),
    resolveChannels: vi.fn().mockReturnValue([
      "card",
      "mobile_money",
      "bank",
      "ussd",
    ]),
  };
});

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

beforeEach(() => {
  vi.mocked(paystack.initTransaction).mockClear();
});

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

  it("returns 400 for the custom-quote ENTERPRISE plan", async () => {
    const req = makeRequest({
      email: "cto@corp.com",
      orgName: "Corp",
      planId: "ENTERPRISE",
    });
    const res = await POST(req);
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.error).toMatch(/sales/i);
  });

  it("returns the Paystack authorizationUrl for a valid BUSINESS checkout", async () => {
    const req = makeRequest({
      email: "cfo@kwabenya.com.gh",
      orgName: "Kwabenya Depot Ltd",
      planId: "BUSINESS",
    });
    const res = await POST(req);
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.authorizationUrl).toBe("https://checkout.paystack.com/test-access-code");

    const call = vi.mocked(paystack.initTransaction).mock.calls[0][0];
    expect(call.currency).toBe("GHS");
    // Business is the default highlighted paid tier (GHS 799/mo = 79900 pesewa).
    expect(call.amountMinor).toBe(79900);
    expect(call.channels).toEqual(expect.arrayContaining(["mobile_money", "card"]));
  });

  it("returns the Paystack authorizationUrl for BASIC plan", async () => {
    const req = makeRequest({
      email: "admin@startup.gh",
      orgName: "Accra Startup Inc",
      planId: "BASIC",
    });
    const res = await POST(req);
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.authorizationUrl).toContain("paystack");

    const call = vi.mocked(paystack.initTransaction).mock.calls[0][0];
    expect(call.currency).toBe("GHS");
    expect(call.amountMinor).toBe(9900);
  });

  it("returns the Paystack authorizationUrl for BUSINESS plan", async () => {
    const req = makeRequest({
      email: "ops@corp.com.gh",
      orgName: "Corp Ghana",
      planId: "BUSINESS",
    });
    const res = await POST(req);
    expect(res.status).toBe(200);

    const call = vi.mocked(paystack.initTransaction).mock.calls[0][0];
    expect(call.currency).toBe("GHS");
    expect(call.amountMinor).toBe(79900);
  });
});
