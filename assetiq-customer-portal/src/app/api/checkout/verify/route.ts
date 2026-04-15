/**
 * GET /api/checkout/verify?ref=<reference>
 *
 * Called by the /checkout/verify page after Paystack redirects back.
 *
 * Two intents are supported, both encoded in the signed Paystack reference:
 *
 *   intent=new (default)
 *     1. Verify payment with Paystack
 *     2. Decode org details from signed reference
 *     3. Issue a new license key via the License Server
 *     4. Send key by email
 *     5. Return key details to the browser
 *
 *   intent=renew
 *     1–2. Same as above
 *     3. Extend the existing key (keyId from reference) via renewKey()
 *     4–5. Same as above
 */

import { NextRequest, NextResponse } from "next/server";
import { verifyTransaction } from "@/lib/paystack";
import { issueKey, renewKey, type Plan } from "@/lib/license-server";
import { sendLicenseKeyEmail, sendRenewalEmail } from "@/lib/email";
import { PLANS, type PlanId } from "@/lib/plans";
import { verifyLimiter } from "@/lib/rate-limit";

// ── Reference decoder ─────────────────────────────────────────────────────────

interface DecodedRef {
  email:   string;
  orgName: string;
  planId:  PlanId;
  intent?: "renew";
  keyId?:  string;
}

function decodeReference(reference: string): DecodedRef | null {
  try {
    const [payload] = reference.split(".");
    const json = Buffer.from(payload, "base64url").toString("utf-8");
    return JSON.parse(json) as DecodedRef;
  } catch {
    return null;
  }
}

// ── Handler ───────────────────────────────────────────────────────────────────

export async function GET(req: NextRequest) {
  const ip = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "unknown";
  const rl = verifyLimiter.check(ip);
  if (!rl.success) {
    return NextResponse.json({ error: "Too many requests." }, { status: 429 });
  }

  const ref = req.nextUrl.searchParams.get("ref");
  if (!ref) {
    return NextResponse.json({ error: "ref parameter is required" }, { status: 400 });
  }

  try {
    // 1. Verify payment
    const tx = await verifyTransaction(ref);
    if (tx.status !== "success") {
      return NextResponse.json(
        { error: `Payment was not successful (status: ${tx.status})` },
        { status: 402 },
      );
    }

    // 2. Decode org details
    const decoded = decodeReference(ref);
    if (!decoded) {
      return NextResponse.json({ error: "Invalid checkout reference" }, { status: 400 });
    }

    const { email, orgName, planId, intent, keyId } = decoded;
    const plan = PLANS[planId];
    if (!plan) {
      return NextResponse.json({ error: "Unknown plan in reference" }, { status: 400 });
    }

    const orgId = email.toLowerCase().replace(/[^a-z0-9]/g, "-");

    // ── Renewal path ───────────────────────────────────────────────────────────
    if (intent === "renew" && keyId) {
      const renewed = await renewKey(keyId, plan.durationDays);

      sendRenewalEmail({
        to: email,
        orgName,
        plan: plan.name,
        keyToken: renewed.keyToken,
        expiresAt: renewed.expiresAt,
      }).catch((err) => console.error("[verify/renew] email failed:", err));

      return NextResponse.json({
        success: true,
        renewed: true,
        key: {
          id:        renewed.id,
          token:     renewed.keyToken,
          plan:      plan.name,
          expiresAt: renewed.expiresAt,
        },
        orgName,
        email,
      });
    }

    // ── New key path ───────────────────────────────────────────────────────────
    const issued = await issueKey({
      orgId,
      orgName,
      orgEmail: email,
      plan: planId as Plan,
      durationDays: plan.durationDays,
    });

    sendLicenseKeyEmail({
      to: email,
      orgName,
      plan: plan.name,
      keyToken: issued.keyToken,
      expiresAt: issued.expiresAt,
    }).catch((err) => console.error("[verify/new] email failed:", err));

    return NextResponse.json({
      success: true,
      renewed: false,
      key: {
        id:        issued.id,
        token:     issued.keyToken,
        plan:      plan.name,
        expiresAt: issued.expiresAt,
      },
      orgName,
      email,
    });

  } catch (err: unknown) {
    console.error("[verify] error:", err);
    const message = err instanceof Error ? err.message : "Verification failed";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
