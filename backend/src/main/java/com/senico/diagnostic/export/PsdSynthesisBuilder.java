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
 * sections approuvees par le DG, la synthese porte donc sur le meme perimetre que le reste du
 * document : elle n'annonce jamais des chiffres qui ne seraient pas retrouvables dans les pages
 * suivantes.</p>
 */
@Component
@RequiredArgsConstructor
class PsdSynthesisBuilder {

    private final ExportContentReader exportContentReader;

    /**
     * @param groups          les directions, dans l'ordre d'affichage
     * @param sectionsByCode  referentiel des sections, indexe par code
     * @param responsesByKey  reponses retenues, indexees "groupId:sectionId"
     * @param approvedCount   nombre de sections approuvees par le DG, tous groupes confondus
     * @param totalCount      nombre total de sections attendues
     */
    List<ExportBlock> build(List<WorkGroup> groups, Map<String, SectionDef> sectionsByCode,
                            Map<String, SectionResponse> responsesByKey,
                            int approvedCount, int totalCount) {
        List<ExportBlock> blocks = new ArrayList<>();

        blocks.add(new ExportBlock.Paragraph(
                "Recap etabli automatiquement a partir des sections reprises dans ce document. "
                        + "Les chiffres ci-dessous agregent les " + groups.size() + " directions.",
                true, false));

        blocks.add(new ExportBlock.Heading("Chiffres clés", 3));
        blocks.add(keyFiguresTable(groups, sectionsByCode, responsesByKey, approvedCount, totalCount));

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
                                              int approvedCount, int totalCount) {
        PsdKeyFigures f = PsdKeyFigures.compute(groups,
                (group, code) -> content(group, code, sectionsByCode, responsesByKey), approvedCount);

        List<ExportBlock.TableRow> rows = new ArrayList<>();
        rows.add(figure("Directions couvertes", String.valueOf(f.directions())));
        rows.add(figure("Sections approuvées par la DG", approvedCount + " / " + totalCount));
        rows.add(figure("Axes stratégiques", String.valueOf(f.axes())));
        rows.add(figure("Objectifs spécifiques", String.valueOf(f.specificObjectives())));
        rows.add(figure("Actions programmées " + SectionLabels.YEARS[0]
                + "-" + SectionLabels.YEARS[SectionLabels.YEARS.length - 1], String.valueOf(f.actions())));
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
