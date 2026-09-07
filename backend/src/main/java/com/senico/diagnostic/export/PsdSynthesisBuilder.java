package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.senico.diagnostic.domain.SectionDef;
import com.senico.diagnostic.domain.SectionResponse;
import com.senico.diagnostic.domain.WorkGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compose la "Synthese du PSD" qui ouvre le Plan Strategique de SENICO : un recap chiffre de
 * l'ensemble des directions, calcule a partir de leurs saisies, la ou il fallait sinon
 * parcourir le document direction par direction pour se faire une idee.
 *
 * <p>Ne lit que les reponses qu'on lui passe. Le document consolide ne lui transmettant que les
 * sections validees, la synthese porte donc sur le meme perimetre que le reste du document :
 * elle n'annonce jamais des chiffres qui ne seraient pas retrouvables dans les pages suivantes.</p>
 */
@Component
@RequiredArgsConstructor
class PsdSynthesisBuilder {

    private final ExportContentReader exportContentReader;

    /**
     * @param groups          les directions, dans l'ordre d'affichage
     * @param sectionsByCode  referentiel des sections, indexe par code
     * @param responsesByKey  reponses retenues, indexees "groupId:sectionId"
     * @param validatedCount  nombre de sections validees, tous groupes confondus
     * @param totalCount      nombre total de sections attendues
     */
    List<ExportBlock> build(List<WorkGroup> groups, Map<String, SectionDef> sectionsByCode,
                            Map<String, SectionResponse> responsesByKey,
                            int validatedCount, int totalCount) {
        List<ExportBlock> blocks = new ArrayList<>();

        blocks.add(new ExportBlock.Paragraph(
                "Recap etabli automatiquement a partir des sections reprises dans ce document. "
                        + "Les chiffres ci-dessous agregent les " + groups.size() + " directions.",
                true, false));

        blocks.add(new ExportBlock.Heading("Chiffres clés", 3));
        blocks.add(keyFiguresTable(groups, sectionsByCode, responsesByKey, validatedCount, totalCount));

        List<String> challenges = collectChallenges(groups, sectionsByCode, responsesByKey);
        if (!challenges.isEmpty()) {
            blocks.add(new ExportBlock.Heading("Défis et enjeux prioritaires", 3));
            blocks.add(new ExportBlock.BulletList(null, challenges));
        }

        ExportBlock.Table visions = visionTable(groups, sectionsByCode, responsesByKey);
        if (!visions.rows().isEmpty()) {
            blocks.add(new ExportBlock.Heading("Vision par direction", 3));
            blocks.add(visions);
        }
        return blocks;
    }

