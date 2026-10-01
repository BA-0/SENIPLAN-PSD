"use client";

import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { WifiOff } from "lucide-react";

import { useNetworkPauseStore } from "@/store/network-pause-store";

/**
 * Le pare-feu AWS bloque momentanement le poste : l'application cesse d'envoyer des requetes le
 * temps qu'il leve son blocage, le dit a l'utilisateur, puis recharge les donnees a la reprise.
 */
export function NetworkPauseBanner() {
  const pausedUntil = useNetworkPauseStore((s) => s.pausedUntil);
  const resume = useNetworkPauseStore((s) => s.resume);
  const queryClient = useQueryClient();
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!pausedUntil) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [pausedUntil]);

  useEffect(() => {
    if (pausedUntil && now >= pausedUntil) {
      resume();
      queryClient.invalidateQueries();
    }
  }, [now, pausedUntil, resume, queryClient]);

  if (!pausedUntil || now >= pausedUntil) return null;

  const secondes = Math.ceil((pausedUntil - now) / 1000);

  return (
    <div className="mb-4 flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50/90 px-4 py-3 text-[14px] dark:border-amber-500/30 dark:bg-amber-500/10">
      <WifiOff className="mt-0.5 h-5 w-5 shrink-0 text-amber-600 dark:text-amber-400" />
      <p className="text-foreground">
        <span className="font-semibold">Connexion momentanément limitée par le réseau.</span>{" "}
        <span className="text-muted-foreground">
          Reprise automatique dans {secondes} s. Vos saisies restent à l&apos;écran et seront enregistrées à la
          reprise : ne rechargez pas la page.
        </span>
      </p>
    </div>
  );
}
