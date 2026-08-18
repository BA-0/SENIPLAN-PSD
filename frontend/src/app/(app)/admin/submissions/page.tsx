"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowDown, ArrowUp, ArrowUpDown, Eye, Inbox, RotateCcw, Send, Search as SearchIcon } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/status-badge";
import { KpiCard } from "@/components/kpi-card";
import { getAdminSubmissions } from "@/lib/api/admin";
import { formatDateTime } from "@/lib/utils";
import type { SubmissionSummaryDto } from "@/types/api";
import type { SectionStatus } from "@/types/common";

const STATUS_OPTIONS: { value: SectionStatus; label: string }[] = [
  { value: "NOT_STARTED", label: "Non commencé" },
  { value: "IN_PROGRESS", label: "En cours" },
  { value: "SUBMITTED", label: "Soumis" },
  { value: "VALIDATED", label: "Validé" },
  { value: "REVISION_REQUESTED", label: "À réviser" },
];

type SortKey = "groupName" | "sectionOrder" | "status" | "submittedAt" | "validatedAt" | "lastActivityAt";

const PAGE_SIZE = 25;

export default function AdminSubmissionsPage() {
  const searchParams = useSearchParams();
  const initialStatus = searchParams.get("status") as SectionStatus | null;

  const [statusFilter, setStatusFilter] = useState<SectionStatus | "">(initialStatus ?? "");
  const [groupFilter, setGroupFilter] = useState<number | "">("");
  const [sectionFilter, setSectionFilter] = useState<string>("");
  const [search, setSearch] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [sortKey, setSortKey] = useState<SortKey>("submittedAt");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "submissions"],
    queryFn: getAdminSubmissions,
    refetchInterval: 15_000,
  });

  const groups = useMemo(() => {
    const map = new Map<number, string>();
    (data ?? []).forEach((s) => map.set(s.groupId, s.groupName));
    return [...map.entries()].sort((a, b) => a[1].localeCompare(b[1]));
  }, [data]);

  const sections = useMemo(() => {
    const map = new Map<string, string>();
    (data ?? []).forEach((s) => map.set(s.sectionCode, s.sectionTitle));
    return [...map.entries()].sort((a, b) => a[0].localeCompare(b[0]));
  }, [data]);

  function toggleSort(key: SortKey) {
    if (sortKey === key) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir(key === "groupName" || key === "sectionOrder" ? "asc" : "desc");
    }
  }

  function resetFilters() {
    setStatusFilter("");
    setGroupFilter("");
    setSectionFilter("");
    setSearch("");
    setDateFrom("");
    setDateTo("");
    setPage(0);
  }

  const hasActiveFilters =
    statusFilter !== "" || groupFilter !== "" || sectionFilter !== "" || search.trim() !== "" || dateFrom !== "" || dateTo !== "";

  const filtered = useMemo(() => {
    if (!data) return [];
    const q = search.trim().toLowerCase();
    const from = dateFrom ? new Date(dateFrom) : null;
    const to = dateTo ? new Date(dateTo + "T23:59:59") : null;

    let rows = data.filter((s) => {
      if (statusFilter !== "" && s.status !== statusFilter) return false;
      if (groupFilter !== "" && s.groupId !== groupFilter) return false;
      if (sectionFilter !== "" && s.sectionCode !== sectionFilter) return false;
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
        case "lastActivityAt":
          cmp = (a.lastActivityAt ?? "").localeCompare(b.lastActivityAt ?? "");
          break;
      }
      return sortDir === "asc" ? cmp : -cmp;
    });

    return rows;
  }, [data, statusFilter, groupFilter, sectionFilter, search, dateFrom, dateTo, sortKey, sortDir]);

  const kpis = useMemo(() => {
    const source = data ?? [];
    return {
      submitted: source.filter((s) => s.status === "SUBMITTED").length,
      validated: source.filter((s) => s.status === "VALIDATED").length,
      revision: source.filter((s) => s.status === "REVISION_REQUESTED").length,
    };
  }, [data]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages - 1);
  const paginated = filtered.slice(currentPage * PAGE_SIZE, currentPage * PAGE_SIZE + PAGE_SIZE);

  return (
    <div className="space-y-6">
      <div>
        <h1>Soumissions</h1>
        <p className="text-[13px] text-muted-foreground mt-1">Toutes les sections soumises par les groupes, à filtrer et à examiner</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <KpiCard icon={Send} label="En attente de validation" value={kpis.submitted} color="blue" />
        <KpiCard icon={Inbox} label="Validées" value={kpis.validated} color="emerald" />
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
              <p className="text-[13px] font-medium text-foreground/90">Section</p>
              <NativeSelect
                value={sectionFilter}
                onChange={(e) => {
                  setSectionFilter(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">Toutes les sections</option>
                {sections.map(([code, title]) => (
                  <option key={code} value={code}>
                    {code} — {title}
                  </option>
                ))}
              </NativeSelect>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>
            {filtered.length} soumission{filtered.length > 1 ? "s" : ""}
          </CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="h-64 bg-muted rounded-xl animate-pulse m-5" />
          ) : filtered.length === 0 ? (
            <p className="text-center text-muted-foreground py-16">Aucune soumission ne correspond à ces filtres</p>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <SortableHead label="Groupe" sortKey="groupName" active={sortKey} dir={sortDir} onClick={toggleSort} />
                    <SortableHead label="Section" sortKey="sectionOrder" active={sortKey} dir={sortDir} onClick={toggleSort} />
                    <SortableHead label="Statut" sortKey="status" active={sortKey} dir={sortDir} onClick={toggleSort} />
                    <TableHead>Version</TableHead>
                    <SortableHead label="Soumis le" sortKey="submittedAt" active={sortKey} dir={sortDir} onClick={toggleSort} />
                    <SortableHead label="Validé le" sortKey="validatedAt" active={sortKey} dir={sortDir} onClick={toggleSort} />
                    <SortableHead label="Dernière activité" sortKey="lastActivityAt" active={sortKey} dir={sortDir} onClick={toggleSort} />
                    <TableHead>Commentaire admin</TableHead>
                    <TableHead />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {paginated.map((s) => (
                    <SubmissionRow key={`${s.groupId}-${s.sectionId}`} submission={s} />
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

function SubmissionRow({ submission: s }: { submission: SubmissionSummaryDto }) {
  return (
    <TableRow>
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
      <TableCell className="text-muted-foreground whitespace-nowrap">{formatDateTime(s.lastActivityAt) || "—"}</TableCell>
      <TableCell className="max-w-[220px] truncate text-muted-foreground" title={s.adminComment ?? undefined}>
        {s.adminComment ?? "—"}
      </TableCell>
      <TableCell>
        <Button variant="ghost" size="icon" asChild title="Voir la section">
          <Link href={`/admin/groups/${s.groupId}/sections/${s.sectionCode}`}>
            <Eye className="h-4 w-4" />
          </Link>
        </Button>
      </TableCell>
    </TableRow>
  );
}
