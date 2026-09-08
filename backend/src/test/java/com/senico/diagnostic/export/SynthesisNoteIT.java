package com.senico.diagnostic.export;

import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.repository.WorkGroupRepository;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Recette de la "Note de synthese" : le resume de toutes les directions doit tenir debout
 * seul — chiffres agreges renseignes, cinq parties presentes, et surtout aucune reprise
 * direction par direction, qui est precisement ce que la note doit eviter.
 *
 * <p>Comme {@link PsdFinalDocumentCompletenessIT}, lit la base de developpement reelle : ce
 * qu'on verifie ici tient autant aux donnees qu'au code. Suffixe {@code IT}, donc hors du
 * {@code mvn test} ordinaire ; se lance a la demande, base locale demarree :</p>
 *
 * <pre>mvn test -Dtest=SynthesisNoteIT</pre>
 */
@SpringBootTest
class SynthesisNoteIT {

    @Autowired
    private WordExportService wordExportService;

    @Autowired
    private PdfExportService pdfExportService;

    @Autowired
    private WorkGroupRepository workGroupRepository;

    private String note() throws Exception {
        assumeTrue(!workGroupRepository.findAll().isEmpty(), "Aucune direction en base : recette ignoree");
        byte[] docx = wordExportService.exportSynthesisNote();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    @Test
    @DisplayName("Les cinq parties de la note sont presentes")
    void lesCinqPartiesSontPresentes() throws Exception {
        assertThat(note()).contains(
                "1. Chiffres clés",
                "2. Où nous en sommes",
                "3. Ce que nous voulons",
                "4. Avec quels moyens",
                "5. Défis prioritaires");
    }

    @Test
    @DisplayName("Les chiffres cles sont renseignes, pas laisses a zero")
    void lesChiffresClesSontRenseignes() throws Exception {
        String contenu = note();
        assertThat(contenu).contains("Directions couvertes", "Budget global", "Évolution des effectifs");
        // Un budget a zero signalerait que les champs calcules ne sont pas appliques a
        // l'export, le defaut corrige par ExportContentReader.
        assertThat(contenu)
                .as("le budget global ne doit pas ressortir a zero")
                .doesNotContain("Budget global\t0 FCFA");
    }

    @Test
    @DisplayName("La note ne deroule pas les directions une par une")
    void neRependPasLesDirectionsUneParUne() throws Exception {
        String contenu = note();
        List<String> directions = workGroupRepository.findAll().stream().map(WorkGroup::getName).toList();
        for (String direction : directions) {
            int occurrences = contenu.split(java.util.regex.Pattern.quote(direction), -1).length - 1;
            assertThat(occurrences)
                    .as("« %s » revient %d fois : la note redeviendrait un document par direction",
                            direction, occurrences)
                    .isLessThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("La note reste courte : c'est un resume, pas le document complet")
    void resteCourte() throws Exception {
        String contenu = note();
        assertThat(contenu.length())
                .as("une note de trois a quatre pages ne depasse pas quelques milliers de caracteres")
                .isLessThan(12_000);
    }

    @Test
    @DisplayName("La version PDF se genere aussi")
    void lePdfSeGenere() {
        assumeTrue(!workGroupRepository.findAll().isEmpty(), "Aucune direction en base : recette ignoree");
        assertThat(pdfExportService.exportSynthesisNote())
                .as("PDF vide ou tronque")
                .hasSizeGreaterThan(5_000);
    }
}
