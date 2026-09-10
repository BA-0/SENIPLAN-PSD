package com.senico.diagnostic.config;

import com.senico.diagnostic.domain.Role;
import com.senico.diagnostic.domain.User;
import com.senico.diagnostic.repository.UserRepository;
import com.senico.diagnostic.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Frontiere de droits de la Direction Generale : le DG voit et arbitre tout, l'administration
 * technique reste a l'admin, et l'approbation finale reste au DG seul.
 *
 * <p>Ces regles ne se verifient pas a l'oeil. Rien ne signale, en lisant {@link SecurityConfig},
 * qu'un matcher place apres un autre plus large ne s'applique jamais : un reordonnancement
 * ouvrirait au DG la suppression de groupes sans que personne ne s'en apercoive.</p>
 *
 * <p>Aucun cas n'ecrit en base. La ou il faut prouver qu'un acces est accorde sur une route qui
 * modifie des donnees, on vise un groupe inexistant : l'autorisation etant evaluee avant le
 * traitement, un 404 prouve que l'acces est passe, sans rien changer.</p>
 *
 * <pre>mvn test -Dtest=DirectionGeneraleAccessIT</pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
class DirectionGeneraleAccessIT {

    /** Groupe volontairement inexistant : voir la note sur l'absence d'ecriture. */
    private static final long GROUPE_INEXISTANT = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private RequestPostProcessor compte(String username) {
        User utilisateur = userRepository.findByUsername(username).orElse(null);
        assumeTrue(utilisateur != null, "Compte " + username + " absent en base");
        return user(new UserPrincipal(utilisateur));
    }

    @Test
    @DisplayName("Le compte du DG existe, sans rattachement a un groupe")
    void leCompteDuDgExiste() {
        User dg = userRepository.findByUsername("m.dia").orElse(null);
        assumeTrue(dg != null, "Compte m.dia absent : migration V15 non appliquee");
        assertThat(dg.getRole()).isEqualTo(Role.DIRECTEUR_GENERAL);
        assertThat(dg.getGroup()).as("le DG n'est pas un groupe de travail").isNull();
        assertThat(dg.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Le DG accede au pilotage et aux documents")
    void leDgConsulte() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard").with(compte("m.dia")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/exports/synthesis/pdf").with(compte("m.dia")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Le DG peut valider en masse : c'est la raison d'etre de son compte")
    void leDgValide() throws Exception {
        mockMvc.perform(post("/api/v1/admin/groups/" + GROUPE_INEXISTANT + "/sections/validate-all")
                        .with(compte("m.dia")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Le DG lit la liste des directions, sans pouvoir la modifier")
    void leDgLitLesDirections() throws Exception {
        RequestPostProcessor dg = compte("m.dia");

        // Sans cette lecture, les selecteurs de direction et les ecrans de documents
        // restent vides pour le DG, qui ne peut alors rien arbitrer.
        mockMvc.perform(get("/api/v1/groups").with(dg)).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/groups/1").contentType("application/json").content("{}").with(dg))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Le DG seul approuve ce qui entre dans les documents consolides")
    void leDgApprouve() throws Exception {
        RequestPostProcessor dg = compte("m.dia");

        mockMvc.perform(post("/api/v1/admin/groups/" + GROUPE_INEXISTANT + "/sections/S01/dg-approval")
                        .contentType("application/json").content("{\"decision\":\"APPROVE\"}").with(dg))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/admin/groups/" + GROUPE_INEXISTANT + "/sections/dg-approve-all").with(dg))
                .andExpect(status().isNotFound());
        // Approbation par lot, toutes directions confondues. On vise la aussi une direction
        // inexistante : le 404 prouve l'acces sans rien approuver en base — et surtout sans
        // toucher a la route « tout approuver », qui elle agirait pour de bon.
        mockMvc.perform(post("/api/v1/admin/dg-approvals/selection")
                        .contentType("application/json")
                        .content("{\"targets\":[{\"groupId\":" + GROUPE_INEXISTANT + ",\"sectionCode\":\"S01\"}]}")
                        .with(dg))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("L'admin valide, mais ne s'approuve pas a lui-meme le passage dans les documents")
    void ladminNapprouvePas() throws Exception {
        RequestPostProcessor admin = compte("admin");

        mockMvc.perform(post("/api/v1/admin/groups/" + GROUPE_INEXISTANT + "/sections/S01/dg-approval")
                        .contentType("application/json").content("{\"decision\":\"APPROVE\"}").with(admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/groups/" + GROUPE_INEXISTANT + "/sections/dg-approve-all").with(admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/dg-approvals/selection")
                        .contentType("application/json")
                        .content("{\"targets\":[{\"groupId\":" + GROUPE_INEXISTANT + ",\"sectionCode\":\"S01\"}]}")
                        .with(admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/dg-approvals/all").with(admin))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("L'administration technique reste fermee au DG")
    void ladministrationTechniqueEstFermeeAuDg() throws Exception {
        RequestPostProcessor dg = compte("m.dia");

        mockMvc.perform(post("/api/v1/groups").with(dg)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users").with(dg)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/groups/" + GROUPE_INEXISTANT + "/cycles/new").with(dg))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/groups/" + GROUPE_INEXISTANT + "/sections/S01/content")
                        .contentType("application/json").content("{}").with(dg))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("L'admin garde l'administration technique")
    void ladminGardeLadministration() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").with(compte("admin")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un chef de groupe n'accede toujours pas a l'espace de pilotage")
    void leChefDeGroupeResteHorsDuPilotage() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard").with(compte("dir.commerciale")))
                .andExpect(status().isForbidden());
    }
}
