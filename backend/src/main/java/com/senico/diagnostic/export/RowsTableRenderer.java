package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Construit un ExportBlock.Table a partir d'un tableau plat de lignes JSON (le motif
 * le plus courant du canevas : S01, S02, S03, S06, S13, S14, S15).
 */
final class RowsTableRenderer {

    private RowsTableRenderer() {
    }

    static ExportBlock.Table render(List<JsonNode> rows, List<JsonUtil.Column> columns) {
        return render(rows, columns, List.of());
    }

    static ExportBlock.Table render(List<JsonNode> rows, List<JsonUtil.Column> columns, List<ExportBlock.TableRow> trailingRows) {
        List<String> headers = columns.stream().map(JsonUtil.Column::header).toList();
        List<ExportBlock.TableRow> tableRows = new java.util.ArrayList<>(rows.stream()
                .map(row -> toRow(row, columns))
                .toList());
        tableRows.addAll(trailingRows);
        return new ExportBlock.Table(headers, tableRows);
    }

    static ExportBlock.TableRow toRow(JsonNode row, List<JsonUtil.Column> columns) {
        return new ExportBlock.TableRow(
                columns.stream()
                        .map(c -> new ExportBlock.Cell(JsonUtil.dash(c.extractor().apply(row)), c.background().apply(row)))
                        .toList());
    }

    static ExportBlock.TableRow totalsRow(List<String> cellTexts) {
        return new ExportBlock.TableRow(cellTexts.stream().map(ExportBlock.Cell::new).toList(), true);
    }
}
