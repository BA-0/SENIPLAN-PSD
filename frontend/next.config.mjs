/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  eslint: {
    ignoreDuringBuilds: false,
  },
  ...(process.env.CLAUDE_VERIFY_DIST_DIR ? { distDir: process.env.CLAUDE_VERIFY_DIST_DIR } : {}),
};

export default nextConfig;
