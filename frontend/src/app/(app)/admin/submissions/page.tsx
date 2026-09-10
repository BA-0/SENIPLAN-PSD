"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowDown, ArrowUp, ArrowUpDown, Eye, Inbox, RotateCcw, Send, Search as SearchIcon, ShieldCheck } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { Button } from "@/components/ui/button";
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
import { KpiCard } from "@/components/kpi-card";
import { dgApproveAllPending, dgApproveSelection, getAdminSubmissions } from "@/lib/api/admin";
import { extractErrorMessage } from "@/lib/api-client";
import { canApproveAsDg } from "@/lib/roles";
import { useCurrentUser } from "@/hooks/use-current-user";
import { formatDateTime } from "@/lib/utils";
import { groupSectionsByPart } from "@/lib/section-groups";
import type { SubmissionSummaryDto } from "@/types/api";
import type { SectionStatus } from "@/types/common";

const STATUS_OPTIONS: { value: SectionStatus; label: string }[] = [
  { value: "NOT_STARTED", label: "Non commencé" },
  { value: "IN_PROGRESS", label: "En cours" },
  { value: "SUBMITTED", label: "Soumis" },
  { value: "VALIDATED", label: "Validé" },
  { value: "REVISION_REQUESTED", label: "À réviser" },
];

type SortKey = "groupName" | "sectionOrder" | "status" | "submittedAt" | "validatedAt" | "dgApprovedAt" | "lastActivityAt";

/**
 * L'approbation du DG est un second axe, independant du statut : une section validee peut
 * attendre son arbitrage, et c'est cette attente qui la tient hors des documents consolides.
 */
type DgFilter = "" | "PENDING" | "APPROVED";

const PAGE_SIZE = 25;

/**
 * Une soumission qui attend l'arbitrage du DG : validee par le comite de pilotage, pas encore
 * approuvee. C'est le seul cas ou l'approbation en masse a un effet — le serveur ignore le
 * reste, l'interface ne propose donc de cocher que celles-la.
 */
function attendLaDg(s: SubmissionSummaryDto): boolean {
  return s.status === "VALIDATED" && !s.dgApprovedAt;
}

/** Identifiant de ligne : une section n'existe qu'une fois par direction. */
function cleDe(s: SubmissionSummaryDto): string {
  return `${s.groupId}::${s.sectionCode}`;
}

