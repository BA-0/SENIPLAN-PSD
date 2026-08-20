"use client";

import Image from "next/image";

import { AuthGuard } from "@/components/layout/auth-guard";
import { Sidebar } from "@/components/layout/sidebar";
import { Header } from "@/components/layout/header";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <div className="relative flex min-h-screen">
        {/* Photo de fond fixe, sous tout le reste. Voilée (couleur de fond
            du thème) pour que le texte hors des cartes reste lisible, sans
            pour autant effacer complètement la photo. */}
        <div className="fixed inset-0 -z-10 overflow-hidden">
          <Image
            src="/login-bg.jpg"
            alt=""
            fill
            priority
            sizes="100vw"
            className="object-cover"
          />
          <div className="absolute inset-0 bg-background/80" />
        </div>
        <Sidebar />
        <div className="flex-1 flex flex-col min-w-0">
          <Header />
          <main className="flex-1 p-4 sm:p-6 max-w-[1400px] w-full mx-auto">{children}</main>
        </div>
      </div>
    </AuthGuard>
  );
}
