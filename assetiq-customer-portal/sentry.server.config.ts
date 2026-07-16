// P0-6: Customer portal — server-side Sentry bootstrap for Next.js API routes
// and Server Components. Inert when SENTRY_DSN is not set.
import * as Sentry from "@sentry/nextjs";

const dsn = process.env.SENTRY_DSN ?? process.env.NEXT_PUBLIC_SENTRY_DSN;

if (dsn) {
  Sentry.init({
    dsn,
    environment: process.env.APP_ENV ?? process.env.NODE_ENV ?? "development",
    release: process.env.APP_VERSION,

    tracesSampleRate: Number(process.env.SENTRY_TRACES_SAMPLE_RATE ?? "0.1"),

    // Never ship PII from server logs.
    sendDefaultPii: false,
  });
}
