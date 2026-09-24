"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  MessageSquarePlus,
  Pencil,
  RotateCcw,
  Save,
  ShieldCheck,
  Trash2,
  Undo2,
  X,
} from "lucide-react";

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
  dgReviewSection,
  getAdminSubmissions,
  getGroupSectionContent,
  listGroupSections,
  resetGroupSection,
  reviewSection,
} from "@/lib/api/admin";
import { listGroups } from "@/lib/api/groups";
import { canAdminister, canApproveAsDg } from "@/lib/roles";
import { useCurrentUser } from "@/hooks/use-current-user";
import { extractErrorMessage } from "@/lib/api-client";
import { formatDateTime } from "@/lib/utils";
import { fileDuDg, lienSection, suivanteDansLaFile } from "@/lib/dg-queue";
import type { SectionType } from "@/types/common";

const RETURNABLE_STATUSES = new Set(["SUBMITTED", "VALIDATED"]);

export default function AdminSectionReviewPage() {
  const params = useParams<{ groupId: string; code: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const groupId = Number(params.groupId);
  const code = params.code;
  const [comment, setComment] = useState("");
  const [dgComment, setDgComment] = useState("");
  const [dgCommentOuvert, setDgCommentOuvert] = useState(false);
  const [editContent, setEditContent] = useState<unknown>(null);
  const { user } = useCurrentUser();
  // Le DG seul fait entrer une section dans les documents consolides, en la validant.
  const peutApprouver = canApproveAsDg(user?.role);
  // Effacer le contenu d'une section reste a l'admin ; le DG valide, refuse et modifie.
  const peutEffacer = canAdminister(user?.role);

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
        REQUEST_REVISION: "Section refusée et renvoyée pour révision",
        RETURN_TO_GROUP: "La main a été redonnée au groupe",
      };
      toast.success(messages[decision]);
      setComment("");
      invalidateAll();
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec de l'action")),
  });

  // File d'attente du DG, pour enchainer les sections sans repasser par la liste des soumissions.
  const { data: submissions } = useQuery({
    queryKey: ["admin", "submissions"],
    queryFn: getAdminSubmissions,
    enabled: peutApprouver,
  });
  const fileDg = fileDuDg(submissions);
  const suivante = suivanteDansLaFile(fileDg, groupId, code);
  const restantes = fileDg.filter((s) => !(s.groupId === groupId && s.sectionCode === code)).length;

  const dgMutation = useMutation({
    mutationFn: (decision: "APPROVE" | "REJECT") => dgReviewSection(groupId, code, decision, dgComment),
    onSuccess: (_, decision) => {
      setDgComment("");
      setDgCommentOuvert(false);
      invalidateAll();
      queryClient.invalidateQueries({ queryKey: ["admin", "submissions"] });
      if (decision === "REJECT") {
        toast.success("Section refusée et renvoyée en révision");
        return;
      }
      // Valider ouvre aussitot la section suivante : le DG relit et decide, sans revenir a la liste.
      if (suivante) {
        toast.success(`Section validée et intégrée à la Note de synthèse — ${restantes} restante${restantes > 1 ? "s" : ""}`);
        router.push(lienSection(suivante));
      } else {
        toast.success("Section validée et intégrée à la Note de synthèse — plus rien n'attend votre validation");
      }
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec de l'arbitrage")),
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
              {s.title}
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
              <h1>{data.title}</h1>
              <StatusBadge status={data.status} />
            </div>

            {!editing && (
              <div className="flex items-center gap-2">
                <Button variant="secondary" size="sm" onClick={() => setEditContent(data.content)}>
                  <Pencil className="h-4 w-4" /> Modifier
                </Button>
                {peutEffacer && (
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
                )}
              </div>
            )}
          </div>

          {/* Decision du DG en tete de page, et collee sous l'en-tete au defilement : il la prend
              en lisant, sans descendre sous tout le contenu de la section pour trouver les boutons. */}
          {!editing && peutApprouver && RETURNABLE_STATUSES.has(data.status) && (
            <div className="sticky top-16 z-[5] rounded-xl border border-primary-200 bg-card/95 px-4 py-3 shadow-sm backdrop-blur dark:border-primary-500/30">
              <div className="flex flex-wrap items-center gap-2">
                {data.dgApprovedAt ? (
                  <p className="mr-auto flex items-center gap-1.5 text-[13px] font-medium text-emerald-700 dark:text-emerald-400">
                    <ShieldCheck className="h-4 w-4" /> Validée le {formatDateTime(data.dgApprovedAt)} — intégrée à la Note de synthèse
                  </p>
                ) : (
                  <p className="mr-auto text-[13px] font-medium text-amber-700 dark:text-amber-400">
                    En attente de votre validation
                    {restantes > 0 && (
                      <span className="font-normal text-muted-foreground">
                        {" "}
                        · {restantes} autre{restantes > 1 ? "s" : ""} section{restantes > 1 ? "s" : ""} en attente
                      </span>
                    )}
                  </p>
                )}

                {!data.dgApprovedAt && (
                  <Button onClick={() => dgMutation.mutate("APPROVE")} loading={dgMutation.isPending}>
                    <CheckCircle2 className="h-4 w-4" />
                    {suivante ? "Valider et passer à la suivante" : "Valider"}
                  </Button>
                )}

                <AlertDialog>
                  <AlertDialogTrigger asChild>
                    <Button variant="secondary" disabled={dgMutation.isPending}>
                      <RotateCcw className="h-4 w-4" />
                      {data.dgApprovedAt ? "Annuler la validation" : "Refuser"}
                    </Button>
                  </AlertDialogTrigger>
                  <AlertDialogContent>
                    <AlertDialogHeader>
                      <AlertDialogTitle>
                        {data.dgApprovedAt ? "Annuler la validation ?" : "Refuser cette section ?"}
                      </AlertDialogTitle>
                      <AlertDialogDescription>
                        La section sera renvoyée en révision à la direction, qui devra la corriger puis la soumettre à
                        nouveau. Indiquez-lui ce qu&apos;il faut revoir.
                      </AlertDialogDescription>
                    </AlertDialogHeader>
                    <Textarea
                      placeholder="Motif du refus (recommandé)…"
                      value={dgComment}
                      onChange={(e) => setDgComment(e.target.value)}
                      rows={3}
                    />
                    <AlertDialogFooter>
                      <AlertDialogCancel>Annuler</AlertDialogCancel>
                      <AlertDialogAction onClick={() => dgMutation.mutate("REJECT")}>Renvoyer en révision</AlertDialogAction>
                    </AlertDialogFooter>
                  </AlertDialogContent>
                </AlertDialog>

                {!data.dgApprovedAt && !dgCommentOuvert && (
                  <Button variant="ghost" size="sm" onClick={() => setDgCommentOuvert(true)}>
                    <MessageSquarePlus className="h-4 w-4" /> Commentaire
                  </Button>
                )}

                {data.dgApprovedAt && suivante && (
                  <Button variant="primary" onClick={() => router.push(lienSection(suivante))}>
                    Section suivante en attente <ArrowRight className="h-4 w-4" />
                  </Button>
                )}
              </div>

              {dgCommentOuvert && !data.dgApprovedAt && (
                <Textarea
                  className="mt-3"
                  placeholder="Commentaire joint à la validation (facultatif)…"
                  value={dgComment}
                  onChange={(e) => setDgComment(e.target.value)}
                  rows={2}
                  autoFocus
                />
              )}
              {data.dgComment && (
                <p className="mt-2 text-[13px] text-muted-foreground">
                  <span className="font-medium">Votre commentaire : </span>
                  {data.dgComment}
                </p>
              )}
            </div>
          )}

          <div className="flex flex-wrap gap-x-6 gap-y-1.5 rounded-lg border border-border bg-muted/50 px-4 py-3 text-[13px]">
            <MetaItem label="Chef de groupe" value={groups?.find((g) => g.id === groupId)?.leaderFullName ?? "—"} />
            <MetaItem label="Version" value={data.version > 0 ? String(data.version) : "—"} />
            <MetaItem label="Soumis le" value={formatDateTime(data.submittedAt) || "—"} />
            <MetaItem label="Validé le" value={formatDateTime(data.validatedAt) || "—"} />
            <MetaItem label="Approuvé par la DG le" value={formatDateTime(data.dgApprovedAt) || "—"} />
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

          {/* Sans ce message, l'absence des boutons de revue passe pour un defaut d'affichage. */}
          {!editing && !RETURNABLE_STATUSES.has(data.status) && (
            <div className="rounded-lg border border-border bg-muted/50 px-4 py-3 text-[13px] text-muted-foreground">
              {data.status === "REVISION_REQUESTED"
                ? "Cette section a été renvoyée en révision : la direction doit la corriger puis la soumettre à nouveau avant de pouvoir être validée."
                : "Cette section n'a pas encore été soumise par la direction : il n'y a rien à valider pour l'instant. Les boutons de validation apparaîtront dès sa soumission."}
            </div>
          )}

          {/* Le DG decide depuis la barre en tete de page : ce bloc reste celui du comite de pilotage. */}
          {!editing && !peutApprouver && RETURNABLE_STATUSES.has(data.status) && (
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
                        <RotateCcw className="h-4 w-4" /> Refuser (renvoyer pour révision)
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

          {/* Le DG decide depuis la barre en tete de page ; ce rappel reste pour le comite de pilotage. */}
          {!editing && !peutApprouver && data.status === "VALIDATED" && (
            <Card>
              <CardHeader>
                <CardTitle>Approbation de la Direction Générale</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {data.dgApprovedAt ? (
                  <p className="text-[13px] text-emerald-700 dark:text-emerald-400">
                    Approuvée par la Direction Générale le {formatDateTime(data.dgApprovedAt)}. Cette section est
                    reprise dans le Document de consolidation, la Note de synthèse et le Plan Stratégique de SENICO.
                  </p>
                ) : (
                  <p className="text-[13px] text-amber-700 dark:text-amber-400">
                    Validée par le comité de pilotage, en attente de la Direction Générale. Tant que le DG n&apos;a
                    pas approuvé, le contenu de cette section n&apos;entre dans aucun document consolidé.
                  </p>
                )}

                {data.dgComment && (
                  <p className="text-[13px] text-muted-foreground">
                    <span className="font-medium">Commentaire du DG : </span>
                    {data.dgComment}
                  </p>
                )}

                <p className="text-[13px] text-muted-foreground">
                  Seule la Direction Générale peut approuver ou refuser une section validée.
                </p>
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
