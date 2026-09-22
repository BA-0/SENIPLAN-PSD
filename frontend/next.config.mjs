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
  compiler: {
    // Les console.* de debogage ne partent pas en production (les erreurs restent).
    removeConsole: process.env.NODE_ENV === "production" ? { exclude: ["error", "warn"] } : false,
  },
};

export default nextConfig;
