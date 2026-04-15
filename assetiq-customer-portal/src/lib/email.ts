/**
 * Email delivery via Resend.
 * Falls back to console logging when RESEND_API_KEY is not set (local dev).
 */

const RESEND_API_KEY = process.env.RESEND_API_KEY ?? "";
const FROM           = process.env.EMAIL_FROM ?? "noreply@assetiq.io";
const APP_URL        = process.env.NEXT_PUBLIC_APP_URL ?? "https://portal.assetiq.io";

interface SendResult { id: string }

async function sendViaResend(params: {
  to: string;
  subject: string;
  html: string;
}): Promise<void> {
  const res = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${RESEND_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ from: FROM, to: params.to, subject: params.subject, html: params.html }),
  });

  if (!res.ok) {
    const body = await res.text();
    throw new Error(`Resend error ${res.status}: ${body}`);
  }
}

/** Send the license key to the customer immediately after payment. */
export async function sendLicenseKeyEmail(params: {
  to: string;
  orgName: string;
  plan: string;
  keyToken: string;
  expiresAt: string;
}): Promise<void> {
  const expiryDisplay = new Date(params.expiresAt).toLocaleDateString("en-US", {
    year: "numeric", month: "long", day: "numeric",
  });

  const html = `
<!DOCTYPE html>
<html lang="en">
<head><meta charset="utf-8"><title>Your AssetIQ License Key</title></head>
<body style="font-family:Inter,system-ui,sans-serif;background:#f8fafc;margin:0;padding:40px 0;">
  <div style="max-width:560px;margin:0 auto;background:#fff;border-radius:12px;
              box-shadow:0 1px 4px rgba(0,0,0,.08);overflow:hidden;">
    <!-- Header -->
    <div style="background:linear-gradient(135deg,#0d9488,#0f766e);padding:32px 40px;">
      <h1 style="color:#fff;margin:0;font-size:22px;font-weight:700;">AssetIQ Standalone</h1>
      <p style="color:#99f6e4;margin:4px 0 0;font-size:14px;">Your license key is ready</p>
    </div>
    <!-- Body -->
    <div style="padding:32px 40px;">
      <p style="color:#334155;margin:0 0 16px;">Hi ${params.orgName},</p>
      <p style="color:#475569;margin:0 0 24px;line-height:1.6;">
        Thank you for purchasing <strong>${params.plan}</strong> plan.
        Copy the key below and paste it into your AssetIQ installation under
        <em>Settings → License</em>.
      </p>

      <!-- Key box -->
      <div style="background:#f1f5f9;border:1px solid #e2e8f0;border-radius:8px;
                  padding:16px;word-break:break-all;margin-bottom:24px;">
        <p style="font-size:10px;color:#64748b;margin:0 0 6px;text-transform:uppercase;
                  letter-spacing:.05em;font-weight:600;">License Key</p>
        <code style="font-family:monospace;font-size:11px;color:#0f172a;line-height:1.5;">
          ${params.keyToken}
        </code>
      </div>

      <table style="width:100%;border-collapse:collapse;margin-bottom:28px;">
        <tr>
          <td style="padding:8px 0;color:#64748b;font-size:13px;border-bottom:1px solid #f1f5f9;">Plan</td>
          <td style="padding:8px 0;color:#0f172a;font-size:13px;font-weight:600;text-align:right;border-bottom:1px solid #f1f5f9;">${params.plan}</td>
        </tr>
        <tr>
          <td style="padding:8px 0;color:#64748b;font-size:13px;">Expires</td>
          <td style="padding:8px 0;color:#0f172a;font-size:13px;font-weight:600;text-align:right;">${expiryDisplay}</td>
        </tr>
      </table>

      <a href="${APP_URL}/dashboard"
         style="display:inline-block;background:#0d9488;color:#fff;text-decoration:none;
                padding:12px 24px;border-radius:8px;font-size:14px;font-weight:600;">
        View your license dashboard →
      </a>

      <p style="color:#94a3b8;font-size:12px;margin:28px 0 0;line-height:1.6;">
        Keep this email safe — you'll need the key if you reinstall AssetIQ.
        Questions? Reply to this email or visit
        <a href="https://docs.assetiq.io" style="color:#0d9488;">docs.assetiq.io</a>.
      </p>
    </div>
  </div>
</body>
</html>`;

  if (!RESEND_API_KEY) {
    console.log("[EMAIL DEV] Would send to:", params.to, "Key:", params.keyToken.slice(0, 40) + "…");
    return;
  }

  await sendViaResend({ to: params.to, subject: "Your AssetIQ License Key", html });
}

/** Notify the customer that their renewal was successful and attach the new key. */
export async function sendRenewalEmail(params: {
  to: string;
  orgName: string;
  plan: string;
  keyToken: string;
  expiresAt: string;
}): Promise<void> {
  const expiryDisplay = new Date(params.expiresAt).toLocaleDateString("en-US", {
    year: "numeric", month: "long", day: "numeric",
  });

  const html = `
<!DOCTYPE html>
<html lang="en">
<head><meta charset="utf-8"><title>AssetIQ License Renewed</title></head>
<body style="font-family:Inter,system-ui,sans-serif;background:#f8fafc;margin:0;padding:40px 0;">
  <div style="max-width:560px;margin:0 auto;background:#fff;border-radius:12px;
              box-shadow:0 1px 4px rgba(0,0,0,.08);overflow:hidden;">
    <div style="background:linear-gradient(135deg,#0d9488,#0f766e);padding:32px 40px;">
      <h1 style="color:#fff;margin:0;font-size:22px;font-weight:700;">License Renewed</h1>
      <p style="color:#99f6e4;margin:4px 0 0;font-size:14px;">AssetIQ Standalone</p>
    </div>
    <div style="padding:32px 40px;">
      <p style="color:#334155;margin:0 0 16px;">Hi ${params.orgName},</p>
      <p style="color:#475569;margin:0 0 24px;line-height:1.6;">
        Your <strong>${params.plan}</strong> license has been renewed. Your new expiry
        date is <strong>${expiryDisplay}</strong>. The updated key is below —
        paste it into <em>Settings → License</em> in your AssetIQ installation.
      </p>
      <div style="background:#f1f5f9;border:1px solid #e2e8f0;border-radius:8px;
                  padding:16px;word-break:break-all;margin-bottom:28px;">
        <p style="font-size:10px;color:#64748b;margin:0 0 6px;text-transform:uppercase;
                  letter-spacing:.05em;font-weight:600;">Updated License Key</p>
        <code style="font-family:monospace;font-size:11px;color:#0f172a;line-height:1.5;">
          ${params.keyToken}
        </code>
      </div>
      <a href="${APP_URL}/dashboard"
         style="display:inline-block;background:#0d9488;color:#fff;text-decoration:none;
                padding:12px 24px;border-radius:8px;font-size:14px;font-weight:600;">
        View dashboard →
      </a>
    </div>
  </div>
</body>
</html>`;

  if (!RESEND_API_KEY) {
    console.log("[EMAIL DEV] Renewal email to:", params.to);
    return;
  }

  await sendViaResend({ to: params.to, subject: "Your AssetIQ License Has Been Renewed", html });
}
