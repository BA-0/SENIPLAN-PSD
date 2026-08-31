package com.senico.diagnostic.export;

import com.senico.diagnostic.domain.WorkGroup;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Fusionne les contributions de plusieurs directions sur un meme champ libre (SWOT, PESTEL,
 * parties prenantes) du "Document final PSD" : un texte identique (une fois normalise) saisi
 * par plusieurs directions n'apparait qu'une seule fois, avec la liste des directions qui l'ont
 * mentionne (pour l'attribution couleur), au lieu d'etre repete pour chaque direction.
 */
final class PsdCrossGroupMerge {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private PsdCrossGroupMerge() {
    }

    record MergedItem(String text, List<WorkGroup> contributors) {
    }

    /**
     * @param entries    une paire (direction, texte libre) par direction ayant renseigne ce champ.
     * @param splitLines si vrai, chaque texte est d'abord decoupe en items sur les retours a la
     *                   ligne (cas des cellules PESTEL, ou un champ libre peut contenir plusieurs
     *                   menaces/opportunites) ; si faux, chaque entree est deja un item unitaire
     *                   (cas des listes SWOT).
     */
    static List<MergedItem> merge(List<Map.Entry<WorkGroup, String>> entries, boolean splitLines) {
        Map<String, MergedItem> byKey = new LinkedHashMap<>();
        for (Map.Entry<WorkGroup, String> entry : entries) {
            String raw = entry.getValue();
            if (raw == null) {
                continue;
            }
            String[] lines = splitLines ? raw.split("\\R") : new String[]{raw};
            for (String line : lines) {
                String item = line.trim();
                if (item.isEmpty()) {
                    continue;
                }
                String key = normalize(item);
                MergedItem existing = byKey.get(key);
                if (existing == null) {
                    List<WorkGroup> contributors = new ArrayList<>();
                    contributors.add(entry.getKey());
                    byKey.put(key, new MergedItem(item, contributors));
                } else if (!existing.contributors().contains(entry.getKey())) {
                    existing.contributors().add(entry.getKey());
                }
            }
        }
        return new ArrayList<>(byKey.values());
    }

    static String normalize(String text) {
        String withoutDiacritics = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return SPACES.matcher(withoutDiacritics.toLowerCase()).replaceAll(" ").trim();
    }
}
