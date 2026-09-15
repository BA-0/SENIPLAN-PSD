package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class V27HarmonisationTextesDirectionsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Les accents sont rétablis, « a » et « ou » selon leur contexte, sans toucher aux codes")
    void retablitLesAccents() {
        assertThat(V27__Harmonisation_textes_directions.accentuate("la formation des commerciaux reste a achever"))
                .isEqualTo("la formation des commerciaux reste à achever");
        assertThat(V27__Harmonisation_textes_directions.accentuate("La maintenance corrective a mobilise les equipes au detriment du preventif"))
                .isEqualTo("La maintenance corrective a mobilisé les équipes au détriment du préventif");
        assertThat(V27__Harmonisation_textes_directions.accentuate("Une ligne de financement partenaire n'a pas abouti"))
                .isEqualTo("Une ligne de financement partenaire n'a pas abouti");
        assertThat(V27__Harmonisation_textes_directions.accentuate("Renforcer les effectifs logistiques la ou la demande croit"))
                .isEqualTo("Renforcer les effectifs logistiques là où la demande croît");
        assertThat(V27__Harmonisation_textes_directions.accentuate("Soutien de l'Etat, part de marche, Mise en oeuvre du schema directeur SI"))
                .isEqualTo("Soutien de l'État, part de marché, Mise en œuvre du schéma directeur SI");
        assertThat(V27__Harmonisation_textes_directions.accentuate("EFFETS_IMMEDIATS")).isEqualTo("EFFETS_IMMEDIATS");
        assertThat(V27__Harmonisation_textes_directions.accentuate("articule les quatre axes du plan autour d'un impact"))
                .isEqualTo("articule ses quatre axes autour d'un impact");
    }

    @Test
    @DisplayName("Le plan d'actions coche chaque exercice budgété, sans décocher ni changer de montant")
    void cocheLesExercicesBudgetes() throws Exception {
        ObjectNode plan = (ObjectNode) mapper.readTree("""
                {"axes":[{"axisCode":"AXE1","effects":[{"rows":[{"activities":"Deployer une offre",
                  "years":{"2027":true,"2028":true,"2029":false,"2030":false,"2031":false}}]}]}]}""");
        JsonNode budget = mapper.readTree("""
                {"axes":[{"axisCode":"AXE1","effects":[{"rows":[{"activities":"Déployer une offre",
                  "years":{"2027":15000000,"2028":0,"2029":19500000,"2030":21800000,"2031":0}}]}]}]}""");

        V27__Harmonisation_textes_directions.scheduleBudgetedYears(plan, budget);

        JsonNode years = plan.at("/axes/0/effects/0/rows/0/years");
        assertThat(years.path("2027").asBoolean()).isTrue();
        assertThat(years.path("2028").asBoolean()).as("une coche existante reste").isTrue();
        assertThat(years.path("2029").asBoolean()).isTrue();
        assertThat(years.path("2030").asBoolean()).isTrue();
        assertThat(years.path("2031").asBoolean()).as("exercice sans budget").isFalse();
    }

    @Test
    @DisplayName("Le cadre logique générique reprend les orientations et les extrants du plan d'actions")
    void specifieLeCadreLogique() throws Exception {
        ObjectNode logframe = (ObjectNode) mapper.readTree("""
                {"axes":[{"axisCode":"AXE1","rows":[
                  {"level":"EFFETS_IMMEDIATS","interventionLogic":"Ameliorer concretement les resultats operationnels lies a l'axe"},
                  {"level":"EXTRANTS","interventionLogic":"Produire les livrables et realisations prevues dans le plan d'actions"},
                  {"level":"IMPACT","interventionLogic":"Texte propre a la direction"}]}]}""");
        JsonNode plan = mapper.readTree("""
                {"axes":[{"axisCode":"AXE1","effects":[
                  {"effectLabel":"Ouvrir 3 points de vente","rows":[{"extrant":"Reseau etendu"}]},
                  {"effectLabel":"","rows":[]}]}]}""");

        V27__Harmonisation_textes_directions.specifyLogframe(logframe, plan);

        assertThat(logframe.at("/axes/0/rows/0/interventionLogic").asText())
                .isEqualTo("Mise en œuvre effective des orientations : Ouvrir 3 points de vente");
        assertThat(logframe.at("/axes/0/rows/1/interventionLogic").asText()).isEqualTo("Production des extrants : Reseau etendu");
        assertThat(logframe.at("/axes/0/rows/2/interventionLogic").asText()).isEqualTo("Texte propre a la direction");
    }

    @Test
    @DisplayName("Seule la phrase d'origine de la Direction Logistique est remplacée")
    void remplaceLaVisionDeLaLogistique() throws Exception {
        ObjectNode logistique = (ObjectNode) mapper.readTree("""
                {"vision":"Leader dans le marché national et international","mission":["Leader dans le marché national et international"],"values":["Foi"]}""");
        ObjectNode autre = (ObjectNode) mapper.readTree("""
                {"vision":"Une vision déjà réécrite","mission":["m"],"values":["v"]}""");

        V27__Harmonisation_textes_directions.replaceLogistiqueFramework(logistique);
        V27__Harmonisation_textes_directions.replaceLogistiqueFramework(autre);

        assertThat(logistique.path("vision").asText()).startsWith("Faire de la Direction Logistique");
        assertThat(logistique.path("mission")).hasSize(3);
        assertThat(logistique.path("values").get(0).asText()).isEqualTo("Fiabilité");
        assertThat(autre.path("vision").asText()).isEqualTo("Une vision déjà réécrite");
    }
}
