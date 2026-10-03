package com.senico.diagnostic.export;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La note de synthese corrigee par la Direction Generale est conservee en JSON (cf.
 * SynthesisNoteService) : chaque sorte de bloc doit en revenir a l'identique.
 */
class ExportBlockJsonTest {

    private static final TypeReference<List<ExportBlock>> BLOCKS = new TypeReference<>() {
    };

    @Test
    @DisplayName("Chaque sorte de bloc revient du JSON a l'identique")
    void allerRetour() throws Exception {
        ExportBlock.Attribution partagee = new ExportBlock.Attribution("Réseau national", List.of("#FF6600", "#0066CC"));
        List<ExportBlock> blocs = List.of(
                new ExportBlock.Heading("I. CONTEXTE", 1),
                new ExportBlock.Paragraph("Texte", true, false),
                new ExportBlock.KeyValueList("Repères", List.of(new ExportBlock.KeyValue("Siège", "Dakar")), true),
                new ExportBlock.BulletList("Points", List.of("Un", "Deux")),
                new ExportBlock.AttributedList("Forces", List.of(partagee)),
                new ExportBlock.AttributedQuadrant(
                        List.of(new ExportBlock.AttributedQuadrantCell("Forces", "À exploiter", List.of(partagee))),
                        List.of(ExportBlock.Background.GREEN)),
                new ExportBlock.ColorLegend("Directions", List.of(new ExportBlock.Attribution("DC", List.of("#FF6600")))),
                new ExportBlock.Table(
                        List.of("OS", "Action"),
                        List.of(
                                ExportBlock.TableRow.band("AXE 1 : Croissance", ExportBlock.Background.PRIMARY_DARK),
                                new ExportBlock.TableRow(List.of(
                                        new ExportBlock.Cell("OS 1").spanning(2),
                                        new ExportBlock.Cell(List.of(partagee)))),
                                new ExportBlock.TableRow(List.of(
                                        ExportBlock.Cell.covered(),
                                        new ExportBlock.Cell("Action 2", true, ExportBlock.Align.RIGHT, ExportBlock.Background.GREY)))),
                        List.of(1, 3),
                        List.of(new ExportBlock.HeaderBand("Cadre", 2))),
                new ExportBlock.Callout("Attention", ExportBlock.Tone.WARNING),
                new ExportBlock.MetricGrid(List.of(new ExportBlock.Metric("Budget", "1 000 FCFA"))),
                new ExportBlock.Quadrant(List.of(new ExportBlock.QuadrantCell("Forces", List.of("Réseau")))),
                new ExportBlock.Chart(ExportBlock.ChartKind.STACKED_COLUMNS, "Budget", "par exercice",
                        List.of("2027", "2028"),
                        List.of(new ExportBlock.ChartSeries("Axe 1", "#FF6600", List.of(600.0, 400.0)))));

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String json = mapper.writerFor(BLOCKS).writeValueAsString(blocs);

        assertThat(mapper.readValue(json, BLOCKS)).isEqualTo(blocs);
        assertThat(json).as("le type de chaque bloc est ecrit").contains("\"type\":\"TABLE\"", "\"type\":\"HEADING\"");
    }
}
