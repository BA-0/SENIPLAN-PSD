package com.senico.diagnostic.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senico.diagnostic.domain.Role;
import com.senico.diagnostic.dto.user.CreateUserAccountRequest;
import com.senico.diagnostic.dto.user.UserAccountDto;
import com.senico.diagnostic.service.UserAccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Premiere connexion : un mot de passe remis par un tiers ne donne acces a rien tant qu'il n'a
 * pas ete remplace. La regle tient au serveur, pas a une redirection du navigateur — c'est
 * precisement ce que ce test verifie, en appelant l'API directement.
 *
 * <p>Le compte de recette est cree puis annule : {@code @Transactional} tient toutes les
 * ecritures dans la transaction du test, que Spring annule a la sortie.</p>
 *
 * <pre>mvn test -Dtest=PremiereConnexionIT</pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PremiereConnexionIT {

    private static final String IDENTIFIANT = "recette.premiere.connexion";
    private static final String MOT_DE_PASSE_REMIS = "RemisParLadmin1!";
    private static final String MOT_DE_PASSE_CHOISI = "ChoisiParMoi2024!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Un compte neuf ne peut rien faire tant qu'il n'a pas choisi son mot de passe")
    void leCompteNeufEstBloqueJusquAuChangement() throws Exception {
        UserAccountDto compte = userAccountService.create(new CreateUserAccountRequest(
                IDENTIFIANT, "Compte de recette", Role.ADMIN, null, MOT_DE_PASSE_REMIS));
        assertThat(compte.mustChangePassword()).isTrue();

        JsonNode session = connexion(MOT_DE_PASSE_REMIS);
        assertThat(session.path("user").path("mustChangePassword").asBoolean())
                .as("la reponse de connexion doit annoncer le changement a faire")
                .isTrue();
        String jeton = session.path("accessToken").asText();

        // Le coeur de la regle : le jeton est valide, mais ne donne acces a rien d'autre.
        mockMvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + jeton))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + jeton)
                        .contentType("application/json")
                        .content(corpsChangement("MauvaisMotDePasse1!", MOT_DE_PASSE_CHOISI)))
                .andExpect(status().isUnauthorized());

        String reponse = mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + jeton)
                        .contentType("application/json")
                        .content(corpsChangement(MOT_DE_PASSE_REMIS, MOT_DE_PASSE_CHOISI)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(reponse).path("user").path("mustChangePassword").asBoolean()).isFalse();

        mockMvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + jeton))
                .andExpect(status().isOk());

        // L'ancien mot de passe ne vaut plus rien, le nouveau ouvre la session.
        mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(corpsConnexion(MOT_DE_PASSE_REMIS)))
                .andExpect(status().isUnauthorized());
        assertThat(connexion(MOT_DE_PASSE_CHOISI).path("user").path("mustChangePassword").asBoolean()).isFalse();
    }

    private JsonNode connexion(String motDePasse) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(corpsConnexion(motDePasse)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(reponse);
    }

    private String corpsConnexion(String motDePasse) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "username", IDENTIFIANT, "password", motDePasse));
    }

    private String corpsChangement(String actuel, String nouveau) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "currentPassword", actuel, "newPassword", nouveau));
    }
}
