package com.senico.diagnostic.controller;

import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.exception.ResourceNotFoundException;
import com.senico.diagnostic.export.ExcelExportService;
import com.senico.diagnostic.export.PdfExportService;
import com.senico.diagnostic.export.WordExportService;
import com.senico.diagnostic.repository.WorkGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/exports")
@RequiredArgsConstructor
public class AdminExportController {

    private static final MediaType DOCX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PdfExportService pdfExportService;
    private final WordExportService wordExportService;
    private final ExcelExportService excelExportService;
    private final WorkGroupRepository workGroupRepository;

    @GetMapping("/groups/{groupId}/pdf")
    public ResponseEntity<byte[]> exportGroupPdf(@PathVariable Long groupId) {
        WorkGroup group = resolveGroup(groupId);
        byte[] pdf = pdfExportService.exportGroupRecap(group);
        return fileResponse(pdf, MediaType.APPLICATION_PDF, "plan-strategique-sectoriel-" + slug(group.getName()) + ".pdf");
    }

    @GetMapping("/groups/{groupId}/word")
    public ResponseEntity<byte[]> exportGroupWord(@PathVariable Long groupId) {
        WorkGroup group = resolveGroup(groupId);
        byte[] docx = wordExportService.exportGroupRecap(group);
        return fileResponse(docx, DOCX_MEDIA_TYPE, "plan-strategique-sectoriel-" + slug(group.getName()) + ".docx");
    }

    @GetMapping("/consolidated/excel")
    public ResponseEntity<byte[]> exportConsolidatedExcel(
            @RequestParam(defaultValue = "4") int months,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate) {
        LocalDate reference = referenceDate != null ? referenceDate : LocalDate.now();
        byte[] xlsx = excelExportService.exportConsolidated(months, reference);
        return fileResponse(xlsx, XLSX_MEDIA_TYPE, "diagnostic-strategique-consolide.xlsx");
    }

    @GetMapping("/consolidated/pdf")
    public ResponseEntity<byte[]> exportConsolidatedPdf() {
        byte[] pdf = pdfExportService.exportConsolidated();
        return fileResponse(pdf, MediaType.APPLICATION_PDF, "diagnostic-strategique-consolide.pdf");
    }

    @GetMapping("/consolidated/excel-complet")
    public ResponseEntity<byte[]> exportConsolidatedExcelFull() {
        byte[] xlsx = excelExportService.exportConsolidatedFull();
        return fileResponse(xlsx, XLSX_MEDIA_TYPE, "diagnostic-strategique-consolide-complet.xlsx");
    }

    @GetMapping("/psd-final/pdf")
    public ResponseEntity<byte[]> exportPsdFinalPdf() {
        byte[] pdf = pdfExportService.exportPsdFinalDocument();
        return fileResponse(pdf, MediaType.APPLICATION_PDF, "plan-strategique-senico-2027-2031.pdf");
    }

    @GetMapping("/psd-final/word")
    public ResponseEntity<byte[]> exportPsdFinalWord() {
        byte[] docx = wordExportService.exportPsdFinalDocument();
        return fileResponse(docx, DOCX_MEDIA_TYPE, "plan-strategique-senico-2027-2031.docx");
    }

    private WorkGroup resolveGroup(Long groupId) {
        return workGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Groupe introuvable : " + groupId));
    }

    private ResponseEntity<byte[]> fileResponse(byte[] content, MediaType mediaType, String filename) {
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }

    private String slug(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
