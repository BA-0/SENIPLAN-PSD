package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.senico.diagnostic.validation.DefaultSectionContentFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Les deux tableaux du bilan des performances (S01B), partages par la note de synthese et le Plan
 * Strategique de SENICO : les cinq exercices ecoules regroupes dans un seul tableau, un bandeau par
 * exercice pour les distinguer, puis l'exercice en cours dans le sien, chaque indicateur portant sa
 * tendance. Les deux tableaux ont exactement les sept colonnes du modele client.
 *
 * <p>La note colorie l'indicateur a la couleur de sa direction, le Plan par direction n'en a pas
 * besoin : la cellule de l'indicateur est fournie par l'appelant ({@link Line}).</p>
 */
final class PerformanceReviewTables {

    /** Exercice en cours : ses resultats attendus en decembre sont des projections tant qu'il n'est pas clos. */
    static final int REVIEW_YEAR = DefaultSectionContentFactory.REVIEW_YEAR;
    static final int[] PAST_YEARS = DefaultSectionContentFactory.PAST_REVIEW_YEARS;

    static final String PAST_TITLE = "Performances des années passées";
    static final String CURRENT_TITLE = "Performances de l'année " + REVIEW_YEAR + " et tendances";

    static final List<String> HEADERS = List.of("Objectif", "Indicateur", "Résultat attendu en décembre", "Écart",
            "Cause", "Cause profonde", "Action entreprise");
    static final List<Integer> WIDTHS = List.of(15, 16, 12, 12, 15, 15, 15);

    /** Une ligne du bilan et la cellule de son indicateur (attribuee a sa direction dans la note, simple ailleurs). */
    record Line(JsonNode row, ExportBlock.Cell indicator) {
    }

    private PerformanceReviewTables() {
    }

    /** Ligne sans attribution de direction : l'indicateur en texte simple. */
    static Line plain(JsonNode row) {
        return new Line(row, new ExportBlock.Cell(JsonUtil.dash(JsonUtil.text(row, "indicator").trim())));
    }

    /** Une ligne sans objectif ni indicateur n'apprend rien au lecteur : elle n'est pas rendue. */
    static boolean hasContent(JsonNode row) {
        return !JsonUtil.text(row, "indicator").isBlank() || !JsonUtil.text(row, "objective").isBlank();
    }

    /** Exercice d'une ligne ; une ligne saisie avec l'ancien tableau, sans exercice, est celle de l'annee en cours. */
    static int year(JsonNode row) {
        JsonNode year = row == null ? null : row.get("year");
        if (year == null || year.isNull()) {
            return REVIEW_YEAR;
        }
        if (year.isNumber()) {
            return year.asInt();
        }
        try {
            return Integer.parseInt(year.asText().trim());
        } catch (NumberFormatException e) {
            return REVIEW_YEAR;
        }
    }

    static boolean isPast(JsonNode row) {
        return year(row) < REVIEW_YEAR;
    }

    /**
     * Les deux tableaux sous leurs intertitres, puis la lecture des tendances de l'exercice en cours :
     * la forme du Plan Strategique, ou le bilan de chaque direction se lit d'un bloc. Le document
     * consolide empile ensuite ces tableaux direction par direction (cf. PsdSectionMerger) : seuls les
     * exercices renseignes y ont leur bandeau, sinon douze directions y aligneraient soixante bandeaux vides.
     */
    static List<ExportBlock> blocks(List<Line> lines, boolean reviewYearClosed) {
        List<ExportBlock> blocks = new ArrayList<>();
        blocks.add(new ExportBlock.Heading(PAST_TITLE, 3));
        blocks.add(pastYears(lines, false));
        blocks.add(new ExportBlock.Heading(CURRENT_TITLE, 3));
        blocks.add(currentYear(lines));
        ExportBlock.Callout trends = trendAnalysis(lines, reviewYearClosed);
        if (trends != null) {
            blocks.add(trends);
        }
        return blocks;
    }

    /**
     * Les exercices ecoules dans un seul tableau : un bandeau par exercice, du plus ancien au plus recent.
     *
     * @param everyYear vrai pour la note de synthese, ou chacun des cinq exercices garde son bandeau et,
     *                  sans ligne, une ligne de tirets : le lecteur voit qu'il n'a rien ete releve plutot
     *                  qu'un exercice oublie ; faux pour le rendu d'une direction, qui ne montre que les
     *                  exercices qu'elle a renseignes (une seule ligne de tirets si aucun)
     */
    static ExportBlock.Table pastYears(List<Line> lines, boolean everyYear) {
        Map<Integer, List<Line>> byYear = new TreeMap<>();
        if (everyYear) {
            for (int year : PAST_YEARS) {
                byYear.put(year, new ArrayList<>());
            }
        }
        for (Line line : lines) {
            if (isPast(line.row())) {
                byYear.computeIfAbsent(year(line.row()), k -> new ArrayList<>()).add(line);
            }
        }
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        for (Map.Entry<Integer, List<Line>> entry : byYear.entrySet()) {
            rows.add(ExportBlock.TableRow.band("Exercice " + entry.getKey(), ExportBlock.Background.GREY));
            if (entry.getValue().isEmpty()) {
                rows.add(emptyRow());
            }
            for (Line line : entry.getValue()) {
                rows.add(row(line, false));
            }
        }
        if (rows.isEmpty()) {
            rows.add(emptyRow());
        }
        return new ExportBlock.Table(HEADERS, rows, WIDTHS);
    }

