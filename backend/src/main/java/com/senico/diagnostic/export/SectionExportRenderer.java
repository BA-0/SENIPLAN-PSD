package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.SectionStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Traduit le JSON libre d'une SectionResponse en une List&lt;ExportBlock&gt; type-aware
 * (tableaux, quadrants SWOT, listes a puces...), une methode par SectionType. Ne depend
 * d'aucune API PDF/Word : PdfBlockEmitter/WordBlockEmitter se chargent de la mise en page.
 */
@Component
public class SectionExportRenderer {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Map<String, String> STATUS_LABELS = Map.of(
            "NOT_STARTED", "Non commencé",
            "IN_PROGRESS", "En cours",
            "SUBMITTED", "Soumis",
            "VALIDATED", "Validé",
            "REVISION_REQUESTED", "À réviser"
    );

    public List<ExportBlock> render(ExportSectionData data) {
        List<ExportBlock> blocks = new ArrayList<>();
        blocks.add(renderMetadataBox(data));

        JsonNode content = data.content();
        if (content == null || content.isNull() || content.isEmpty()) {
            blocks.add(new ExportBlock.Paragraph("Aucune donnée saisie pour cette section.", true, false));
            return blocks;
        }

        blocks.addAll(switch (data.section().getType()) {
            case STAKEHOLDERS -> List.of(RowsTableRenderer.render(JsonUtil.arr(content, "rows"), stakeholdersColumns()));
            case RESOURCES_MATRIX -> renderResourcesMatrix(content);
            case PESTEL -> List.of(RowsTableRenderer.render(JsonUtil.arr(content, "rows"), pestelColumns()));
            case SWOT -> List.of(swotQuadrant(content));
            case TOWS_MATRIX -> renderTowsMatrix(content);
            case CAUSAL_ANALYSIS -> renderCausalAnalysis(JsonUtil.arr(content, "rows"));
            case INVENTORY -> renderInventory(content);
            case STRATEGIC_AXES -> renderStrategicAxes(content);
            case LOGICAL_FRAMEWORK -> renderLogicalFramework(content);
            case ACTION_PLAN -> renderActionPlan(content);
            case BUDGET -> renderBudget(content);
            case PERFORMANCE_FRAMEWORK -> renderPerformanceFramework(content);
            case INDICATOR_SHEET -> renderIndicatorSheet(content);
            case RISK_MATRIX -> renderRiskMatrix(content);
            case FINANCING_PLAN -> renderFinancingPlan(content);
            case BUSINESS_PLAN -> renderBusinessPlan(content);
            case STRATEGIC_SUMMARY -> renderStrategicSummary(content);
        });

        return blocks;
    }

    public ExportBlock.KeyValueList renderMetadataBox(ExportSectionData data) {
        GroupSectionStatus status = data.status();
        SectionStatus statusEnum = status != null ? status.getStatus() : SectionStatus.NOT_STARTED;
        List<ExportBlock.KeyValue> pairs = new ArrayList<>();
        pairs.add(new ExportBlock.KeyValue("Statut", STATUS_LABELS.getOrDefault(statusEnum.name(), statusEnum.name())));
        pairs.add(new ExportBlock.KeyValue("Version", data.version() != null && data.version() > 0 ? String.valueOf(data.version()) : "—"));
        pairs.add(new ExportBlock.KeyValue("Soumis le", formatDate(status != null ? status.getSubmittedAt() : null)));
        pairs.add(new ExportBlock.KeyValue("Validé le", formatDate(status != null ? status.getValidatedAt() : null)));
        if (status != null && status.getAdminComment() != null && !status.getAdminComment().isBlank()) {
            pairs.add(new ExportBlock.KeyValue("Commentaire admin", status.getAdminComment()));
        }
        return new ExportBlock.KeyValueList(null, pairs, true);
    }

    private String formatDate(LocalDateTime dt) {
        return dt == null ? "—" : dt.format(DATE_FORMAT);
    }

    // ---- Colonnes reutilisees (S01/S03 et leur reprise en lecture seule dans S07) ----

