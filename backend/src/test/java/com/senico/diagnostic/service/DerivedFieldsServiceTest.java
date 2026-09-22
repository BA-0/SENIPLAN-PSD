package com.senico.diagnostic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.senico.diagnostic.domain.SectionType;
import com.senico.diagnostic.repository.SectionResponseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Champs derives du plan d'evolution des effectifs (S14B) et de la matrice des ressources (S02) :
 * sans base de donnees, ces deux calculs ne lisent aucune autre section.
 */
class DerivedFieldsServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DerivedFieldsService service = new DerivedFieldsService(mock(SectionResponseRepository.class), mapper);

    private ObjectNode apply(SectionType type, String json) throws Exception {
        return service.apply(type, 1L, (ObjectNode) mapper.readTree(json));
    }

    private List<String> rowIds(JsonNode content, String field) {
        List<String> ids = new ArrayList<>();
        content.get("rows").forEach(row -> ids.add(row.path(field).asText() + row.path("label").asText()));
        return ids;
    }

    @Test
    @DisplayName("Effectifs : le total ne compte pas deux fois les agents ventilés par hiérarchie et par statut")
    void leTotalSuitLaHierarchie() throws Exception {
        ObjectNode content = apply(SectionType.STAFF_EVOLUTION, """
                {"rows":[{"category":"HIERARCHIE","staffKey":"CADRE","years":{"2027":{"male":8,"female":4}}},
                         {"category":"STATUT","staffKey":"CDI","years":{"2027":{"male":8,"female":4}}},
                         {"category":"STATUT","staffKey":"CDD","years":{"2028":{"male":3,"female":2}}}]}""");

        assertThat(content.at("/totals/2027/total").asInt()).isEqualTo(12);
        assertThat(content.at("/totals/2028/total").asInt())
                .as("seule la ventilation par statut est renseignée cette année-là : elle fait foi")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("Effectifs : les lignes saisies sont remises dans l'ordre du modèle, sans rajouter les manquantes, « Fonctionnaire » retiré")
    void ordonneLesLignesDuModele() throws Exception {
        ObjectNode content = apply(SectionType.STAFF_EVOLUTION, """
                {"rows":[{"category":"STATUT","staffKey":"CDI","years":{}},
                         {"category":"STATUT","staffKey":"FONCTIONNAIRE","years":{}},
                         {"category":"HIERARCHIE","staffKey":"JOURNALIER","years":{"2027":{"male":3,"female":1}}},
                         {"category":"HIERARCHIE","staffKey":"","label":"Intérimaire","years":{}},
                         {"category":"HIERARCHIE","staffKey":"CADRE","years":{}}]}""");

        assertThat(rowIds(content, "staffKey")).containsExactly("CADRE", "JOURNALIER", "Intérimaire", "CDI");
        assertThat(content.at("/rows/1/years/2027/male").asInt())
                .as("les journaliers saisis dans la hiérarchie y restent")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("Ressources : seules les lignes saisies restent, dans l'ordre du modèle client")
    void ordonneLaMatriceDesRessources() throws Exception {
        ObjectNode content = apply(SectionType.RESOURCES_MATRIX, """
                {"rows":[{"resourceKey":"COMPETENCES","strengths":"Equipes experimentees","weaknesses":"","challenges":""},
                         {"resourceKey":"CADRE_JURIDIQUE_INSTITUTIONNEL","strengths":"","weaknesses":"","challenges":""}]}""");

        assertThat(rowIds(content, "resourceKey")).containsExactly("CADRE_JURIDIQUE_INSTITUTIONNEL", "COMPETENCES");
        assertThat(content.at("/rows/1/strengths").asText()).isEqualTo("Equipes experimentees");
    }

    @Test
    @DisplayName("Performances 2026 : un délai ou un nombre d'incidents au-dessus de la cible donne un taux sous 100 %")
    void leTauxSuitLeSensDeLIndicateur() throws Exception {
        ObjectNode content = apply(SectionType.PERFORMANCE_REVIEW_2026, """
                {"rows":[{"indicator":"Chiffre d'affaires commercial annuel","target2026":4200,"achieved2026":3980},
                         {"indicator":"Délai moyen d'acheminement (jours)","target2026":3,"achieved2026":3.8},
                         {"indicator":"Nombre d'incidents techniques majeurs","target2026":10,"achieved2026":17},
                         {"indicator":"Taux de satisfaction sur les délais de livraison (%)","target2026":80,"achieved2026":65},
                         {"indicator":"Pannes du parc","lowerIsBetter":true,"target2026":4,"achieved2026":5}]}""");

        assertThat(content.at("/rows/0/rate").asDouble()).isEqualTo(94.8);
        assertThat(content.at("/rows/1/rate").asDouble()).isEqualTo(78.9);
        assertThat(content.at("/rows/2/rate").asDouble()).isEqualTo(58.8);
        assertThat(content.at("/rows/3/rate").asDouble()).as("un taux de satisfaction se lit à la hausse").isEqualTo(81.3);
        assertThat(content.at("/rows/4/rate").asDouble()).as("le sens précisé par la ligne l'emporte").isEqualTo(80);
    }
}
