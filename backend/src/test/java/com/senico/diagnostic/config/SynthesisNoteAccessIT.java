package com.senico.diagnostic.config;

import com.senico.diagnostic.domain.Role;
import com.senico.diagnostic.domain.User;
import com.senico.diagnostic.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Qui corrige la note de synthese : le role DG et le compte dir.generale, personne d'autre.
 *
 * <p>Les comptes sont fabriques ici plutot que lus en base : la regle se verifie meme sur une
 * base de developpement ou le compte du DG n'existe pas. Aucun cas n'ecrit : une note vide est
 * refusee (400), ce qui prouve que l'acces est passe sans rien enregistrer.</p>
 *
 * <pre>mvn test -Dtest=SynthesisNoteAccessIT</pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
class SynthesisNoteAccessIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static RequestPostProcessor compte(String username, Role role) {
        return user(new UserPrincipal(User.builder().id(-1L).username(username).fullName(username).role(role).build()));
    }

    @Test
    @DisplayName("Le DG et dir.generale ouvrent, enregistrent et telechargent la note corrigee")
    void laDirectionGeneraleCorrige() throws Exception {
        for (RequestPostProcessor compte : new RequestPostProcessor[]{
                compte("dg.test", Role.DIRECTEUR_GENERAL), compte("dir.generale", Role.GROUP_LEADER)}) {
            mockMvc.perform(get("/api/v1/synthesis-note").with(compte)).andExpect(status().isOk());
            mockMvc.perform(put("/api/v1/synthesis-note")
                            .contentType("application/json").content("{\"blocks\":[]}").with(compte))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/v1/synthesis-note/pdf").with(compte)).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("L'admin, le superviseur et les autres directions n'y ont pas acces")
    void lesAutresComptesSontRefuses() throws Exception {
        for (RequestPostProcessor compte : new RequestPostProcessor[]{
                compte("admin.test", Role.ADMIN), compte("sup.test", Role.SUPERVISEUR), compte("dir.rh", Role.GROUP_LEADER)}) {
            mockMvc.perform(get("/api/v1/synthesis-note").with(compte)).andExpect(status().isForbidden());
            mockMvc.perform(put("/api/v1/synthesis-note")
                            .contentType("application/json").content("{\"blocks\":[]}").with(compte))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/synthesis-note").with(compte)).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/synthesis-note/pdf").with(compte)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/synthesis-note")).andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional // annule a la fin : la base de developpement n'est pas touchee
    @DisplayName("Deux postes enregistrent leurs corrections : la note et ses exports reprennent les deux")
    void laCorrectionEstReprise() throws Exception {
        RequestPostProcessor dg = compte("dir.generale", Role.GROUP_LEADER);
        JsonNode note = lire(dg);
        String depart = note.get("version").asText();
        assertThat(depart).hasSize(64);

        List<Integer> titres = new ArrayList<>();
        for (int i = 0; i < note.get("blocks").size(); i++) {
            if ("HEADING".equals(note.get("blocks").get(i).get("type").asText())) {
                titres.add(i);
            }
        }
        assertThat(titres).as("la note a au moins deux titres").hasSizeGreaterThanOrEqualTo(2);
        int a = titres.get(0);
        int b = titres.get(1);

        // Deux postes partent de la meme version et corrigent chacun un titre.
        JsonNode posteA = enregistrer(dg, depart, a, titre(note, a, "TITRE CORRIGÉ PAR LE POSTE A"), "poste-A");
        assertThat(posteA.get("blocks").get(a).get("text").asText()).isEqualTo("TITRE CORRIGÉ PAR LE POSTE A");
        JsonNode posteB = enregistrer(dg, depart, b, titre(note, b, "TITRE CORRIGÉ PAR LE POSTE B"), "poste-B");
        assertThat(posteB.get("blocks").get(a).get("text").asText()).as("la correction du poste A reste").isEqualTo("TITRE CORRIGÉ PAR LE POSTE A");
        assertThat(posteB.get("blocks").get(b).get("text").asText()).isEqualTo("TITRE CORRIGÉ PAR LE POSTE B");
        assertThat(posteB.get("conflicts").asInt()).isZero();

        JsonNode relue = lire(dg);
        assertThat(relue.get("edited").asBoolean()).isTrue();
        assertThat(relue.get("updatedBy").asText()).isEqualTo("dir.generale");
        assertThat(relue.get("blocks")).isEqualTo(posteB.get("blocks"));
        assertThat(relue.get("version").asText()).isEqualTo(posteB.get("version").asText());

        // Onglet ouvert avant la mise en ligne de la fusion : il envoie encore la note entiere.
        ObjectNode ancien = (ObjectNode) relue.get("blocks").get(a).deepCopy();
        ancien.put("text", "TITRE ENVOYÉ À L'ANCIENNE");
        com.fasterxml.jackson.databind.node.ArrayNode entiere = (com.fasterxml.jackson.databind.node.ArrayNode) relue.get("blocks").deepCopy();
        entiere.set(a, ancien);
        mockMvc.perform(put("/api/v1/synthesis-note").contentType("application/json")
                        .content(objectMapper.writeValueAsString(objectMapper.createObjectNode().set("blocks", entiere)))
                        .with(dg))
                .andExpect(status().isOk());
        JsonNode apresAncien = lire(dg);
        assertThat(apresAncien.get("blocks").get(a).get("text").asText()).isEqualTo("TITRE ENVOYÉ À L'ANCIENNE");
        assertThat(apresAncien.get("blocks").get(b).get("text").asText()).isEqualTo("TITRE CORRIGÉ PAR LE POSTE B");

        byte[] word = mockMvc.perform(get("/api/v1/synthesis-note/word").with(dg))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(word));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            assertThat(extractor.getText()).as("le Word reprend les corrections")
                    .contains("TITRE ENVOYÉ À L'ANCIENNE", "TITRE CORRIGÉ PAR LE POSTE B");
        }
        mockMvc.perform(get("/api/v1/synthesis-note/pdf").with(dg)).andExpect(status().isOk());

        JsonNode reprise = objectMapper.readTree(mockMvc.perform(delete("/api/v1/synthesis-note").with(dg))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(reprise.get("edited").asBoolean()).isFalse();
    }

    private JsonNode lire(RequestPostProcessor compte) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/synthesis-note").with(compte))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static JsonNode titre(JsonNode note, int index, String texte) {
        ObjectNode bloc = (ObjectNode) note.get("blocks").get(index).deepCopy();
        bloc.put("text", texte);
        return bloc;
    }

    private JsonNode enregistrer(RequestPostProcessor compte, String version, int index, JsonNode bloc, String poste) throws Exception {
        ObjectNode corps = objectMapper.createObjectNode();
        corps.put("baseVersion", version);
        corps.put("clientId", poste);
        corps.putArray("changes").addObject().put("index", index).set("block", bloc);
        return objectMapper.readTree(mockMvc.perform(put("/api/v1/synthesis-note").contentType("application/json")
                        .content(objectMapper.writeValueAsString(corps)).with(compte))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
