package com.senico.diagnostic.export;

import com.senico.diagnostic.domain.WorkGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recette des briques du cadre strategique consolide : lecture des axes arretes par la Direction
 * Generale, mise en forme des textes narratifs, rapprochement des constats formules differemment,
 * et rendu des graphiques.
 */
class PsdStrategicFrameworkTest {

    @Test
    @DisplayName("Les axes de l'entreprise se lisent avec leurs rattachements")
    void litLesAxesConsolides() {
        List<PsdConsolidatedAxes.Axis> axes = PsdConsolidatedAxes.parse("""
                {"axes":[{"title":"Moderniser","objective":"Faire mieux",
                          "links":[{"groupId":2,"axisCode":"AXE1"},{"groupId":null,"axisCode":"AXE2"}]},
                         {"title":"  ","links":[]}]}""");

        assertThat(axes).singleElement().satisfies(axis -> {
            assertThat(axis.title()).isEqualTo("Moderniser");
            assertThat(axis.objective()).isEqualTo("Faire mieux");
            assertThat(axis.links())
                    .as("un rattachement sans direction retrouvee est ignore, pas publie de travers")
                    .containsExactly(new PsdConsolidatedAxes.Link(2, "AXE1"));
        });
    }

    @Test
    @DisplayName("Un contenu illisible ne casse pas l'export et se refuse a la saisie")
    void refuseUnContenuIllisible() {
        assertThat(PsdConsolidatedAxes.parse("{pas du json")).isEmpty();
        assertThat(PsdConsolidatedAxes.validationError("{pas du json")).isNotNull();
        assertThat(PsdConsolidatedAxes.validationError("{\"axes\":[{\"title\":\"\"}]}")).isNotNull();
        assertThat(PsdConsolidatedAxes.validationError("{\"axes\":[{\"title\":\"Axe\"}]}")).isNull();
        assertThat(PsdConsolidatedAxes.validationError("")).as("vider le bloc reste permis").isNull();
    }

    @Test
    @DisplayName("Un texte redige devient paragraphes, listes et intertitres")
    void metEnFormeUnTexteRedige() {
        List<ExportBlock> blocks = PsdNarrativeText.blocks("""
                Premier paragraphe.

                Niveau stratégique :
                - le Conseil d'Administration ;
                - le comité de pilotage.
                Dernier paragraphe.""");

        assertThat(blocks).hasSize(4);
        assertThat(blocks.get(0)).isEqualTo(new ExportBlock.Paragraph("Premier paragraphe."));
        assertThat(blocks.get(1)).isEqualTo(new ExportBlock.Heading("Niveau stratégique", 4));
        assertThat(blocks.get(2)).isEqualTo(new ExportBlock.BulletList(null,
                List.of("le Conseil d'Administration ;", "le comité de pilotage.")));
        assertThat(blocks.get(3)).isEqualTo(new ExportBlock.Paragraph("Dernier paragraphe."));
    }

    @Test
    @DisplayName("Deux constats voisins mais distincts ne sont pas fusionnes")
    void neFusionnePasDeuxConstatsDistincts() {
        assertThat(PsdCrossGroupMerge.similar(
                "Delais d'acheminement parfois superieurs a la concurrence",
                "Délais d'acheminement supérieurs à ceux de la concurrence")).isTrue();
        assertThat(PsdCrossGroupMerge.similar(
                "Couverture technique de l'ensemble du territoire national",
                "Reseau de centres de tri couvrant l'ensemble du territoire national")).isFalse();
        assertThat(PsdCrossGroupMerge.similar(
                "Concurrence accrue des operateurs prives",
                "Concurrence croissante d'operateurs prives de livraison")).isFalse();
    }

    @Test
    @DisplayName("La fusion garde les auteurs des deux formulations")
    void fusionneEnGardantLesAuteurs() {
        WorkGroup technique = new WorkGroup();
        technique.setId(2L);
        WorkGroup logistique = new WorkGroup();
        logistique.setId(5L);

        List<PsdCrossGroupMerge.MergedItem> merged = PsdCrossGroupMerge.merge(List.of(
                Map.entry(technique, "Delais d'acheminement parfois superieurs a la concurrence"),
                Map.entry(logistique, "Delais d'acheminement superieurs a ceux de la concurrence")), false);

        assertThat(merged).singleElement()
                .satisfies(item -> assertThat(item.contributors()).containsExactly(technique, logistique));
    }

    @Test
    @DisplayName("Les deux formes de graphique se rendent en image")
    void rendLesGraphiques() {
        ExportBlock.Chart columns = new ExportBlock.Chart(ExportBlock.ChartKind.STACKED_COLUMNS, "Budget", "M FCFA",
                List.of("2027", "2028"),
                List.of(new ExportBlock.ChartSeries("Axe 1", "#2A78D6", List.of(1_500_000_000d, 1_700_000_000d)),
                        new ExportBlock.ChartSeries("Axe 2", "#EB6834", List.of(400_000_000d, 0d))));
        ExportBlock.Chart bar = new ExportBlock.Chart(ExportBlock.ChartKind.STACKED_BAR, "Couverture", null,
                List.of("Budget"),
                List.of(new ExportBlock.ChartSeries("Couvert", "#2D7A45", List.of(3d)),
                        new ExportBlock.ChartSeries("Reste", "#D6D3CB", List.of(9d))));

        for (ExportBlock.Chart chart : List.of(columns, bar)) {
            byte[] png = ChartImageRenderer.render(chart);
            assertThat(png).hasSizeGreaterThan(2_000);
            assertThat(new String(png, 1, 3, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("PNG");
        }
    }

    @Test
    @DisplayName("Les graduations tombent sur des valeurs rondes")
    void graduationsRondes() {
        assertThat(ChartImageRenderer.niceStep(780_000_000)).isEqualTo(1_000_000_000d);
        assertThat(ChartImageRenderer.niceStep(430_000_000)).isEqualTo(500_000_000d);
        assertThat(ChartImageRenderer.niceStep(120)).isEqualTo(200d);
    }
}
