package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Extraction et formatage partages par les renderers d'export (tables, montants).
 */
final class JsonUtil {

    private static final DecimalFormat CURRENCY_FORMAT;
    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        CURRENCY_FORMAT = new DecimalFormat("#,##0", symbols);
    }

    private JsonUtil() {
    }

    static String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    static double num(JsonNode node, String field) {
        if (node == null) {
            return 0;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? 0 : value.asDouble();
    }

    static boolean bool(JsonNode node, String field) {
        if (node == null) {
            return false;
        }
        JsonNode value = node.get(field);
        return value != null && value.asBoolean();
    }

    static List<JsonNode> arr(JsonNode node, String field) {
        List<JsonNode> out = new ArrayList<>();
        if (node == null) {
            return out;
        }
        JsonNode value = node.get(field);
        if (value != null && value.isArray()) {
            value.forEach(out::add);
        }
        return out;
    }

    static List<String> strList(JsonNode node, String field) {
        return arr(node, field).stream().map(JsonNode::asText).toList();
    }

    static String dash(String text) {
        return text == null || text.isBlank() ? "—" : text;
    }

    static String formatCurrency(double amount) {
        return CURRENCY_FORMAT.format(Math.round(amount)) + " FCFA";
    }

    static String formatPercent(double percent) {
        return CURRENCY_FORMAT.format(Math.round(percent)) + " %";
    }

    static String formatCheck(boolean value) {
        return value ? "✓" : "";
    }

    /**
     * Colonne generique reutilisee par RowsTableRenderer et YearlyTableRenderer.
     */
    record Column(String header, Function<JsonNode, String> extractor, Function<JsonNode, ExportBlock.Background> background) {
        Column(String header, Function<JsonNode, String> extractor) {
            this(header, extractor, n -> ExportBlock.Background.NONE);
        }
    }
}
