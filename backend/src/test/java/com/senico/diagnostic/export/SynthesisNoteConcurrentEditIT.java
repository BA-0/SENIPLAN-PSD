package com.senico.diagnostic.export;

import com.senico.diagnostic.dto.synthesis.SynthesisNoteDto;
import com.senico.diagnostic.dto.synthesis.UpdateSynthesisNoteRequest.BlockChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Deux postes corrigent la note de synthese en meme temps, depuis la meme version : chacun
 * retrouve, apres enregistrement, ses corrections et celles de l'autre (dans les PDF et Word aussi,
 * qui rendent {@link SynthesisNoteService#effectiveBlocks()}).
 *
 * <p>Lit la base de developpement reelle, dans une transaction annulee a la fin : rien n'y reste.</p>
 *
 * <pre>mvn test -Dtest=SynthesisNoteConcurrentEditIT</pre>
 */
@SpringBootTest
@Transactional
class SynthesisNoteConcurrentEditIT {

    @Autowired
    private SynthesisNoteService service;

    @Test
    @DisplayName("Deux cellules du meme tableau corrigees par deux postes : les deux restent")
    void deuxPostesMemeTableau() {
        SynthesisNoteDto depart = service.view();
        int index = indexOfTableWithRows(depart.blocks(), 2);
        assumeTrue(index >= 0, "Aucun tableau de deux lignes ou plus : recette ignoree");
        ExportBlock.Table table = (ExportBlock.Table) depart.blocks().get(index);

        ExportBlock.Table posteA = withCell(table, 0, "Correction du poste A");
        ExportBlock.Table posteB = withCell(table, 1, "Correction du poste B");

        service.save(depart.version(), List.of(new BlockChange(index, posteA)), "dir.gen", "poste-A");
        SynthesisNoteDto apres = service.save(depart.version(), List.of(new BlockChange(index, posteB)), "dir.gen", "poste-B");

        ExportBlock.Table fusion = (ExportBlock.Table) apres.blocks().get(index);
        assertThat(firstText(fusion, 0)).isEqualTo("Correction du poste A");
        assertThat(firstText(fusion, 1)).isEqualTo("Correction du poste B");
        assertThat(apres.conflicts()).isZero();
        assertThat(service.effectiveBlocks()).as("les exports reprennent la note fusionnee").isEqualTo(apres.blocks());
        assertThat(service.view().version()).isEqualTo(apres.version());
    }

    @Test
    @DisplayName("Copie entiere d'un onglet d'avant la fusion : ses corrections s'ajoutent, sans effacer celles des autres")
    void copieEntiereDunAncienOnglet() {
        SynthesisNoteDto depart = service.view();
        int index = indexOfTableWithRows(depart.blocks(), 2);
        assumeTrue(index >= 0, "Aucun tableau de deux lignes ou plus : recette ignoree");
        ExportBlock.Table table = (ExportBlock.Table) depart.blocks().get(index);

        service.save(depart.version(), List.of(new BlockChange(index, withCell(table, 0, "Correction du poste A"))),
                "dir.gen", "poste-A");

        // L'ancien onglet, reste sur la version de depart, renvoie toute la note : d'abord telle quelle,
        // puis avec sa propre correction.
        SynthesisNoteDto inchangee = service.reintegrate(depart.blocks(), "dir.gen");
        assertThat(firstText((ExportBlock.Table) inchangee.blocks().get(index), 0))
                .as("une copie perimee n'efface pas la correction du poste A").isEqualTo("Correction du poste A");

        List<ExportBlock> copie = new ArrayList<>(depart.blocks());
        copie.set(index, withCell(table, 1, "Correction de l'ancien onglet"));
        SynthesisNoteDto apres = service.reintegrate(copie, "dir.gen");

        ExportBlock.Table fusion = (ExportBlock.Table) apres.blocks().get(index);
        assertThat(firstText(fusion, 0)).isEqualTo("Correction du poste A");
        assertThat(firstText(fusion, 1)).isEqualTo("Correction de l'ancien onglet");
        assertThat(service.effectiveBlocks()).isEqualTo(apres.blocks());
    }

    private static int indexOfTableWithRows(List<ExportBlock> blocks, int rows) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i) instanceof ExportBlock.Table t
                    && t.rows().stream().filter(r -> !r.band() && !r.cells().isEmpty() && r.cells().get(0).rowSpan() == 1).count() >= rows) {
                return i;
            }
        }
        return -1;
    }

    /** Remplace le texte de la premiere cellule de la n-ieme ligne ordinaire. */
    private static ExportBlock.Table withCell(ExportBlock.Table table, int n, String text) {
        List<ExportBlock.TableRow> rows = new ArrayList<>(table.rows());
        int seen = 0;
        for (int r = 0; r < rows.size(); r++) {
            ExportBlock.TableRow row = rows.get(r);
            if (row.band() || row.cells().isEmpty() || row.cells().get(0).rowSpan() != 1) {
                continue;
            }
            if (seen++ == n) {
                List<ExportBlock.Cell> cells = new ArrayList<>(row.cells());
                ExportBlock.Cell c = cells.get(0);
                cells.set(0, new ExportBlock.Cell(text, c.bold(), c.align(), c.background(), List.of(), 1));
                rows.set(r, new ExportBlock.TableRow(cells, row.emphasized(), row.rowBackground(), row.band()));
                break;
            }
        }
        return new ExportBlock.Table(table.columnHeaders(), rows, table.widths(), table.bands());
    }

    private static String firstText(ExportBlock.Table table, int n) {
        int seen = 0;
        for (ExportBlock.TableRow row : table.rows()) {
            if (row.band() || row.cells().isEmpty() || row.cells().get(0).rowSpan() != 1) {
                continue;
            }
            if (seen++ == n) {
                return row.cells().get(0).text();
            }
        }
        return null;
    }
}
