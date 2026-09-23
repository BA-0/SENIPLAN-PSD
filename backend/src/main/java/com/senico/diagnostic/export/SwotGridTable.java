package com.senico.diagnostic.export;

import java.util.ArrayList;
import java.util.List;

/**
 * Tableau SWOT (FFOM) sur la grille du canevas : l'environnement INTERNE (fusionne sur deux lignes) porte
 * l'intitule « FORCES | FAIBLESSES » puis leurs elements ; l'environnement EXTERNE, de meme,
 * « OPPORTUNITÉS | MENACES » puis leurs elements.
 * Les saisies historiques ventilees en interne/externe par categorie sont regroupees dans leur categorie.
 * Partage par l'export d'une section et par la note de synthese, qui ne different que par le contenu des cellules.
 */
final class SwotGridTable {

    /**
     * Les huit listes du SWOT, dans l'ordre de lecture de la grille. Les champs historiques (strengths,
     * weaknesses, opportunities, threats) gardent leur environnement naturel ; les quatre autres completent
     * la grille.
     */
    static final List<String> FIELDS = List.of(
            "strengths", "weaknesses",
            "strengthsExternal", "weaknessesExternal",
            "opportunitiesInternal", "threatsInternal",
            "opportunities", "threats");

    /** Les quatre categories du SWOT, chacune avec son champ interne puis son champ externe. */
    static final List<Category> CATEGORIES = List.of(
            new Category("Force", "Forces", "strengths", "strengthsExternal"),
            new Category("Faiblesse", "Faiblesses", "weaknesses", "weaknessesExternal"),
            new Category("Opportunité", "Opportunités", "opportunitiesInternal", "opportunities"),
            new Category("Menace", "Menaces", "threatsInternal", "threats"));

    record Category(String singular, String plural, String internalField, String externalField) {
        List<String> fields() {
            return List.of(internalField, externalField);
        }
    }

    private SwotGridTable() {
    }

    /** @param cells les huit cellules, dans l'ordre de {@link #FIELDS}. */
    static ExportBlock.Table build(List<ExportBlock.Cell> cells) {
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        rows.add(new ExportBlock.TableRow(List.of(environment("INTERNE").spanning(2),
                heading("FORCES"), heading("FAIBLESSES"))));
        rows.add(new ExportBlock.TableRow(List.of(ExportBlock.Cell.covered(),
                merge(cells.get(0), cells.get(2)), merge(cells.get(1), cells.get(3)))));
        rows.add(new ExportBlock.TableRow(List.of(environment("EXTERNE").spanning(2),
                heading("OPPORTUNITÉS"), heading("MENACES"))));
        rows.add(new ExportBlock.TableRow(List.of(ExportBlock.Cell.covered(),
                merge(cells.get(4), cells.get(6)), merge(cells.get(5), cells.get(7)))));
        return new ExportBlock.Table(List.of("Environnement", "", ""), rows, List.of(16, 42, 42));
    }

    /** Reunit les elements internes et externes d'une categorie ; « — » quand les deux sont vides. */
    private static ExportBlock.Cell merge(ExportBlock.Cell internal, ExportBlock.Cell external) {
        List<ExportBlock.Attribution> items = new ArrayList<>(internal.attributions());
        external.attributions().stream().filter(item -> !items.contains(item)).forEach(items::add);
        return items.isEmpty() ? new ExportBlock.Cell("—") : new ExportBlock.Cell(items);
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
