package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.senico.diagnostic.domain.WorkGroup;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Les chiffres du PSD agreges sur toutes les directions : ce qu'on veut savoir sans avoir a
 * ouvrir le document complet. Partage par la synthese qui ouvre le Plan Strategique de SENICO
 * ({@link PsdSynthesisBuilder}) et par la note de synthese autonome ({@link PsdBriefBuilder}),
 * pour que les deux annoncent forcement les memes totaux.
 *
 * <p>La lecture du contenu est passee en parametre plutot que branchee sur le depot : c'est
 * l'appelant qui decide du perimetre (sections validees seulement, ou toutes les soumissions),
 * et le calcul reste testable sans base de donnees.</p>
 */
record PsdKeyFigures(int directions, int sectionsCovered, int axes, int specificObjectives,
                     int actions, double budget, double financing,
                     int staffFirstYear, int staffLastYear) {

    /**
     * @param contentLookup rend le contenu d'une section (par son code) pour une direction,
     *                      ou un objet vide si elle n'entre pas dans le perimetre retenu
     */
    static PsdKeyFigures compute(List<WorkGroup> groups,
                                 BiFunction<WorkGroup, String, JsonNode> contentLookup,
                                 int sectionsCovered) {
        int axes = 0;
        int specificObjectives = 0;
        int actions = 0;
        double budget = 0;
        double financing = 0;
        int staffFirstYear = 0;
        int staffLastYear = 0;

        String firstYear = SectionLabels.YEARS[0];
        String lastYear = SectionLabels.YEARS[SectionLabels.YEARS.length - 1];

        for (WorkGroup group : groups) {
            JsonNode strategicAxes = contentLookup.apply(group, "S08");
            for (JsonNode axis : JsonUtil.arr(strategicAxes, "axes")) {
                if (!JsonUtil.text(axis, "title").isBlank()) {
                    axes++;
                }
                specificObjectives += JsonUtil.arr(axis, "specificObjectives").size();
            }

            JsonNode actionPlan = contentLookup.apply(group, "S10");
            for (JsonNode axis : JsonUtil.arr(actionPlan, "axes")) {
                for (JsonNode effect : JsonUtil.arr(axis, "effects")) {
                    actions += JsonUtil.arr(effect, "rows").size();
                }
            }

            budget += JsonUtil.num(contentLookup.apply(group, "S11"), "grandTotal");
            financing += JsonUtil.num(contentLookup.apply(group, "S15"), "total");

            JsonNode totals = contentLookup.apply(group, "S14B").get("totals");
            if (totals != null) {
                staffFirstYear += (int) JsonUtil.num(totals.get(firstYear), "total");
                staffLastYear += (int) JsonUtil.num(totals.get(lastYear), "total");
            }
        }

        return new PsdKeyFigures(groups.size(), sectionsCovered, axes, specificObjectives,
                actions, budget, financing, staffFirstYear, staffLastYear);
    }

    /** Effectifs a zero des deux cotes : la section n'est pas renseignee, "0 -> 0" ne dirait rien. */
    String staffEvolutionLabel() {
        if (staffFirstYear == 0 && staffLastYear == 0) {
            return "—";
        }
        return staffFirstYear + " → " + staffLastYear + " agents";
    }
}
