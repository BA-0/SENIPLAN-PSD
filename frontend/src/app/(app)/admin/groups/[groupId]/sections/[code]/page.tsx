"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, CheckCircle2, Pencil, RotateCcw, Save, Trash2, Undo2, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { NativeSelect } from "@/components/ui/native-select";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { StatusBadge } from "@/components/status-badge";
import { SectionFormRouter } from "@/components/sections/section-form-router";
import { VersionHistory } from "@/components/sections/version-history";
import {
  adminUpdateSectionContent,
  getGroupSectionContent,
  listGroupSections,
  resetGroupSection,
  reviewSection,
} from "@/lib/api/admin";
import { listGroups } from "@/lib/api/groups";
import { extractErrorMessage } from "@/lib/api-client";
import { formatDateTime } from "@/lib/utils";
import type { SectionType } from "@/types/common";

const RETURNABLE_STATUSES = new Set(["SUBMITTED", "VALIDATED"]);

export default function AdminSectionReviewPage() {
  const params = useParams<{ groupId: string; code: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const groupId = Number(params.groupId);
  const code = params.code;
  const [comment, setComment] = useState("");
  const [editContent, setEditContent] = useState<unknown>(null);

  const { data: groups } = useQuery({ queryKey: ["admin", "groups"], queryFn: listGroups });
  const { data: sections } = useQuery({
    queryKey: ["admin", "group-sections", groupId],
    queryFn: () => listGroupSections(groupId),
  });
  const { data, isLoading } = useQuery({
    queryKey: ["admin", "section-content", groupId, code],
    queryFn: () => getGroupSectionContent(groupId, code),
  });

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: ["admin", "section-content", groupId, code] });
    queryClient.invalidateQueries({ queryKey: ["admin", "group-sections", groupId] });
    queryClient.invalidateQueries({ queryKey: ["admin", "matrix"] });
    queryClient.invalidateQueries({ queryKey: ["admin", "dashboard"] });
  };

  const reviewMutation = useMutation({
    mutationFn: (decision: "VALIDATE" | "REQUEST_REVISION" | "RETURN_TO_GROUP") =>
      reviewSection(groupId, code, decision, comment),
    onSuccess: (_, decision) => {
      const messages: Record<string, string> = {
        VALIDATE: "Section validée",
        REQUEST_REVISION: "Révision demandée",
        RETURN_TO_GROUP: "La main a été redonnée au groupe",
      };
      toast.success(messages[decision]);
      setComment("");
      invalidateAll();
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec de l'action")),
  });

  const updateContentMutation = useMutation({
    mutationFn: (content: unknown) => adminUpdateSectionContent(groupId, code, content),
    onSuccess: () => {
      toast.success("Section mise à jour");
      setEditContent(null);
      invalidateAll();
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec de l'enregistrement")),
  });

  const resetMutation = useMutation({
    mutationFn: () => resetGroupSection(groupId, code),
    onSuccess: () => {
      toast.success("Section réinitialisée");
      invalidateAll();
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec de la réinitialisation")),
  });

  const editing = editContent !== null;

  return (
    <div className="space-y-5">
      <Link
        href="/admin/submissions"
        className="inline-flex items-center gap-1.5 text-[13px] text-muted-foreground hover:text-primary-600 transition-colors"
      >
        <ArrowLeft className="h-3.5 w-3.5" /> Retour aux soumissions
      </Link>

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
        <div className="h-96 bg-muted rounded-xl animate-pulse" />
      ) : (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2.5">
              <span className="text-muted-foreground text-sm">{data.code}</span>
              <h1>{data.title}</h1>
              <StatusBadge status={data.status} />
            </div>

            {!editing && (
              <div className="flex items-center gap-2">
                <Button variant="secondary" size="sm" onClick={() => setEditContent(data.content)}>
                  <Pencil className="h-4 w-4" /> Modifier
                </Button>
                <AlertDialog>
                  <AlertDialogTrigger asChild>
                    <Button variant="destructiveGhost" size="sm">
                      <Trash2 className="h-4 w-4" /> Supprimer
                    </Button>
                  </AlertDialogTrigger>
                  <AlertDialogContent>
                    <AlertDialogHeader>
                      <AlertDialogTitle>Supprimer le contenu de cette section ?</AlertDialogTitle>
                      <AlertDialogDescription>
                        Le contenu saisi sera effacé et la section repassera à &quot;Non commencé&quot;, comme si le
                        groupe n&apos;avait rien rempli. L&apos;historique des versions reste consultable pour audit.
                      </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                      <AlertDialogCancel>Annuler</AlertDialogCancel>
                      <AlertDialogAction onClick={() => resetMutation.mutate()}>Supprimer</AlertDialogAction>
                    </AlertDialogFooter>
                  </AlertDialogContent>
                </AlertDialog>
              </div>
            )}
          </div>

          <div className="flex flex-wrap gap-x-6 gap-y-1.5 rounded-lg border border-border bg-muted/50 px-4 py-3 text-[13px]">
            <MetaItem label="Chef de groupe" value={groups?.find((g) => g.id === groupId)?.leaderFullName ?? "—"} />
            <MetaItem label="Version" value={data.version > 0 ? String(data.version) : "—"} />
            <MetaItem label="Soumis le" value={formatDateTime(data.submittedAt) || "—"} />
            <MetaItem label="Validé le" value={formatDateTime(data.validatedAt) || "—"} />
            <MetaItem label="Dernière activité" value={formatDateTime(data.lastActivityAt) || "—"} />
          </div>

          <SectionFormRouter
            type={data.type as SectionType}
            content={editing ? editContent : data.content}
            onChange={(updater) => setEditContent((prev: unknown) => updater(prev))}
            readOnly={!editing}
          />

          {editing && (
            <div className="flex gap-2">
              <Button
                variant="primary"
                onClick={() => updateContentMutation.mutate(editContent)}
                loading={updateContentMutation.isPending}
              >
                <Save className="h-4 w-4" /> Enregistrer les modifications
              </Button>
              <Button variant="secondary" onClick={() => setEditContent(null)}>
                <X className="h-4 w-4" /> Annuler
              </Button>
            </div>
          )}

          {!editing && RETURNABLE_STATUSES.has(data.status) && (
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
                <div className="flex flex-wrap gap-2">
                  {data.status === "SUBMITTED" && (
                    <>
                      <Button
                        variant="primary"
                        onClick={() => reviewMutation.mutate("VALIDATE")}
                        loading={reviewMutation.isPending}
                      >
                        <CheckCircle2 className="h-4 w-4" /> Valider la section
                      </Button>
                      <Button
                        variant="secondary"
                        onClick={() => reviewMutation.mutate("REQUEST_REVISION")}
                        loading={reviewMutation.isPending}
                      >
                        <RotateCcw className="h-4 w-4" /> Renvoyer pour révision
                      </Button>
                    </>
                  )}
                  {RETURNABLE_STATUSES.has(data.status) && (
                    <Button
                      variant="secondary"
                      onClick={() => reviewMutation.mutate("RETURN_TO_GROUP")}
                      loading={reviewMutation.isPending}
                    >
                      <Undo2 className="h-4 w-4" /> Redonner la main au groupe
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          )}

          {data.adminComment && data.status !== "SUBMITTED" && (
            <div className="rounded-lg border border-border bg-muted px-4 py-3 text-[13px] text-muted-foreground">
              <span className="font-medium">Dernier commentaire : </span>
              {data.adminComment}
            </div>
          )}

          <VersionHistory groupId={groupId} code={code} />
        </>
      )}
    </div>
  );
}

function MetaItem({ label, value }: { label: string; value: string }) {
  return (
    <span className="text-muted-foreground">
      <span className="font-medium text-foreground/80">{label} : </span>
      {value}
    </span>
  );
}
