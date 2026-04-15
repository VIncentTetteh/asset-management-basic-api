/**
 * Server-side Paystack client.
 * All calls happen inside Next.js API routes — the secret key never reaches
 * the browser.
 */

const BASE = "https://api.paystack.co";
const SECRET = process.env.PAYSTACK_SECRET_KEY ?? "";

function headers() {
  if (!SECRET) throw new Error("PAYSTACK_SECRET_KEY is not set");
  return {
    Authorization: `Bearer ${SECRET}`,
    "Content-Type": "application/json",
  };
}

export interface InitTransactionResult {
  authorizationUrl: string;
  accessCode: string;
  reference: string;
}

/** Initialize a one-time Paystack charge and return the hosted-page URL. */
export async function initTransaction(params: {
  email: string;
  amountKobo: number;  // amount in smallest currency unit (kobo / cents)
  reference: string;
  metadata?: Record<string, unknown>;
  callbackUrl: string;
}): Promise<InitTransactionResult> {
  const res = await fetch(`${BASE}/transaction/initialize`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify({
      email: params.email,
      amount: params.amountKobo,
      reference: params.reference,
      callback_url: params.callbackUrl,
      metadata: params.metadata ?? {},
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
  amountKobo: number;
  email: string;
  metadata: Record<string, unknown>;
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
    amountKobo: d.amount,
    email: d.customer.email,
    metadata: d.metadata ?? {},
    paidAt: d.paid_at,
  };
}

/**
 * Validate the HMAC-SHA512 signature on inbound Paystack webhooks.
 * Returns true when the signature is authentic.
 */
export async function validateWebhookSignature(
  rawBody: string,
  signature: string
): Promise<boolean> {
  const { createHmac } = await import("crypto");
  const expected = createHmac("sha512", SECRET)
    .update(rawBody)
    .digest("hex");
  return expected === signature;
}
