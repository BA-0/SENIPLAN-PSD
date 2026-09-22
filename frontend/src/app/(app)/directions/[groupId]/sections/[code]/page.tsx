"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Eye, PenLine } from "lucide-react";

import { NativeSelect } from "@/components/ui/native-select";
import { StatusBadge } from "@/components/status-badge";
import { SectionFormRouter } from "@/components/sections/section-form-router";
import { getPeerSectionContent, listPeerGroups, listPeerSections } from "@/lib/api/peers";
import { useRealtimePeers } from "@/hooks/use-realtime-peers";
import { useGroupTypingSection } from "@/store/presence-store";
import { formatDateTime } from "@/lib/utils";
import type { SectionType } from "@/types/common";

// Lecture seule : le formulaire ne propage jamais de modification.
const noop = () => {};

export default function PeerSectionPage() {
  const params = useParams<{ groupId: string; code: string }>();
  const router = useRouter();
  const groupId = Number(params.groupId);
  const code = params.code;

  const { data: groups } = useQuery({ queryKey: ["peers", "groups"], queryFn: listPeerGroups });
  const { data: sections } = useQuery({
    queryKey: ["peers", "sections", groupId],
    queryFn: () => listPeerSections(groupId),
  });
  const { data, isLoading } = useQuery({
    queryKey: ["peers", "section", groupId, code],
    queryFn: () => getPeerSectionContent(groupId, code),
    refetchInterval: 30_000,
  });

  useRealtimePeers([groupId]);
  const typing = useGroupTypingSection(groupId) === code;

  return (
    <div className="space-y-5">
      <Link
        href={`/directions/${groupId}`}
        className="inline-flex items-center gap-1.5 text-[13px] text-muted-foreground hover:text-primary-600 transition-colors"
      >
        <ArrowLeft className="h-3.5 w-3.5" /> Retour aux sections de la direction
      </Link>

      <div className="flex flex-wrap items-center gap-3">
        <NativeSelect
          value={groupId}
          onChange={(e) => router.push(`/directions/${e.target.value}/sections/${code}`)}
          className="max-w-xs"
        >
          {groups?.map((g) => (
            <option key={g.id} value={g.id}>
              {g.name}
            </option>
          ))}
        </NativeSelect>
        <NativeSelect
          value={code}
          onChange={(e) => router.push(`/directions/${groupId}/sections/${e.target.value}`)}
          className="max-w-xs"
        >
          {sections?.map((s) => (
            <option key={s.code} value={s.code}>
              {s.title}
            </option>
          ))}
        </NativeSelect>
      </div>

      {isLoading || !data ? (
        <div className="h-96 bg-muted rounded-xl animate-pulse" />
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-2.5">
            <h1>{data.title}</h1>
            <StatusBadge status={data.status} />
          </div>

          <div className="flex flex-wrap items-center gap-x-6 gap-y-1.5 rounded-lg border border-sky-200 dark:border-sky-500/30 bg-sky-50/70 dark:bg-sky-500/10 px-4 py-3 text-[13px]">
            <span className="inline-flex items-center gap-1.5 font-medium text-sky-800 dark:text-sky-200">
              <Eye className="h-4 w-4" /> Lecture seule — saisie de {data.groupName}, mise à jour en temps réel
            </span>
            {typing && (
              <span className="inline-flex items-center gap-1.5 font-medium text-sky-700 dark:text-sky-300">
                <PenLine className="h-4 w-4 animate-pulse" /> Saisie en cours…
              </span>
            )}
            <span className="text-muted-foreground">
              <span className="font-medium text-foreground/80">Dernière mise à jour : </span>
              {formatDateTime(data.lastActivityAt ?? data.updatedAt) || "—"}
            </span>
          </div>

          <SectionFormRouter type={data.type as SectionType} content={data.content} onChange={noop} readOnly />
        </>
      )}
    </div>
  );
}
