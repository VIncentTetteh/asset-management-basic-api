/**
 * POST /api/checkout
 *
 * Body: { email, orgName, planId }
 * Response: { authorizationUrl }  — redirect the browser here for payment
 *
 * Stores plan + org details in a signed reference so /api/checkout/verify
 * can reconstruct them after payment without a DB.
 */

import { NextRequest, NextResponse } from "next/server";
import { initTransaction } from "@/lib/paystack";
import { PLANS, type PlanId } from "@/lib/plans";
import { randomBytes, createHmac } from "crypto";
import { checkoutLimiter } from "@/lib/rate-limit";

const APP_URL = process.env.NEXT_PUBLIC_APP_URL ?? "http://localhost:3001";
const SESSION_SECRET = process.env.CHECKOUT_SESSION_SECRET ?? "dev-secret";

function buildSignedReference(data: object): string {
  const nonce = randomBytes(8).toString("hex");
  const payload = Buffer.from(JSON.stringify({ ...data, nonce })).toString("base64url");
  const sig = createHmac("sha256", SESSION_SECRET).update(payload).digest("hex").slice(0, 16);
  return `${payload}.${sig}`;
}

export async function POST(req: NextRequest) {
  // Rate limiting — 5 checkout initiations per IP per minute
  const ip = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? req.headers.get("x-real-ip") ?? "unknown";
  const rl = checkoutLimiter.check(ip);
  if (!rl.success) {
    return NextResponse.json({ error: "Too many requests. Please wait before trying again." }, { status: 429 });
  }

  try {
    const body = await req.json();
    const { email, orgName, planId } = body as {
      email: string;
      orgName: string;
      planId: PlanId;
    };

    if (!email || !orgName || !planId) {
      return NextResponse.json({ error: "email, orgName and planId are required" }, { status: 400 });
    }

    const plan = PLANS[planId];
    if (!plan) {
      return NextResponse.json({ error: "Invalid plan" }, { status: 400 });
    }

    // Encode org data into the Paystack reference so /verify can issue the key
    // without hitting a separate session store.
    const reference = buildSignedReference({ email, orgName, planId });

    const result = await initTransaction({
      email,
      amountKobo: plan.priceKobo,
      reference,
      callbackUrl: `${APP_URL}/checkout/verify?ref=${encodeURIComponent(reference)}`,
      metadata: { orgName, planId, plan: plan.name },
    });

    return NextResponse.json({ authorizationUrl: result.authorizationUrl });
  } catch (err: unknown) {
    console.error("[checkout] init error:", err);
    const message = err instanceof Error ? err.message : "Checkout initialization failed";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