    private List<JsonUtil.Column> stakeholdersColumns() {
        return List.of(
                new JsonUtil.Column("Acteur", n -> JsonUtil.text(n, "actor")),
                new JsonUtil.Column("Rôles", n -> JsonUtil.text(n, "roles")),
                new JsonUtil.Column("Attentes", n -> JsonUtil.text(n, "expectations")),
                new JsonUtil.Column("Stratégie d'adaptation", n -> JsonUtil.text(n, "adaptationStrategy")),
                new JsonUtil.Column("Importance", n -> JsonUtil.text(n, "importance")),
                new JsonUtil.Column("Influence", n -> JsonUtil.text(n, "influence")),
                new JsonUtil.Column("Actions", n -> JsonUtil.text(n, "actions"))
        );
    }

    private List<JsonUtil.Column> pestelColumns() {
        return List.of(
                new JsonUtil.Column("Axe", n -> SectionLabels.pestel(JsonUtil.text(n, "axis"))),
                new JsonUtil.Column("Menaces", n -> JsonUtil.text(n, "threats")),
                new JsonUtil.Column("Opportunités", n -> JsonUtil.text(n, "opportunities")),
                new JsonUtil.Column("Actions", n -> JsonUtil.text(n, "actions"))
        );
    }

    // ---- S02 ----
    private List<ExportBlock> renderResourcesMatrix(JsonNode content) {
        List<JsonUtil.Column> columns = List.of(
                new JsonUtil.Column("Domaine", n -> SectionLabels.resource(JsonUtil.text(n, "resourceKey"))),
                new JsonUtil.Column("Forces", n -> JsonUtil.text(n, "strengths")),
                new JsonUtil.Column("Faiblesses", n -> JsonUtil.text(n, "weaknesses")),
                new JsonUtil.Column("Défis", n -> JsonUtil.text(n, "challenges"))
        );
        return List.of(RowsTableRenderer.render(JsonUtil.arr(content, "rows"), columns));
    }

    // ---- S04 ----
    private ExportBlock.Quadrant swotQuadrant(JsonNode content) {
        return new ExportBlock.Quadrant(List.of(
                new ExportBlock.QuadrantCell("Forces", JsonUtil.strList(content, "strengths")),
                new ExportBlock.QuadrantCell("Faiblesses", JsonUtil.strList(content, "weaknesses")),
                new ExportBlock.QuadrantCell("Opportunités", JsonUtil.strList(content, "opportunities")),
                new ExportBlock.QuadrantCell("Menaces", JsonUtil.strList(content, "threats"))
        ));
    }

    // ---- S05 ----
    private List<ExportBlock> renderTowsMatrix(JsonNode content) {
        List<ExportBlock> blocks = new ArrayList<>();
        blocks.add(new ExportBlock.Heading("SWOT (rappel)", 3));
        blocks.add(swotQuadrant(content));
        blocks.add(new ExportBlock.Heading("Stratégies de confrontation", 3));
        blocks.add(new ExportBlock.KeyValueList(null, List.of(
                new ExportBlock.KeyValue("Forces à maximiser", JsonUtil.dash(JsonUtil.text(content, "maximizeStrengths"))),
                new ExportBlock.KeyValue("Forces pour saisir les opportunités (SO)", JsonUtil.dash(JsonUtil.text(content, "strengthsForOpportunities"))),
                new ExportBlock.KeyValue("Forces pour maîtriser les faiblesses", JsonUtil.dash(JsonUtil.text(content, "strengthsControlWeaknesses"))),
                new ExportBlock.KeyValue("Opportunités à maximiser", JsonUtil.dash(JsonUtil.text(content, "maximizeOpportunities"))),
                new ExportBlock.KeyValue("Faiblesses à minimiser", JsonUtil.dash(JsonUtil.text(content, "minimizeWeaknesses"))),
                new ExportBlock.KeyValue("Corriger les faiblesses grâce aux opportunités (WO)", JsonUtil.dash(JsonUtil.text(content, "correctWeaknessesViaOpportunities"))),
                new ExportBlock.KeyValue("Menaces à minimiser", JsonUtil.dash(JsonUtil.text(content, "minimizeThreats"))),
                new ExportBlock.KeyValue("Forces pour réduire les menaces (ST)", JsonUtil.dash(JsonUtil.text(content, "strengthsReduceThreats"))),
                new ExportBlock.KeyValue("Minimiser faiblesses et menaces (WT)", JsonUtil.dash(JsonUtil.text(content, "minimizeWeaknessesAndThreats"))),
                new ExportBlock.KeyValue("Opportunités pour réduire les menaces", JsonUtil.dash(JsonUtil.text(content, "opportunitiesMinimizeThreats")))
        ), false));
        return blocks;
    }

