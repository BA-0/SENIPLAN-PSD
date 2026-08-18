package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Construit un ExportBlock.Table pour les sections a colonnes annees 2027-2031
 * (S10 Plan d'actions, S11 Budget, S12 Cadre de mesure de rendement, S16 Business plan).
 */
final class YearlyTableRenderer {

    private YearlyTableRenderer() {
    }

    @FunctionalInterface
    interface YearValueFormatter {
        String format(JsonNode yearsNode, String year);
    }

    static ExportBlock.Table render(
            List<JsonNode> rows,
            List<JsonUtil.Column> leadingColumns,
            String[] years,
            YearValueFormatter yearFormatter,
            List<JsonUtil.Column> trailingColumns,
            Predicate<JsonNode> emphasize) {

        List<String> headers = new ArrayList<>();
        leadingColumns.forEach(c -> headers.add(c.header()));
        headers.addAll(Arrays.asList(years));
        trailingColumns.forEach(c -> headers.add(c.header()));

        List<ExportBlock.TableRow> tableRows = rows.stream().map(row -> {
            boolean bold = emphasize.test(row);
            JsonNode yearsNode = row.get("years");
            List<ExportBlock.Cell> cells = new ArrayList<>();
            leadingColumns.forEach(c -> cells.add(new ExportBlock.Cell(
                    JsonUtil.dash(c.extractor().apply(row)), bold, ExportBlock.Align.LEFT, c.background().apply(row))));
            for (String year : years) {
                cells.add(new ExportBlock.Cell(yearFormatter.format(yearsNode, year), bold, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
            }
            trailingColumns.forEach(c -> cells.add(new ExportBlock.Cell(
                    JsonUtil.dash(c.extractor().apply(row)), bold, ExportBlock.Align.RIGHT, c.background().apply(row))));
            return new ExportBlock.TableRow(cells, bold);
        }).toList();

        return new ExportBlock.Table(headers, tableRows);
    }
}
