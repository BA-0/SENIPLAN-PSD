package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senico.diagnostic.domain.WorkGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les chiffres annonces en tete de la note de synthese doivent additionner les directions,
 * pas se contenter de la premiere ni compter deux fois. C'est le seul endroit du document ou
 * une erreur de calcul passerait inapercue : les tableaux detailles, eux, sont verifiables a
 * l'oeil.
 */
class PsdKeyFiguresTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static WorkGroup group(long id) {
        WorkGroup g = new WorkGroup();
        g.setId(id);
        g.setName("Direction " + id);
        return g;
    }

    private static JsonNode json(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("Les montants et les comptes de deux directions s'additionnent")
    void additionneLesDirections() {
        WorkGroup a = group(1);
        WorkGroup b = group(2);

        Map<String, String> pourA = Map.of(
                "S08", "{\"axes\":[{\"title\":\"Axe A\",\"specificObjectives\":[\"o1\",\"o2\"]}]}",
                "S10", "{\"axes\":[{\"effects\":[{\"rows\":[{},{},{}]}]}]}",
                "S11", "{\"grandTotal\":1000}",
                "S15", "{\"total\":600}",
                "S14B", "{\"totals\":{\"2027\":{\"total\":10},\"2031\":{\"total\":20}}}");
        Map<String, String> pourB = Map.of(
                "S08", "{\"axes\":[{\"title\":\"Axe B\",\"specificObjectives\":[\"o3\"]}]}",
                "S10", "{\"axes\":[{\"effects\":[{\"rows\":[{},{}]}]}]}",
                "S11", "{\"grandTotal\":500}",
                "S15", "{\"total\":400}",
                "S14B", "{\"totals\":{\"2027\":{\"total\":5},\"2031\":{\"total\":8}}}");

        PsdKeyFigures figures = PsdKeyFigures.compute(List.of(a, b),
                (group, code) -> json((group == a ? pourA : pourB).getOrDefault(code, "{}")), 42);

        assertThat(figures.directions()).isEqualTo(2);
        assertThat(figures.sectionsCovered()).isEqualTo(42);
        assertThat(figures.axes()).isEqualTo(2);
        assertThat(figures.specificObjectives()).isEqualTo(3);
        assertThat(figures.actions()).isEqualTo(5);
        assertThat(figures.budget()).isEqualTo(1500);
        assertThat(figures.financing()).isEqualTo(1000);
        assertThat(figures.staffFirstYear()).isEqualTo(15);
        assertThat(figures.staffLastYear()).isEqualTo(28);
    }

    @Test
    @DisplayName("Un axe sans intitulé n'est pas compté comme un axe défini")
    void ignoreLesAxesSansIntitule() {
        PsdKeyFigures figures = PsdKeyFigures.compute(List.of(group(1)),
                (g, code) -> json("S08".equals(code)
                        ? "{\"axes\":[{\"title\":\"\",\"specificObjectives\":[]},{\"title\":\"Réel\",\"specificObjectives\":[]}]}"
                        : "{}"),
                1);

        assertThat(figures.axes()).isEqualTo(1);
    }

    @Test
    @DisplayName("Sections absentes : tout est a zero, sans exception")
    void supporteLesSectionsAbsentes() {
        PsdKeyFigures figures = PsdKeyFigures.compute(List.of(group(1)), (g, code) -> json("{}"), 0);

        assertThat(figures.axes()).isZero();
        assertThat(figures.budget()).isZero();
        assertThat(figures.staffEvolutionLabel())
                .as("effectifs jamais renseignés : « 0 → 0 » ne dirait rien")
                .isEqualTo("—");
    }

    @Test
    @DisplayName("Effectifs renseignés : l'évolution est annoncée telle quelle")
    void annonceLEvolutionDesEffectifs() {
        PsdKeyFigures figures = PsdKeyFigures.compute(List.of(group(1)),
                (g, code) -> json("S14B".equals(code)
                        ? "{\"totals\":{\"2027\":{\"total\":412},\"2031\":{\"total\":508}}}"
                        : "{}"),
                1);

        assertThat(figures.staffEvolutionLabel()).isEqualTo("412 → 508 agents");
    }
}
