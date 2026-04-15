/**
 * POST /api/webhooks/paystack
 *
 * Receives Paystack webhook events. Paystack delivers webhooks for every event
 * (charge.success, subscription.not_renew, etc.).
 *
 * We handle:
 *   charge.success — idempotent re-issue guard (key may already exist from /verify)
 *
 * All other events are acknowledged with 200 and ignored.
 */

import { NextRequest, NextResponse } from "next/server";
import { validateWebhookSignature, verifyTransaction } from "@/lib/paystack";
import { issueKey, type Plan } from "@/lib/license-server";
import { sendLicenseKeyEmail } from "@/lib/email";
import { PLANS, type PlanId } from "@/lib/plans";

export async function POST(req: NextRequest) {
  const signature = req.headers.get("x-paystack-signature");
  if (!signature) {
    return NextResponse.json({ error: "Missing signature" }, { status: 401 });
  }

  const rawBody = await req.text();

  const isValid = await validateWebhookSignature(rawBody, signature);
  if (!isValid) {
    return NextResponse.json({ error: "Invalid signature" }, { status: 401 });
  }

  let event: { event: string; data: Record<string, unknown> };
  try {
    event = JSON.parse(rawBody);
  } catch {
    return NextResponse.json({ error: "Invalid JSON" }, { status: 400 });
  }

  if (event.event === "charge.success") {
    const reference = event.data.reference as string;

    try {
      const tx = await verifyTransaction(reference);
      if (tx.status !== "success") {
        // Not actually paid — ignore
        return NextResponse.json({ received: true });
      }

      const meta = tx.metadata as Record<string, string>;
      const planId = meta.planId as PlanId;
      const orgName = meta.orgName ?? "Unknown Organisation";
      const plan = PLANS[planId];

      if (plan) {
        const orgId = tx.email.toLowerCase().replace(/[^a-z0-9]/g, "-");

        // Issue key — the license server's unique-key-per-org logic prevents
        // duplicate issuance for the same reference.
        const issued = await issueKey({
          orgId,
          orgName,
          orgEmail: tx.email,
          plan: planId as Plan,
          durationDays: plan.durationDays,
        }).catch((err) => {
          // Key may already exist from /verify — log and continue
          console.warn("[webhook] issueKey skipped:", err.message);
          return null;
        });

        if (issued) {
          await sendLicenseKeyEmail({
            to: tx.email,
            orgName,
            plan: plan.name,
            keyToken: issued.keyToken,
            expiresAt: issued.expiresAt,
          }).catch((err) => console.error("[webhook] email failed:", err));
        }
      }
    } catch (err) {
      console.error("[webhook] charge.success handling error:", err);
      // Return 200 so Paystack doesn't retry indefinitely
    }
  }

  return NextResponse.json({ received: true });
}
