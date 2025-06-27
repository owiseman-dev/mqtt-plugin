import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  async rewrites() {
    return [
      {
        source: '/@node-red/:path*',
        destination: '/@node-red/:path*',
      },
    ];
  },
};

export default nextConfig;
