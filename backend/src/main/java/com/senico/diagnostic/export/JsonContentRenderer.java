package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Convertit recursivement le JSON libre d'une SectionResponse en lignes de texte indentees,
 * lisibles dans les exports PDF/Word/Excel, sans avoir a maintenir un gabarit par type de section.
 * Les cles techniques (camelCase, MAJUSCULES_SNAKE) sont humanisees pour l'affichage.
 */
public final class JsonContentRenderer {

    public record Line(int indent, String text) {
    }

    private static final Pattern CAMEL = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Map<String, String> KNOWN_LABELS = Map.ofEntries(
            Map.entry("rows", "Lignes"),
            Map.entry("axes", "Axes"),
            Map.entry("effects", "Effets"),
            Map.entry("groups", "Groupes"),
            Map.entry("items", "Éléments"),
            Map.entry("years", "Années"),
            Map.entry("strengths", "Forces"),
            Map.entry("weaknesses", "Faiblesses"),
            Map.entry("opportunities", "Opportunités"),
            Map.entry("threats", "Menaces"),
            Map.entry("orientations", "Orientations stratégiques"),
            Map.entry("actions", "Actions")
    );

    private JsonContentRenderer() {
    }

    public static List<Line> render(JsonNode node) {
        List<Line> lines = new ArrayList<>();
        renderNode(node, 0, lines);
        return lines;
    }

    public static String renderAsText(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        for (Line line : render(node)) {
            sb.append("  ".repeat(line.indent())).append(line.text()).append('\n');
        }
        return sb.toString();
    }

    private static void renderNode(JsonNode node, int indent, List<Line> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                renderField(entry.getKey(), entry.getValue(), indent, out);
            }
        } else if (node.isArray()) {
            int i = 1;
            for (JsonNode item : node) {
                if (item.isObject() || item.isArray()) {
                    out.add(new Line(indent, "#" + i));
                    renderNode(item, indent + 1, out);
                } else {
                    out.add(new Line(indent, "- " + item.asText()));
                }
                i++;
            }
        } else {
            out.add(new Line(indent, node.asText()));
        }
    }

    private static void renderField(String key, JsonNode value, int indent, List<Line> out) {
        String label = humanize(key);
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isTextual() || value.isNumber() || value.isBoolean()) {
            String text = value.isBoolean() ? (value.asBoolean() ? "Oui" : "Non") : value.asText();
            if (!text.isBlank()) {
                out.add(new Line(indent, label + " : " + text));
            }
        } else if (value.isArray()) {
            if (value.isEmpty()) {
                return;
            }
            out.add(new Line(indent, label + " :"));
            renderNode(value, indent + 1, out);
        } else if (value.isObject()) {
            if (value.isEmpty()) {
                return;
            }
            out.add(new Line(indent, label + " :"));
            renderNode(value, indent + 1, out);
        }
    }

    private static String humanize(String key) {
        if (KNOWN_LABELS.containsKey(key)) {
            return KNOWN_LABELS.get(key);
        }
        String withSpaces = CAMEL.matcher(key).replaceAll("$1 $2");
        withSpaces = withSpaces.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(withSpaces.charAt(0)) + withSpaces.substring(1);
    }
}
