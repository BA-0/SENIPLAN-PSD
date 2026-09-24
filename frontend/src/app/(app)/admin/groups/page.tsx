"use client";

import { useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { toast } from "sonner";
import { Archive, CheckCheck, FileDown, FileText, KeyRound, Pencil, Plus, Power, RotateCcw, Trash2, UserPlus } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
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
import {
  getGroupCycleSectionContent,
  getGroupCycleSections,
  listGroupCycles,
  startNewCycle,
  validateAllSubmitted,
} from "@/lib/api/admin";
import { downloadConsolidatedExcel, downloadGroupPdf, downloadGroupWord } from "@/lib/api/exports";
import { canAdminister, canApproveAsDg } from "@/lib/roles";
import { useCurrentUser } from "@/hooks/use-current-user";
import { createUserAccount, listUserAccounts, purgeGroupData, resetUserPassword, updateUserUsername } from "@/lib/api/admin";
import type { UserAccount } from "@/lib/api/admin";
import { extractErrorMessage } from "@/lib/api-client";
import { formatDateTime } from "@/lib/utils";
import { CycleArchivePanel } from "@/components/cycles/cycle-archive-panel";
import type { CreateWorkGroupPayload, UpdateWorkGroupPayload } from "@/lib/api/groups";

// Teintes bien distinctes les unes des autres, dans l'ordre ou elles sont proposees aux nouvelles directions :
// une direction creee recoit la premiere encore libre, pour ne pas se confondre avec une autre dans les documents.
const DIRECTION_PALETTE = [
  "#1F4FD8", "#2E8B2E", "#E36A00", "#7B2CBF", "#D62828", "#E0409A",
  "#0E9AA7", "#7F4A1E", "#C9A000", "#1B2A41", "#6B7280", "#800020",
  "#00A86B", "#4B0082", "#FF8C69", "#556B2F",
];
import type { WorkGroupDto } from "@/types/api";

const schema = z.object({
  name: z.string().min(1, "Le nom du groupe est requis"),
  description: z.string().optional(),
  color: z.string().optional(),
  leaderUsername: z.string().min(1, "L'identifiant est requis"),
  leaderFullName: z.string().min(1, "Le nom complet est requis"),
  leaderPassword: z.union([z.string().min(6, "Au moins 6 caractères"), z.literal("")]).optional(),
});

/** Memes regles que le serveur, a la creation comme au changement d'identifiant. */
const usernameField = z
  .string()
  .trim()
  .min(1, "L'identifiant est requis")
  .max(60, "60 caractères au maximum")
  .regex(/^[a-zA-Z0-9._-]+$/, "Lettres, chiffres, point, tiret ou tiret bas uniquement");

/**
 * Creation d'un compte. La direction n'a de sens que pour un chef de groupe : c'est elle qui
 * decide du canevas rempli, et le serveur refuse le rattachement pour les autres roles.
 */
const accountSchema = z
  .object({
    username: usernameField,
    fullName: z.string().min(1, "Le nom complet est requis"),
    role: z.enum(["ADMIN", "DIRECTEUR_GENERAL", "GROUP_LEADER"]),
    groupId: z.string().optional(),
    password: z.union([z.string().min(8, "Au moins 8 caractères"), z.literal("")]).optional(),
  })
  .refine((v) => v.role !== "GROUP_LEADER" || !!v.groupId, {
    path: ["groupId"],
    message: "La direction est requise pour un chef de groupe",
  });

type AccountFormValues = z.infer<typeof accountSchema>;

const usernameSchema = z.object({ username: usernameField });

type UsernameFormValues = z.infer<typeof usernameSchema>;

/** Reinitialisation : l'admin choisit le mot de passe et le transmet lui-meme au titulaire. */
const resetPasswordSchema = z
  .object({
    password: z.string().min(8, "Au moins 8 caractères").max(100, "100 caractères au maximum"),
    confirmation: z.string(),
  })
  .refine((v) => v.password === v.confirmation, {
    path: ["confirmation"],
    message: "Les deux saisies ne correspondent pas",
  });

type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>;

/** Compte vise par la reinitialisation : titulaire d'une direction, ou compte de la liste. */
type PasswordTarget = { kind: "leader" | "account"; id: number; username: string; label: string };

/** Mot a recopier pour confirmer un effacement : un clic ne suffit pas a tout perdre. */
const PURGE_CONFIRMATION_WORD = "EFFACER";

/** Effacement des saisies : une direction, ou toutes (groupId absent). */
type PurgeTarget = { groupId?: number; label: string };

const editSchema = z.object({
  name: z.string().min(1, "Le nom du groupe est requis"),
  description: z.string().optional(),
  color: z.string().optional(),
  leaderFullName: z.string().min(1, "Le nom complet est requis"),
});

export default function AdminGroupsPage() {
  const queryClient = useQueryClient();
  const { user, logout } = useCurrentUser();
  // Creation, edition, cycles, mots de passe, activation : administration technique,
  // fermee au DG cote serveur (SecurityConfig) autant qu'ici.
  const peutAdministrer = canAdminister(user?.role);
  // Second niveau : le DG seul ouvre les documents consolides a une direction.
  const peutApprouver = canApproveAsDg(user?.role);
  const [createOpen, setCreateOpen] = useState(false);
  const [editingGroup, setEditingGroup] = useState<WorkGroupDto | null>(null);
  const [newCredentials, setNewCredentials] = useState<{ username: string; password: string } | null>(null);
  const [exportingExcel, setExportingExcel] = useState(false);
  const [exportPeriodMonths, setExportPeriodMonths] = useState(4);
  const [archiveGroup, setArchiveGroup] = useState<WorkGroupDto | null>(null);
  const [accountOpen, setAccountOpen] = useState(false);
  const [renamingAccount, setRenamingAccount] = useState<UserAccount | null>(null);
  const [passwordTarget, setPasswordTarget] = useState<PasswordTarget | null>(null);
  const [purgeTarget, setPurgeTarget] = useState<PurgeTarget | null>(null);
  const [purgeConfirmation, setPurgeConfirmation] = useState("");

  async function handleExportExcel() {
    setExportingExcel(true);
    try {
      await downloadConsolidatedExcel(exportPeriodMonths);
    } catch (error) {
      toast.error(extractErrorMessage(error, "Échec de l'export Excel"));
    } finally {
      setExportingExcel(false);
    }
  }

  const { data: groups, isLoading } = useQuery({ queryKey: ["admin", "groups"], queryFn: listGroups });
  const usedColors = new Set((groups ?? []).map((g) => g.color?.toUpperCase()));
  const nextColor = DIRECTION_PALETTE.find((c) => !usedColors.has(c)) ?? DIRECTION_PALETTE[0];

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

  const {
    register: registerAccount,
    handleSubmit: handleAccountSubmit,
    reset: resetAccount,
    watch: watchAccount,
    formState: { errors: accountErrors },
  } = useForm<AccountFormValues>({
    resolver: zodResolver(accountSchema),
    defaultValues: { role: "GROUP_LEADER" },
  });

  const accountRole = watchAccount("role");

  const {
    register: registerUsername,
    handleSubmit: handleUsernameSubmit,
    reset: resetUsername,
    formState: { errors: usernameErrors },
  } = useForm<UsernameFormValues>({ resolver: zodResolver(usernameSchema) });

  const {
    register: registerResetPassword,
    handleSubmit: handleResetPasswordSubmit,
    reset: resetResetPassword,
    formState: { errors: resetPasswordErrors },
  } = useForm<ResetPasswordFormValues>({ resolver: zodResolver(resetPasswordSchema) });

  function openPasswordDialog(target: PasswordTarget) {
    setPasswordTarget(target);
    resetResetPassword({ password: "", confirmation: "" });
  }

  function openUsernameDialog(account: UserAccount) {
    setRenamingAccount(account);
    resetUsername({ username: account.username });
  }

  function openEditDialog(group: WorkGroupDto) {
    setEditingGroup(group);
    resetEdit({
      name: group.name,
      description: group.description ?? "",
      color: group.color ?? "#2563EB",
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
    mutationFn: ({ target, password }: { target: PasswordTarget; password: string }) =>
      target.kind === "leader" ? resetLeaderPassword(target.id, password) : resetUserPassword(target.id, password),
    onSuccess: (data) => {
      setPasswordTarget(null);
      toast.success(`Mot de passe de ${data.username} réinitialisé — communiquez-le à son titulaire`);
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec de la réinitialisation")),
  });

  const { data: accounts } = useQuery({
    queryKey: ["admin", "users"],
    queryFn: listUserAccounts,
    enabled: peutAdministrer,
  });

  const createAccountMutation = useMutation({
    mutationFn: (values: AccountFormValues) =>
      createUserAccount({
        username: values.username,
        fullName: values.fullName,
        role: values.role,
        groupId: values.role === "GROUP_LEADER" && values.groupId ? Number(values.groupId) : undefined,
        password: values.password ? values.password : undefined,
      }),
    onSuccess: (account) => {
      setAccountOpen(false);
      resetAccount({ role: "GROUP_LEADER" });
      // Mot de passe choisi par l'admin : rien a afficher, il le connait deja.
      if (account.temporaryPassword) {
        setNewCredentials({ username: account.username, password: account.temporaryPassword });
      } else {
        toast.success(`Compte ${account.username} créé`);
      }
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec de la création du compte")),
  });

  const changeUsernameMutation = useMutation({
    mutationFn: ({ id, username }: { id: number; username: string }) => updateUserUsername(id, username),
    onSuccess: (account) => {
      setRenamingAccount(null);
      // Les jetons de la session portent l'ancien identifiant : ils ne valent plus rien.
      if (account.id === user?.id) {
        toast.success(`Identifiant changé en ${account.username} — reconnectez-vous avec celui-ci`);
        logout();
        return;
      }
      toast.success(`Identifiant changé en ${account.username}`);
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "groups"] });
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec du changement d'identifiant")),
  });

  const validateAllMutation = useMutation({
    mutationFn: (groupId: number) => validateAllSubmitted(groupId),
    onSuccess: (result) => {
      if (result.validatedCount === 0) {
        toast.info("Aucune section en attente de validation pour cette direction");
      } else {
        toast.success(`${result.validatedCount} section(s) validée(s)`);
      }
      queryClient.invalidateQueries({ queryKey: ["admin"] });
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec de la validation en masse")),
  });

  function openPurgeDialog(target: PurgeTarget) {
    setPurgeTarget(target);
    setPurgeConfirmation("");
  }

  const purgeMutation = useMutation({
    mutationFn: (target: PurgeTarget) => purgeGroupData(target.groupId),
    onSuccess: (result) => {
      setPurgeTarget(null);
      toast.success(
        result.groupsCount > 1
          ? `Saisies effacées pour ${result.groupsCount} directions`
          : "Saisies de la direction effacées"
      );
      // Tableau de bord, soumissions, activité : tout ce qui lit les saisies est a recharger.
      queryClient.invalidateQueries();
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec de l'effacement des saisies")),
  });

  const newCycleMutation = useMutation({
    mutationFn: (groupId: number) => startNewCycle(groupId),
    onSuccess: (summary) => {
      toast.success(`Cycle ${summary.cycleNumber} archivé — nouvelle saisie démarrée pour la direction`);
      queryClient.invalidateQueries({ queryKey: ["admin", "groups"] });
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec du démarrage du nouveau cycle")),
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1>Groupes de travail</h1>
          <p className="text-[13px] text-muted-foreground mt-1">Départements participant au plan stratégique</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <NativeSelect
            value={String(exportPeriodMonths)}
            onChange={(e) => setExportPeriodMonths(Number(e.target.value))}
            className="w-40"
          >
            <option value="4">Période : 4 mois</option>
            <option value="1">Période : mensuel</option>
          </NativeSelect>
          <Button variant="secondary" onClick={handleExportExcel} loading={exportingExcel}>
            <FileDown className="h-4 w-4" /> Export Excel consolidé
          </Button>
        <Dialog open={createOpen} onOpenChange={setCreateOpen}>
          {peutAdministrer && (
            <DialogTrigger asChild>
              <Button variant="primary">
                <Plus className="h-4 w-4" /> Nouveau groupe
              </Button>
            </DialogTrigger>
          )}
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
              <div className="space-y-1.5">
                <Label>Couleur de la direction</Label>
                <input
                  {...register("color")}
                  type="color"
                  defaultValue={nextColor}
                  className="h-10 w-16 rounded-lg border border-border bg-card p-1"
                />
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
              <div key={g.id} className="flex flex-col sm:flex-row sm:items-center justify-between px-5 py-4 gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span
                      className="h-3 w-3 shrink-0 rounded-full border border-border/60"
                      style={{ backgroundColor: g.color ?? "transparent" }}
                      title={g.color ?? undefined}
                    />
                    <Link href={`/admin/groups/${g.id}/sections/S01`} className="text-[14px] font-medium text-foreground hover:text-primary-600">
                      {g.name}
                    </Link>
                    {!g.enabled && <span className="text-[11px] rounded-full bg-muted text-muted-foreground px-2 py-0.5">Désactivé</span>}
                    <span className="text-[11px] rounded-full bg-muted text-muted-foreground px-2 py-0.5">Cycle {g.currentCycle}</span>
                  </div>
                  <p className="text-[12px] text-muted-foreground mt-0.5">
                    {g.leaderFullName} · {g.leaderUsername} · Dernière activité : {g.lastActivityAt ? formatDateTime(g.lastActivityAt) : "—"}
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-4 sm:shrink-0">
                  <span className="text-[13px] font-semibold text-primary-600 w-12 text-right">{g.completionPercent}%</span>
                  {peutAdministrer && (
                    <Button variant="ghost" size="icon" title="Modifier le groupe" onClick={() => openEditDialog(g)}>
                      <Pencil className="h-4 w-4" />
                    </Button>
                  )}
                  {peutAdministrer && (
                  <AlertDialog>
                    <AlertDialogTrigger asChild>
                      <Button
                        variant="ghost"
                        size="icon"
                        title={
                          g.completionPercent === 100
                            ? "Démarrer un nouveau cycle"
                            : "Toutes les sections doivent être soumises (100%) pour démarrer un nouveau cycle"
                        }
                        disabled={g.completionPercent !== 100}
                      >
                        <RotateCcw className="h-4 w-4" />
                      </Button>
                    </AlertDialogTrigger>
                    <AlertDialogContent>
                      <AlertDialogHeader>
                        <AlertDialogTitle>Démarrer un nouveau cycle ?</AlertDialogTitle>
                        <AlertDialogDescription>
                          Le cycle {g.currentCycle} de « {g.name} » sera archivé — la saisie soumise reste consultable
                          ensuite dans les archives — puis toutes les sections seront remises à zéro pour une nouvelle
                          saisie (cycle {g.currentCycle + 1}).
                        </AlertDialogDescription>
                      </AlertDialogHeader>
                      <AlertDialogFooter>
                        <AlertDialogCancel>Annuler</AlertDialogCancel>
                        <AlertDialogAction onClick={() => newCycleMutation.mutate(g.id)}>
                          Démarrer le cycle {g.currentCycle + 1}
                        </AlertDialogAction>
                      </AlertDialogFooter>
                    </AlertDialogContent>
                  </AlertDialog>
                  )}
<Button variant="ghost" size="icon" title="Archives des cycles précédents" onClick={() => setArchiveGroup(g)}>
                    <Archive className="h-4 w-4" />
                  </Button>
                  {peutAdministrer && g.leaderUsername && (
                    <Button
                      variant="ghost"
                      size="icon"
                      title="Réinitialiser le mot de passe"
                      onClick={() =>
                        openPasswordDialog({
                          kind: "leader",
                          id: g.id,
                          username: g.leaderUsername!,
                          label: g.leaderFullName ?? g.leaderUsername!,
                        })
                      }
                    >
                      <KeyRound className="h-4 w-4" />
                    </Button>
                  )}
                  {/* Le DG ne valide que depuis l'onglet « À valider ». */}
                  {!peutApprouver && (
                    <AlertDialog>
                      <AlertDialogTrigger asChild>
                        <Button variant="secondary" size="sm" title="Valider toutes les sections soumises">
                          <CheckCheck className="h-4 w-4" /> Tout valider
                        </Button>
                      </AlertDialogTrigger>
                      <AlertDialogContent>
                        <AlertDialogHeader>
                          <AlertDialogTitle>Valider toutes les sections soumises ?</AlertDialogTitle>
                          <AlertDialogDescription>
                            Toutes les sections de {g.name} au statut « Soumis » passeront à « Validé ».{" "}
                            Elles n&apos;entreront dans les documents consolidés qu&apos;une fois approuvées par la Direction
                            Générale.{" "}
                            Les brouillons en cours et les sections renvoyées pour révision ne sont pas touchés.
                          </AlertDialogDescription>
                        </AlertDialogHeader>
                        <AlertDialogFooter>
                          <AlertDialogCancel>Annuler</AlertDialogCancel>
                          <AlertDialogAction onClick={() => validateAllMutation.mutate(g.id)}>
                            Tout valider
                          </AlertDialogAction>
                        </AlertDialogFooter>
                      </AlertDialogContent>
                    </AlertDialog>
                  )}
                  <Button
                    variant="ghost"
                    size="icon"
                    title="Plan Stratégique Sectoriel — PDF"
                    onClick={() => downloadGroupPdf(g.id).catch((error) => toast.error(extractErrorMessage(error, "Échec de l'export PDF")))}
                  >
                    <FileDown className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    title="Plan Stratégique Sectoriel — Word"
                    onClick={() => downloadGroupWord(g.id).catch((error) => toast.error(extractErrorMessage(error, "Échec de l'export Word")))}
                  >
                    <FileText className="h-4 w-4" />
                  </Button>
                  {peutAdministrer && (
                    <Button
                      variant="ghost"
                      size="icon"
                      title={g.enabled ? "Désactiver le groupe" : "Activer le groupe"}
                      onClick={() => toggleMutation.mutate({ id: g.id, enabled: !g.enabled })}
                    >
                      <Power className={g.enabled ? "h-4 w-4 text-primary-500" : "h-4 w-4 text-muted-foreground"} />
                    </Button>
                  )}
                  {peutAdministrer && (
                    <Button
                      variant="ghost"
                      size="icon"
                      title="Effacer toutes les saisies de la direction"
                      onClick={() => openPurgeDialog({ groupId: g.id, label: g.name })}
                    >
                      <Trash2 className="h-4 w-4 text-red-500 dark:text-red-400" />
                    </Button>
                  )}
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
              <Label>Couleur de la direction</Label>
              <input
                {...registerEdit("color")}
                type="color"
                className="h-10 w-16 rounded-lg border border-border bg-card p-1"
              />
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

      {peutAdministrer && (
        <Card>
          <CardHeader className="flex flex-row items-center justify-between gap-3">
            <CardTitle>Comptes utilisateurs</CardTitle>
            <Dialog open={accountOpen} onOpenChange={setAccountOpen}>
              <DialogTrigger asChild>
                <Button variant="primary" size="sm">
                  <UserPlus className="h-4 w-4" /> Créer un compte
                </Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>Créer un compte</DialogTitle>
                </DialogHeader>
                <form
                  onSubmit={handleAccountSubmit((values) => createAccountMutation.mutate(values))}
                  className="space-y-4"
                >
                  <div className="space-y-1.5">
                    <Label>Rôle</Label>
                    <NativeSelect {...registerAccount("role")}>
                      <option value="GROUP_LEADER">Chef de groupe — remplit le canevas d&apos;une direction</option>
                      <option value="ADMIN">Administrateur — pilotage et administration technique</option>
                      <option value="DIRECTEUR_GENERAL">
                        Direction Générale — consulte et approuve les documents
                      </option>
                    </NativeSelect>
                  </div>

                  {accountRole === "GROUP_LEADER" && (
                    <div className="space-y-1.5">
                      <Label>Direction</Label>
                      <NativeSelect {...registerAccount("groupId")} defaultValue="">
                        <option value="">Choisir une direction…</option>
                        {(groups ?? []).map((g) => (
                          <option key={g.id} value={g.id}>
                            {g.name}
                          </option>
                        ))}
                      </NativeSelect>
                      {accountErrors.groupId && (
                        <p className="text-[13px] text-accent-700 dark:text-accent-300">
                          {accountErrors.groupId.message}
                        </p>
                      )}
                      <p className="text-[12px] text-muted-foreground">
                        Le compte travaillera sur le canevas de cette direction, aux côtés de son chef de groupe.
                      </p>
                    </div>
                  )}

                  <div className="space-y-1.5">
                    <Label>Nom complet</Label>
                    <Input {...registerAccount("fullName")} error={!!accountErrors.fullName} />
                    {accountErrors.fullName && (
                      <p className="text-[13px] text-accent-700 dark:text-accent-300">
                        {accountErrors.fullName.message}
                      </p>
                    )}
                  </div>

                  <div className="space-y-1.5">
                    <Label>Identifiant</Label>
                    <Input {...registerAccount("username")} error={!!accountErrors.username} placeholder="p.diop" />
                    {accountErrors.username && (
                      <p className="text-[13px] text-accent-700 dark:text-accent-300">
                        {accountErrors.username.message}
                      </p>
                    )}
                  </div>

                  <div className="space-y-1.5">
                    <Label>Mot de passe (optionnel)</Label>
                    <Input type="text" {...registerAccount("password")} error={!!accountErrors.password} />
                    {accountErrors.password ? (
                      <p className="text-[13px] text-accent-700 dark:text-accent-300">
                        {accountErrors.password.message}
                      </p>
                    ) : (
                      <p className="text-[12px] text-muted-foreground">
                        Laissé vide, un mot de passe est généré et affiché une seule fois. Dans les deux cas, la
                        personne devra le remplacer à sa première connexion.
                      </p>
                    )}
                  </div>

                  <DialogFooter>
                    <Button type="submit" variant="primary" loading={createAccountMutation.isPending}>
                      Créer le compte
                    </Button>
                  </DialogFooter>
                </form>
              </DialogContent>
            </Dialog>
          </CardHeader>
          <CardContent className="space-y-3">
            <p className="text-[13px] text-muted-foreground">
              Tous les accès à l&apos;application, directions comprises. Le mot de passe généré ne s&apos;affiche
              qu&apos;une seule fois ; son titulaire le remplace à sa première connexion.
            </p>
            <div className="divide-y divide-border">
              {(accounts ?? []).map((account) => (
                <div key={account.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
                  <div className="min-w-0">
                    <p className="text-[14px] font-medium text-foreground">
                      {account.fullName}
                      <span className="ml-2 rounded-full bg-muted px-2 py-0.5 text-[11px] font-normal text-muted-foreground">
                        {account.roleLabel}
                        {account.groupName ? ` · ${account.groupName}` : ""}
                      </span>
                      {account.mustChangePassword && (
                        <span className="ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-normal text-amber-800 dark:bg-amber-500/20 dark:text-amber-300">
                          Mot de passe à changer
                        </span>
                      )}
                    </p>
                    <p className="text-[12px] text-muted-foreground">
                      {account.username}
                      {account.lastLoginAt
                        ? ` · Dernière connexion : ${formatDateTime(account.lastLoginAt)}`
                        : " · Jamais connecté"}
                    </p>
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <Button variant="secondary" size="sm" onClick={() => openUsernameDialog(account)}>
                      <Pencil className="h-4 w-4" /> Modifier l&apos;identifiant
                    </Button>
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() =>
                        openPasswordDialog({
                          kind: "account",
                          id: account.id,
                          username: account.username,
                          label: account.fullName,
                        })
                      }
                    >
                      <KeyRound className="h-4 w-4" /> Réinitialiser le mot de passe
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {peutAdministrer && (
        <Card>
          <CardHeader>
            <CardTitle>Données d&apos;essai</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <p className="text-[13px] text-muted-foreground">
              Efface les saisies de toutes les directions pour repartir d&apos;une base propre avant le lancement
              réel. Les directions, les comptes et les textes du plan stratégique sont conservés. Pour une seule
              direction, utilisez l&apos;icône corbeille sur sa ligne.
            </p>
            <Button
              variant="secondary"
              className="sm:shrink-0"
              onClick={() => openPurgeDialog({ label: "toutes les directions" })}
            >
              <Trash2 className="h-4 w-4 text-red-500 dark:text-red-400" /> Effacer toutes les saisies
            </Button>
          </CardContent>
        </Card>
      )}

      <Dialog open={!!purgeTarget} onOpenChange={(open) => !open && setPurgeTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Effacer les saisies — {purgeTarget?.label}</DialogTitle>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (purgeTarget && purgeConfirmation.trim() === PURGE_CONFIRMATION_WORD) {
                purgeMutation.mutate(purgeTarget);
              }
            }}
            className="space-y-4"
          >
            <p className="text-[13px] text-muted-foreground">
              Seront supprimés définitivement, sans archive : le contenu de toutes les sections, leur historique
              de versions, les cycles archivés et le journal d&apos;activité
              {purgeTarget?.groupId === undefined ? " de chaque direction" : " de la direction"}. Chaque section
              repart à « non commencée », au cycle 1. Les directions, les comptes et les textes du plan
              stratégique ne sont pas touchés.
            </p>
            <div className="space-y-1.5">
              <Label required>
                Tapez {PURGE_CONFIRMATION_WORD} pour confirmer
              </Label>
              <Input
                autoComplete="off"
                value={purgeConfirmation}
                onChange={(e) => setPurgeConfirmation(e.target.value)}
              />
            </div>
            <DialogFooter>
              <Button
                type="submit"
                variant="primary"
                loading={purgeMutation.isPending}
                disabled={purgeConfirmation.trim() !== PURGE_CONFIRMATION_WORD}
              >
                <Trash2 className="h-4 w-4" /> Effacer définitivement
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={!!renamingAccount} onOpenChange={(open) => !open && setRenamingAccount(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Modifier l&apos;identifiant</DialogTitle>
          </DialogHeader>
          <form
            onSubmit={handleUsernameSubmit((values) => {
              if (!renamingAccount) return;
              if (values.username === renamingAccount.username) {
                setRenamingAccount(null);
                return;
              }
              changeUsernameMutation.mutate({ id: renamingAccount.id, username: values.username });
            })}
            className="space-y-4"
          >
            <p className="text-[13px] text-muted-foreground">
              {renamingAccount?.fullName} se connectera désormais avec ce nouvel identifiant ; son mot de passe ne
              change pas. Ses sessions ouvertes seront fermées
              {renamingAccount && renamingAccount.id === user?.id ? ", la vôtre comprise" : ""}.
            </p>
            <div className="space-y-1.5">
              <Label required>Identifiant</Label>
              <Input {...registerUsername("username")} error={!!usernameErrors.username} />
              {usernameErrors.username && (
                <p className="text-[13px] text-accent-700 dark:text-accent-300">{usernameErrors.username.message}</p>
              )}
            </div>
            <DialogFooter>
              <Button type="submit" variant="primary" loading={changeUsernameMutation.isPending}>
                Enregistrer
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={!!passwordTarget} onOpenChange={(open) => !open && setPasswordTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Réinitialiser le mot de passe</DialogTitle>
          </DialogHeader>
          <form
            onSubmit={handleResetPasswordSubmit((values) => {
              if (!passwordTarget) return;
              resetPasswordMutation.mutate({ target: passwordTarget, password: values.password });
            })}
            className="space-y-4"
          >
            <p className="text-[13px] text-muted-foreground">
              Saisissez le nouveau mot de passe de {passwordTarget?.label} ({passwordTarget?.username}), puis
              communiquez-le lui. L&apos;ancien cessera immédiatement de fonctionner, et la personne devra choisir le
              sien à sa prochaine connexion.
            </p>
            <div className="space-y-1.5">
              <Label required>Nouveau mot de passe</Label>
              <Input
                type="text"
                autoComplete="off"
                {...registerResetPassword("password")}
                error={!!resetPasswordErrors.password}
              />
              {resetPasswordErrors.password && (
                <p className="text-[13px] text-accent-700 dark:text-accent-300">
                  {resetPasswordErrors.password.message}
                </p>
              )}
            </div>
            <div className="space-y-1.5">
              <Label required>Confirmation</Label>
              <Input
                type="text"
                autoComplete="off"
                {...registerResetPassword("confirmation")}
                error={!!resetPasswordErrors.confirmation}
              />
              {resetPasswordErrors.confirmation && (
                <p className="text-[13px] text-accent-700 dark:text-accent-300">
                  {resetPasswordErrors.confirmation.message}
                </p>
              )}
            </div>
            <DialogFooter>
              <Button type="submit" variant="primary" loading={resetPasswordMutation.isPending}>
                Réinitialiser
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

      {archiveGroup && (
        <CycleArchivePanel
          open={!!archiveGroup}
          onOpenChange={(open) => !open && setArchiveGroup(null)}
          title={`Cycles archivés — ${archiveGroup.name}`}
          queryKeyPrefix={["admin", "groups", archiveGroup.id]}
          listCycles={() => listGroupCycles(archiveGroup.id)}
          listSections={(cycleNumber) => getGroupCycleSections(archiveGroup.id, cycleNumber)}
          getSectionContent={(cycleNumber, code) => getGroupCycleSectionContent(archiveGroup.id, cycleNumber, code)}
        />
      )}
    </div>
  );
}
