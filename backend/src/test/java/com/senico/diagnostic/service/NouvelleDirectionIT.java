package com.senico.diagnostic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senico.diagnostic.dto.group.CreateWorkGroupRequest;
import com.senico.diagnostic.dto.group.WorkGroupDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Une direction que l'admin vient de creer trouve toutes ses sections vides : aucune ligne, aucun
 * texte, rien a effacer avant de saisir. Seule l'ossature du plan (les quatre axes et leurs cadres)
 * est en place, sans contenu. Le scenario passe par l'API, comme le ferait le chef de groupe.
 *
 * <p>La direction et son compte sont crees puis annules : {@code @Transactional} tient toutes les
 * ecritures dans la transaction du test, que Spring annule a la sortie.</p>
 *
 * <pre>mvn test -Dtest=NouvelleDirectionIT</pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NouvelleDirectionIT {

    private static final String IDENTIFIANT = "recette.direction.neuve";
    private static final String MOT_DE_PASSE_REMIS = "RemisParLadmin1!";
    private static final String MOT_DE_PASSE_CHOISI = "ChoisiParMoi2027!";

    /** Tableaux qui structurent le plan : leurs elements sont l'ossature, pas une saisie. */
    private static final Set<String> OSSATURE = Set.of("axes", "effects", "groups");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkGroupService workGroupService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Une direction neuve voit toutes ses sections vides, sans ligne pre-remplie")
    void laDirectionNeuveVoitSesSectionsVides() throws Exception {
        WorkGroupDto direction = workGroupService.create(new CreateWorkGroupRequest(
                "Direction de recette", "", null, IDENTIFIANT, "Chef de groupe de recette", MOT_DE_PASSE_REMIS));
        assertThat(direction.completionPercent()).isZero();

        String jeton = jetonApresChangementDeMotDePasse();

        JsonNode sections = objectMapper.readTree(mockMvc.perform(get("/api/v1/me/sections")
                        .header("Authorization", "Bearer " + jeton))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(sections.size()).as("toutes les sections du canevas sont proposees").isGreaterThan(15);

        for (JsonNode section : sections) {
            String code = section.path("code").asText();
            assertThat(section.path("status").asText()).as(code).isEqualTo("NOT_STARTED");

            JsonNode contenu = objectMapper.readTree(mockMvc.perform(get("/api/v1/me/sections/" + code)
                            .header("Authorization", "Bearer " + jeton))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());

            assertThat(contenu.path("groupId").asLong()).as(code + " : c'est bien sa direction").isEqualTo(direction.id());
            assertThat(contenu.path("version").asInt()).as(code + " : rien n'a encore ete enregistre").isZero();
            assertThat(lignesSaisies(contenu.path("content"), null))
                    .as(code + " : aucune ligne pre-remplie, la direction ajoute les siennes")
                    .isZero();
            assertThat(textesRenseignes(contenu.path("content")))
                    .as(code + " : aucun texte pre-rempli")
                    .isZero();
        }
    }

    private String jetonApresChangementDeMotDePasse() throws Exception {
        String jetonProvisoire = connexion(MOT_DE_PASSE_REMIS).path("accessToken").asText();
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + jetonProvisoire)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", MOT_DE_PASSE_REMIS, "newPassword", MOT_DE_PASSE_CHOISI))))
                .andExpect(status().isOk());
        return connexion(MOT_DE_PASSE_CHOISI).path("accessToken").asText();
    }

    private JsonNode connexion(String motDePasse) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("username", IDENTIFIANT, "password", motDePasse))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(reponse);
    }

    /** Nombre d'elements de tableau, a tous les niveaux, hors ossature du plan. */
    private static int lignesSaisies(JsonNode node, String cle) {
        int total = 0;
        if (node.isArray()) {
            if (cle == null || !OSSATURE.contains(cle)) {
                total += node.size();
            }
            for (JsonNode child : node) {
                total += lignesSaisies(child, null);
            }
        } else if (node.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                total += lignesSaisies(field.getValue(), field.getKey());
            }
        }
        return total;
    }

    /** Nombre de chaines non vides, a tous les niveaux, hors codes d'ossature (AXE1, EFFET1, OS1, niveaux). */
    private static int textesRenseignes(JsonNode node) {
        int total = 0;
        if (node.isTextual()) {
            String text = node.asText().trim();
            return text.isEmpty() || text.matches("[A-Z0-9_]+") ? 0 : 1;
        }
        for (JsonNode child : node) {
            total += textesRenseignes(child);
        }
        return total;
    }
}
