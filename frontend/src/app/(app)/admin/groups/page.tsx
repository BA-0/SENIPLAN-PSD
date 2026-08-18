"use client";

import { useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { toast } from "sonner";
import { FileDown, FileText, KeyRound, Pencil, Plus, Power } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
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
import { createGroup, listGroups, resetLeaderPassword, setGroupEnabled, updateGroup } from "@/lib/api/groups";
import { downloadConsolidatedExcel, downloadGroupPdf, downloadGroupWord } from "@/lib/api/exports";
import { extractErrorMessage } from "@/lib/api-client";
import { formatDateTime } from "@/lib/utils";
import type { CreateWorkGroupPayload, UpdateWorkGroupPayload } from "@/lib/api/groups";
import type { WorkGroupDto } from "@/types/api";

const schema = z.object({
  name: z.string().min(1, "Le nom du groupe est requis"),
  description: z.string().optional(),
  leaderUsername: z.string().min(1, "L'identifiant est requis"),
  leaderFullName: z.string().min(1, "Le nom complet est requis"),
  leaderPassword: z.union([z.string().min(6, "Au moins 6 caractères"), z.literal("")]).optional(),
});

const editSchema = z.object({
  name: z.string().min(1, "Le nom du groupe est requis"),
  description: z.string().optional(),
  leaderFullName: z.string().min(1, "Le nom complet est requis"),
});

export default function AdminGroupsPage() {
  const queryClient = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [editingGroup, setEditingGroup] = useState<WorkGroupDto | null>(null);
  const [newCredentials, setNewCredentials] = useState<{ username: string; password: string } | null>(null);
  const [exportingExcel, setExportingExcel] = useState(false);

  async function handleExportExcel() {
    setExportingExcel(true);
    try {
      await downloadConsolidatedExcel();
    } catch (error) {
      toast.error(extractErrorMessage(error, "Échec de l'export Excel"));
    } finally {
      setExportingExcel(false);
    }
  }

  const { data: groups, isLoading } = useQuery({ queryKey: ["admin", "groups"], queryFn: listGroups });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CreateWorkGroupPayload>({ resolver: zodResolver(schema) });

  const {
    register: registerEdit,
    handleSubmit: handleEditSubmit,
    reset: resetEdit,
    formState: { errors: editErrors },
  } = useForm<UpdateWorkGroupPayload>({ resolver: zodResolver(editSchema) });

  function openEditDialog(group: WorkGroupDto) {
    setEditingGroup(group);
    resetEdit({
      name: group.name,
      description: group.description ?? "",
      leaderFullName: group.leaderFullName ?? "",
    });
  }

  const updateMutation = useMutation({
    mutationFn: (values: UpdateWorkGroupPayload) => updateGroup(editingGroup!.id, values),
    onSuccess: (group) => {
      toast.success(`Groupe « ${group.name} » mis à jour`);
      queryClient.invalidateQueries({ queryKey: ["admin", "groups"] });
      setEditingGroup(null);
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec de la mise à jour du groupe")),
  });

  const createMutation = useMutation({
    mutationFn: createGroup,
    onSuccess: (group) => {
      toast.success(`Groupe « ${group.name} » créé`);
      queryClient.invalidateQueries({ queryKey: ["admin", "groups"] });
      setCreateOpen(false);
      reset();
      if (group.leaderUsername && group.temporaryPassword) {
        setNewCredentials({ username: group.leaderUsername, password: group.temporaryPassword });
      }
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec de la création du groupe")),
  });

  const toggleMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) => setGroupEnabled(id, enabled),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "groups"] });
      toast.success("Statut du groupe mis à jour");
    },
    onError: (error) => toast.error(extractErrorMessage(error)),
  });

  const resetPasswordMutation = useMutation({
    mutationFn: resetLeaderPassword,
    onSuccess: (data) => setNewCredentials({ username: data.username, password: data.temporaryPassword }),
    onError: (error) => toast.error(extractErrorMessage(error, "Échec de la réinitialisation")),
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1>Groupes de travail</h1>
          <p className="text-[13px] text-muted-foreground mt-1">Départements participant au plan stratégique</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="secondary" onClick={handleExportExcel} loading={exportingExcel}>
            <FileDown className="h-4 w-4" /> Export Excel consolidé
          </Button>
        <Dialog open={createOpen} onOpenChange={setCreateOpen}>
          <DialogTrigger asChild>
            <Button variant="primary">
              <Plus className="h-4 w-4" /> Nouveau groupe
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Créer un groupe de travail</DialogTitle>
            </DialogHeader>
            <form
              onSubmit={handleSubmit((values) =>
                createMutation.mutate({ ...values, leaderPassword: values.leaderPassword || undefined })
              )}
              className="space-y-4"
            >
              <div className="space-y-1.5">
                <Label required>Nom du groupe (département)</Label>
                <Input {...register("name")} error={!!errors.name} placeholder="ex. Direction Marketing" />
                {errors.name && <p className="text-[13px] text-accent-700 dark:text-accent-300">{errors.name.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>Description</Label>
                <Textarea {...register("description")} rows={2} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label required>Identifiant du chef de groupe</Label>
                  <Input {...register("leaderUsername")} error={!!errors.leaderUsername} placeholder="ex. dir.marketing" />
                  {errors.leaderUsername && <p className="text-[13px] text-accent-700 dark:text-accent-300">{errors.leaderUsername.message}</p>}
                </div>
                <div className="space-y-1.5">
                  <Label required>Nom complet du chef de groupe</Label>
                  <Input {...register("leaderFullName")} error={!!errors.leaderFullName} />
                  {errors.leaderFullName && <p className="text-[13px] text-accent-700 dark:text-accent-300">{errors.leaderFullName.message}</p>}
                </div>
              </div>
              <div className="space-y-1.5">
                <Label>Mot de passe du chef de groupe</Label>
                <Input
                  {...register("leaderPassword")}
                  type="text"
                  error={!!errors.leaderPassword}
                  placeholder="Laisser vide pour générer automatiquement"
                />
                {errors.leaderPassword && <p className="text-[13px] text-accent-700 dark:text-accent-300">{errors.leaderPassword.message}</p>}
                <p className="text-[13px] text-muted-foreground">
                  Si laissé vide, un mot de passe temporaire sera généré automatiquement et affiché après la création.
                </p>
              </div>
              <DialogFooter>
                <Button type="submit" variant="primary" loading={createMutation.isPending}>
                  Créer le groupe
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{groups?.length ?? 0} groupe(s)</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          <div className="divide-y divide-border/60">
            {isLoading && <p className="px-5 py-8 text-center text-muted-foreground">Chargement…</p>}
            {groups?.map((g) => (
              <div key={g.id} className="flex items-center justify-between px-5 py-4 gap-4">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <Link href={`/admin/groups/${g.id}/sections/S01`} className="text-[14px] font-medium text-foreground hover:text-primary-600">
                      {g.name}
                    </Link>
                    {!g.enabled && <span className="text-[11px] rounded-full bg-muted text-muted-foreground px-2 py-0.5">Désactivé</span>}
                  </div>
                  <p className="text-[12px] text-muted-foreground mt-0.5">
                    {g.leaderFullName} · {g.leaderUsername} · Dernière activité : {g.lastActivityAt ? formatDateTime(g.lastActivityAt) : "—"}
                  </p>
                </div>
                <div className="flex items-center gap-4 shrink-0">
                  <span className="text-[13px] font-semibold text-primary-600 w-12 text-right">{g.completionPercent}%</span>
                  <Button variant="ghost" size="icon" title="Modifier le groupe" onClick={() => openEditDialog(g)}>
                    <Pencil className="h-4 w-4" />
                  </Button>
                  <AlertDialog>
                    <AlertDialogTrigger asChild>
                      <Button variant="ghost" size="icon" title="Réinitialiser le mot de passe">
                        <KeyRound className="h-4 w-4" />
                      </Button>
                    </AlertDialogTrigger>
                    <AlertDialogContent>
                      <AlertDialogHeader>
                        <AlertDialogTitle>Réinitialiser le mot de passe ?</AlertDialogTitle>
                        <AlertDialogDescription>
                          Un nouveau mot de passe temporaire sera généré pour {g.leaderUsername}.
                        </AlertDialogDescription>
                      </AlertDialogHeader>
                      <AlertDialogFooter>
                        <AlertDialogCancel>Annuler</AlertDialogCancel>
                        <AlertDialogAction onClick={() => resetPasswordMutation.mutate(g.id)}>Réinitialiser</AlertDialogAction>
                      </AlertDialogFooter>
                    </AlertDialogContent>
                  </AlertDialog>
                  <Button
                    variant="ghost"
                    size="icon"
                    title="Exporter en PDF"
                    onClick={() => downloadGroupPdf(g.id).catch((error) => toast.error(extractErrorMessage(error, "Échec de l'export PDF")))}
                  >
                    <FileDown className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    title="Exporter en Word"
                    onClick={() => downloadGroupWord(g.id).catch((error) => toast.error(extractErrorMessage(error, "Échec de l'export Word")))}
                  >
                    <FileText className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    title={g.enabled ? "Désactiver le groupe" : "Activer le groupe"}
                    onClick={() => toggleMutation.mutate({ id: g.id, enabled: !g.enabled })}
                  >
                    <Power className={g.enabled ? "h-4 w-4 text-primary-500" : "h-4 w-4 text-muted-foreground"} />
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      <Dialog open={!!editingGroup} onOpenChange={(open) => !open && setEditingGroup(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Modifier le groupe</DialogTitle>
          </DialogHeader>
          <form
            onSubmit={handleEditSubmit((values) => updateMutation.mutate(values))}
            className="space-y-4"
          >
            <div className="space-y-1.5">
              <Label required>Nom du groupe (département)</Label>
              <Input {...registerEdit("name")} error={!!editErrors.name} />
              {editErrors.name && <p className="text-[13px] text-accent-700 dark:text-accent-300">{editErrors.name.message}</p>}
            </div>
            <div className="space-y-1.5">
              <Label>Description</Label>
              <Textarea {...registerEdit("description")} rows={2} />
            </div>
            <div className="space-y-1.5">
              <Label required>Nom complet du chef de groupe</Label>
              <Input {...registerEdit("leaderFullName")} error={!!editErrors.leaderFullName} />
              {editErrors.leaderFullName && <p className="text-[13px] text-accent-700 dark:text-accent-300">{editErrors.leaderFullName.message}</p>}
            </div>
            <DialogFooter>
              <Button type="submit" variant="primary" loading={updateMutation.isPending}>
                Enregistrer les modifications
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={!!newCredentials} onOpenChange={(open) => !open && setNewCredentials(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Identifiants générés</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <p className="text-[13px] text-muted-foreground">
              Communiquez ces identifiants au chef de groupe. Ce mot de passe ne sera plus affiché ensuite.
            </p>
            <div className="rounded-lg bg-muted p-4 space-y-1.5 font-mono text-sm">
              <p>
                <span className="text-muted-foreground">Identifiant : </span>
                {newCredentials?.username}
              </p>
              <p>
                <span className="text-muted-foreground">Mot de passe : </span>
                {newCredentials?.password}
              </p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="primary" onClick={() => setNewCredentials(null)}>
              Fermer
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
