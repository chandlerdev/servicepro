import type { NextConfig } from "next";

/**
 * NEXT_PUBLIC_BASE_PATH lets the admin panel live under a sub-path behind a reverse proxy,
 * e.g. "/admin" on https://example.com/admin. Leave it empty for local development (http://localhost:3001).
 * It is applied at build time — rebuild after changing it.
 */
const basePath = (process.env.NEXT_PUBLIC_BASE_PATH ?? "").replace(/\/+$/, "");

const nextConfig: NextConfig = {
  // Only the release workflow sets NEXT_OUTPUT=standalone, which makes the
  // build a self-contained server for deploy/deploy.sh. A normal
  // `npm run build` is unchanged.
  output: process.env.NEXT_OUTPUT === "standalone" ? "standalone" : undefined,
  basePath: basePath || undefined,
};

export default nextConfig;
