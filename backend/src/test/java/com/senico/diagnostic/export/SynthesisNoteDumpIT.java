package com.senico.diagnostic.export;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utilitaire de relecture : imprime la note de synthese telle qu'elle sort et depose ses exports
 * dans target/exports, pour la relire sans passer par l'application. N'affirme rien — les
 * verifications sont dans {@link SynthesisNoteIT}.
 *
 * <pre>mvn test -Dtest=SynthesisNoteDumpIT</pre>
 */
@SpringBootTest
class SynthesisNoteDumpIT {

    @Autowired
    private WordExportService wordExportService;

    @Autowired
    private PdfExportService pdfExportService;

    @Test
    void imprimerLaNote() throws Exception {
        byte[] docx = wordExportService.exportSynthesisNote();
        Path directory = Files.createDirectories(Path.of("target", "exports"));
        Files.write(directory.resolve("note-de-synthese.docx"), docx);
        Files.write(directory.resolve("note-de-synthese.pdf"), pdfExportService.exportSynthesisNote());
        Files.write(directory.resolve("plan-strategique-senico.pdf"), pdfExportService.exportPsdFinalDocument());
        Files.write(directory.resolve("plan-strategique-senico.docx"), wordExportService.exportPsdFinalDocument());

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            System.out.println("=====DEBUT NOTE=====");
            System.out.println(extractor.getText());
            System.out.println("=====FIN NOTE=====");
        }
        System.out.println("Exports deposes dans " + directory.toAbsolutePath());
    }
}
