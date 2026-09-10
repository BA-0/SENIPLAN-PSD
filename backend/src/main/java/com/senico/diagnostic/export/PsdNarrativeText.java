package com.senico.diagnostic.export;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Met en forme un bloc de texte redige par l'admin (Mot du DG, Enjeux, Dispositif de pilotage...).
 *
 * <p>Le texte est saisi dans une simple zone de texte. Trois conventions suffisent a lui donner la
 * tenue d'un document redige, sans editeur riche : une ligne commencant par « - » est une puce,
 * des lignes successives de ce type forment une liste ; une ligne courte terminee par « : »
 * introduit ce qui suit (« Facteurs clés de réussite : ») ; tout le reste est un paragraphe.</p>
 */
final class PsdNarrativeText {

    private static final Pattern BULLET = Pattern.compile("^\\s*[-•–]\\s+");
    private static final int MAX_LEAD_LENGTH = 90;

    private PsdNarrativeText() {
    }

    static boolean isBlank(String content) {
        return content == null || content.isBlank();
    }

    static List<ExportBlock> blocks(String content) {
        List<ExportBlock> blocks = new ArrayList<>();
        if (isBlank(content)) {
            return blocks;
        }
        List<String> bullets = new ArrayList<>();
        for (String raw : content.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                flush(blocks, bullets);
                continue;
            }
            if (BULLET.matcher(line).find()) {
                bullets.add(BULLET.matcher(line).replaceFirst("").trim());
                continue;
            }
            flush(blocks, bullets);
            if (line.endsWith(":") && line.length() <= MAX_LEAD_LENGTH) {
                blocks.add(new ExportBlock.Heading(line.substring(0, line.length() - 1).trim(), 4));
            } else {
                blocks.add(new ExportBlock.Paragraph(line));
            }
        }
        flush(blocks, bullets);
        return blocks;
    }

    /** Lignes non vides, puces retirees : pour les blocs lus comme des listes (mission, valeurs). */
    static List<String> lines(String content) {
        List<String> lines = new ArrayList<>();
        if (isBlank(content)) {
            return lines;
        }
        for (String raw : content.split("\\R")) {
            String line = BULLET.matcher(raw.trim()).replaceFirst("").trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static void flush(List<ExportBlock> blocks, List<String> bullets) {
        if (!bullets.isEmpty()) {
            blocks.add(new ExportBlock.BulletList(null, List.copyOf(bullets)));
            bullets.clear();
        }
    }
}
