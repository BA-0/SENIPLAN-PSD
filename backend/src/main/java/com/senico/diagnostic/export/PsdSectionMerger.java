package com.senico.diagnostic.export;

import com.senico.diagnostic.domain.WorkGroup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fusionne, pour une meme section, les rendus des differentes directions en un tableau unique
 * portant une colonne "Direction".
 *
 * <p>Le document consolide repetait chaque rubrique autant de fois qu'il y a de directions :
 * pour lire le budget de SENICO il fallait parcourir cinq blocs successifs et faire les totaux
 * de tete. Les tableaux de meme structure, sous les memes intertitres, sont desormais empiles
 * dans un seul tableau, une direction par ligne.</p>
 *
 * <p>Ce qui n'est pas un tableau (listes a puces du cadre strategique, paragraphes de total)
 * n'est pas fusionnable ligne a ligne : ces blocs restent presentes direction par direction,
 * sous un intertitre portant le nom de la direction.</p>
 */
final class PsdSectionMerger {

    private static final String DIRECTION_COLUMN = "Direction";

    private PsdSectionMerger() {
    }

    /** Un rendu de section pour une direction donnee : ses blocs, ou la raison de son absence. */
    record GroupBlocks(WorkGroup group, List<ExportBlock> blocks, boolean included, String exclusionReason) {
    }

    static List<ExportBlock> merge(List<GroupBlocks> perGroup) {
        // Ordonne par premiere apparition : l'ordre du canevas est celui de la premiere
        // direction qui a rempli la section, les suivantes viennent s'y ranger.
        Map<String, Object> slots = new LinkedHashMap<>();
        List<ExportBlock> trailing = new ArrayList<>();
        List<String> excluded = new ArrayList<>();

        for (GroupBlocks entry : perGroup) {
            if (!entry.included()) {
                excluded.add(entry.group().getName() + " (" + entry.exclusionReason() + ")");
                continue;
            }
            mergeOneGroup(entry, slots, trailing);
        }

        List<ExportBlock> out = new ArrayList<>();
        for (Object slot : slots.values()) {
            if (slot instanceof ExportBlock.Heading heading) {
                out.add(heading);
            } else if (slot instanceof MergedTable table) {
                out.add(table.toBlock());
            }
        }
        out.addAll(trailing);

        if (!excluded.isEmpty()) {
            out.add(new ExportBlock.Paragraph(
                    "Non repris dans ce tableau : " + String.join(", ", excluded) + ".", true, false));
        }
        if (out.isEmpty()) {
            out.add(new ExportBlock.Paragraph("Aucune donnée reprise pour cette section.", true, false));
        }
        return out;
    }

    private static void mergeOneGroup(GroupBlocks entry, Map<String, Object> slots, List<ExportBlock> trailing) {
        List<String> headingPath = new ArrayList<>();
        boolean directionHeaderEmitted = false;

        for (ExportBlock block : entry.blocks()) {
            if (block instanceof ExportBlock.KeyValueList kv && kv.boxed()) {
                continue; // encart de metadonnees : sans objet dans un tableau fusionne
            }
            if (block instanceof ExportBlock.Heading heading) {
                setHeadingPath(headingPath, heading);
                slots.putIfAbsent("H:" + String.join(" > ", headingPath), heading);
            } else if (block instanceof ExportBlock.Table table) {
                String key = "T:" + String.join(" > ", headingPath) + "|" + String.join("~", table.columnHeaders());
                MergedTable merged = (MergedTable) slots.computeIfAbsent(key, k -> new MergedTable(table));
                merged.append(entry.group(), table);
            } else {
                if (!directionHeaderEmitted) {
                    trailing.add(new ExportBlock.Heading(entry.group().getName(), 4));
                    directionHeaderEmitted = true;
                }
                trailing.add(block);
            }
        }
    }

    /**
     * Maintient le chemin d'intertitres courant. Un titre de niveau n remplace le niveau n et
     * tout ce qui suit : c'est ce qui distingue le tableau "Axe 1 / Effet 2" de "Axe 2 / Effet 2",
     * qui ont pourtant les memes colonnes et ne doivent pas se retrouver fusionnes.
     */
    private static void setHeadingPath(List<String> path, ExportBlock.Heading heading) {
        int depth = Math.max(0, heading.level() - 2);
        while (path.size() > depth) {
            path.remove(path.size() - 1);
        }
        while (path.size() < depth) {
            path.add("");
        }
        path.add(heading.text());
    }

    /**
     * Tableau en cours de constitution : les colonnes d'origine, precedees de la direction. La
     * ligne d'intitules qui coiffe les colonnes (« 2027 » au-dessus de M, F, Total) et les largeurs
     * suivent : sans elles, un tableau d'effectifs ne dirait plus a quelle annee se rapporte chaque
     * colonne.
     */
    private static final class MergedTable {
        private static final int DIRECTION_WIDTH = 14;

        private final List<String> headers = new ArrayList<>();
        private final List<ExportBlock.TableRow> rows = new ArrayList<>();
        private final List<Integer> widths = new ArrayList<>();
        private final List<ExportBlock.HeaderBand> bands = new ArrayList<>();

        MergedTable(ExportBlock.Table original) {
            headers.add(DIRECTION_COLUMN);
            headers.addAll(original.columnHeaders());
            if (original.widths() != null && !original.widths().isEmpty()) {
                widths.add(DIRECTION_WIDTH);
                widths.addAll(original.widths());
            }
            if (original.bands() != null && !original.bands().isEmpty()) {
                bands.add(new ExportBlock.HeaderBand("", 1));
                bands.addAll(original.bands());
            }
        }

        void append(WorkGroup group, ExportBlock.Table table) {
            for (ExportBlock.TableRow row : table.rows()) {
                if (row.band()) {
                    // L'intertitre (« Hiérarchie ») garde toute la largeur ; il dit en plus de quelle direction il s'agit.
                    String text = row.cells().isEmpty() ? "" : row.cells().get(0).text();
                    rows.add(ExportBlock.TableRow.band(group.getName() + " — " + text, row.rowBackground()));
                    continue;
                }
                List<ExportBlock.Cell> cells = new ArrayList<>();
                cells.add(new ExportBlock.Cell(group.getName()));
                cells.addAll(row.cells());
                rows.add(new ExportBlock.TableRow(cells, row.emphasized(), row.rowBackground()));
            }
        }

        ExportBlock.Table toBlock() {
            return new ExportBlock.Table(headers, rows, widths, bands);
        }
    }
}
