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
    /** Taux de realisation (S01B) : une decimale, la ou les montants restent entiers. */
    private static final DecimalFormat RATE_FORMAT;
    /** Milliards dans le texte : deux decimales, « 12,51 milliards ». */
    private static final DecimalFormat AMOUNT_FORMAT;
    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        CURRENCY_FORMAT = new DecimalFormat("#,##0", symbols);
        RATE_FORMAT = new DecimalFormat("#,##0.#", symbols);
        AMOUNT_FORMAT = new DecimalFormat("#,##0.##", symbols);
    }

    private JsonUtil() {
    }

    /** Objet vide, pour les sections absentes : evite un null a propager dans les extracteurs. */
    static JsonNode emptyObject() {
        return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
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

    /** Effectifs, cibles, realises : entiers groupes, sans unite (contrairement a formatCurrency). */
    static String formatNumber(double value) {
        return CURRENCY_FORMAT.format(Math.round(value));
    }

    static String formatRate(double percent) {
        return RATE_FORMAT.format(percent) + " %";
    }

    /** Valeur a une decimale au plus (3,8 jours ; 96,5 %) : formatNumber arrondirait 3,8 a 4. */
    static String formatDecimal(double value) {
        return RATE_FORMAT.format(value);
    }

    /** Montant exprime en millions, a une decimale : l'unite des tableaux de budget de la note. */
    static String formatMillions(double amount) {
        return RATE_FORMAT.format(amount / 1_000_000d);
    }

    /**
     * Montant en toutes lettres pour le corps du texte, comme dans un PSD publie :
     * « 12,51 milliards FCFA » plutot que « 12 511 100 000 FCFA ».
     */
    static String formatAmountLabel(double amount) {
        double abs = Math.abs(amount);
        if (abs >= 1_000_000_000d) {
            return AMOUNT_FORMAT.format(amount / 1_000_000_000d) + " milliards FCFA";
        }
        if (abs >= 1_000_000d) {
            return RATE_FORMAT.format(amount / 1_000_000d) + " millions FCFA";
        }
        return formatCurrency(amount);
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
