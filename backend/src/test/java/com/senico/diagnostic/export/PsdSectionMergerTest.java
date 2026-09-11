package com.senico.diagnostic.export;

import com.senico.diagnostic.domain.WorkGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La fusion des rubriques du document consolide est la partie la moins evidente de l'export :
 * elle doit reunir ce qui se correspond d'une direction a l'autre, sans melanger ce qui se
 * ressemble seulement. Ces cas verrouillent ce comportement.
 */
class PsdSectionMergerTest {

    private static WorkGroup group(long id, String name) {
        WorkGroup group = new WorkGroup();
        group.setId(id);
        group.setName(name);
        return group;
    }

    private static ExportBlock.Table table(List<String> headers, String... cellTexts) {
        List<ExportBlock.Cell> cells = java.util.Arrays.stream(cellTexts).map(ExportBlock.Cell::new).toList();
        return new ExportBlock.Table(headers, List.of(new ExportBlock.TableRow(cells)));
    }

    private static PsdSectionMerger.GroupBlocks included(WorkGroup group, List<ExportBlock> blocks) {
        return new PsdSectionMerger.GroupBlocks(group, blocks, true, null);
    }

    @Test
    @DisplayName("Deux directions, meme tableau : un seul tableau, une colonne Direction, une ligne par direction")
    void fusionneLesTableauxDeMemeStructure() {
        List<String> headers = List.of("Extrant", "Budget");
        List<ExportBlock> merged = PsdSectionMerger.merge(List.of(
                included(group(1, "Commerciale"), List.of(table(headers, "E1", "100"))),
                included(group(2, "Technique"), List.of(table(headers, "E2", "200")))));

        List<ExportBlock.Table> tables = merged.stream()
                .filter(ExportBlock.Table.class::isInstance).map(ExportBlock.Table.class::cast).toList();

        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).columnHeaders()).containsExactly("Direction", "Extrant", "Budget");
        assertThat(tables.get(0).rows()).hasSize(2);
        assertThat(tables.get(0).rows().get(0).cells().get(0).text()).isEqualTo("Commerciale");
        assertThat(tables.get(0).rows().get(1).cells().get(0).text()).isEqualTo("Technique");
    }

    @Test
    @DisplayName("La fusion garde les annees qui coiffent les colonnes et les intertitres pleine largeur")
    void garderLesIntitulesDeColonnesEtLesIntertitres() {
        ExportBlock.Table effectifs = new ExportBlock.Table(List.of("Effectifs", "M", "F", "Total"),
                List.of(ExportBlock.TableRow.band("Hiérarchie", ExportBlock.Background.GREY),
                        new ExportBlock.TableRow(List.of(new ExportBlock.Cell("Cadre"), new ExportBlock.Cell("8"),
                                new ExportBlock.Cell("4"), new ExportBlock.Cell("12")))),
                List.of(40, 20, 20, 20),
                List.of(new ExportBlock.HeaderBand("Années", 1), new ExportBlock.HeaderBand("2027", 3)));

        ExportBlock.Table merged = PsdSectionMerger.merge(List.of(included(group(1, "Commerciale"), List.of(effectifs))))
                .stream().filter(ExportBlock.Table.class::isInstance).map(ExportBlock.Table.class::cast)
                .findFirst().orElseThrow();

        assertThat(merged.bands()).extracting(ExportBlock.HeaderBand::label).containsExactly("", "Années", "2027");
        assertThat(merged.widths()).hasSize(merged.columnHeaders().size());
        assertThat(merged.rows().get(0).band()).isTrue();
        assertThat(merged.rows().get(0).cells().get(0).text()).isEqualTo("Commerciale — Hiérarchie");
        assertThat(merged.rows().get(1).cells().get(0).text()).isEqualTo("Commerciale");
    }

    @Test
    @DisplayName("Memes colonnes sous des intertitres differents : deux tableaux distincts, pas un melange")
    void neMelangePasDeuxAxesAuxMemesColonnes() {
        List<String> headers = List.of("Extrant", "Budget");
        List<ExportBlock> blocs = List.of(
                new ExportBlock.Heading("Axe 1", 3), table(headers, "E1", "100"),
                new ExportBlock.Heading("Axe 2", 3), table(headers, "E2", "200"));

        List<ExportBlock> merged = PsdSectionMerger.merge(List.of(
                included(group(1, "Commerciale"), blocs),
                included(group(2, "Technique"), blocs)));

        List<ExportBlock.Table> tables = merged.stream()
                .filter(ExportBlock.Table.class::isInstance).map(ExportBlock.Table.class::cast).toList();

        assertThat(tables).as("un tableau par axe, pas un seul fourre-tout").hasSize(2);
        assertThat(tables.get(0).rows()).hasSize(2);
        assertThat(tables.get(1).rows()).hasSize(2);
        assertThat(tables.get(0).rows().get(0).cells().get(1).text()).isEqualTo("E1");
        assertThat(tables.get(1).rows().get(0).cells().get(1).text()).isEqualTo("E2");
    }

    @Test
    @DisplayName("L'encart de metadonnees de chaque direction ne se retrouve pas dans le tableau fusionne")
    void ecarteLesEncartsDeMetadonnees() {
        ExportBlock.KeyValueList encart = new ExportBlock.KeyValueList(
                null, List.of(new ExportBlock.KeyValue("Statut", "Validé")), true);

        List<ExportBlock> merged = PsdSectionMerger.merge(List.of(
                included(group(1, "Commerciale"), List.of(encart, table(List.of("Extrant"), "E1")))));

        assertThat(merged).noneMatch(ExportBlock.KeyValueList.class::isInstance);
    }

    @Test
    @DisplayName("Les directions ecartees tiennent en une seule ligne, au lieu d'un bloc chacune")
    void resumeLesDirectionsEcartees() {
        List<ExportBlock> merged = PsdSectionMerger.merge(List.of(
                included(group(1, "Commerciale"), List.of(table(List.of("Extrant"), "E1"))),
                new PsdSectionMerger.GroupBlocks(group(2, "Technique"), List.of(), false, "non validée — soumis"),
                new PsdSectionMerger.GroupBlocks(group(3, "Financière"), List.of(), false, "non renseignée")));

        List<ExportBlock.Paragraph> paragraphes = merged.stream()
                .filter(ExportBlock.Paragraph.class::isInstance).map(ExportBlock.Paragraph.class::cast).toList();

        assertThat(paragraphes).hasSize(1);
        assertThat(paragraphes.get(0).text())
                .contains("Technique (non validée — soumis)")
                .contains("Financière (non renseignée)");
    }

    @Test
    @DisplayName("Aucune direction reprise : un message, pas un tableau vide")
    void signaleUneSectionEntierementAbsente() {
        List<ExportBlock> merged = PsdSectionMerger.merge(List.of(
                new PsdSectionMerger.GroupBlocks(group(1, "Commerciale"), List.of(), false, "non renseignée")));

        assertThat(merged).hasSize(1);
        assertThat(((ExportBlock.Paragraph) merged.get(0)).text()).contains("Commerciale");
    }

    @Test
    @DisplayName("Ce qui n'est pas un tableau reste presente direction par direction")
    void conserveLesBlocsNonFusionnablesParDirection() {
        ExportBlock.BulletList valeurs = new ExportBlock.BulletList("Valeurs", List.of("Rigueur"));

        List<ExportBlock> merged = PsdSectionMerger.merge(List.of(
                included(group(1, "Commerciale"), List.of(valeurs)),
                included(group(2, "Technique"), List.of(valeurs))));

        List<String> titres = merged.stream()
                .filter(ExportBlock.Heading.class::isInstance).map(b -> ((ExportBlock.Heading) b).text()).toList();

        assertThat(titres).containsExactly("Commerciale", "Technique");
        assertThat(merged).filteredOn(ExportBlock.BulletList.class::isInstance).hasSize(2);
    }
}
