"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { AlertTriangle, BarChart3, FileDown, FileText, Plus, RotateCcw, Save, Trash2, Undo2 } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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
import { getSynthesisNote, resetSynthesisNote, saveSynthesisNote } from "@/lib/api/synthesis-note";
import { downloadCorrectedSynthesisNotePdf, downloadCorrectedSynthesisNoteWord } from "@/lib/api/exports";
import { extractErrorMessage } from "@/lib/api-client";
import { canEditSynthesisNote, canPilot } from "@/lib/roles";
import { useCurrentUser } from "@/hooks/use-current-user";
import { useRealtimeSynthesisNote } from "@/hooks/use-realtime-synthesis-note";
import { useConnectionStore } from "@/store/connection-store";
import { cn } from "@/lib/utils";
import type { Attribution, Cell, NoteBlock, SynthesisNote, TableRow } from "@/types/synthesis-note";

/**
 * Correction de la note de synthèse par la Direction Générale (demande client du 03/10/2026).
 *
 * La note générée à partir des sections approuvées s'ouvre partie par partie ; le DG en corrige
 * les titres et le contenu des tableaux, ajoute ou retire des lignes. Plusieurs personnes la
 * corrigent en même temps, sur des postes différents et souvent le même compte : chaque poste
 * enregistre de lui-même ses blocs modifiés, que le serveur fusionne avec ceux des autres, et
 * l'annonce temps réel (/topic/synthesis-note) fait relire la note aux autres postes. Les
 * téléchargements PDF et Word reprennent la dernière version enregistrée. Les contenus approuvés ensuite y sont
 * fusionnés par le serveur (SynthesisNoteMerge) ; un bandeau le signale. Ouvert au DG et au compte dir.generale, que le serveur vérifie aussi
 * (SynthesisNoteAccess) : la page vit hors de /admin, ce dernier compte n'ayant pas le pilotage.
 */
