package com.senico.diagnostic.controller;

import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.exception.ResourceNotFoundException;
import com.senico.diagnostic.export.PdfExportService;
import com.senico.diagnostic.repository.WorkGroupRepository;
import com.senico.diagnostic.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/exports")
@RequiredArgsConstructor
public class MyExportController {

    private final PdfExportService pdfExportService;
    private final WorkGroupRepository workGroupRepository;

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> exportOwnGroupPdf(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal.getGroupId() == null) {
            throw new ResourceNotFoundException("Ce compte n'est rattache a aucun groupe de travail");
        }
        WorkGroup group = workGroupRepository.findById(principal.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Groupe introuvable"));

        byte[] pdf = pdfExportService.exportGroupRecap(group);
        String filename = "diagnostic-strategique-" + slug(group.getName()) + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdf);
    }

    private String slug(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
