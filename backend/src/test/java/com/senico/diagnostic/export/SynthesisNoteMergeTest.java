package com.senico.diagnostic.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.senico.diagnostic.export.SynthesisNoteMerge.Winner.OURS;
import static com.senico.diagnostic.export.SynthesisNoteMerge.Winner.THEIRS;
import static org.assertj.core.api.Assertions.assertThat;

/** Fusion de la note corrigee : avec le document genere mis a jour, et entre postes qui corrigent en meme temps. */
class SynthesisNoteMergeTest {

    private static ExportBlock h(String text) {
        return new ExportBlock.Heading(text, 2);
    }

    private static ExportBlock p(String text) {
        return new ExportBlock.Paragraph(text, false, false);
    }

    private static ExportBlock.TableRow row(String... cells) {
        return new ExportBlock.TableRow(java.util.Arrays.stream(cells).map(ExportBlock.Cell::new).toList());
    }

    private static ExportBlock table(ExportBlock.TableRow... rows) {
        return new ExportBlock.Table(List.of("Extrants", "Activités", "2027"), List.of(rows));
    }

    @Test
    @DisplayName("Un tableau approuve apres la correction entre dans la note, les corrections restent")
    void nouveauContenuIntegre() {
        List<ExportBlock> base = List.of(h("Tableau 3 : Budget du plan"), p("Axe 1"));
        List<ExportBlock> ours = List.of(h("Tableau 3 : Budget détaillé"), p("Axe 1 corrigé"));
        List<ExportBlock> theirs = List.of(h("Tableau 3 : Budget du plan"), p("Axe 1"), p("Budget Marketing"));

        assertThat(SynthesisNoteMerge.merge(base, ours, theirs, THEIRS).blocks())
                .containsExactly(h("Tableau 3 : Budget détaillé"), p("Axe 1 corrigé"), p("Budget Marketing"));
    }

    @Test
    @DisplayName("Deux postes corrigent deux cellules du meme tableau : les deux corrections restent")
    void deuxCellulesDuMemeTableau() {
        List<ExportBlock> base = List.of(h("Budget"), table(row("E1", "A1", "100"), row("E2", "A2", "200")));
        List<ExportBlock> posteA = List.of(h("Budget"), table(row("E1", "A1", "150"), row("E2", "A2", "200")));
        List<ExportBlock> posteB = List.of(h("Budget"), table(row("E1", "A1", "100"), row("E2", "A2 revue", "200")));

        SynthesisNoteMerge.Result result = SynthesisNoteMerge.merge(base, posteB, posteA, OURS);

        assertThat(result.blocks()).containsExactly(h("Budget"),
                table(row("E1", "A1", "150"), row("E2", "A2 revue", "200")));
        assertThat(result.conflicts()).isZero();
    }

    @Test
    @DisplayName("Une ligne corrigee des deux cotes, pres d'une ligne ajoutee : les deux corrections restent")
    void ligneCorrigeeDesDeuxCotesPresDuneLigneAjoutee() {
        List<ExportBlock> base = List.of(table(row("E1", "A1", "150", "—"), row("E2", "A2", "200", "—")));
        // L'ancien onglet corrige le montant et le responsable des deux lignes ;
        ExportBlock ours = table(row("E1", "A1", "15", "DT"), row("E2", "A2", "20", "DT"));
        // l'autre poste a corrige l'intitule de la premiere et ajoute une ligne juste apres.
        ExportBlock theirs = table(row("E1", "A1 revue", "150", "—"), row("E9", "A9", "50", "—"), row("E2", "A2", "200", "—"));

        SynthesisNoteMerge.Result result = SynthesisNoteMerge.merge(base, List.of(ours), List.of(theirs), OURS);

        assertThat(result.blocks()).containsExactly(
                table(row("E1", "A1 revue", "15", "DT"), row("E9", "A9", "50", "—"), row("E2", "A2", "20", "DT")));
        assertThat(result.conflicts()).isZero();
    }

    @Test
    @DisplayName("Une ligne ajoutee par un poste et une cellule corrigee par l'autre se cumulent")
    void ligneAjouteeEtCelluleCorrigee() {
        List<ExportBlock> base = List.of(table(row("E1", "A1", "100"), row("E2", "A2", "200")));
        List<ExportBlock> posteA = List.of(table(row("E1", "A1", "100"), row("E2", "A2", "200"), row("E3", "A3", "300")));
        List<ExportBlock> posteB = List.of(table(row("E1", "A1 revue", "100"), row("E2", "A2", "200")));

        assertThat(SynthesisNoteMerge.merge(base, posteB, posteA, OURS).blocks()).containsExactly(
                table(row("E1", "A1 revue", "100"), row("E2", "A2", "200"), row("E3", "A3", "300")));
    }

    @Test
    @DisplayName("La meme cellule corrigee par deux postes : le gagnant l'emporte, le conflit est compte")
    void memeCellule() {
        List<ExportBlock> base = List.of(table(row("E1", "A1", "100")));
        List<ExportBlock> posteA = List.of(table(row("E1", "A1", "150")));
        List<ExportBlock> posteB = List.of(table(row("E1", "A1", "175")));

        SynthesisNoteMerge.Result result = SynthesisNoteMerge.merge(base, posteB, posteA, OURS);

        assertThat(result.blocks()).containsExactly(table(row("E1", "A1", "175")));
        assertThat(result.conflicts()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un paragraphe modifie des deux cotes revient au gagnant")
    void paragrapheEnConflit() {
        List<ExportBlock> base = List.of(h("A"), p("ancien"), h("B"));
        List<ExportBlock> ours = List.of(h("A corrigé"), p("ancien corrigé"), h("B"));
        List<ExportBlock> theirs = List.of(h("A"), p("nouveau"), h("B"));

        assertThat(SynthesisNoteMerge.merge(base, ours, theirs, THEIRS).blocks())
                .containsExactly(h("A corrigé"), p("nouveau"), h("B"));
    }
}
