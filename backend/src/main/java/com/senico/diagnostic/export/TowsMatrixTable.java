package com.senico.diagnostic.export;

import java.util.List;
import java.util.function.Function;

/**
 * Matrice de mise en relation du diagnostic (S05), sur la grille du canevas : l'approche interne coiffe
 * les colonnes (liste des forces, liste des faiblesses), l'approche externe les lignes (liste des
 * opportunites, liste des menaces). Partagee par l'export d'une section et par la note de synthese, qui
 * ne different que par le contenu des cellules de reponse.
 */
final class TowsMatrixTable {

    private TowsMatrixTable() {
    }

    /** {@code answer} donne la cellule de reponse d'un champ S05 (maximizeStrengths, ...). */
    static ExportBlock.Table build(Function<String, ExportBlock.Cell> answer) {
        ExportBlock.Cell none = new ExportBlock.Cell("", ExportBlock.Background.GREY);
        ExportBlock.Cell covered = ExportBlock.Cell.covered();
        List<ExportBlock.TableRow> rows = List.of(
                new ExportBlock.TableRow(List.of(none, none, none,
                        answer.apply("maximizeStrengths"), answer.apply("minimizeWeaknesses"),
                        answer.apply("strengthsControlWeaknesses"))),
                new ExportBlock.TableRow(List.of(label("Approche externe").spanning(3), label("Liste des opportunités"),
                        answer.apply("maximizeOpportunities"), answer.apply("strengthsForOpportunities"),
                        answer.apply("correctWeaknessesViaOpportunities"), none)),
                new ExportBlock.TableRow(List.of(covered, label("Liste des menaces"),
                        answer.apply("minimizeThreats"), answer.apply("strengthsReduceThreats"),
                        answer.apply("minimizeWeaknessesAndThreats"), none)),
                new ExportBlock.TableRow(List.of(covered,
                        label(SectionLabels.TOWS_ACTION_LABELS.get("opportunitiesMinimizeThreats")),
                        answer.apply("opportunitiesMinimizeThreats"), none, none, none)));
        return new ExportBlock.Table(
                List.of("", "", "", "Liste des forces", "Liste des faiblesses",
                        SectionLabels.TOWS_ACTION_LABELS.get("strengthsControlWeaknesses")),
                rows, List.of(8, 15, 19, 19, 19, 20),
                List.of(new ExportBlock.HeaderBand("", 3), new ExportBlock.HeaderBand("Approche interne", 3)));
    }

    private static ExportBlock.Cell label(String text) {
        return new ExportBlock.Cell(text, true, ExportBlock.Align.LEFT, ExportBlock.Background.GREY);
    }
}
