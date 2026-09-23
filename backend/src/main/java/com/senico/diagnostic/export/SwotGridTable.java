package com.senico.diagnostic.export;

import java.util.List;

/**
 * Tableau SWOT (FFOM) sur la grille du canevas : une colonne « Environnement » ; la ligne INTERNE porte les
 * forces et les faiblesses, puis, sous l'intitule « OPPORTUNITÉS | MENACES », la ligne EXTERNE. Partage par
 * l'export d'une section et par la note de synthese, qui ne different que par le contenu des cellules.
 */
final class SwotGridTable {

    private SwotGridTable() {
    }

    static ExportBlock.Table build(ExportBlock.Cell strengths, ExportBlock.Cell weaknesses,
                                   ExportBlock.Cell opportunities, ExportBlock.Cell threats) {
        List<ExportBlock.TableRow> rows = List.of(
                new ExportBlock.TableRow(List.of(environment("INTERNE").spanning(2), strengths, weaknesses)),
                new ExportBlock.TableRow(List.of(ExportBlock.Cell.covered(), heading("OPPORTUNITÉS"), heading("MENACES"))),
                new ExportBlock.TableRow(List.of(environment("EXTERNE"), opportunities, threats)));
        return new ExportBlock.Table(List.of("Environnement", "FORCES", "FAIBLESSES"), rows, List.of(16, 42, 42));
    }

    /** Liste simple (sans couleur de direction), une puce par element. */
    static ExportBlock.Cell list(List<String> items) {
        return items.isEmpty() ? new ExportBlock.Cell("—")
                : new ExportBlock.Cell(items.stream().map(ExportBlock.Attribution::new).toList());
    }

    private static ExportBlock.Cell environment(String text) {
        return new ExportBlock.Cell(text, true, ExportBlock.Align.CENTER, ExportBlock.Background.GREY);
    }

    private static ExportBlock.Cell heading(String text) {
        return new ExportBlock.Cell(text, true, ExportBlock.Align.CENTER, ExportBlock.Background.GREY);
    }
}