    // ---- S06 ----
    private List<ExportBlock> renderCausalAnalysis(List<JsonNode> rows) {
        List<ExportBlock> blocks = new ArrayList<>();
        for (JsonNode row : rows) {
            blocks.add(new ExportBlock.BulletList(SectionLabels.causal(JsonUtil.text(row, "source")), JsonUtil.strList(row, "items")));
        }
        return blocks;
    }

    // ---- S07 ----
    private List<ExportBlock> renderInventory(JsonNode content) {
        List<ExportBlock> blocks = new ArrayList<>();
        String note = JsonUtil.text(content, "synthesisNote");
        if (!note.isBlank()) {
            blocks.add(new ExportBlock.Heading("Synthèse", 3));
            blocks.add(new ExportBlock.Paragraph(note));
        }
        blocks.add(new ExportBlock.Heading("Parties prenantes", 3));
        blocks.add(RowsTableRenderer.render(JsonUtil.arr(content, "stakeholders"), stakeholdersColumns()));
        blocks.add(new ExportBlock.Heading("PESTEL", 3));
        blocks.add(RowsTableRenderer.render(JsonUtil.arr(content, "pestel"), pestelColumns()));
        blocks.add(new ExportBlock.Heading("SWOT", 3));
        JsonNode swot = content.get("swot");
        if (swot != null) {
            blocks.add(swotQuadrant(swot));
        }
        blocks.add(new ExportBlock.Heading("Analyse causale", 3));
        blocks.addAll(renderCausalAnalysis(JsonUtil.arr(content, "causalAnalysis")));
        return blocks;
    }

    // ---- S08 ----
    private List<ExportBlock> renderStrategicAxes(JsonNode content) {
        List<JsonUtil.Column> columns = List.of(
                new JsonUtil.Column("Axe", n -> JsonUtil.text(n, "axisCode")),
                new JsonUtil.Column("Titre", n -> JsonUtil.text(n, "title")),
                new JsonUtil.Column("Description", n -> JsonUtil.text(n, "description"))
        );
        return List.of(RowsTableRenderer.render(JsonUtil.arr(content, "axes"), columns));
    }

    private String axisTitle(JsonNode axis) {
        String code = JsonUtil.text(axis, "axisCode");
        String title = JsonUtil.text(axis, "axisTitle");
        return title.isBlank() ? code : code + " — " + title;
    }

    private String effectTitle(JsonNode effect) {
        String code = JsonUtil.text(effect, "effectCode");
        String os = JsonUtil.text(effect, "osCode");
        String label = JsonUtil.text(effect, "effectLabel");
        String head = os.isBlank() ? code : os + " (" + code + ")";
        return label.isBlank() ? head : head + " — " + label;
    }

    // ---- S09 ----
    private List<ExportBlock> renderLogicalFramework(JsonNode content) {
        List<ExportBlock> blocks = new ArrayList<>();
        List<JsonUtil.Column> columns = List.of(
                new JsonUtil.Column("Niveau", n -> SectionLabels.logframe(JsonUtil.text(n, "level"))),
                new JsonUtil.Column("Logique d'intervention", n -> JsonUtil.text(n, "interventionLogic")),
                new JsonUtil.Column("IOV", n -> JsonUtil.text(n, "iov")),
                new JsonUtil.Column("Moyens de vérification", n -> JsonUtil.text(n, "verificationMeans")),
                new JsonUtil.Column("Hypothèses", n -> JsonUtil.text(n, "assumptions"))
        );
        for (JsonNode axis : JsonUtil.arr(content, "axes")) {
            blocks.add(new ExportBlock.Heading(axisTitle(axis), 3));
            String objective = JsonUtil.text(axis, "objective");
            if (!objective.isBlank()) {
                blocks.add(new ExportBlock.Paragraph("Objectif : " + objective, false, true));
            }
            blocks.add(RowsTableRenderer.render(JsonUtil.arr(axis, "rows"), columns));
        }
        return blocks;
    }

