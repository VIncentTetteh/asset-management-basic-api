/**
 * GET /api/keys/lookup?email=<email>
 *
 * Looks up all license keys associated with an email address.
 * Used by the customer dashboard to show key status without an account system.
 */

import { NextRequest, NextResponse } from "next/server";
import { listKeysByOrg } from "@/lib/license-server";
import { lookupLimiter } from "@/lib/rate-limit";

export async function GET(req: NextRequest) {
  const ip = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "unknown";
  const rl = lookupLimiter.check(ip);
  if (!rl.success) {
    return NextResponse.json({ error: "Too many requests." }, { status: 429 });
  }

  const email = req.nextUrl.searchParams.get("email");
  if (!email) {
    return NextResponse.json({ error: "email parameter is required" }, { status: 400 });
  }

  // orgId is derived from email — must match the derivation used in /verify
  const orgId = email.toLowerCase().replace(/[^a-z0-9]/g, "-");

  try {
    const keys = await listKeysByOrg(orgId);
    return NextResponse.json({ keys });
  } catch (err: unknown) {
    console.error("[keys/lookup] error:", err);
    const message = err instanceof Error ? err.message : "Lookup failed";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
