import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Customer Portal is a standalone Next.js app with its own API routes.
  // No backend proxy needed — all backend calls go server-side via API routes.
  output: "standalone",
};

export default nextConfig;