    // ---- S10 ----
    private List<ExportBlock> renderActionPlan(JsonNode content) {
        List<ExportBlock> blocks = new ArrayList<>();
        List<JsonUtil.Column> leading = List.of(
                new JsonUtil.Column("Extrant", n -> JsonUtil.text(n, "extrant")),
                new JsonUtil.Column("Activités", n -> JsonUtil.text(n, "activities"))
        );
        List<JsonUtil.Column> trailing = List.of(
                new JsonUtil.Column("Responsable", n -> JsonUtil.text(n, "responsible"))
        );
        for (JsonNode axis : JsonUtil.arr(content, "axes")) {
            blocks.add(new ExportBlock.Heading(axisTitle(axis), 3));
            for (JsonNode effect : JsonUtil.arr(axis, "effects")) {
                blocks.add(new ExportBlock.Heading(effectTitle(effect), 4));
                blocks.add(YearlyTableRenderer.render(
                        JsonUtil.arr(effect, "rows"), leading, SectionLabels.YEARS,
                        (yearsNode, year) -> JsonUtil.formatCheck(yearsNode != null && yearsNode.has(year) && yearsNode.get(year).asBoolean()),
                        trailing, row -> false));
            }
        }
        return blocks;
    }

    // ---- S11 ----
    private List<ExportBlock> renderBudget(JsonNode content) {
        List<ExportBlock> blocks = new ArrayList<>();
        List<JsonUtil.Column> leading = List.of(
                new JsonUtil.Column("Extrant", n -> JsonUtil.text(n, "extrant")),
                new JsonUtil.Column("Activités", n -> JsonUtil.text(n, "activities"))
        );
        List<JsonUtil.Column> trailing = List.of(
                new JsonUtil.Column("Total", n -> JsonUtil.formatCurrency(JsonUtil.num(n, "rowTotal"))),
                new JsonUtil.Column("Responsable", n -> JsonUtil.text(n, "responsible"))
        );
        for (JsonNode axis : JsonUtil.arr(content, "axes")) {
            blocks.add(new ExportBlock.Heading(axisTitle(axis), 3));
            for (JsonNode effect : JsonUtil.arr(axis, "effects")) {
                blocks.add(new ExportBlock.Heading(effectTitle(effect), 4));
                ExportBlock.Table table = YearlyTableRenderer.render(
                        JsonUtil.arr(effect, "rows"), leading, SectionLabels.YEARS,
                        (yearsNode, year) -> JsonUtil.formatCurrency(yearNumber(yearsNode, year)),
                        trailing, row -> false);
                blocks.add(withEffectTotalsRow(table, effect));
            }
            blocks.add(new ExportBlock.Paragraph("Total de l'axe : " + JsonUtil.formatCurrency(JsonUtil.num(axis, "axisTotal")), false, true));
        }
        blocks.add(new ExportBlock.Paragraph("Total général : " + JsonUtil.formatCurrency(JsonUtil.num(content, "grandTotal")), false, true));
        return blocks;
    }

    private double yearNumber(JsonNode yearsNode, String year) {
        return yearsNode != null && yearsNode.has(year) ? yearsNode.get(year).asDouble() : 0;
    }

