"use client";

import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { FileDown, FileText } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { listGroups } from "@/lib/api/groups";
import { listNarrativeBlocks, updateNarrativeBlock } from "@/lib/api/psd-narrative";
import { downloadPsdFinalPdf, downloadPsdFinalWord } from "@/lib/api/exports";
import { extractErrorMessage } from "@/lib/api-client";
import type { NarrativeBlock } from "@/types/psd-narrative";

export default function AdminPsdFinalPage() {
  const [exportingPdf, setExportingPdf] = useState(false);
  const [exportingWord, setExportingWord] = useState(false);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState<Record<string, boolean>>({});

  const { data: groups } = useQuery({ queryKey: ["admin", "groups"], queryFn: listGroups });
  const { data: blocks, refetch } = useQuery({ queryKey: ["admin", "psd-narrative"], queryFn: listNarrativeBlocks });

  useEffect(() => {
    if (!blocks) return;
    setDrafts((prev) => {
      const next = { ...prev };
      for (const block of blocks) {
        if (next[block.key] === undefined) next[block.key] = block.content ?? "";
      }
      return next;
    });
  }, [blocks]);

  async function handleSave(block: NarrativeBlock) {
    setSaving((s) => ({ ...s, [block.key]: true }));
    try {
      await updateNarrativeBlock(block.key, drafts[block.key] ?? "");
      toast.success(`« ${block.label} » enregistré`);
      await refetch();
    } catch (error) {
      toast.error(extractErrorMessage(error, "Échec de l'enregistrement"));
    } finally {
      setSaving((s) => ({ ...s, [block.key]: false }));
    }
  }

  async function handleExportPdf() {
    setExportingPdf(true);
    try {
      await downloadPsdFinalPdf();
    } catch (error) {
      toast.error(extractErrorMessage(error, "Échec de l'export PDF"));
    } finally {
      setExportingPdf(false);
    }
  }

  async function handleExportWord() {
    setExportingWord(true);
    try {
      await downloadPsdFinalWord();
    } catch (error) {
      toast.error(extractErrorMessage(error, "Échec de l'export Word"));
    } finally {
      setExportingWord(false);
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1>Document final PSD 2027-2031</h1>
        <p className="text-[13px] text-muted-foreground mt-1">
          Reprend le sommaire officiel du PSD (Mot du DG, Préambule, Cadre stratégique, Cadre de mise en œuvre…) en
          combinant le texte ci-dessous avec les tableaux déjà saisis dans le canevas, identifiés par la couleur de
          chaque direction.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Télécharger le document final</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap gap-3">
            <Button variant="primary" onClick={handleExportPdf} loading={exportingPdf}>
              <FileDown className="h-4 w-4" /> Télécharger le PDF
            </Button>
            <Button variant="secondary" onClick={handleExportWord} loading={exportingWord}>
              <FileText className="h-4 w-4" /> Télécharger le Word
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Contenu narratif</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          {(blocks ?? []).map((block) => (
            <div key={block.key} className="space-y-2">
              <Label htmlFor={`narrative-${block.key}`}>{block.label}</Label>
              <Textarea
                id={`narrative-${block.key}`}
                rows={4}
                value={drafts[block.key] ?? ""}
                onChange={(e) => setDrafts((d) => ({ ...d, [block.key]: e.target.value }))}
                placeholder={`Saisir le texte « ${block.label} »…`}
              />
              <div className="flex justify-end">
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => handleSave(block)}
                  loading={saving[block.key] ?? false}
                >
                  Enregistrer
                </Button>
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Légende des directions</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-4">
            {(groups ?? []).map((g) => (
              <span key={g.id} className="flex items-center gap-2 text-[13px] text-foreground">
                <span
                  className="h-3 w-3 shrink-0 rounded-full border border-border/60"
                  style={{ backgroundColor: g.color ?? "transparent" }}
                  title={g.color ?? undefined}
                />
                {g.name}
              </span>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
