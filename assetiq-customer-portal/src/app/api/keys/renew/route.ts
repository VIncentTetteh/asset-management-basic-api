/**
 * POST /api/keys/renew
 *
 * Body: { email, keyId, planId }
 * Initializes a Paystack payment for key renewal. On success the /verify
 * endpoint calls renewKey() instead of issueKey().
 *
 * We encode intent=renew + keyId into the signed reference so /verify
 * knows to extend an existing key rather than issue a new one.
 */

import { NextRequest, NextResponse } from "next/server";
import { initTransaction } from "@/lib/paystack";
import { PLANS, type PlanId } from "@/lib/plans";
import { randomBytes, createHmac } from "crypto";

const APP_URL = process.env.NEXT_PUBLIC_APP_URL ?? "http://localhost:3001";
const SESSION_SECRET = process.env.CHECKOUT_SESSION_SECRET ?? "dev-secret";

function buildSignedReference(data: object): string {
  const nonce = randomBytes(8).toString("hex");
  const payload = Buffer.from(JSON.stringify({ ...data, nonce })).toString("base64url");
  const sig = createHmac("sha256", SESSION_SECRET).update(payload).digest("hex").slice(0, 16);
  return `${payload}.${sig}`;
}

export async function POST(req: NextRequest) {
  try {
    const { email, orgName, keyId, planId } = await req.json() as {
      email: string;
      orgName: string;
      keyId: string;
      planId: PlanId;
    };

    if (!email || !keyId || !planId) {
      return NextResponse.json({ error: "email, keyId and planId are required" }, { status: 400 });
    }

    const plan = PLANS[planId];
    if (!plan) return NextResponse.json({ error: "Invalid plan" }, { status: 400 });

    const reference = buildSignedReference({ email, orgName, planId, keyId, intent: "renew" });

    const result = await initTransaction({
      email,
      amountKobo: plan.priceKobo,
      reference,
      callbackUrl: `${APP_URL}/checkout/verify?ref=${encodeURIComponent(reference)}`,
      metadata: { orgName, planId, plan: plan.name, keyId, intent: "renew" },
    });

    return NextResponse.json({ authorizationUrl: result.authorizationUrl });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : "Renewal initialization failed";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