export default function CorrectionSynthesePage() {
  const { user } = useCurrentUser();
  const peutCorriger = canEditSynthesisNote(user);
  const queryClient = useQueryClient();
  const connected = useConnectionStore((s) => s.connected);
  const clientId = useMemo(newClientId, []);

  const { data, isLoading, isError } = useQuery({
    queryKey: NOTE_KEY,
    queryFn: getSynthesisNote,
    enabled: peutCorriger,
    refetchOnWindowFocus: false,
    // Le temps réel annonce chaque enregistrement ; sans lui, une relecture toutes les 2 min (le
    // pare-feu AWS bloque au-delà de 100 requêtes par IP, et plusieurs postes partagent souvent la même).
    refetchInterval: connected ? false : 120_000,
  });

  // `base` : la note telle que le serveur l'a donnée, avec sa version. `blocks` : le brouillon du
  // poste, qui partage les blocs de `base` tant qu'ils ne sont pas modifiés (comparaison par référence).
  const [base, setBase] = useState<SynthesisNote | null>(null);
  const [blocks, setBlocks] = useState<NoteBlock[] | null>(null);
  const [partIndex, setPartIndex] = useState(0);
  const [saving, setSaving] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [exporting, setExporting] = useState<"pdf" | "word" | null>(null);
  const [remoteAt, setRemoteAt] = useState<Date | null>(null);

  const baseRef = useRef(base);
  const blocksRef = useRef(blocks);
  baseRef.current = base;
  blocksRef.current = blocks;
  const savingRef = useRef(false);
  const saveAgainRef = useRef(false);
  const remoteDuringSaveRef = useRef(false);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const refetchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastRefetchAt = useRef(0);

  const dirty = isDirty(base, blocks);

  const adopt = useCallback(
    (note: SynthesisNote) => {
      setBase(note);
      setBlocks(note.blocks);
      queryClient.setQueryData(NOTE_KEY, note);
    },
    [queryClient]
  );

  // Une version arrivée du serveur s'affiche aussitôt, sauf sur une saisie en cours : celle-ci
  // part alors à l'enregistrement, qui la fusionne avec la nouvelle version et renvoie le tout.
  useEffect(() => {
    if (!data) return;
    const current = baseRef.current;
    if (!current) {
      adopt(data);
    } else if (data.version !== current.version && !savingRef.current) {
      if (isDirty(current, blocksRef.current)) scheduleSave(1_500);
      else adopt(data);
    }
    // scheduleSave ne dépend que de refs : inutile de relancer l'effet pour lui.
  }, [data, adopt]);

  useEffect(() => {
    if (!dirty) return;
    const avertir = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", avertir);
    return () => window.removeEventListener("beforeunload", avertir);
  }, [dirty]);

  useEffect(
    () => () => {
      if (saveTimer.current) clearTimeout(saveTimer.current);
      if (refetchTimer.current) clearTimeout(refetchTimer.current);
    },
    []
  );

  // Un autre poste a enregistré : on recharge la note (une fois, même s'il enregistre plusieurs
  // fois de suite), ou, sur une saisie en cours, on l'enregistre pour la fusionner.
  useRealtimeSynthesisNote((event) => {
    if (event.clientId === clientId || event.version === baseRef.current?.version) return;
    setRemoteAt(new Date());
    if (savingRef.current) {
      // L'enregistrement en cours ne contient peut-être pas cette version : on relira après lui.
      remoteDuringSaveRef.current = true;
    } else if (isDirty(baseRef.current, blocksRef.current)) {
      scheduleSave(1_500);
    } else {
      requestRefetch();
    }
  });

  function requestRefetch() {
    if (refetchTimer.current) return;
    const wait = Math.max(1_000, lastRefetchAt.current + REMOTE_REFETCH_MIN_GAP_MS - Date.now());
    refetchTimer.current = setTimeout(() => {
      refetchTimer.current = null;
      lastRefetchAt.current = Date.now();
      void queryClient.invalidateQueries({ queryKey: NOTE_KEY });
    }, wait);
  }

  const parts = useMemo(() => splitIntoParts(blocks ?? []), [blocks]);
  const part = parts[Math.min(partIndex, Math.max(parts.length - 1, 0))];
  const annexTables = useMemo(() => annexTableIndexes(blocks ?? []), [blocks]);

  function scheduleSave(delay: number) {
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(() => {
      saveTimer.current = null;
      void save(false);
    }, delay);
  }

  function update(index: number, mutate: (block: NoteBlock) => void) {
    setBlocks((prev) => {
      if (!prev) return prev;
      const next = [...prev];
      const copy = structuredClone(next[index]);
      mutate(copy);
      next[index] = copy;
      return next;
    });
    scheduleSave(AUTOSAVE_DEBOUNCE_MS);
  }

  async function save(manual: boolean) {
    const from = baseRef.current;
    const draft = blocksRef.current;
    if (!from || !draft) return;
    if (savingRef.current) {
      saveAgainRef.current = true;
      return;
    }
    const changes = draft.flatMap((block, index) => (block !== from.blocks[index] ? [{ index, block }] : []));
    if (changes.length === 0) {
      if (manual) toast.info("Aucune modification à enregistrer");
      return;
    }
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = null;
    savingRef.current = true;
    setSaving(true);
    try {
      const saved = await saveSynthesisNote(from.version, changes, clientId);
      if (blocksRef.current === draft) {
        adopt(saved);
      } else {
        // Saisie pendant l'enregistrement : le brouillon reste rapporté à son ancienne version, et
        // repart ; ce qui vient d'être enregistré s'y retrouve à l'identique, sans conflit.
        saveAgainRef.current = true;
      }
      if (saved.conflicts > 0) {
        toast.warning(
          `${saved.conflicts} élément${saved.conflicts > 1 ? "s" : ""} modifié${saved.conflicts > 1 ? "s" : ""} aussi par un autre poste : votre version a été retenue`
        );
      } else if (manual) {
        toast.success("Note de synthèse enregistrée : les téléchargements reprennent cette version");
      }
    } catch (error) {
      toast.error(extractErrorMessage(error, "Échec de l'enregistrement de la note"));
    } finally {
      savingRef.current = false;
      setSaving(false);
      if (saveAgainRef.current) {
        saveAgainRef.current = false;
        scheduleSave(AUTOSAVE_DEBOUNCE_MS);
      }
      if (remoteDuringSaveRef.current) {
        remoteDuringSaveRef.current = false;
        requestRefetch();
      }
    }
  }

  function handleDiscard() {
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = null;
    // Une version arrivée d'un autre poste pendant la saisie s'affiche dès qu'on l'abandonne.
    if (data && base && data.version !== base.version) adopt(data);
    else if (base) setBlocks(base.blocks);
  }

  async function handleReset() {
    setResetting(true);
    try {
      adopt(await resetSynthesisNote(clientId));
      toast.success("Corrections abandonnées : la note reprend le document généré à jour");
    } catch (error) {
      toast.error(extractErrorMessage(error, "Échec du retour au document généré"));
    } finally {
      setResetting(false);
    }
  }

  async function handleExport(kind: "pdf" | "word") {
    setExporting(kind);
    try {
      await (kind === "pdf" ? downloadCorrectedSynthesisNotePdf() : downloadCorrectedSynthesisNoteWord());
    } catch (error) {
      toast.error(extractErrorMessage(error, "Échec de la génération de la note de synthèse"));
    } finally {
      setExporting(null);
    }
  }

  if (!peutCorriger) {
    return (
      <div className="space-y-4">
        <h1>Corriger la note de synthèse</h1>
        <p className="text-[13px] text-muted-foreground">
          Cette page est réservée à la Direction Générale.
          {canPilot(user?.role) && (
            <>
              {" "}La note se télécharge depuis la page{" "}
              <Link prefetch={false} href="/admin/synthesis" className="font-medium text-primary-600 hover:underline">
                Note de synthèse
              </Link>
              .
            </>
          )}
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1>Corriger la note de synthèse</h1>
          <p className="text-[13px] text-muted-foreground mt-1 max-w-3xl">
            Corrigez les titres et le contenu des tableaux, ajoutez ou retirez des lignes : chaque correction
            s&apos;enregistre d&apos;elle-même quelques secondes après la saisie et s&apos;affiche aussitôt sur les autres postes
            ouverts sur cette page. Les corrections faites en même temps sur plusieurs postes se cumulent. Les
            téléchargements PDF et Word reprennent la dernière version enregistrée. Les graphiques se recalculent à
            partir des données et ne se modifient pas ici.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" size="sm" onClick={() => handleExport("pdf")} loading={exporting === "pdf"} disabled={dirty}
            title={dirty ? "Enregistrez d'abord vos corrections" : "Télécharger la note telle qu'enregistrée"}>
            <FileDown className="h-4 w-4" /> PDF
          </Button>
          <Button variant="secondary" size="sm" onClick={() => handleExport("word")} loading={exporting === "word"} disabled={dirty}
            title={dirty ? "Enregistrez d'abord vos corrections" : "Télécharger la note telle qu'enregistrée"}>
            <FileText className="h-4 w-4" /> Word
          </Button>
        </div>
      </div>

      {base?.edited && base.sourceChanged && !dirty && (
        <div role="alert" className="flex flex-wrap items-start gap-3 rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-[13px] text-amber-900 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-200">
          <AlertTriangle className="h-4 w-4 mt-0.5 shrink-0" aria-hidden="true" />
          <p className="flex-1">
            Des contenus ont été approuvés ou modifiés depuis votre correction : ils sont repris ci-dessous et dans
            les téléchargements. Vos corrections sont gardées, sauf sur les tableaux et textes que ces contenus
            ont modifiés. Vérifiez-les, puis enregistrez.
          </p>
        </div>
      )}

      <div className="sticky top-0 z-10 -mx-1 flex flex-wrap items-center gap-3 rounded-xl border border-border bg-card/95 px-4 py-3 backdrop-blur">
        <p className="flex-1 text-[13px] text-muted-foreground">
          {saving
            ? "Enregistrement…"
            : dirty
              ? "Modifications en cours : enregistrement automatique dans quelques secondes."
              : base?.edited
                ? `Version corrigée enregistrée${base.updatedAt ? ` le ${formatDate(base.updatedAt)}` : ""}${base.updatedBy ? ` par ${base.updatedBy}` : ""}.`
                : "Document généré à partir des sections approuvées, sans correction."}
          {remoteAt && (
            <span className="ml-2 text-primary-600 dark:text-primary-300">
              Corrections d&apos;un autre poste reçues à{" "}
              {remoteAt.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" })}.
            </span>
          )}
          {!connected && (
            <span className="ml-2 text-amber-600 dark:text-amber-400">
              Temps réel indisponible : la note se relit toutes les 2 minutes.
            </span>
          )}
        </p>
        {dirty && (
          <Button variant="ghost" size="sm" onClick={handleDiscard}>
            <Undo2 className="h-4 w-4" /> Annuler
          </Button>
        )}
        {base?.edited && (
          <AlertDialog>
            <AlertDialogTrigger asChild>
              <Button variant="destructiveGhost" size="sm" disabled={resetting}>
                <RotateCcw className="h-4 w-4" /> Repartir du document à jour
              </Button>
            </AlertDialogTrigger>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>Abandonner vos corrections ?</AlertDialogTitle>
                <AlertDialogDescription>
                  La note reprendra le document généré à partir des sections approuvées, à jour. Toutes les
                  corrections enregistrées, depuis tous les postes, seront perdues.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>Annuler</AlertDialogCancel>
                <AlertDialogAction onClick={handleReset}>Repartir du document à jour</AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        )}
        <Button variant="primary" size="sm" onClick={() => void save(true)} loading={saving} disabled={!dirty}>
          <Save className="h-4 w-4" /> Enregistrer
        </Button>
      </div>

      {isLoading || (!blocks && !isError) ? (
        <div className="h-80 animate-pulse rounded-xl bg-muted" />
      ) : isError || !blocks ? (
        <div role="alert" className="flex items-center gap-2 py-6 text-[13px] text-amber-600 dark:text-amber-400">
          <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" />
          Impossible de charger la note de synthèse.
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-[260px_minmax(0,1fr)]">
          <nav aria-label="Parties de la note" className="space-y-0.5 lg:sticky lg:top-20 lg:self-start">
            {parts.map((p, i) => (
              <button
                key={p.start}
                type="button"
                onClick={() => setPartIndex(i)}
                className={cn(
                  "block w-full rounded-lg px-3 py-2 text-left text-[13px] transition-colors",
                  i === partIndex ? "bg-primary-50 font-medium text-primary-700 dark:bg-primary-500/15 dark:text-primary-200" : "text-muted-foreground hover:bg-muted"
                )}
              >
                {p.title}
              </button>
            ))}
          </nav>

          <div className="min-w-0 space-y-4">
            {part &&
              blocks.slice(part.start, part.end).map((block, offset) => {
                const index = part.start + offset;
                return (
                  <BlockEditor
                    key={index}
                    block={block}
                    onChange={(mutate) => update(index, mutate)}
                    lockedColumns={annexTables.has(index) ? LOCKED_ANNEX_COLUMNS : 0}
                  />
                );
              })}
          </div>
        </div>
      )}
    </div>
  );
}

const NOTE_KEY = ["admin", "synthesis-note"];

/** Délai entre la dernière frappe et l'enregistrement automatique (cf. use-section-autosave). */
const AUTOSAVE_DEBOUNCE_MS = 6_000;

/**
 * Écart minimal entre deux relectures déclenchées par les autres postes : quand plusieurs personnes
 * enregistrent coup sur coup, chaque poste ne relit la note qu'une fois par période (pare-feu AWS).
 */
const REMOTE_REFETCH_MIN_GAP_MS = 10_000;

/** Identifiant de l'onglet, pour reconnaître ses propres annonces temps réel. */
function newClientId(): string {
  return typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

/** Vrai si le brouillon a des blocs modifiés par rapport à la version dont il part. */
function isDirty(base: SynthesisNote | null, blocks: NoteBlock[] | null): boolean {
  return !!base && !!blocks && blocks !== base.blocks && blocks.some((block, i) => block !== base.blocks[i]);
}

interface Part {
  title: string;
  start: number;
  end: number;
}

/** Une partie par titre de niveau 1 (« I. CONTEXTE… ») ; ce qui précède forme le début du document. */
function splitIntoParts(blocks: NoteBlock[]): Part[] {
  const parts: Part[] = [];
  blocks.forEach((block, index) => {
    if (block.type === "HEADING" && block.level === 1) {
      if (parts.length === 0 && index > 0) parts.push({ title: "Début du document", start: 0, end: index });
      parts.push({ title: block.text || "(sans titre)", start: index, end: blocks.length });
      if (parts.length > 1) parts[parts.length - 2].end = index;
    }
  });
  if (parts.length === 0 && blocks.length > 0) parts.push({ title: "Document", start: 0, end: blocks.length });
  return parts;
}

/** Colonnes figées dans les tableaux 1 à 4 des annexes : elles reprennent le canevas tel qu'approuvé. */
const LOCKED_ANNEX_COLUMNS = 2;

/**
 * Tableaux 1 à 4 des annexes (cadre logique, planification, budget, cadre de mesure de rendement).
 * Repérés par leur place et non par leur intitulé, que le DG peut corriger : les annexes forment la
 * dernière partie, ouverte par la fiche des indicateurs, puis un sous-titre par tableau.
 */
function annexTableIndexes(blocks: NoteBlock[]): Set<number> {
  const indexes = new Set<number>();
  let annexStart = -1;
  blocks.forEach((block, i) => {
    if (block.type === "HEADING" && block.level === 1) annexStart = i;
  });
  if (annexStart < 0) return indexes;
  let subheadings = 0;
  for (let i = annexStart + 1; i < blocks.length; i++) {
    const block = blocks[i];
    if (block.type === "HEADING" && block.level === 2) subheadings++;
    else if (block.type === "TABLE" && subheadings >= 2) indexes.add(i);
  }
  return indexes;
}

function formatDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString("fr-FR", { day: "numeric", month: "long", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

type Mutator = (mutate: (block: NoteBlock) => void) => void;

/** Montant ou chiffre seul (« 28 820 000 000 FCFA », « 12,5 % ») : il ne doit jamais passer à la ligne. */
function isFigure(text: string) {
  const t = text.trim();
  return t.length > 0 && t.length <= 40 && /\d/.test(t) && /^[-+−]?[\d\s.,]+\s*(%|(M\s|millions\s|milliards\s)?FCFA)?$/.test(t);
}

/**
 * Champ de texte qui grandit avec son contenu : une cellule de tableau peut tenir plusieurs lignes.
 *
 * Un double invisible du texte, posé sous la zone de saisie, donne sa taille à la cellule. Seule, une
 * zone de saisie ne réserve aucune largeur dans un tableau (les colonnes du budget s'écrasaient) et,
 * sans field-sizing (Firefox, Safari), reste sur une ligne : un montant coupé à chaque espace des
 * milliers n'en montrait que le premier groupe. Un chiffre reste en outre sur une seule ligne.
 */
function TextField({
  value,
  onChange,
  className,
  ariaLabel,
  readOnly = false,
}: {
  value: string;
  onChange: (v: string) => void;
  className?: string;
  ariaLabel?: string;
  readOnly?: boolean;
}) {
  const shared = cn(
    "col-start-1 row-start-1 rounded-md border border-transparent px-1.5 py-1 text-[12.5px] leading-snug",
    isFigure(value) ? "whitespace-pre" : "whitespace-pre-wrap break-words",
    className
  );
  return (
    <div className="grid w-full">
      <span aria-hidden="true" className={cn(shared, "invisible")}>
        {value + " "}
      </span>
      <textarea
        aria-label={ariaLabel}
        value={value}
        rows={1}
        cols={1}
        readOnly={readOnly}
        title={readOnly ? "Lecture seule : repris du canevas approuvé" : undefined}
        onChange={(e) => onChange(e.target.value)}
        className={cn(
          "block w-full min-w-0 resize-none overflow-hidden bg-transparent text-foreground",
          readOnly
            ? "cursor-default text-muted-foreground focus:outline-none"
            : "hover:border-border focus:border-primary-500 focus:bg-card focus:outline-none focus:ring-2 focus:ring-primary-500/20",
          shared
        )}
      />
    </div>
  );
}

function BlockEditor({ block, onChange, lockedColumns = 0 }: { block: NoteBlock; onChange: Mutator; lockedColumns?: number }) {
  switch (block.type) {
    case "HEADING":
      return (
        <Input
          aria-label={`Titre de niveau ${block.level}`}
          value={block.text}
          onChange={(e) => onChange((b) => { if (b.type === "HEADING") b.text = e.target.value; })}
          className={cn(
            "font-semibold",
            block.level === 1 ? "text-[17px] uppercase" : block.level === 2 ? "text-[15px]" : "text-[13.5px]"
          )}
        />
      );
    case "TABLE":
      return <TableEditor block={block} onChange={onChange} lockedColumns={lockedColumns} />;
    case "CHART":
      return (
        <div className="flex items-center gap-2 rounded-lg border border-dashed border-border px-4 py-3 text-[12.5px] text-muted-foreground">
          <BarChart3 className="h-4 w-4 shrink-0" aria-hidden="true" />
          Graphique « {block.title} » : repris tel quel, il ne se corrige pas ici.
        </div>
      );
    case "METRIC_GRID":
      return (
        <Card>
          <CardContent className="grid gap-2 pt-4 sm:grid-cols-2">
            {block.metrics.map((m, i) => (
              <div key={i} className="rounded-lg border border-border p-2">
                <TextField ariaLabel="Intitulé" value={m.label} onChange={(v) => onChange((b) => { if (b.type === "METRIC_GRID") b.metrics[i].label = v; })} className="text-muted-foreground" />
                <TextField ariaLabel="Valeur" value={m.value} onChange={(v) => onChange((b) => { if (b.type === "METRIC_GRID") b.metrics[i].value = v; })} className="font-semibold text-[14px]" />
              </div>
            ))}
          </CardContent>
        </Card>
      );
    case "KEY_VALUE_LIST":
      return (
        <Card>
          {block.title != null && (
            <CardHeader>
              <TextField ariaLabel="Titre" value={block.title} onChange={(v) => onChange((b) => { if (b.type === "KEY_VALUE_LIST") b.title = v; })} className="font-semibold" />
            </CardHeader>
          )}
          <CardContent className="space-y-1 pt-4">
            {block.pairs.map((pair, i) => (
              <div key={i} className="grid grid-cols-[minmax(0,1fr)_minmax(0,2fr)] gap-2">
                <TextField ariaLabel="Intitulé" value={pair.label} onChange={(v) => onChange((b) => { if (b.type === "KEY_VALUE_LIST") b.pairs[i].label = v; })} className="font-medium" />
                <TextField ariaLabel="Valeur" value={pair.value} onChange={(v) => onChange((b) => { if (b.type === "KEY_VALUE_LIST") b.pairs[i].value = v; })} />
              </div>
            ))}
          </CardContent>
        </Card>
      );
    case "PARAGRAPH":
    case "CALLOUT":
      return (
        <TextField
          ariaLabel="Texte"
          value={block.text}
          onChange={(v) => onChange((b) => { if (b.type === "PARAGRAPH" || b.type === "CALLOUT") b.text = v; })}
          className="border-border text-[13px]"
        />
      );
    case "BULLET_LIST":
      return (
        <ListCard
          title={block.title}
          onTitle={(v) => onChange((b) => { if (b.type === "BULLET_LIST") b.title = v; })}
          items={block.items.map((text) => ({ text, colorHexes: [] }))}
          onItem={(i, v) => onChange((b) => { if (b.type === "BULLET_LIST") b.items[i] = v; })}
        />
      );
    case "ATTRIBUTED_LIST":
    case "COLOR_LEGEND": {
      const items = block.type === "ATTRIBUTED_LIST" ? block.items : block.entries;
      return (
        <ListCard
          title={block.title}
          onTitle={(v) => onChange((b) => { if (b.type === "ATTRIBUTED_LIST" || b.type === "COLOR_LEGEND") b.title = v; })}
          items={items}
          onItem={(i, v) =>
            onChange((b) => {
              if (b.type === "ATTRIBUTED_LIST") b.items[i].text = v;
              if (b.type === "COLOR_LEGEND") b.entries[i].text = v;
            })
          }
        />
      );
    }
    case "QUADRANT":
    case "ATTRIBUTED_QUADRANT":
      return (
        <div className="grid gap-3 sm:grid-cols-2">
          {block.cells.map((cell, ci) => (
            <ListCard
              key={ci}
              title={cell.title}
              onTitle={(v) => onChange((b) => { if (b.type === "QUADRANT" || b.type === "ATTRIBUTED_QUADRANT") b.cells[ci].title = v; })}
              items={block.type === "QUADRANT" ? block.cells[ci].items.map((text) => ({ text, colorHexes: [] })) : block.cells[ci].items as Attribution[]}
              onItem={(i, v) =>
                onChange((b) => {
                  if (b.type === "QUADRANT") b.cells[ci].items[i] = v;
                  if (b.type === "ATTRIBUTED_QUADRANT") b.cells[ci].items[i].text = v;
                })
              }
            />
          ))}
        </div>
      );
  }
}

function ListCard({
  title,
  onTitle,
  items,
  onItem,
}: {
  title: string | null;
  onTitle: (v: string) => void;
  items: Attribution[];
  onItem: (i: number, v: string) => void;
}) {
  return (
    <Card>
      {title != null && (
        <CardHeader className="pb-1">
          <CardTitle className="text-[13.5px]">
            <TextField ariaLabel="Titre" value={title} onChange={onTitle} className="font-semibold" />
          </CardTitle>
        </CardHeader>
      )}
      <CardContent className={cn("space-y-0.5", title == null && "pt-4")}>
        {items.map((item, i) => (
          <div key={i} className="flex items-start gap-1.5">
            <Dots colors={item.colorHexes} />
            <TextField ariaLabel="Élément" value={item.text} onChange={(v) => onItem(i, v)} />
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

/** Pastilles des directions qui ont écrit l'élément. */
function Dots({ colors }: { colors: string[] }) {
  if (colors.length === 0) return <span className="mt-2.5 h-1.5 w-1.5 shrink-0 rounded-full bg-muted-foreground/40" />;
  return (
    <span className="mt-2 flex shrink-0 gap-0.5">
      {colors.map((c, i) => (
        <span key={i} className="h-2 w-2 rounded-full" style={{ backgroundColor: c }} />
      ))}
    </span>
  );
}

const BAND_CLASSES: Record<string, string> = {
  PRIMARY_DARK: "bg-primary-700 text-white",
  PRIMARY_LIGHT: "bg-primary-50 dark:bg-primary-500/15",
  GREY: "bg-muted",
};

/**
 * Une ligne peut être retirée, ou dupliquée vide juste en dessous, quand elle ne participe à
 * aucune fusion : retirer une ligne qu'une cellule fusionnée recouvre décalerait tout le tableau.
 */
function isSimpleRow(row: TableRow): boolean {
  return !row.band && row.cells.every((c) => c.rowSpan === 1);
}

function emptyCopy(row: TableRow): TableRow {
  return {
    ...row,
    emphasized: false,
    cells: row.cells.map((c) => ({ ...c, text: "", bold: false, attributions: [], rowSpan: 1 })),
  };
}

/**
 * `lockedColumns` : nombre de premières colonnes en lecture seule (en-têtes compris). Une ligne
 * ajoutée y resterait vide sans pouvoir être remplie : ces tableaux n'en proposent donc pas.
 */
function TableEditor({
  block,
  onChange,
  lockedColumns = 0,
}: {
  block: Extract<NoteBlock, { type: "TABLE" }>;
  onChange: Mutator;
  lockedColumns?: number;
}) {
  const locked = (ci: number) => ci < lockedColumns;
  const columns = Math.max(block.columnHeaders.length, ...block.rows.map((r) => r.cells.length), 1);
  const showsHeaders = block.columnHeaders.some((h) => h && h.trim() !== "");

  const table = (fn: (b: Extract<NoteBlock, { type: "TABLE" }>) => void) =>
    onChange((b) => { if (b.type === "TABLE") fn(b); });

  function setCell(ri: number, ci: number, value: string, ai?: number) {
    table((b) => {
      const target = b.rows[ri].cells[ci];
      if (ai !== undefined) target.attributions[ai].text = value;
      else target.text = value;
    });
  }

  function appendRow() {
    table((b) => {
      const cell: Cell = { text: "", bold: false, align: "LEFT", background: "NONE", attributions: [], rowSpan: 1 };
      b.rows.push({ cells: Array.from({ length: columns }, () => ({ ...cell })), emphasized: false, rowBackground: "NONE", band: false });
    });
  }

  return (
    <div className="overflow-x-auto rounded-xl border border-border">
      <table className="w-full border-collapse text-[12.5px]">
        <thead>
          {block.bands.length > 0 && (
            <tr className="bg-primary-50 dark:bg-primary-500/10">
              {block.bands.map((band, i) => (
                <th key={i} colSpan={band.span} className="border border-border p-0.5">
                  <TextField ariaLabel="Intitulé de colonnes" value={band.label} onChange={(v) => table((b) => { b.bands[i].label = v; })} className="text-center font-semibold" />
                </th>
              ))}
              <th className="w-16" />
            </tr>
          )}
          {showsHeaders && (
            <tr className="bg-primary-50 dark:bg-primary-500/10">
              {block.columnHeaders.map((header, i) => (
                <th key={i} className="border border-border p-0.5 align-top" style={block.widths[i] ? { width: `${block.widths[i]}%` } : undefined}>
                  <TextField ariaLabel="En-tête de colonne" readOnly={locked(i)} value={header} onChange={(v) => table((b) => { b.columnHeaders[i] = v; })} className="font-semibold" />
                </th>
              ))}
              <th className="w-16" />
            </tr>
          )}
        </thead>
        <tbody>
          {block.rows.map((row, ri) => (
            <tr key={ri} className={cn("group", row.band && (BAND_CLASSES[row.rowBackground] ?? "bg-muted"), row.emphasized && !row.band && "font-semibold")}>
              {row.band ? (
                <td colSpan={columns} className="border border-border p-0.5">
                  <TextField ariaLabel="Intertitre du tableau" value={row.cells[0]?.text ?? ""} onChange={(v) => table((b) => { b.rows[ri].cells[0].text = v; })} className="font-semibold text-inherit" />
                </td>
              ) : (
                row.cells.map((cell, ci) =>
                  cell.rowSpan === 0 ? null : (
                    <td key={ci} rowSpan={cell.rowSpan > 1 ? cell.rowSpan : undefined} className={cn("border border-border p-0.5 align-top", cell.bold && "font-semibold")}>
                      {cell.attributions.length > 0 ? (
                        cell.attributions.map((a, ai) => (
                          <div key={ai} className="flex items-start gap-1">
                            <Dots colors={a.colorHexes} />
                            <TextField ariaLabel="Élément de la cellule" readOnly={locked(ci)} value={a.text} onChange={(v) => setCell(ri, ci, v, ai)} />
                          </div>
                        ))
                      ) : (
                        <TextField
                          ariaLabel="Cellule"
                          readOnly={locked(ci)}
                          value={cell.text}
                          onChange={(v) => setCell(ri, ci, v)}
                          className={cn(cell.align === "RIGHT" && "text-right", cell.align === "CENTER" && "text-center")}
                        />
                      )}
                    </td>
                  )
                )
              )}
              <td className="w-16 border-l border-border px-1 align-middle">
                {isSimpleRow(row) && (
                  <div className="flex gap-0.5 opacity-40 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100">
                    {lockedColumns === 0 && (
                      <button type="button" title="Ajouter une ligne vide en dessous" aria-label="Ajouter une ligne en dessous"
                        className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
                        onClick={() => table((b) => { b.rows.splice(ri + 1, 0, emptyCopy(b.rows[ri])); })}>
                        <Plus className="h-3.5 w-3.5" />
                      </button>
                    )}
                    <button type="button" title="Supprimer la ligne" aria-label="Supprimer la ligne"
                      className="rounded p-1 text-muted-foreground hover:bg-accent-50 hover:text-accent-600 dark:hover:bg-accent-500/15"
                      onClick={() => table((b) => { b.rows.splice(ri, 1); })}>
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {lockedColumns === 0 && (
        <button type="button" onClick={appendRow}
          className="flex w-full items-center justify-center gap-1 border-t border-border py-1.5 text-[12px] text-muted-foreground hover:bg-muted hover:text-foreground">
          <Plus className="h-3.5 w-3.5" /> Ajouter une ligne
        </button>
      )}
    </div>
  );
}
