// P0-6: Customer portal — browser-side Sentry bootstrap.
//
// This file is automatically imported by @sentry/nextjs at client runtime.
// It is safe to ship to every customer: Sentry is completely inert unless
// NEXT_PUBLIC_SENTRY_DSN is defined in the deployment environment.
import * as Sentry from "@sentry/nextjs";

const dsn = process.env.NEXT_PUBLIC_SENTRY_DSN;

if (dsn) {
  Sentry.init({
    dsn,
    environment: process.env.NEXT_PUBLIC_APP_ENV ?? "development",
    release: process.env.NEXT_PUBLIC_APP_VERSION,

    // Keep sampling low by default — marketing sites generate a lot of traffic.
    // Override with NEXT_PUBLIC_SENTRY_TRACES_SAMPLE_RATE in production.
    tracesSampleRate: Number(
      process.env.NEXT_PUBLIC_SENTRY_TRACES_SAMPLE_RATE ?? "0.1"
    ),

    // We do not record session replays on the public site — the license
    // purchase flow is the only sensitive surface and it already lives behind
    // Stripe/Paystack hosted fields.
    replaysSessionSampleRate: 0,
    replaysOnErrorSampleRate: 0,

    // Privacy: never send PII unless operators explicitly opt in.
    sendDefaultPii: false,

    // Reduce noise from third-party scripts we don't own.
    ignoreErrors: [
      "ResizeObserver loop limit exceeded",
      "Non-Error promise rejection captured",
    ],
  });
}
