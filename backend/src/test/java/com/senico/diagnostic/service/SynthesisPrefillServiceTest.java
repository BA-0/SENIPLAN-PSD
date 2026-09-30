package com.senico.diagnostic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.senico.diagnostic.domain.SectionResponse;
import com.senico.diagnostic.domain.SectionType;
import com.senico.diagnostic.repository.SectionResponseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pre-remplissage des tableaux de synthese a partir des tableaux deja saisis par la direction.
 */
class SynthesisPrefillServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SectionResponseRepository repository = mock(SectionResponseRepository.class);
    private final SynthesisPrefillService service = new SynthesisPrefillService(repository, mapper);

    private static final String RESOURCES_MATRIX = """
            {"rows":[{"resourceKey":"COMPETENCES","strengths":"- Equipe experimentee\\n- Expertise metier",
                      "weaknesses":"- Backups insuffisants","challenges":"- Former les talents"},
                     {"resourceKey":"CAPACITES_INSTITUTIONNELLES","strengths":"","weaknesses":"Outils vieillissants","challenges":""}]}""";

    private void source(int sectionId, String json) {
        when(repository.findByGroupIdAndSectionId(eq(1L), eq(sectionId)))
                .thenReturn(Optional.of(SectionResponse.builder().contentJson(json).build()));
    }

    private ObjectNode json(String json) throws Exception {
        return (ObjectNode) mapper.readTree(json);
    }

    {
        when(repository.findByGroupIdAndSectionId(anyLong(), anyInt())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("S03B vide : forces, faiblesses, défis et note repris de la matrice des ressources, dans l'ordre du modèle")
    void resourcesSynthesisFromMatrix() throws Exception {
        source(2, RESOURCES_MATRIX);
        ObjectNode content = json("""
                {"synthesisNote":"","majorStrengths":[],"majorWeaknesses":[""],"priorityChallenges":[]}""");

        List<String> filled = service.apply(SectionType.RESOURCES_SYNTHESIS, 1L, content);

        assertThat(filled).containsExactlyInAnyOrder("majorStrengths", "majorWeaknesses", "priorityChallenges", "synthesisNote");
        assertThat(content.at("/majorStrengths/0").asText()).isEqualTo("Compétences : Equipe experimentee ; Expertise metier");
        assertThat(content.at("/majorWeaknesses/0").asText())
                .as("capacités institutionnelles précèdent les compétences dans le modèle client ; libellé sans parenthèse")
                .isEqualTo("Capacités institutionnelles : Outils vieillissants");
        assertThat(content.path("synthesisNote").asText()).contains("2 domaines");
        assertThat(content.path("autoFilled")).hasSize(4);
    }

    @Test
    @DisplayName("S03B déjà commencée : la note saisie est gardée, seules les listes vides sont proposées")
    void keepsWhatTheDirectionWrote() throws Exception {
        source(2, RESOURCES_MATRIX);
        ObjectNode content = json("""
                {"synthesisNote":"Notre note","majorStrengths":["Force saisie"],"majorWeaknesses":[],"priorityChallenges":[]}""");

        List<String> filled = service.apply(SectionType.RESOURCES_SYNTHESIS, 1L, content);

        assertThat(filled).containsExactlyInAnyOrder("majorWeaknesses", "priorityChallenges");
        assertThat(content.path("synthesisNote").asText()).isEqualTo("Notre note");
        assertThat(content.at("/majorStrengths/0").asText()).isEqualTo("Force saisie");
    }

    @Test
    @DisplayName("Une proposition effacée par la direction ne revient pas")
    void neverRefillsAFieldTwice() throws Exception {
        source(2, RESOURCES_MATRIX);
        ObjectNode content = json("""
                {"synthesisNote":"","majorStrengths":[],"majorWeaknesses":[],"priorityChallenges":[],
                 "autoFilled":["synthesisNote","majorStrengths","majorWeaknesses","priorityChallenges"]}""");

        assertThat(service.apply(SectionType.RESOURCES_SYNTHESIS, 1L, content)).isEmpty();
        assertThat(content.path("majorStrengths")).isEmpty();
    }

    @Test
    @DisplayName("S06B vide : un domaine par ressource, contraintes = faiblesses, défis = défis")
    void constraintsSynthesisFromMatrix() throws Exception {
        source(2, RESOURCES_MATRIX);
        ObjectNode content = json("""
                {"rows":[{"domain":"","constraints":[""],"challenges":[]}]}""");

        assertThat(service.apply(SectionType.CONSTRAINTS_SYNTHESIS, 1L, content)).containsExactly("rows");
        assertThat(content.path("rows")).hasSize(2);
        assertThat(content.at("/rows/1/domain").asText()).isEqualTo("Compétences");
        assertThat(content.at("/rows/1/constraints/0").asText()).isEqualTo("Backups insuffisants");
        assertThat(content.at("/rows/1/challenges/0").asText()).isEqualTo("Former les talents");
    }

    @Test
    @DisplayName("S17 vide : vision du cadre stratégique, orientations = extrants du plan d'actions, actions = activités")
    void strategicSummaryFromActionPlan() throws Exception {
        source(18, """
                {"vision":"Être le leader régional","mission":[],"values":[]}""");
        source(10, """
                {"axes":[{"axisCode":"AXE1","effects":[{"effectLabel":"Taux de service","rows":[
                  {"extrant":"Diversifier le sourcing","activities":"- Multiplier les origines\\n- Mettre à jour la matrice pays\\n\\n"}]}]}]}""");
        source(8, """
                {"axes":[{"axisCode":"AXE2","title":"Entrepôts","specificObjectives":["Mettre en œuvre les 5S"]}]}""");
        ObjectNode content = json("""
                {"vision":"","axes":[{"axisCode":"AXE1","orientations":[]},{"axisCode":"AXE2","orientations":[]},
                                     {"axisCode":"AXE3","orientations":[]}]}""");

        assertThat(service.apply(SectionType.STRATEGIC_SUMMARY, 1L, content)).containsExactly("vision", "axes");
        assertThat(content.path("vision").asText()).isEqualTo("Être le leader régional");
        assertThat(content.at("/axes/0/orientations/0/label").asText()).isEqualTo("Diversifier le sourcing");
        assertThat(content.at("/axes/0/orientations/0/actions")).hasSize(2);
        assertThat(content.at("/axes/0/orientations/0/actions/1/label").asText()).isEqualTo("Mettre à jour la matrice pays");
        assertThat(content.at("/axes/1/orientations/0/label").asText())
                .as("sans plan d'actions pour l'axe, les objectifs spécifiques de S08 tiennent lieu d'orientations")
                .isEqualTo("Mettre en œuvre les 5S");
        assertThat(content.at("/axes/2/orientations")).isEmpty();
    }

    @Test
    @DisplayName("S17 déjà commencée : aucune orientation n'est ajoutée")
    void strategicSummaryAlreadyStarted() throws Exception {
        source(10, """
                {"axes":[{"axisCode":"AXE1","effects":[{"rows":[{"extrant":"X","activities":"Y"}]}]}]}""");
        ObjectNode content = json("""
                {"vision":"Vision","axes":[{"axisCode":"AXE1","orientations":[{"label":"Mon orientation","actions":[]}]}]}""");

        assertThat(service.apply(SectionType.STRATEGIC_SUMMARY, 1L, content)).isEmpty();
        assertThat(content.at("/axes/0/orientations")).hasSize(1);
    }

    @Test
    @DisplayName("S07 et S09B : notes de synthèse rédigées à partir des analyses et du cadre logique")
    void synthesisNotes() throws Exception {
        source(4, """
                {"strengths":["Equipe soudée"],"weaknesses":["Outils manuels"],"opportunities":[],"threats":[]}""");
        source(6, """
                {"rows":[{"source":"CAUSES_PROFONDES","items":["Sous-investissement"]},{"source":"SOLUTIONS","items":["Digitaliser"]}]}""");
        source(9, """
                {"axes":[{"axisCode":"AXE1","objective":"Moderniser","rows":[{"level":"IMPACT","interventionLogic":"Entreprise performante"}]}]}""");
        source(8, """
                {"axes":[{"axisCode":"AXE1","title":"Transformation digitale"}]}""");

        ObjectNode inventory = json("""
                {"synthesisNote":""}""");
        assertThat(service.apply(SectionType.INVENTORY, 1L, inventory)).containsExactly("synthesisNote");
        assertThat(inventory.path("synthesisNote").asText())
                .contains("Equipe soudée").contains("Outils manuels").contains("Sous-investissement").contains("Digitaliser");

        ObjectNode logframe = json("""
                {"synthesisNote":""}""");
        assertThat(service.apply(SectionType.LOGFRAME_SYNTHESIS, 1L, logframe)).containsExactly("synthesisNote");
        assertThat(logframe.path("synthesisNote").asText())
                .contains("1 axe stratégique").contains("Axe 1 – Transformation digitale").contains("Entreprise performante");
    }

    @Test
    @DisplayName("Tableaux sources vides : rien n'est proposé")
    void nothingWithoutSources() throws Exception {
        ObjectNode content = json("""
                {"synthesisNote":"","majorStrengths":[],"majorWeaknesses":[],"priorityChallenges":[]}""");
        assertThat(service.apply(SectionType.RESOURCES_SYNTHESIS, 1L, content)).isEmpty();
        assertThat(content.has("autoFilled")).isFalse();
    }
}
