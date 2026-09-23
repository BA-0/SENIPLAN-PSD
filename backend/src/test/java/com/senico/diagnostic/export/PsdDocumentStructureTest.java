package com.senico.diagnostic.export;

import com.senico.diagnostic.domain.NarrativeBlockKey;
import com.senico.diagnostic.export.PsdDocumentStructure.Entry;
import com.senico.diagnostic.export.PsdDocumentStructure.NarrativeEntry;
import com.senico.diagnostic.export.PsdDocumentStructure.SectionEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recette du sommaire du "Plan Strategique de SENICO". Le document a longtemps ecarte des
 * sections pourtant saisies par les directions (S02, S05, S14) et ignore des parties attendues
 * d'un PSD publie : rien ne le signalait, puisqu'un sommaire incomplet reste un sommaire
 * valide. Ces invariants font echouer le prochain oubli du meme genre.
 */
class PsdDocumentStructureTest {

    private final List<Entry> entries = PsdDocumentStructure.entries();

    @Test
    @DisplayName("Chaque bloc narratif de l'enum a sa place dans le document, une seule fois")
    void chaqueBlocNarratifEstPublie() {
        List<NarrativeBlockKey> publies = entries.stream()
                .filter(NarrativeEntry.class::isInstance)
                .map(entry -> ((NarrativeEntry) entry).key())
                .toList();

        assertThat(publies)
                .as("un bloc que l'admin peut rediger sans qu'il ressorte nulle part est du travail perdu")
                .containsExactlyInAnyOrder(NarrativeBlockKey.values())
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Chaque section du canevas est publiee : le client y retrouve tous ses tableaux")
    void publieToutesLesSectionsDuCanevas() {
        assertThat(codesPublies())
                .as("S02, S05, S14, puis S06 et S07 ont ete ecartees tour a tour sans que rien ne le signale ; "
                        + "S03B est retiree a la demande du client (23/09/2026)")
                .containsExactlyInAnyOrder(
                        "S01", "S01B", "S02", "S03", "S04", "S05", "S06", "S06B", "S07", "S07B",
                        "S08", "S09", "S09B", "S10", "S11", "S12", "S13", "S14", "S14B", "S15", "S17");
    }

    @Test
    @DisplayName("Aucune section n'est publiee deux fois")
    void neRepetePasUneSection() {
        assertThat(codesPublies()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Le document s'ouvre sur le mot du DG et se ferme sur la conclusion")
    void encadreLeDocument() {
        assertThat(entries.get(0))
                .isInstanceOfSatisfying(NarrativeEntry.class,
                        first -> assertThat(first.key()).isEqualTo(NarrativeBlockKey.MOT_DU_DG));
        assertThat(entries.get(entries.size() - 1))
                .isInstanceOfSatisfying(NarrativeEntry.class,
                        last -> assertThat(last.key()).isEqualTo(NarrativeBlockKey.CONCLUSION));
    }

    private List<String> codesPublies() {
        List<String> codes = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry instanceof SectionEntry section) {
                codes.addAll(section.sectionCodes());
            }
        }
        return codes;
    }
}
