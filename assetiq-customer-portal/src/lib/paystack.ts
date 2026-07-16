/**
 * Server-side Paystack client.
 * All calls happen inside Next.js API routes — the secret key never reaches
 * the browser.
 *
 * P1-5 / P1-7: The portal now runs on GHS by default and forwards the
 * configured payment channel list to Paystack so MTN MoMo, Telecel Cash,
 * and AirtelTigo Money appear on the hosted checkout.
 */

const BASE = "https://api.paystack.co";
const SECRET = process.env.PAYSTACK_SECRET_KEY ?? "";

/**
 * Comma-separated Paystack channel list. Mirrors the backend
 * {@code app.billing.paystack.channels} property. Defaults to the full
 * Ghana set so MoMo, card, bank, and USSD all light up out of the box.
 */
const DEFAULT_CHANNELS = "card,mobile_money,bank,ussd";

function headers() {
  if (!SECRET) throw new Error("PAYSTACK_SECRET_KEY is not set");
  return {
    Authorization: `Bearer ${SECRET}`,
    "Content-Type": "application/json",
  };
}

/** Lower-cased, de-duped channel list taken from env + allowlist. */
export function resolveChannels(raw?: string): string[] {
  const csv = raw ?? process.env.PAYSTACK_CHANNELS ?? DEFAULT_CHANNELS;
  return Array.from(
    new Set(
      csv
        .split(",")
        .map((c) => c.trim().toLowerCase())
        .filter(Boolean),
    ),
  );
}

export interface InitTransactionResult {
  authorizationUrl: string;
  accessCode: string;
  reference: string;
}

/** Initialize a one-time Paystack charge and return the hosted-page URL. */
export async function initTransaction(params: {
  email: string;
  /** Amount in the smallest currency unit (pesewa for GHS, kobo for NGN). */
  amountMinor: number;
  /** ISO-4217 alpha-3 currency code (e.g. "GHS"). */
  currency: string;
  reference: string;
  metadata?: Record<string, unknown>;
  callbackUrl: string;
  /** Optional override; falls back to {@link resolveChannels}(). */
  channels?: string[];
}): Promise<InitTransactionResult> {
  const channels = params.channels ?? resolveChannels();

  const res = await fetch(`${BASE}/transaction/initialize`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify({
      email: params.email,
      amount: params.amountMinor,
      currency: params.currency,
      reference: params.reference,
      callback_url: params.callbackUrl,
      metadata: params.metadata ?? {},
      ...(channels.length > 0 ? { channels } : {}),
    }),
  });

  const body = await res.json();
  if (!body.status) throw new Error(body.message ?? "Paystack init failed");

  return {
    authorizationUrl: body.data.authorization_url,
    accessCode: body.data.access_code,
    reference: body.data.reference,
  };
}

export interface VerifiedTransaction {
  reference: string;
  status: "success" | "failed" | "abandoned" | string;
  /** Amount in minor units (pesewa / kobo / cents). */
  amountMinor: number;
  /** ISO-4217 currency reported by Paystack on the verified transaction. */
  currency: string;
  email: string;
  metadata: Record<string, unknown>;
  /** Channel the customer actually used (e.g. "mobile_money"). */
  channel?: string;
  paidAt: string;
}

/** Verify a Paystack transaction by reference after the customer returns. */
export async function verifyTransaction(reference: string): Promise<VerifiedTransaction> {
  const res = await fetch(`${BASE}/transaction/verify/${encodeURIComponent(reference)}`, {
    headers: headers(),
    cache: "no-store",
  });

  const body = await res.json();
  if (!body.status) throw new Error(body.message ?? "Paystack verify failed");

  const d = body.data;
  return {
    reference: d.reference,
    status: d.status,
    amountMinor: d.amount,
    currency: d.currency ?? "GHS",
    email: d.customer?.email ?? "",
    metadata: d.metadata ?? {},
    channel: d.channel,
    paidAt: d.paid_at,
  };
}

/**
 * Validate the HMAC-SHA512 signature on inbound Paystack webhooks.
 * Returns true when the signature is authentic.
 */
export async function validateWebhookSignature(
  rawBody: string,
  signature: string,
): Promise<boolean> {
  const { createHmac } = await import("crypto");
  const expected = createHmac("sha512", SECRET).update(rawBody).digest("hex");
  return expected === signature;
}