    private ExportBlock.Table keyFiguresTable(List<WorkGroup> groups, Map<String, SectionDef> sectionsByCode,
                                              Map<String, SectionResponse> responsesByKey,
                                              int validatedCount, int totalCount) {
        int axes = 0;
        int specificObjectives = 0;
        int actions = 0;
        double budget = 0;
        double financing = 0;
        int staffFirstYear = 0;
        int staffLastYear = 0;

        String firstYear = SectionLabels.YEARS[0];
        String lastYear = SectionLabels.YEARS[SectionLabels.YEARS.length - 1];

        for (WorkGroup group : groups) {
            JsonNode strategicAxes = content(group, "S08", sectionsByCode, responsesByKey);
            for (JsonNode axis : JsonUtil.arr(strategicAxes, "axes")) {
                if (!JsonUtil.text(axis, "title").isBlank()) {
                    axes++;
                }
                specificObjectives += JsonUtil.arr(axis, "specificObjectives").size();
            }

            JsonNode actionPlan = content(group, "S10", sectionsByCode, responsesByKey);
            for (JsonNode axis : JsonUtil.arr(actionPlan, "axes")) {
                for (JsonNode effect : JsonUtil.arr(axis, "effects")) {
                    actions += JsonUtil.arr(effect, "rows").size();
                }
            }

            budget += JsonUtil.num(content(group, "S11", sectionsByCode, responsesByKey), "grandTotal");
            financing += JsonUtil.num(content(group, "S15", sectionsByCode, responsesByKey), "total");

            JsonNode staff = content(group, "S14B", sectionsByCode, responsesByKey);
            JsonNode totals = staff.get("totals");
            if (totals != null) {
                staffFirstYear += (int) JsonUtil.num(totals.get(firstYear), "total");
                staffLastYear += (int) JsonUtil.num(totals.get(lastYear), "total");
            }
        }

        List<ExportBlock.TableRow> rows = new ArrayList<>();
        rows.add(figure("Directions couvertes", String.valueOf(groups.size())));
        rows.add(figure("Sections validées", validatedCount + " / " + totalCount));
        rows.add(figure("Axes stratégiques", String.valueOf(axes)));
        rows.add(figure("Objectifs spécifiques", String.valueOf(specificObjectives)));
        rows.add(figure("Actions programmées " + firstYear + "-" + lastYear, String.valueOf(actions)));
        rows.add(figure("Budget global " + firstYear + "-" + lastYear, JsonUtil.formatCurrency(budget)));
        rows.add(figure("Financement mobilisé", JsonUtil.formatCurrency(financing)));
        // Effectifs a zero des deux cotes : la section n'est pas encore renseignee, une
        // evolution "0 -> 0" ne dirait rien, on l'annonce comme non renseignee.
        rows.add(figure("Effectifs " + firstYear + " → " + lastYear,
                staffFirstYear == 0 && staffLastYear == 0
                        ? "—"
                        : staffFirstYear + " → " + staffLastYear + " agents"));

        return new ExportBlock.Table(List.of("Indicateur", "Valeur"), rows);
    }

    private ExportBlock.TableRow figure(String label, String value) {
        return new ExportBlock.TableRow(List.of(
                new ExportBlock.Cell(label),
                new ExportBlock.Cell(value, true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE)));
    }

    /** Defis de la synthese des enjeux (S06B), dedoublonnes : plusieurs directions citent souvent le meme. */
    private List<String> collectChallenges(List<WorkGroup> groups, Map<String, SectionDef> sectionsByCode,
                                           Map<String, SectionResponse> responsesByKey) {
        Map<String, String> byNormalized = new LinkedHashMap<>();
        for (WorkGroup group : groups) {
            JsonNode constraints = content(group, "S06B", sectionsByCode, responsesByKey);
            for (JsonNode row : JsonUtil.arr(constraints, "rows")) {
                for (String challenge : JsonUtil.strList(row, "challenges")) {
                    String text = challenge.trim();
                    if (!text.isEmpty()) {
                        byNormalized.putIfAbsent(PsdCrossGroupMerge.normalize(text), text);
                    }
                }
            }
        }
        return new ArrayList<>(byNormalized.values());
    }

    private ExportBlock.Table visionTable(List<WorkGroup> groups, Map<String, SectionDef> sectionsByCode,
                                          Map<String, SectionResponse> responsesByKey) {
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        for (WorkGroup group : groups) {
            String vision = JsonUtil.text(content(group, "S07B", sectionsByCode, responsesByKey), "vision").trim();
            if (!vision.isEmpty()) {
                rows.add(new ExportBlock.TableRow(List.of(
                        new ExportBlock.Cell(group.getName()),
                        new ExportBlock.Cell(vision))));
            }
        }
        return new ExportBlock.Table(List.of("Direction", "Vision"), rows);
    }

    private JsonNode content(WorkGroup group, String sectionCode, Map<String, SectionDef> sectionsByCode,
                             Map<String, SectionResponse> responsesByKey) {
        SectionDef section = sectionsByCode.get(sectionCode);
        if (section == null) {
            return JsonUtil.emptyObject();
        }
        SectionResponse response = responsesByKey.get(group.getId() + ":" + section.getId());
        return exportContentReader.read(group.getId(), section, response);
    }
}
