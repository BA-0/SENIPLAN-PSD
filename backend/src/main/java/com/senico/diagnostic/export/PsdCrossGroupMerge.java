package com.senico.diagnostic.export;

import com.senico.diagnostic.domain.WorkGroup;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fusionne les contributions de plusieurs directions sur un meme champ libre (SWOT, PESTEL,
 * parties prenantes) du "Document final PSD" : un texte identique (une fois normalise) saisi
 * par plusieurs directions n'apparait qu'une seule fois, avec la liste des directions qui l'ont
 * mentionne (pour l'attribution couleur), au lieu d'etre repete pour chaque direction.
 *
 * <p>Deux formulations presque identiques du meme constat (« Délais d'acheminement parfois
 * supérieurs à la concurrence » / « Délais d'acheminement supérieurs à ceux de la concurrence »)
 * sont aussi fusionnees (cf. {@link #similar}). Le seuil est volontairement strict : deux constats
 * voisins mais distincts (« couverture technique » / « couverture logistique ») restent separes,
 * car les fusionner ferait disparaitre l'un des deux du document.</p>
 */
final class PsdCrossGroupMerge {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");

    /** Mots qui ne portent pas le sens d'un constat : les ignorer evite de fusionner sur « de la ». */
    private static final Set<String> STOPWORDS = Set.of(
            "les", "des", "une", "aux", "pour", "par", "sur", "dans", "avec", "ceux", "celles", "cette",
            "ces", "son", "ses", "leur", "leurs", "plus", "moins", "tres", "tout", "tous", "toute",
            "toutes", "que", "qui", "pas", "parfois", "certaines", "certains", "encore", "deja", "entre",
            "sous", "sans", "chez", "dont", "est", "sont", "etre", "the", "and");

    /** Part minimale de racines communes (indice de Jaccard) pour tenir deux textes pour le meme constat. */
    private static final double SIMILARITY_THRESHOLD = 0.75;
    private static final int MIN_STEMS = 3;
    private static final int STEM_LENGTH = 6;

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
                String key = matchingKey(byKey.keySet(), normalize(item));
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

    /** Cle d'un texte deja retenu qui exprime le meme constat, ou la cle du texte lui-meme. */
    static String matchingKey(Iterable<String> existingKeys, String normalized) {
        for (String existing : existingKeys) {
            if (existing.equals(normalized)) {
                return existing;
            }
        }
        for (String existing : existingKeys) {
            if (similar(existing, normalized)) {
                return existing;
            }
        }
        return normalized;
    }

    static String normalize(String text) {
        String withoutDiacritics = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return SPACES.matcher(withoutDiacritics.toLowerCase()).replaceAll(" ").trim();
    }

    /**
     * Meme constat, a la formulation pres : les racines des mots porteurs de sens se recouvrent
     * a 75 % au moins. En deca de trois mots porteurs, seule l'egalite compte — sur deux mots,
     * une seule difference change le sens.
     */
    static boolean similar(String a, String b) {
        Set<String> left = stems(a);
        Set<String> right = stems(b);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        if (Math.min(left.size(), right.size()) < MIN_STEMS) {
            return left.equals(right);
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size() >= SIMILARITY_THRESHOLD;
    }

    private static Set<String> stems(String text) {
        Set<String> stems = new HashSet<>();
        for (String word : NON_WORD.split(normalize(text))) {
            if (word.length() <= 2 || STOPWORDS.contains(word)) {
                continue;
            }
            stems.add(word.length() > STEM_LENGTH ? word.substring(0, STEM_LENGTH) : word);
        }
        return stems;
    }
}