export default function AdminSubmissionsPage() {
  const searchParams = useSearchParams();
  const initialStatus = searchParams.get("status") as SectionStatus | null;

  const [statusFilter, setStatusFilter] = useState<SectionStatus | "">(initialStatus ?? "");
  const [groupFilter, setGroupFilter] = useState<number | "">("");
  const [sectionFilter, setSectionFilter] = useState<string>("");
  const [dgFilter, setDgFilter] = useState<DgFilter>("");
  const [search, setSearch] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [sortKey, setSortKey] = useState<SortKey>("submittedAt");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const [page, setPage] = useState(0);
  const [selection, setSelection] = useState<Set<string>>(() => new Set());

  const { user } = useCurrentUser();
  const peutApprouver = canApproveAsDg(user?.role);
  const queryClient = useQueryClient();

  /**
   * Ce que le DG vient d'approuver depuis cet ecran. Le filtre « En attente de la DG » les
   * exclurait aussitot : la liste se viderait sous les yeux de celui qui vient de cliquer,
   * sans qu'il puisse verifier ce qu'il a fait. On les garde affichees, grisees, jusqu'a ce
   * qu'il change de filtre ou recharge.
   */
  const [recemmentApprouvees, setRecemmentApprouvees] = useState<Set<string>>(new Set());

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "submissions"],
    queryFn: getAdminSubmissions,
    refetchInterval: 15_000,
  });

  /**
   * Approbation en masse. Sans filtre, on passe par la route « tout ce qui attend » : le DG
   * n'a alors rien a designer, et le serveur travaille sur son propre etat plutot que sur une
   * liste vieille de quelques secondes. Des qu'un filtre restreint la liste, on envoie au
   * contraire les lignes exactes, pour ne jamais approuver ce que l'ecran ne montrait pas.
   */
  const approuverMutation = useMutation({
    mutationFn: (cibles: SubmissionSummaryDto[] | "TOUT") =>
      cibles === "TOUT"
        ? dgApproveAllPending()
        : dgApproveSelection(cibles.map((s) => ({ groupId: s.groupId, sectionCode: s.sectionCode }))),
    onMutate: (cibles) => {
      const touchees = cibles === "TOUT" ? (data ?? []).filter(attendLaDg) : cibles;
      setRecemmentApprouvees((precedent) => {
        const suivant = new Set(precedent);
        touchees.forEach((s) => suivant.add(cleDe(s)));
        return suivant;
      });
    },
    onSuccess: (result) => {
      if (result.approvedCount === 0) {
        toast.info("Aucune section n'attendait l'approbation de la Direction Générale");
      } else {
        toast.success(
          `${result.approvedCount} section(s) approuvée(s) — elles entrent dans les documents consolidés`
        );
      }
      setSelection(new Set());
      queryClient.invalidateQueries({ queryKey: ["admin"] });
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec de l'approbation en masse")),
  });

  /**
   * Approbation d'une seule ligne. Le DG relit souvent section par section : l'obliger a
   * cocher puis confirmer, ou a ouvrir la section pour approuver, ajoute deux gestes a une
   * decision qu'il a deja prise en lisant la ligne.
   */
  const approuverLigne = useMutation({
    mutationFn: (s: SubmissionSummaryDto) =>
      dgApproveSelection([{ groupId: s.groupId, sectionCode: s.sectionCode }]),
    onMutate: (s) => {
      setRecemmentApprouvees((precedent) => new Set(precedent).add(cleDe(s)));
    },
    onSuccess: (_result, s) => {
      toast.success(`${s.sectionCode} — ${s.groupName} approuvée`);
      queryClient.invalidateQueries({ queryKey: ["admin"] });
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec de l'approbation")),
  });

  const groups = useMemo(() => {
    const map = new Map<number, string>();
    (data ?? []).forEach((s) => map.set(s.groupId, s.groupName));
    return [...map.entries()].sort((a, b) => a[1].localeCompare(b[1]));
  }, [data]);

  // Le filtre porte sur les 23 sections du canevas : liste trop longue a plat,
  // on la presente par partie (optgroup).
  const sectionParts = useMemo(() => {
    const map = new Map<string, string>();
    (data ?? []).forEach((s) => map.set(s.sectionCode, s.sectionTitle));
    const flat = [...map.entries()]
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([code, title]) => ({ code, title }));
    return groupSectionsByPart(flat);
  }, [data]);

  function toggleSort(key: SortKey) {
    if (sortKey === key) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir(key === "groupName" || key === "sectionOrder" ? "asc" : "desc");
    }
  }

  // Une selection ne survit pas a un changement de filtre : approuver en masse des lignes que
  // l'ecran ne montre plus serait exactement l'erreur que ce raccourci doit eviter.
  useEffect(() => {
    setSelection(new Set());
  }, [statusFilter, groupFilter, sectionFilter, dgFilter, search, dateFrom, dateTo]);

  function resetFilters() {
    setStatusFilter("");
    setGroupFilter("");
    setSectionFilter("");
    setDgFilter("");
    setSearch("");
    setDateFrom("");
    setDateTo("");
    setPage(0);
  }

  const hasActiveFilters =
    statusFilter !== "" ||
    groupFilter !== "" ||
    sectionFilter !== "" ||
    dgFilter !== "" ||
    search.trim() !== "" ||
    dateFrom !== "" ||
    dateTo !== "";

  const filtered = useMemo(() => {
    if (!data) return [];
    const q = search.trim().toLowerCase();
    const from = dateFrom ? new Date(dateFrom) : null;
    const to = dateTo ? new Date(dateTo + "T23:59:59") : null;

    let rows = data.filter((s) => {
      if (statusFilter !== "" && s.status !== statusFilter) return false;
      if (groupFilter !== "" && s.groupId !== groupFilter) return false;
      if (sectionFilter !== "" && s.sectionCode !== sectionFilter) return false;
      if (dgFilter === "APPROVED" && !s.dgApprovedAt) return false;
      if (
        dgFilter === "PENDING" &&
        !(s.status === "VALIDATED" && !s.dgApprovedAt) &&
        !recemmentApprouvees.has(cleDe(s))
      ) {
        return false;
      }
      if (q) {
        const haystack = `${s.groupName} ${s.leaderFullName ?? ""} ${s.sectionCode} ${s.sectionTitle}`.toLowerCase();
        if (!haystack.includes(q)) return false;
      }
      if (from || to) {
        if (!s.submittedAt) return false;
        const submitted = new Date(s.submittedAt);
        if (from && submitted < from) return false;
        if (to && submitted > to) return false;
      }
      return true;
    });

    rows = rows.sort((a, b) => {
      let cmp = 0;
      switch (sortKey) {
        case "groupName":
          cmp = a.groupName.localeCompare(b.groupName);
          break;
        case "sectionOrder":
          cmp = a.sectionOrder - b.sectionOrder;
          break;
        case "status":
          cmp = a.status.localeCompare(b.status);
          break;
        case "submittedAt":
          cmp = (a.submittedAt ?? "").localeCompare(b.submittedAt ?? "");
          break;
        case "validatedAt":
          cmp = (a.validatedAt ?? "").localeCompare(b.validatedAt ?? "");
          break;
        case "dgApprovedAt":
          cmp = (a.dgApprovedAt ?? "").localeCompare(b.dgApprovedAt ?? "");
          break;
        case "lastActivityAt":
          cmp = (a.lastActivityAt ?? "").localeCompare(b.lastActivityAt ?? "");
          break;
      }
      return sortDir === "asc" ? cmp : -cmp;
    });

    return rows;
  }, [
    data,
    statusFilter,
    groupFilter,
    sectionFilter,
    dgFilter,
    search,
    dateFrom,
    dateTo,
    sortKey,
    sortDir,
    recemmentApprouvees,
  ]);

  const kpis = useMemo(() => {
    const source = data ?? [];
    return {
      submitted: source.filter((s) => s.status === "SUBMITTED").length,
      awaitingDg: source.filter((s) => s.status === "VALIDATED" && !s.dgApprovedAt).length,
      dgApproved: source.filter((s) => s.dgApprovedAt).length,
      revision: source.filter((s) => s.status === "REVISION_REQUESTED").length,
    };
  }, [data]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages - 1);
  const paginated = filtered.slice(currentPage * PAGE_SIZE, currentPage * PAGE_SIZE + PAGE_SIZE);

  const enAttenteFiltrees = useMemo(() => filtered.filter(attendLaDg), [filtered]);
  const enAttenteSurLaPage = useMemo(() => paginated.filter(attendLaDg), [paginated]);
  const selectionnees = useMemo(
    () => enAttenteFiltrees.filter((s) => selection.has(cleDe(s))),
    [enAttenteFiltrees, selection]
  );
  const pageEntiereCochee =
    enAttenteSurLaPage.length > 0 && enAttenteSurLaPage.every((s) => selection.has(cleDe(s)));
  const toutesLesFiltreesCochees =
    enAttenteFiltrees.length > 0 && selectionnees.length === enAttenteFiltrees.length;

  function basculerLigne(s: SubmissionSummaryDto) {
    setSelection((precedente) => {
      const suivante = new Set(precedente);
      const cle = cleDe(s);
      if (suivante.has(cle)) {
        suivante.delete(cle);
      } else {
        suivante.add(cle);
      }
      return suivante;
    });
  }

  function basculerLaPage() {
    setSelection((precedente) => {
      const suivante = new Set(precedente);
      enAttenteSurLaPage.forEach((s) => (pageEntiereCochee ? suivante.delete(cleDe(s)) : suivante.add(cleDe(s))));
      return suivante;
    });
  }

  return (
    <div className="space-y-6">
      <div>
        <h1>Soumissions</h1>
        <p className="text-[13px] text-muted-foreground mt-1">
          Toutes les sections soumises par les groupes, à filtrer et à examiner. Une section validée n&apos;entre dans
          les documents de consolidation et de synthèse qu&apos;une fois approuvée par la Direction Générale.
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <KpiCard icon={Send} label="En attente de validation" value={kpis.submitted} color="blue" />
        <KpiCard icon={Inbox} label="En attente de la DG" value={kpis.awaitingDg} color="amber" />
        <KpiCard icon={ShieldCheck} label="Approuvées par la DG" value={kpis.dgApproved} color="emerald" />
        <KpiCard icon={RotateCcw} label="À réviser" value={kpis.revision} color="orange" />
      </div>

      <Card>
        <CardContent className="pt-5 space-y-4">
          <div className="flex flex-wrap items-end gap-4">
            <div className="space-y-1.5 flex-1 min-w-[220px]">
              <p className="text-[13px] font-medium text-foreground/90">Recherche</p>
              <div className="relative">
                <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  value={search}
                  onChange={(e) => {
                    setSearch(e.target.value);
                    setPage(0);
                  }}
                  placeholder="Groupe, chef de groupe, section…"
                  className="pl-9"
                />
              </div>
            </div>
            <div className="space-y-1.5">
              <p className="text-[13px] font-medium text-foreground/90">Soumis depuis le</p>
              <Input
                type="date"
                value={dateFrom}
                onChange={(e) => {
                  setDateFrom(e.target.value);
                  setPage(0);
                }}
                className="w-40"
              />
            </div>
            <div className="space-y-1.5">
              <p className="text-[13px] font-medium text-foreground/90">Jusqu'au</p>
              <Input
                type="date"
                value={dateTo}
                onChange={(e) => {
                  setDateTo(e.target.value);
                  setPage(0);
                }}
                className="w-40"
              />
            </div>
            <Button
              variant={statusFilter === "SUBMITTED" ? "primary" : "secondary"}
              size="sm"
              onClick={() => {
                setStatusFilter(statusFilter === "SUBMITTED" ? "" : "SUBMITTED");
                setPage(0);
              }}
            >
              <Send className="h-3.5 w-3.5" /> En attente uniquement
            </Button>
            <Button
              variant={dgFilter === "PENDING" ? "primary" : "secondary"}
              size="sm"
              onClick={() => {
                setDgFilter(dgFilter === "PENDING" ? "" : "PENDING");
                setPage(0);
              }}
            >
              <ShieldCheck className="h-3.5 w-3.5" /> En attente de la DG
            </Button>
            {hasActiveFilters && (
              <Button variant="ghost" size="sm" onClick={resetFilters}>
                <RotateCcw className="h-3.5 w-3.5" /> Réinitialiser les filtres
              </Button>
            )}
          </div>

          <div className="flex flex-wrap gap-4">
            <div className="space-y-1.5 w-56">
              <p className="text-[13px] font-medium text-foreground/90">Statut</p>
              <NativeSelect
                value={statusFilter}
                onChange={(e) => {
                  setStatusFilter(e.target.value as SectionStatus | "");
                  setPage(0);
                }}
              >
                <option value="">Tous les statuts</option>
                {STATUS_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </NativeSelect>
            </div>
            <div className="space-y-1.5 w-56">
              <p className="text-[13px] font-medium text-foreground/90">Groupe</p>
              <NativeSelect
                value={groupFilter}
                onChange={(e) => {
                  setGroupFilter(e.target.value === "" ? "" : Number(e.target.value));
                  setPage(0);
                }}
              >
                <option value="">Tous les groupes</option>
                {groups.map(([id, name]) => (
                  <option key={id} value={id}>
                    {name}
                  </option>
                ))}
              </NativeSelect>
            </div>
            <div className="space-y-1.5 w-56">
              <p className="text-[13px] font-medium text-foreground/90">Approbation DG</p>
              <NativeSelect
                value={dgFilter}
                onChange={(e) => {
                  setDgFilter(e.target.value as DgFilter);
                  setPage(0);
                }}
              >
                <option value="">Toutes</option>
                <option value="PENDING">Validées, en attente de la DG</option>
                <option value="APPROVED">Approuvées par la DG</option>
              </NativeSelect>
            </div>
            <div className="space-y-1.5 w-56">
              <p className="text-[13px] font-medium text-foreground/90">Section</p>
              <NativeSelect
                value={sectionFilter}
                onChange={(e) => {
                  setSectionFilter(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">Toutes les sections</option>
                {sectionParts.map(({ part, sections: partSections }) => (
                  <optgroup key={part.id} label={part.numeral ? `Partie ${part.numeral} — ${part.title}` : part.title}>
                    {partSections.map((s) => (
                      <option key={s.code} value={s.code}>
                        {s.code} — {s.title}
                      </option>
                    ))}
                  </optgroup>
                ))}
              </NativeSelect>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between gap-4">
          <CardTitle>
            {filtered.length} soumission{filtered.length > 1 ? "s" : ""}
          </CardTitle>
          {peutApprouver ? (
            <div className="flex items-center gap-2">
              {selectionnees.length > 0 ? (
                <>
                  <span className="text-[13px] text-muted-foreground">
                    {selectionnees.length} sélectionnée{selectionnees.length > 1 ? "s" : ""}
                  </span>
                  <Button variant="ghost" size="sm" onClick={() => setSelection(new Set())}>
                    Effacer
                  </Button>
                  <ApprobationEnMasse
                    label={`Approuver la sélection (${selectionnees.length})`}
                    titre="Approuver les sections sélectionnées ?"
                    description={`${selectionnees.length} section(s) validée(s) par le comité de pilotage seront approuvées et entreront dans le Document de consolidation, la Note de synthèse et le Plan Stratégique de SENICO. Les autres lignes ne sont pas touchées.`}
                    enCours={approuverMutation.isPending}
                    onConfirm={() => approuverMutation.mutate(selectionnees)}
                  />
                </>
              ) : (
                <ApprobationEnMasse
                  label={
                    enAttenteFiltrees.length === 0
                      ? "Tout est approuvé"
                      : hasActiveFilters
                        ? `Approuver les ${enAttenteFiltrees.length} en attente (filtrées)`
                        : `Tout approuver (${enAttenteFiltrees.length})`
                  }
                  titre={
                    hasActiveFilters
                      ? "Approuver les sections filtrées en attente ?"
                      : "Approuver tout ce qui attend la Direction Générale ?"
                  }
                  description={
                    hasActiveFilters
                      ? `Les ${enAttenteFiltrees.length} section(s) que ces filtres affichent et qui attendent encore votre arbitrage seront approuvées, toutes directions confondues. Elles entreront dans le Document de consolidation, la Note de synthèse et le Plan Stratégique de SENICO.`
                      : `Les ${enAttenteFiltrees.length} section(s) validées par le comité de pilotage qui attendent encore votre arbitrage seront approuvées, toutes directions confondues, et entreront dans le Document de consolidation, la Note de synthèse et le Plan Stratégique de SENICO. Les sections non validées, en cours ou renvoyées pour révision ne sont pas touchées.`
                  }
                  enCours={approuverMutation.isPending}
                  desactive={enAttenteFiltrees.length === 0}
                  onConfirm={() => approuverMutation.mutate(hasActiveFilters ? enAttenteFiltrees : "TOUT")}
                />
              )}
            </div>
          ) : (
            // L'admin pilote la campagne mais n'approuve pas : sans cette mention, il cherche
            // un bouton que l'ecran ne lui montrera jamais.
            <p className="flex items-center gap-1.5 text-[13px] text-muted-foreground">
              <ShieldCheck className="h-3.5 w-3.5" /> Approbation réservée à la Direction Générale
            </p>
          )}
        </CardHeader>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="h-64 bg-muted rounded-xl animate-pulse m-5" />
          ) : filtered.length === 0 ? (
            <p className="text-center text-muted-foreground py-16">Aucune soumission ne correspond à ces filtres</p>
          ) : (
            <>
              {peutApprouver && pageEntiereCochee && !toutesLesFiltreesCochees && (
                <div className="flex flex-wrap items-center gap-2 px-5 py-2.5 border-b border-border/60 bg-amber-50/70 dark:bg-amber-500/10 text-[13px]">
                  <span>
                    Les {enAttenteSurLaPage.length} section{enAttenteSurLaPage.length > 1 ? "s" : ""} en attente de
                    cette page sont sélectionnées.
                  </span>
                  <Button
                    variant="link"
                    size="sm"
                    onClick={() => setSelection(new Set(enAttenteFiltrees.map(cleDe)))}
                  >
                    Sélectionner les {enAttenteFiltrees.length} en attente de toute la liste
                  </Button>
                </div>
              )}
              <Table>
                <TableHeader>
                  <TableRow>
                    {peutApprouver && (
                      <TableHead className="w-10">
                        <input
                          type="checkbox"
                          checked={pageEntiereCochee}
                          disabled={enAttenteSurLaPage.length === 0}
                          onChange={basculerLaPage}
                          aria-label="Sélectionner les sections de cette page en attente de la Direction Générale"
                          className="h-4 w-4 rounded border-slate-300 text-primary-500 focus:ring-primary-500/40"
                        />
                      </TableHead>
                    )}
                    <SortableHead label="Groupe" sortKey="groupName" active={sortKey} dir={sortDir} onClick={toggleSort} />
                    <SortableHead label="Section" sortKey="sectionOrder" active={sortKey} dir={sortDir} onClick={toggleSort} />
                    <SortableHead label="Statut" sortKey="status" active={sortKey} dir={sortDir} onClick={toggleSort} />
                    <TableHead>Version</TableHead>
                    <SortableHead label="Soumis le" sortKey="submittedAt" active={sortKey} dir={sortDir} onClick={toggleSort} />
                    <SortableHead label="Validé le" sortKey="validatedAt" active={sortKey} dir={sortDir} onClick={toggleSort} />
                    <SortableHead label="Approuvé DG" sortKey="dgApprovedAt" active={sortKey} dir={sortDir} onClick={toggleSort} />
                    <SortableHead label="Dernière activité" sortKey="lastActivityAt" active={sortKey} dir={sortDir} onClick={toggleSort} />
                    <TableHead>Commentaire admin</TableHead>
                    <TableHead />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {paginated.map((s) => (
                    <SubmissionRow
                      key={`${s.groupId}-${s.sectionId}`}
                      submission={s}
                      selectable={peutApprouver}
                      selected={selection.has(cleDe(s))}
                      onToggle={() => basculerLigne(s)}
                      peutApprouver={peutApprouver}
                      approbationEnCours={approuverLigne.isPending}
                      onApprouver={() => approuverLigne.mutate(s)}
                    />
                  ))}
                </TableBody>
              </Table>
              {totalPages > 1 && (
                <div className="flex items-center justify-between px-5 py-3 border-t border-border/60">
                  <p className="text-[13px] text-muted-foreground">
                    Page {currentPage + 1} / {totalPages}
                  </p>
                  <div className="flex gap-2">
                    <Button variant="secondary" size="sm" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)}>
                      Précédent
                    </Button>
                    <Button
                      variant="secondary"
                      size="sm"
                      disabled={currentPage >= totalPages - 1}
                      onClick={() => setPage(currentPage + 1)}
                    >
                      Suivant
                    </Button>
                  </div>
                </div>
              )}
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function SortableHead({
  label,
  sortKey,
  active,
  dir,
  onClick,
}: {
  label: string;
  sortKey: SortKey;
  active: SortKey;
  dir: "asc" | "desc";
  onClick: (key: SortKey) => void;
}) {
  const isActive = active === sortKey;
  return (
    <TableHead>
      <button
        type="button"
        onClick={() => onClick(sortKey)}
        className="flex items-center gap-1 hover:text-foreground transition-colors"
      >
        {label}
        {isActive ? (
          dir === "asc" ? (
            <ArrowUp className="h-3 w-3" />
          ) : (
            <ArrowDown className="h-3 w-3" />
          )
        ) : (
          <ArrowUpDown className="h-3 w-3 opacity-40" />
        )}
      </button>
    </TableHead>
  );
}

function SubmissionRow({
  submission: s,
  selectable,
  selected,
  onToggle,
  peutApprouver,
  approbationEnCours,
  onApprouver,
}: {
  submission: SubmissionSummaryDto;
  selectable: boolean;
  selected: boolean;
  onToggle: () => void;
  peutApprouver: boolean;
  approbationEnCours: boolean;
  onApprouver: () => void;
}) {
  // Seule une section validee et pas encore approuvee peut etre cochee : le reste n'attend
  // rien du DG, et la case n'aurait aucun effet a la confirmation.
  const cochable = attendLaDg(s);
  // Une section approuvee reste a l'ecran mais s'efface : le DG voit ce qu'il a traite sans
  // que cela concurrence visuellement ce qui lui reste a faire.
  const traitee = Boolean(s.dgApprovedAt);
  return (
    <TableRow
      className={
        selected
          ? "bg-primary-50/60 dark:bg-primary-500/10"
          : traitee
            ? "opacity-55"
            : undefined
      }
    >
      {selectable && (
        <TableCell>
          {cochable ? (
            <input
              type="checkbox"
              checked={selected}
              onChange={onToggle}
              aria-label={`Sélectionner ${s.sectionCode} — ${s.groupName}`}
              className="h-4 w-4 rounded border-slate-300 text-primary-500 focus:ring-primary-500/40"
            />
          ) : null}
        </TableCell>
      )}
      <TableCell>
        <p className="font-medium text-foreground">{s.groupName}</p>
        {s.leaderFullName && <p className="text-[12px] text-muted-foreground">{s.leaderFullName}</p>}
      </TableCell>
      <TableCell>
        <span className="text-muted-foreground mr-1.5">{s.sectionCode}</span>
        {s.sectionTitle}
      </TableCell>
      <TableCell>
        <StatusBadge status={s.status} />
      </TableCell>
      <TableCell className="text-muted-foreground">{s.version > 0 ? s.version : "—"}</TableCell>
      <TableCell className="text-muted-foreground whitespace-nowrap">{formatDateTime(s.submittedAt) || "—"}</TableCell>
      <TableCell className="text-muted-foreground whitespace-nowrap">{formatDateTime(s.validatedAt) || "—"}</TableCell>
      <TableCell className="whitespace-nowrap">
        {s.dgApprovedAt ? (
          <span className="text-emerald-700 dark:text-emerald-400">{formatDateTime(s.dgApprovedAt)}</span>
        ) : s.status === "VALIDATED" ? (
          <span className="text-amber-700 dark:text-amber-400">En attente</span>
        ) : (
          <span className="text-muted-foreground">—</span>
        )}
      </TableCell>
      <TableCell className="text-muted-foreground whitespace-nowrap">{formatDateTime(s.lastActivityAt) || "—"}</TableCell>
      <TableCell className="max-w-[220px] truncate text-muted-foreground" title={s.adminComment ?? undefined}>
        {s.adminComment ?? "—"}
      </TableCell>
      <TableCell>
        <div className="flex items-center justify-end gap-1">
          {peutApprouver && cochable && (
            <Button
              variant="secondary"
              size="sm"
              disabled={approbationEnCours}
              onClick={onApprouver}
              title="Approuver cette section"
            >
              <ShieldCheck className="h-3.5 w-3.5" /> Approuver
            </Button>
          )}
          <Button variant="ghost" size="icon" asChild title="Voir la section">
            <Link href={`/admin/groups/${s.groupId}/sections/${s.sectionCode}`}>
              <Eye className="h-4 w-4" />
            </Link>
          </Button>
        </div>
      </TableCell>
    </TableRow>
  );
}

/**
 * Bouton d'approbation en masse et sa confirmation. Le DG approuve par dizaines en fin de
 * campagne : le geste doit rester court, mais jamais silencieux — la confirmation dit combien
 * de sections basculent et ou elles vont.
 */
function ApprobationEnMasse({
  label,
  titre,
  description,
  enCours,
  desactive = false,
  onConfirm,
}: {
  label: string;
  titre: string;
  description: string;
  enCours: boolean;
  /** Rien a approuver : le bouton reste a sa place, grise, plutot que de disparaitre. */
  desactive?: boolean;
  onConfirm: () => void;
}) {
  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>
        <Button size="sm" disabled={enCours || desactive}>
          <ShieldCheck className="h-3.5 w-3.5" /> {label}
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{titre}</AlertDialogTitle>
          <AlertDialogDescription>{description}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Annuler</AlertDialogCancel>
          <AlertDialogAction onClick={onConfirm}>Approuver</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
