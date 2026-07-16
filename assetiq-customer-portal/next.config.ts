import type { NextConfig } from "next";
import { withSentryConfig } from "@sentry/nextjs";

const nextConfig: NextConfig = {
  // Customer Portal is a standalone Next.js app with its own API routes.
  // No backend proxy needed — all backend calls go server-side via API routes.
  output: "standalone",
};

// P0-6: wrap the Next.js config with Sentry. Source maps are only uploaded
// when both SENTRY_AUTH_TOKEN and SENTRY_ORG/SENTRY_PROJECT are set — local
// dev builds ignore Sentry entirely so build times stay fast.
const sentryConfigured = Boolean(
  process.env.SENTRY_DSN || process.env.NEXT_PUBLIC_SENTRY_DSN
);

export default sentryConfigured
  ? withSentryConfig(nextConfig, {
      org: process.env.SENTRY_ORG,
      project: process.env.SENTRY_PROJECT,
      authToken: process.env.SENTRY_AUTH_TOKEN,
      silent: !process.env.CI,
      widenClientFileUpload: true,
      // Tunnel Sentry events through the portal to dodge ad-blockers on the
      // marketing site; Sentry generates this endpoint automatically.
      tunnelRoute: "/monitoring",
      hideSourceMaps: true,
      disableLogger: true,
      telemetry: false,
    })
  : nextConfig;
