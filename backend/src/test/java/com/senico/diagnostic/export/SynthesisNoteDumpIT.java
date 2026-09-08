package com.senico.diagnostic.export;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;

/**
 * Utilitaire de relecture : imprime la note de synthese telle qu'elle sort, pour la relire
 * sans passer par l'application. N'affirme rien — les verifications sont dans
 * {@link SynthesisNoteIT}.
 *
 * <pre>mvn test -Dtest=SynthesisNoteDumpIT</pre>
 */
@SpringBootTest
class SynthesisNoteDumpIT {

    @Autowired
    private WordExportService wordExportService;

    @Test
    void imprimerLaNote() throws Exception {
        byte[] docx = wordExportService.exportSynthesisNote();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            System.out.println("=====DEBUT NOTE=====");
            System.out.println(extractor.getText());
            System.out.println("=====FIN NOTE=====");
        }
    }
}
