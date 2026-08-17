"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { CheckCircle2, RotateCcw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { NativeSelect } from "@/components/ui/native-select";
import { StatusBadge } from "@/components/status-badge";
import { SectionFormRouter } from "@/components/sections/section-form-router";
import { getGroupSectionContent, listGroupSections, reviewSection } from "@/lib/api/admin";
import { listGroups } from "@/lib/api/groups";
import { extractErrorMessage } from "@/lib/api-client";
import type { SectionType } from "@/types/common";

export default function AdminSectionReviewPage() {
  const params = useParams<{ groupId: string; code: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const groupId = Number(params.groupId);
  const code = params.code;
  const [comment, setComment] = useState("");

  const { data: groups } = useQuery({ queryKey: ["admin", "groups"], queryFn: listGroups });
  const { data: sections } = useQuery({
    queryKey: ["admin", "group-sections", groupId],
    queryFn: () => listGroupSections(groupId),
  });
  const { data, isLoading } = useQuery({
    queryKey: ["admin", "section-content", groupId, code],
    queryFn: () => getGroupSectionContent(groupId, code),
  });

  const reviewMutation = useMutation({
    mutationFn: (decision: "VALIDATE" | "REQUEST_REVISION") => reviewSection(groupId, code, decision, comment),
    onSuccess: (_, decision) => {
      toast.success(decision === "VALIDATE" ? "Section validée" : "Révision demandée");
      setComment("");
      queryClient.invalidateQueries({ queryKey: ["admin", "section-content", groupId, code] });
      queryClient.invalidateQueries({ queryKey: ["admin", "group-sections", groupId] });
      queryClient.invalidateQueries({ queryKey: ["admin", "matrix"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "dashboard"] });
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec de l'action")),
  });

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-3">
        <NativeSelect
          value={groupId}
          onChange={(e) => router.push(`/admin/groups/${e.target.value}/sections/${code}`)}
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
          onChange={(e) => router.push(`/admin/groups/${groupId}/sections/${e.target.value}`)}
          className="max-w-xs"
        >
          {sections?.map((s) => (
            <option key={s.code} value={s.code}>
              {s.code} — {s.title}
            </option>
          ))}
        </NativeSelect>
      </div>

      {isLoading || !data ? (
        <div className="h-96 bg-slate-200 rounded-xl animate-pulse" />
      ) : (
        <>
          <div className="flex items-center gap-2.5">
            <span className="text-slate-400 text-sm">{data.code}</span>
            <h1>{data.title}</h1>
            <StatusBadge status={data.status} />
          </div>

          <SectionFormRouter type={data.type as SectionType} content={data.content} onChange={() => {}} readOnly />

          {data.status === "SUBMITTED" && (
            <Card>
              <CardHeader>
                <CardTitle>Revue de la section</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <Textarea
                  placeholder="Commentaire (optionnel pour validation, recommandé pour une révision)…"
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  rows={3}
                />
                <div className="flex gap-2">
                  <Button variant="primary" onClick={() => reviewMutation.mutate("VALIDATE")} loading={reviewMutation.isPending}>
                    <CheckCircle2 className="h-4 w-4" /> Valider la section
                  </Button>
                  <Button variant="secondary" onClick={() => reviewMutation.mutate("REQUEST_REVISION")} loading={reviewMutation.isPending}>
                    <RotateCcw className="h-4 w-4" /> Renvoyer pour révision
                  </Button>
                </div>
              </CardContent>
            </Card>
          )}

          {data.adminComment && data.status !== "SUBMITTED" && (
            <div className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-[13px] text-slate-600">
              <span className="font-medium">Dernier commentaire : </span>
              {data.adminComment}
            </div>
          )}
        </>
      )}
    </div>
  );
}