    /** L'exercice en cours dans son propre tableau, la tendance de chaque indicateur dans la colonne de l'ecart. */
    static ExportBlock.Table currentYear(List<Line> lines) {
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        for (Line line : lines) {
            if (!isPast(line.row())) {
                rows.add(row(line, true));
            }
        }
        if (rows.isEmpty()) {
            rows.add(emptyRow());
        }
        return new ExportBlock.Table(HEADERS, rows, WIDTHS);
    }

    /**
     * Lecture des tendances de l'exercice en cours : combien d'indicateurs vont vers leur cible de
     * decembre, combien sont stables, combien s'en eloignent ; et le statut de ces chiffres, projections
     * a date tant que l'exercice n'est pas clos. Nul sans ligne pour l'exercice.
     */
    static ExportBlock.Callout trendAnalysis(List<Line> lines, boolean reviewYearClosed) {
        int total = 0;
        int favorable = 0;
        int stable = 0;
        int unfavorable = 0;
        for (Line line : lines) {
            if (isPast(line.row())) {
                continue;
            }
            total++;
            switch (JsonUtil.text(line.row(), "trend").trim()) {
                case "FAVORABLE" -> favorable++;
                case "STABLE" -> stable++;
                case "DEFAVORABLE" -> unfavorable++;
                default -> {
                }
            }
        }
        if (total == 0) {
            return null;
        }
        int unset = total - favorable - stable - unfavorable;
        StringBuilder text = new StringBuilder("Analyse : tendances " + REVIEW_YEAR + " — sur " + total
                + (total > 1 ? " indicateurs, " : " indicateur, ") + favorable + " à tendance favorable, "
                + stable + (stable > 1 ? " stables" : " stable") + " et " + unfavorable + " à tendance défavorable");
        if (unset > 0) {
            text.append(", ").append(unset).append(" sans tendance renseignée");
        }
        text.append(". ");
        text.append(reviewYearClosed
                ? "L'exercice " + REVIEW_YEAR + " est clos : les résultats attendus en décembre se lisent comme des réalisations."
                : "L'exercice " + REVIEW_YEAR + " n'étant pas clos, les résultats attendus en décembre sont des projections à date.");
        return new ExportBlock.Callout(text.toString());
    }

    /** « favorable », « stable », « défavorable » ; vide pour une tendance non renseignee ou inconnue. */
    static String trendLabel(String trend) {
        return switch (trend == null ? "" : trend.trim()) {
            case "FAVORABLE" -> "favorable";
            case "STABLE" -> "stable";
            case "DEFAVORABLE" -> "défavorable";
            default -> "";
        };
    }

    private static ExportBlock.Background trendBackground(String trend) {
        return switch (trend) {
            case "FAVORABLE" -> ExportBlock.Background.GREEN;
            case "STABLE" -> ExportBlock.Background.GREY;
            case "DEFAVORABLE" -> ExportBlock.Background.ORANGE;
            default -> ExportBlock.Background.NONE;
        };
    }

    private static ExportBlock.TableRow row(Line line, boolean withTrend) {
        JsonNode row = line.row();
        String gap = JsonUtil.text(row, "gap").trim();
        String trend = withTrend ? JsonUtil.text(row, "trend").trim() : "";
        String trendLabel = trendLabel(trend);
        ExportBlock.Cell gapCell = trendLabel.isEmpty()
                ? new ExportBlock.Cell(JsonUtil.dash(gap))
                : new ExportBlock.Cell(gap.isEmpty() ? "Tendance " + trendLabel : gap + " (tendance " + trendLabel + ")",
                        trendBackground(trend));
        return new ExportBlock.TableRow(List.of(
                new ExportBlock.Cell(JsonUtil.dash(JsonUtil.text(row, "objective").trim())),
                line.indicator(),
                new ExportBlock.Cell(JsonUtil.dash(JsonUtil.text(row, "expectedResult").trim())),
                gapCell,
                new ExportBlock.Cell(JsonUtil.dash(JsonUtil.text(row, "cause").trim())),
                new ExportBlock.Cell(JsonUtil.dash(JsonUtil.text(row, "rootCause").trim())),
                new ExportBlock.Cell(JsonUtil.dash(JsonUtil.text(row, "action").trim()))));
    }

    private static ExportBlock.TableRow emptyRow() {
        List<ExportBlock.Cell> cells = new ArrayList<>();
        for (int i = 0; i < HEADERS.size(); i++) {
            cells.add(new ExportBlock.Cell("—"));
        }
        return new ExportBlock.TableRow(cells);
    }
}
