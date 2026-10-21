import type { NextConfig } from "next";

/**
 * NEXT_PUBLIC_BASE_PATH lets the admin panel live under a sub-path behind a reverse proxy,
 * e.g. "/admin" on https://example.com/admin. Leave it empty for local development (http://localhost:3001).
 * It is applied at build time — rebuild after changing it.
 */
const basePath = (process.env.NEXT_PUBLIC_BASE_PATH ?? "").replace(/\/+$/, "");

const nextConfig: NextConfig = {
  basePath: basePath || undefined,
};

export default nextConfig;
