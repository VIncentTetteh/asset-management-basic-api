import { withSentryConfig } from "@sentry/nextjs";

const nextConfig = {
  // Customer Portal is a standalone Next.js app with its own API routes.
  // No backend proxy needed — all backend calls go server-side via API routes.
  output: "standalone",
};

// Source maps are uploaded only when Sentry is configured. Local builds avoid
// the wrapper so they remain fast and do not require release credentials.
const sentryConfigured = Boolean(
  process.env.SENTRY_DSN || process.env.NEXT_PUBLIC_SENTRY_DSN,
);

export default sentryConfigured
  ? withSentryConfig(nextConfig, {
      org: process.env.SENTRY_ORG,
      project: process.env.SENTRY_PROJECT,
      authToken: process.env.SENTRY_AUTH_TOKEN,
      silent: !process.env.CI,
      widenClientFileUpload: true,
      tunnelRoute: "/monitoring",
      hideSourceMaps: true,
      disableLogger: true,
      telemetry: false,
    })
  : nextConfig;
