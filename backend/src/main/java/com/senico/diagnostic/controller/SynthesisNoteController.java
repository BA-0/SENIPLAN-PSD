package com.senico.diagnostic.controller;

import com.senico.diagnostic.dto.synthesis.SynthesisNoteDto;
import com.senico.diagnostic.dto.synthesis.UpdateSynthesisNoteRequest;
import com.senico.diagnostic.export.PdfExportService;
import com.senico.diagnostic.export.SynthesisNoteService;
import com.senico.diagnostic.export.WordExportService;
import com.senico.diagnostic.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Correction de la note de synthese, hors de l'espace de pilotage : la Direction Generale et le
 * compte de chef de groupe de la Direction Generale y ont acces (cf. SynthesisNoteAccess et
 * SecurityConfig). Les telechargements sont repris ici, ce dernier compte n'ayant pas acces aux
 * exports du pilotage.
 */
@RestController
@RequestMapping("/api/v1/synthesis-note")
@RequiredArgsConstructor
public class SynthesisNoteController {

    private static final MediaType DOCX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final String FILENAME = "note-de-synthese-plan-strategique-2027-2031";

    private final SynthesisNoteService synthesisNoteService;
    private final PdfExportService pdfExportService;
    private final WordExportService wordExportService;

    @GetMapping
    public ResponseEntity<SynthesisNoteDto> get() {
        return ResponseEntity.ok(synthesisNoteService.view());
    }

    @PutMapping
    public ResponseEntity<SynthesisNoteDto> save(
            @RequestBody UpdateSynthesisNoteRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Onglet ouvert avant la mise en ligne de la fusion : il renvoie sa copie entiere de la note,
        // qui ecraserait les corrections enregistrees depuis par les autres postes. Seules ses propres
        // corrections sont reintegrees, et il recoit la note fusionnee, qu'il affiche.
        if (request.changes() == null) {
            if (request.blocks() == null || request.blocks().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La note de synthèse ne peut pas être vide");
            }
            return ResponseEntity.ok(synthesisNoteService.keepAndReintegrate(request.blocks(), principal.getUsername()));
        }
        return ResponseEntity.ok(synthesisNoteService.save(request.baseVersion(), request.changes(),
                principal.getUsername(), request.clientId()));
    }

    @DeleteMapping
    public ResponseEntity<SynthesisNoteDto> reset(@RequestParam(required = false) String clientId) {
        // Desactive le 03/10/2026 : plusieurs groupes corrigent la note en meme temps sur le meme compte,
        // et un clic effacait les corrections de tous. Les sections approuvees entrent deja d'elles-memes.
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "« Repartir du document à jour » est désactivé : il effacerait les corrections de tous les groupes. "
                        + "Les sections approuvées entrent déjà d'elles-mêmes dans la note.");
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> exportPdf() {
        return fileResponse(pdfExportService.exportSynthesisNote(), MediaType.APPLICATION_PDF, FILENAME + ".pdf");
    }

    @GetMapping("/word")
    public ResponseEntity<byte[]> exportWord() {
        return fileResponse(wordExportService.exportSynthesisNote(), DOCX_MEDIA_TYPE, FILENAME + ".docx");
    }

    private ResponseEntity<byte[]> fileResponse(byte[] content, MediaType mediaType, String filename) {
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }
}
