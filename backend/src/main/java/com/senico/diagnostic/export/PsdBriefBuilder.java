package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.SectionDef;
import com.senico.diagnostic.domain.SectionResponse;
import com.senico.diagnostic.domain.SectionStatus;
import com.senico.diagnostic.domain.WorkGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compose la "Note de synthese" : trois a quatre pages qui resument l'ensemble des directions,
 * sans derouler les tableaux ni reprendre les rubriques direction par direction.
 *
 * <p>Repond a un besoin distinct des documents existants. Le plan sectoriel couvre une seule
 * direction ; le Document de consolidation et le Plan Strategique de SENICO reprennent toutes
 * les rubriques en detail. Aucun ne permettait de saisir l'ensemble d'un coup d'oeil, ce qu'on
 * attend pourtant d'un comite de pilotage.</p>
 *
 * <p>Perimetre : les sections soumises ou validees. Les brouillons en cours sont ecartes — une
 * note de synthese n'a pas a s'appuyer sur du travail que la direction n'a pas encore rendu.</p>
 */
@Component
@RequiredArgsConstructor
class PsdBriefBuilder {

    /** Au-dela, ce n'est plus une synthese : on garde les premiers elements cites. */
    private static final int MAX_ITEMS_PER_LIST = 8;

    private final ExportContentReader exportContentReader;

    List<ExportBlock> build(List<WorkGroup> groups, Map<String, SectionDef> sectionsByCode,
                            Map<String, SectionResponse> responsesByKey,
                            Map<String, GroupSectionStatus> statusesByKey) {

        Lookup lookup = new Lookup(sectionsByCode, responsesByKey, statusesByKey);
        int covered = (int) statusesByKey.values().stream().filter(PsdBriefBuilder::isSubmittedOrValidated).count();
        PsdKeyFigures figures = PsdKeyFigures.compute(groups, lookup::content, covered);

        List<ExportBlock> blocks = new ArrayList<>();

        blocks.add(new ExportBlock.Paragraph(
                "Résumé de l'ensemble des contributions des directions, sans le détail des tableaux. "
                        + "Porte sur les sections soumises ou validées ; les brouillons en cours sont écartés.",
                true, false));

        blocks.add(new ExportBlock.Heading("1. Chiffres clés", 2));
        blocks.add(keyFiguresTable(figures));

        blocks.add(new ExportBlock.Heading("2. Où nous en sommes", 2));
        blocks.add(mergedSwot(groups, lookup));

        blocks.add(new ExportBlock.Heading("3. Ce que nous voulons", 2));
        blocks.addAll(ambitions(groups, lookup));

        blocks.add(new ExportBlock.Heading("4. Avec quels moyens", 2));
        blocks.addAll(resources(groups, lookup, figures));

        blocks.add(new ExportBlock.Heading("5. Défis prioritaires", 2));
        blocks.add(new ExportBlock.BulletList(null, dedupedList(groups, lookup, "S06B", "challenges")));

        return blocks;
    }

    private static boolean isSubmittedOrValidated(GroupSectionStatus status) {
        SectionStatus value = status != null ? status.getStatus() : null;
        return value == SectionStatus.SUBMITTED || value == SectionStatus.VALIDATED;
    }

    private ExportBlock.Table keyFiguresTable(PsdKeyFigures f) {
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        rows.add(figure("Directions couvertes", String.valueOf(f.directions())));
        rows.add(figure("Sections résumées", String.valueOf(f.sectionsCovered())));
        rows.add(figure("Axes stratégiques", String.valueOf(f.axes())));
        rows.add(figure("Objectifs spécifiques", String.valueOf(f.specificObjectives())));
        rows.add(figure("Actions programmées", String.valueOf(f.actions())));
        rows.add(figure("Budget global", JsonUtil.formatCurrency(f.budget())));
        rows.add(figure("Financement mobilisé", JsonUtil.formatCurrency(f.financing())));
        rows.add(figure("Évolution des effectifs", f.staffEvolutionLabel()));
        return new ExportBlock.Table(List.of("Indicateur", "Valeur"), rows);
    }

    private ExportBlock.TableRow figure(String label, String value) {
        return new ExportBlock.TableRow(List.of(
                new ExportBlock.Cell(label),
                new ExportBlock.Cell(value, true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE)));
    }

    /** SWOT de toutes les directions fondu en un seul cadran : un element cite plusieurs fois n'apparait qu'une. */
    private ExportBlock.Quadrant mergedSwot(List<WorkGroup> groups, Lookup lookup) {
        List<ExportBlock.QuadrantCell> cells = new ArrayList<>();
        for (String[] field : new String[][]{
                {"strengths", "Forces"}, {"weaknesses", "Faiblesses"},
                {"opportunities", "Opportunités"}, {"threats", "Menaces"}}) {
            List<Map.Entry<WorkGroup, String>> entries = new ArrayList<>();
            for (WorkGroup group : groups) {
                for (String item : JsonUtil.strList(lookup.content(group, "S04"), field[0])) {
                    entries.add(Map.entry(group, item));
                }
            }
            List<String> merged = PsdCrossGroupMerge.merge(entries, false).stream()
                    .map(PsdCrossGroupMerge.MergedItem::text)
                    .limit(MAX_ITEMS_PER_LIST)
                    .toList();
            cells.add(new ExportBlock.QuadrantCell(field[1], merged));
        }
        return new ExportBlock.Quadrant(cells);
    }

