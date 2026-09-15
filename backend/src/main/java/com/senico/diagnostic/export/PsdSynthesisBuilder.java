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
     * @param strategicAxes   axes strategiques de l'entreprise (cf. PsdBriefBuilder#strategicAxisCount)
     */
    List<ExportBlock> build(List<WorkGroup> groups, Map<String, SectionDef> sectionsByCode,
                            Map<String, SectionResponse> responsesByKey,
                            int approvedCount, int strategicAxes) {
        List<ExportBlock> blocks = new ArrayList<>();

        blocks.add(new ExportBlock.Paragraph(
                "Chiffres consolidés à partir des sections reprises dans ce document, pour l'ensemble des "
                        + groups.size() + " directions.",
                true, false));

        blocks.add(new ExportBlock.Heading("Chiffres clés", 3));
        blocks.add(keyFiguresTable(groups, sectionsByCode, responsesByKey, approvedCount, strategicAxes));

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
                                              int approvedCount, int strategicAxes) {
        PsdKeyFigures f = PsdKeyFigures.compute(groups,
                (group, code) -> content(group, code, sectionsByCode, responsesByKey), approvedCount);

        List<ExportBlock.TableRow> rows = new ArrayList<>();
        rows.add(figure("Directions couvertes", f.contributorsLabel()));
        // Pas de « sections approuvees par la DG, 110 / 110 » : un compteur de l'application, sans
        // objet dans un plan remis au Conseil d'Administration.
        // Les axes de l'entreprise, que le cadre strategique numerote : compter ici ceux des directions
        // annoncait 20 axes la ou le plan en presente 5.
        rows.add(figure("Axes stratégiques", String.valueOf(strategicAxes)));
        if (f.axes() != strategicAxes) {
            rows.add(figure("Axes d'intervention des directions", String.valueOf(f.axes())));
        }
        rows.add(figure("Objectifs spécifiques", String.valueOf(f.specificObjectives())));
        rows.add(figure("Actions programmées " + SectionLabels.YEARS[0]
                + "-" + SectionLabels.YEARS[SectionLabels.YEARS.length - 1], String.valueOf(f.actions())));
        rows.add(figure("Budget global", JsonUtil.formatCurrency(f.budget())));
        // « Identifie », pas « mobilise » : le plan de financement prevoit, rien n'est encore obtenu.
        rows.add(figure("Financement identifié", JsonUtil.formatCurrency(f.financing())));
        if (f.budget() > 0 && f.financing() < f.budget()) {
            double gap = f.budget() - f.financing();
            rows.add(figure("Financement restant à mobiliser", JsonUtil.formatCurrency(gap)
                    + " (" + JsonUtil.formatPercent(gap / f.budget() * 100) + " du budget)"));
        }
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