    private ExportBlock.Table withEffectTotalsRow(ExportBlock.Table table, JsonNode effect) {
        JsonNode yearTotals = effect.get("yearTotals");
        List<ExportBlock.Cell> cells = new ArrayList<>();
        cells.add(new ExportBlock.Cell("Total de l'effet"));
        cells.add(new ExportBlock.Cell(""));
        for (String year : SectionLabels.YEARS) {
            cells.add(new ExportBlock.Cell(JsonUtil.formatCurrency(yearNumber(yearTotals, year)), false, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
        }
        cells.add(new ExportBlock.Cell(JsonUtil.formatCurrency(JsonUtil.num(effect, "effectTotal")), false, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
        cells.add(new ExportBlock.Cell(""));
        List<ExportBlock.TableRow> rows = new ArrayList<>(table.rows());
        rows.add(new ExportBlock.TableRow(cells, true, ExportBlock.Background.PRIMARY_LIGHT));
        return new ExportBlock.Table(table.columnHeaders(), rows);
    }

    // ---- S12 ----
    private List<ExportBlock> renderPerformanceFramework(JsonNode content) {
        List<ExportBlock> blocks = new ArrayList<>();
        List<JsonUtil.Column> leading = List.of(
                new JsonUtil.Column("Résultat / Extrant", n -> JsonUtil.text(n, "resultOrExtrant")),
                new JsonUtil.Column("Indicateur", n -> JsonUtil.text(n, "indicator")),
                new JsonUtil.Column("Référence 2026", n -> JsonUtil.text(n, "ref2026"))
        );
        List<JsonUtil.Column> trailing = List.of(
                new JsonUtil.Column("Responsable", n -> JsonUtil.text(n, "responsible"))
        );
        for (JsonNode axis : JsonUtil.arr(content, "axes")) {
            blocks.add(new ExportBlock.Heading(axisTitle(axis), 3));
            for (JsonNode group : JsonUtil.arr(axis, "groups")) {
                blocks.add(new ExportBlock.Heading(SectionLabels.logframe(JsonUtil.text(group, "level")), 4));
                blocks.add(YearlyTableRenderer.render(
                        JsonUtil.arr(group, "rows"), leading, SectionLabels.YEARS,
                        (yearsNode, year) -> JsonUtil.dash(yearsNode != null && yearsNode.has(year) ? yearsNode.get(year).asText() : ""),
                        trailing, row -> false));
            }
        }
        return blocks;
    }

    // ---- S13 ----
    private List<ExportBlock> renderIndicatorSheet(JsonNode content) {
        List<JsonUtil.Column> columns = List.of(
                new JsonUtil.Column("Titre indicateur", n -> JsonUtil.text(n, "indicatorTitle")),
                new JsonUtil.Column("Méthode de calcul", n -> JsonUtil.text(n, "calculationMethod")),
                new JsonUtil.Column("Périodicité", n -> JsonUtil.text(n, "periodicity")),
                new JsonUtil.Column("Source de collecte", n -> JsonUtil.text(n, "collectionSource")),
                new JsonUtil.Column("Source de vérification", n -> JsonUtil.text(n, "verificationSource")),
                new JsonUtil.Column("Structure responsable", n -> JsonUtil.text(n, "responsibleStructure"))
        );
        return List.of(RowsTableRenderer.render(JsonUtil.arr(content, "rows"), columns));
    }

    // ---- S14 ----
    private List<ExportBlock> renderRiskMatrix(JsonNode content) {
        List<JsonUtil.Column> columns = List.of(
                new JsonUtil.Column("Catégorie", n -> JsonUtil.text(n, "category")),
                new JsonUtil.Column("Présent", n -> JsonUtil.formatCheck(JsonUtil.bool(n, "present"))),
                new JsonUtil.Column("Détails du risque", n -> JsonUtil.text(n, "riskDetails")),
                new JsonUtil.Column("Niveau (N)", n -> String.valueOf((int) JsonUtil.num(n, "levelN"))),
                new JsonUtil.Column("Domaines d'impact", n -> JsonUtil.text(n, "impactAreas")),
                new JsonUtil.Column("Cotation (Q)", n -> String.valueOf((int) JsonUtil.num(n, "quotationQ"))),
                new JsonUtil.Column("Criticité", n -> SectionLabels.criticality(JsonUtil.text(n, "criticalityLabel")),
                        n -> SectionLabels.criticalityBackground(JsonUtil.text(n, "criticalityLabel"))),
                new JsonUtil.Column("Actions d'atténuation", n -> JsonUtil.text(n, "mitigationActions"))
        );
        return List.of(RowsTableRenderer.render(JsonUtil.arr(content, "rows"), columns));
    }

    // ---- S15 ----
    private List<ExportBlock> renderFinancingPlan(JsonNode content) {
        List<JsonUtil.Column> columns = List.of(
                new JsonUtil.Column("Source", n -> SectionLabels.financing(JsonUtil.text(n, "source"))),
                new JsonUtil.Column("Montant", n -> JsonUtil.formatCurrency(JsonUtil.num(n, "amount"))),
                new JsonUtil.Column("%", n -> JsonUtil.formatPercent(JsonUtil.num(n, "percent"))),
                new JsonUtil.Column("Modalités", n -> JsonUtil.text(n, "modalities")),
                new JsonUtil.Column("Période", n -> JsonUtil.text(n, "period")),
                new JsonUtil.Column("Responsable", n -> JsonUtil.text(n, "responsible"))
        );
        ExportBlock.TableRow totals = RowsTableRenderer.totalsRow(List.of(
                "Total", JsonUtil.formatCurrency(JsonUtil.num(content, "total")), "100 %", "", "", ""));
        return List.of(RowsTableRenderer.render(JsonUtil.arr(content, "rows"), columns, List.of(totals)));
    }

    // ---- S16 ----
    private List<ExportBlock> renderBusinessPlan(JsonNode content) {
        List<ExportBlock> blocks = new ArrayList<>();
        blocks.add(new ExportBlock.Heading("Compte d'exploitation", 3));
        blocks.add(businessYearlyTable(content.get("operatingAccount"), SectionLabels.OPERATING_ACCOUNT_LABELS));
        blocks.add(new ExportBlock.Heading("Flux de trésorerie", 3));
        blocks.add(businessYearlyTable(content.get("cashFlow"), SectionLabels.CASH_FLOW_LABELS));
        return blocks;
    }

    private ExportBlock.Table businessYearlyTable(JsonNode block, Map<String, String> labels) {
        List<JsonUtil.Column> leading = List.of(
                new JsonUtil.Column("Poste", n -> labels.getOrDefault(JsonUtil.text(n, "label"), JsonUtil.text(n, "label")))
        );
        List<JsonUtil.Column> trailing = List.of(
                new JsonUtil.Column("Total", n -> JsonUtil.formatCurrency(JsonUtil.num(n, "total")))
        );
        Set<String> computedKeys = SectionLabels.COMPUTED_ROW_LABELS;
        return YearlyTableRenderer.render(
                JsonUtil.arr(block, "rows"), leading, SectionLabels.YEARS,
                (yearsNode, year) -> JsonUtil.formatCurrency(yearNumber(yearsNode, year)),
                trailing, row -> computedKeys.contains(JsonUtil.text(row, "label")));
    }

    // ---- S17 ----
    private List<ExportBlock> renderStrategicSummary(JsonNode content) {
        List<ExportBlock> blocks = new ArrayList<>();
        String vision = JsonUtil.text(content, "vision");
        if (!vision.isBlank()) {
            blocks.add(new ExportBlock.Heading("Vision", 3));
            blocks.add(new ExportBlock.Paragraph(vision));
        }
        List<JsonUtil.Column> actionColumns = List.of(
                new JsonUtil.Column("Action", n -> JsonUtil.text(n, "label")),
                new JsonUtil.Column("Contraintes / Opportunités", n -> JsonUtil.text(n, "constraintsOrOpportunities"))
        );
        for (JsonNode axis : JsonUtil.arr(content, "axes")) {
            blocks.add(new ExportBlock.Heading(axisTitle(axis), 3));
            for (JsonNode orientation : JsonUtil.arr(axis, "orientations")) {
                blocks.add(new ExportBlock.Heading(JsonUtil.dash(JsonUtil.text(orientation, "label")), 4));
                blocks.add(RowsTableRenderer.render(JsonUtil.arr(orientation, "actions"), actionColumns));
            }
        }
        return blocks;
    }
}