    private List<ExportBlock> ambitions(List<WorkGroup> groups, Lookup lookup) {
        List<ExportBlock> blocks = new ArrayList<>();

        Map<String, String> axisTitles = new LinkedHashMap<>();
        Map<String, String> objectives = new LinkedHashMap<>();
        for (WorkGroup group : groups) {
            for (JsonNode axis : JsonUtil.arr(lookup.content(group, "S08"), "axes")) {
                putIfMeaningful(axisTitles, JsonUtil.text(axis, "title"));
                for (JsonNode objective : JsonUtil.arr(axis, "specificObjectives")) {
                    putIfMeaningful(objectives, objective.asText(""));
                }
            }
        }

        blocks.add(new ExportBlock.BulletList("Axes stratégiques", capped(axisTitles.values())));
        blocks.add(new ExportBlock.BulletList("Objectifs spécifiques", capped(objectives.values())));
        return blocks;
    }

    private List<ExportBlock> resources(List<WorkGroup> groups, Lookup lookup, PsdKeyFigures figures) {
        List<ExportBlock> blocks = new ArrayList<>();

        // Budget par axe : les montants des directions sur un meme axe sont additionnes.
        Map<String, Double> budgetByAxis = new LinkedHashMap<>();
        for (WorkGroup group : groups) {
            for (JsonNode axis : JsonUtil.arr(lookup.content(group, "S11"), "axes")) {
                String label = JsonUtil.text(axis, "axisTitle").isBlank()
                        ? JsonUtil.text(axis, "axisCode").replace("AXE", "Axe ")
                        : JsonUtil.text(axis, "axisTitle");
                budgetByAxis.merge(label, JsonUtil.num(axis, "axisTotal"), Double::sum);
            }
        }
        blocks.add(new ExportBlock.Heading("Budget par axe", 3));
        blocks.add(amountTable("Axe", budgetByAxis, figures.budget()));

        // Financement par source : meme principe, sur les cinq sources du canevas.
        Map<String, Double> financingBySource = new LinkedHashMap<>();
        for (WorkGroup group : groups) {
            for (JsonNode row : JsonUtil.arr(lookup.content(group, "S15"), "rows")) {
                financingBySource.merge(SectionLabels.financing(JsonUtil.text(row, "source")),
                        JsonUtil.num(row, "amount"), Double::sum);
            }
        }
        blocks.add(new ExportBlock.Heading("Origine du financement", 3));
        blocks.add(amountTable("Source", financingBySource, figures.financing()));
        return blocks;
    }

    private ExportBlock.Table amountTable(String firstHeader, Map<String, Double> amounts, double total) {
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        amounts.forEach((label, amount) -> rows.add(new ExportBlock.TableRow(List.of(
                new ExportBlock.Cell(label),
                new ExportBlock.Cell(JsonUtil.formatCurrency(amount), false, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE),
                new ExportBlock.Cell(total > 0 ? JsonUtil.formatPercent(amount / total * 100) : "—",
                        false, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE)))));
        rows.add(new ExportBlock.TableRow(List.of(
                new ExportBlock.Cell("TOTAL", true, ExportBlock.Align.LEFT, ExportBlock.Background.NONE),
                new ExportBlock.Cell(JsonUtil.formatCurrency(total), true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE),
                new ExportBlock.Cell(total > 0 ? "100 %" : "—", true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE)),
                true, ExportBlock.Background.PRIMARY_LIGHT));
        return new ExportBlock.Table(List.of(firstHeader, "Montant", "Part"), rows);
    }

    /** Elements d'un champ liste, fondus sur toutes les directions et dedoublonnes. */
    private List<String> dedupedList(List<WorkGroup> groups, Lookup lookup, String sectionCode, String field) {
        Map<String, String> byNormalized = new LinkedHashMap<>();
        for (WorkGroup group : groups) {
            for (JsonNode row : JsonUtil.arr(lookup.content(group, sectionCode), "rows")) {
                for (String item : JsonUtil.strList(row, field)) {
                    putIfMeaningful(byNormalized, item);
                }
            }
        }
        return capped(byNormalized.values());
    }

    private void putIfMeaningful(Map<String, String> target, String raw) {
        String text = raw == null ? "" : raw.trim();
        if (!text.isEmpty()) {
            target.putIfAbsent(PsdCrossGroupMerge.normalize(text), text);
        }
    }

    /**
     * Tronque la liste, en le disant. Sans cette derniere ligne, la note annoncerait
     * "Axes strategiques : 20" au-dessus d'une liste de huit, sans que le lecteur puisse
     * savoir s'il en manque.
     */
    private List<String> capped(java.util.Collection<String> values) {
        if (values.size() <= MAX_ITEMS_PER_LIST) {
            return List.copyOf(values);
        }
        List<String> shown = new ArrayList<>(values.stream().limit(MAX_ITEMS_PER_LIST).toList());
        shown.add("… et " + (values.size() - MAX_ITEMS_PER_LIST) + " autres");
        return shown;
    }

    /** Acces au contenu d'une section pour une direction, restreint aux sections soumises ou validees. */
    private final class Lookup {
        private final Map<String, SectionDef> sectionsByCode;
        private final Map<String, SectionResponse> responsesByKey;
        private final Map<String, GroupSectionStatus> statusesByKey;

        Lookup(Map<String, SectionDef> sectionsByCode, Map<String, SectionResponse> responsesByKey,
               Map<String, GroupSectionStatus> statusesByKey) {
            this.sectionsByCode = sectionsByCode;
            this.responsesByKey = responsesByKey;
            this.statusesByKey = statusesByKey;
        }

        JsonNode content(WorkGroup group, String sectionCode) {
            SectionDef section = sectionsByCode.get(sectionCode);
            if (section == null) {
                return JsonUtil.emptyObject();
            }
            String key = group.getId() + ":" + section.getId();
            if (!isSubmittedOrValidated(statusesByKey.get(key))) {
                return JsonUtil.emptyObject();
            }
            return exportContentReader.read(group.getId(), section, responsesByKey.get(key));
        }
    }
}
