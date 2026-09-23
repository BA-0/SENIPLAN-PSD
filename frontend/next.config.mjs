/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  // `npm run prod` construit et sert dans .next-prod : un `npm run dev` lance en parallele
  // ecrit dans .next et corromprait sinon le build de production (pages en erreur 500).
  distDir: process.env.NEXT_DIST_DIR || ".next",
  eslint: {
    ignoreDuringBuilds: false,
  },
  images: {
    // WebP seul : une variante par taille au lieu de deux, servie plus vite et mise en cache
    // plus longtemps (les images du dossier public ne changent qu'avec un nouveau build).
    formats: ["image/webp"],
    minimumCacheTTL: 60 * 60 * 24 * 30,
  },
  // Relais vers le backend local : les postes du LAN n'ont besoin que du port 3000, et les
  // appels restent sur la meme origine (pas de CORS). Actif quand NEXT_PUBLIC_API_BASE_URL
  // est relatif (/api/v1) ; en production, Nginx fait ce travail avant d'arriver ici.
  async rewrites() {
    const backend = process.env.BACKEND_INTERNAL_URL || "http://127.0.0.1:8080";
    return [
      { source: "/api/v1/:path*", destination: `${backend}/api/v1/:path*` },
      { source: "/ws/:path*", destination: `${backend}/ws/:path*` },
    ];
  },
  compiler: {
    // Les console.* de debogage ne partent pas en production (les erreurs restent).
    removeConsole: process.env.NODE_ENV === "production" ? { exclude: ["error", "warn"] } : false,
  },
};

export default nextConfig;
