"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight, Eye, PenLine } from "lucide-react";

import { Card, CardContent } from "@/components/ui/card";
import { listPeerGroups } from "@/lib/api/peers";
import { useRealtimePeers } from "@/hooks/use-realtime-peers";
import { useGroupTypingSection } from "@/store/presence-store";
import { timeAgo } from "@/lib/utils";
import type { PeerGroupDto } from "@/types/api";

/**
 * Consultation croisee : chaque direction suit, en lecture seule et en direct, la saisie des
 * autres directions (cf. PeerSectionController cote serveur).
 */
export default function PeerDirectionsPage() {
  const { data: groups, isLoading } = useQuery({
    queryKey: ["peers", "groups"],
    queryFn: listPeerGroups,
    refetchInterval: 30_000,
  });

  useRealtimePeers(groups?.map((g) => g.id) ?? []);

  return (
    <div className="space-y-6">
      <div>
        <h1>Autres directions</h1>
        <p className="text-[13px] text-muted-foreground mt-1 flex items-center gap-1.5">
          <Eye className="h-3.5 w-3.5" /> Consultation en lecture seule, mise à jour en temps réel
        </p>
      </div>

      {isLoading || !groups ? (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4 animate-pulse">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-40 bg-muted rounded-xl" />
          ))}
        </div>
      ) : groups.length === 0 ? (
        <Card>
          <CardContent className="py-10 text-center text-[13px] text-muted-foreground">
            Aucune autre direction à consulter pour le moment.
          </CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {groups.map((g) => (
            <PeerGroupCard key={g.id} group={g} />
          ))}
        </div>
      )}
    </div>
  );
}

function PeerGroupCard({ group }: { group: PeerGroupDto }) {
  const typingSection = useGroupTypingSection(group.id);

  return (
    <Link href={`/directions/${group.id}`} className="group block">
      <Card className="h-full overflow-hidden transition-shadow group-hover:shadow-md">
        <div className="h-1.5" style={{ backgroundColor: group.color ?? "#2D7A45" }} />
        <CardContent className="pt-4 space-y-3">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="font-semibold text-foreground truncate">{group.name}</p>
              {group.leaderFullName && (
                <p className="text-[12px] text-muted-foreground truncate">{group.leaderFullName}</p>
              )}
            </div>
            <ArrowRight className="h-4 w-4 shrink-0 text-muted-foreground group-hover:text-primary-600 transition-colors" />
          </div>

          <div>
            <div className="flex items-center justify-between text-[12px] text-muted-foreground mb-1">
              <span>Avancement</span>
              <span className="tabular-nums font-medium text-foreground">{group.completionPercent}%</span>
            </div>
            <div className="h-2 rounded-full bg-muted overflow-hidden">
              <div
                className="h-full rounded-full bg-primary-500 transition-[width] duration-500"
                style={{ width: `${Math.max(0, Math.min(100, group.completionPercent))}%` }}
              />
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[12px] text-muted-foreground">
            <span>
              <span className="font-medium text-foreground tabular-nums">{group.sectionsSubmitted}</span> soumises
            </span>
            <span>
              <span className="font-medium text-foreground tabular-nums">{group.sectionsValidated}</span> validées
            </span>
            {group.lastActivityAt && <span>Activité {timeAgo(group.lastActivityAt)}</span>}
          </div>

          {typingSection && (
            <p className="inline-flex items-center gap-1.5 rounded-md bg-sky-100 dark:bg-sky-500/20 px-2 py-1 text-[12px] font-medium text-sky-700 dark:text-sky-300">
              <PenLine className="h-3.5 w-3.5 animate-pulse" /> Saisie en cours
            </p>
          )}
        </CardContent>
      </Card>
    </Link>
  );
}
