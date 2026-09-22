"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, ArrowRight, Eye, PenLine } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { NativeSelect } from "@/components/ui/native-select";
import { StatusBadge } from "@/components/status-badge";
import { listPeerGroups, listPeerSections } from "@/lib/api/peers";
import { useRealtimePeers } from "@/hooks/use-realtime-peers";
import { useGroupTypingSection } from "@/store/presence-store";
import { countValidated, groupSectionsByPart } from "@/lib/section-groups";
import { timeAgo } from "@/lib/utils";

export default function PeerGroupPage() {
  const params = useParams<{ groupId: string }>();
  const router = useRouter();
  const groupId = Number(params.groupId);

  const { data: groups } = useQuery({ queryKey: ["peers", "groups"], queryFn: listPeerGroups });
  const { data: sections, isLoading } = useQuery({
    queryKey: ["peers", "sections", groupId],
    queryFn: () => listPeerSections(groupId),
    refetchInterval: 30_000,
  });

  useRealtimePeers([groupId]);
  const typingSection = useGroupTypingSection(groupId);

  const group = groups?.find((g) => g.id === groupId);
  const parts = groupSectionsByPart(sections ?? []);

  return (
    <div className="space-y-5">
      <Link
        href="/directions"
        className="inline-flex items-center gap-1.5 text-[13px] text-muted-foreground hover:text-primary-600 transition-colors"
      >
        <ArrowLeft className="h-3.5 w-3.5" /> Toutes les directions
      </Link>

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1>{group?.name ?? "Direction"}</h1>
          <p className="text-[13px] text-muted-foreground mt-1 flex items-center gap-1.5">
            <Eye className="h-3.5 w-3.5" /> Lecture seule · mise à jour en temps réel
            {group && <> · {group.completionPercent}% d&apos;avancement</>}
          </p>
        </div>
        <NativeSelect
          value={groupId}
          onChange={(e) => router.push(`/directions/${e.target.value}`)}
          className="max-w-xs"
        >
          {groups?.map((g) => (
            <option key={g.id} value={g.id}>
              {g.name}
            </option>
          ))}
        </NativeSelect>
      </div>

      {isLoading || !sections ? (
        <div className="h-96 bg-muted rounded-xl animate-pulse" />
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>Sections du canevas</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {parts.map(({ part, subGroups, sections: partSections }, partIndex) => (
              <div key={part.id} className={partIndex > 0 ? "border-t border-border" : undefined}>
                <div className="flex items-center justify-between gap-3 bg-muted/40 px-5 py-2">
                  <p className="text-[12px] font-semibold uppercase tracking-wide text-muted-foreground">
                    {part.numeral ? `Partie ${part.numeral} — ${part.title}` : part.title}
                  </p>
                  <span className="shrink-0 text-[12px] tabular-nums text-muted-foreground">
                    {countValidated(partSections)}/{partSections.length} validées
                  </span>
                </div>
                {subGroups.map((subGroup) => (
                  <div key={subGroup.title ?? "sections"}>
                    {subGroup.title && (
                      <p className="border-t border-border px-5 py-1.5 text-[12px] font-medium text-muted-foreground/80">
                        {subGroup.title}
                      </p>
                    )}
                    <div className="divide-y divide-border border-t border-border">
                      {subGroup.sections.map((s) => (
                        <Link
                          key={s.code}
                          href={`/directions/${groupId}/sections/${s.code}`}
                          className="flex items-center justify-between gap-4 px-5 py-3 hover:bg-primary-50/60 dark:hover:bg-white/5 transition-colors"
                        >
                          <div className="flex items-center gap-3 min-w-0">
                            <span className="text-[13px] text-foreground truncate">{s.title}</span>
                            {typingSection === s.code && (
                              <span className="inline-flex shrink-0 items-center gap-1 text-[12px] font-medium text-sky-600 dark:text-sky-300">
                                <PenLine className="h-3.5 w-3.5 animate-pulse" /> saisie en cours
                              </span>
                            )}
                          </div>
                          <div className="flex items-center gap-3 shrink-0">
                            {s.lastActivityAt && (
                              <span className="hidden sm:inline text-[12px] text-muted-foreground">
                                {timeAgo(s.lastActivityAt)}
                              </span>
                            )}
                            <StatusBadge status={s.status} />
                            <ArrowRight className="h-4 w-4 text-muted-foreground" />
                          </div>
                        </Link>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
