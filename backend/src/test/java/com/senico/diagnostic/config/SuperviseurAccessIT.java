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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Frontiere de droits du superviseur : il voit tout le pilotage en temps reel, et n'ecrit rien.
 *
 * <p>Comme pour {@link DirectionGeneraleAccessIT}, aucun cas n'ecrit en base : les routes qui
 * modifient des donnees visent un groupe inexistant, et le refus (403) arrive avant tout
 * traitement.</p>
 *
 * <pre>mvn test -Dtest=SuperviseurAccessIT</pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
class SuperviseurAccessIT {

    /** Groupe volontairement inexistant : voir la note sur l'absence d'ecriture. */
    private static final long GROUPE_INEXISTANT = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private RequestPostProcessor superviseur() {
        User utilisateur = userRepository.findByUsername("superviseur").orElse(null);
        assumeTrue(utilisateur != null, "Compte superviseur absent : migration V43 non appliquee");
        return user(new UserPrincipal(utilisateur));
    }

    @Test
    @DisplayName("Le compte superviseur existe, sans rattachement a un groupe")
    void leCompteExiste() {
        User compte = userRepository.findByUsername("superviseur").orElse(null);
        assumeTrue(compte != null, "Compte superviseur absent : migration V43 non appliquee");
        assertThat(compte.getRole()).isEqualTo(Role.SUPERVISEUR);
        assertThat(compte.getGroup()).as("le superviseur n'est pas un groupe de travail").isNull();
    }

    @Test
    @DisplayName("Le superviseur consulte le pilotage, les directions et les documents")
    void leSuperviseurConsulte() throws Exception {
        RequestPostProcessor sup = superviseur();

        mockMvc.perform(get("/api/v1/admin/dashboard").with(sup)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/submissions").with(sup)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/activity").with(sup)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/groups").with(sup)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/exports/synthesis/pdf").with(sup)).andExpect(status().isOk());
        // PDF sectoriel d'une direction : la securite laisse passer, seul le groupe inexistant repond 404.
        mockMvc.perform(get("/api/v1/admin/exports/groups/" + GROUPE_INEXISTANT + "/pdf").with(sup))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Le superviseur ne valide, n'approuve, ne modifie ni n'efface rien")
    void leSuperviseurNecritRien() throws Exception {
        RequestPostProcessor sup = superviseur();
        String section = "/api/v1/admin/groups/" + GROUPE_INEXISTANT + "/sections/S01";

        mockMvc.perform(post(section + "/review")
                        .contentType("application/json").content("{\"decision\":\"VALIDATE\"}").with(sup))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(section + "/dg-approval")
                        .contentType("application/json").content("{\"decision\":\"APPROVE\"}").with(sup))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/dg-approvals/selection")
                        .contentType("application/json")
                        .content("{\"targets\":[{\"groupId\":" + GROUPE_INEXISTANT + ",\"sectionCode\":\"S01\"}]}")
                        .with(sup))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/validations/all").with(sup)).andExpect(status().isForbidden());
        mockMvc.perform(put(section + "/content")
                        .contentType("application/json").content("{\"rows\":[]}").with(sup))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(section).with(sup)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/psd-narrative/VISION")
                        .contentType("application/json").content("{\"content\":\"\"}").with(sup))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/activity").with(sup)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("L'administration technique reste fermee au superviseur")
    void ladministrationTechniqueEstFermee() throws Exception {
        RequestPostProcessor sup = superviseur();

        mockMvc.perform(get("/api/v1/admin/users").with(sup)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/groups").with(sup)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/groups/1").contentType("application/json").content("{}").with(sup))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/groups/" + GROUPE_INEXISTANT + "/cycles/new").with(sup))
                .andExpect(status().isForbidden());
    }
}
