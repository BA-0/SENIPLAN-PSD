package com.senico.diagnostic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.senico.diagnostic.domain.SectionType;
import com.senico.diagnostic.repository.SectionResponseRepository;
import com.senico.diagnostic.validation.DefaultSectionContentFactory;
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
    @DisplayName("Effectifs : la ligne « Fonctionnaire » apparaît dans un plan saisi avant son ajout")
    void rajouteLesLignesDuModele() throws Exception {
        ObjectNode content = apply(SectionType.STAFF_EVOLUTION, """
                {"rows":[{"category":"STATUT","staffKey":"CDI","years":{}},
                         {"category":"HIERARCHIE","staffKey":"","label":"Stagiaire","years":{}}]}""");

        assertThat(rowIds(content, "staffKey")).containsExactly(
                "CADRE", "AGENTS_MAITRISE", "EMPLOYE", "JOURNALIER", "Stagiaire", "FONCTIONNAIRE", "CDI", "CDD", "EXPATRIE");
    }

    @Test
    @DisplayName("Ressources : les lignes du modèle client sont rajoutées dans son ordre, sans perdre la saisie")
    void completeLaMatriceDesRessources() throws Exception {
        ObjectNode content = apply(SectionType.RESOURCES_MATRIX, """
                {"rows":[{"resourceKey":"COMPETENCES","strengths":"Equipes experimentees","weaknesses":"","challenges":""}]}""");

        assertThat(rowIds(content, "resourceKey")).containsExactly(DefaultSectionContentFactory.RESOURCE_KEYS);
        assertThat(content.at("/rows/4/strengths").asText()).isEqualTo("Equipes experimentees");
    }
}
