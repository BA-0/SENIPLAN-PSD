package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import java.awt.Color;
import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.SectionDef;
import com.senico.diagnostic.domain.SectionResponse;
import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.repository.GroupSectionStatusRepository;
import com.senico.diagnostic.repository.SectionDefRepository;
import com.senico.diagnostic.repository.SectionResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PdfExportService {

    private static final Color PRIMARY = new Color(0x2D, 0x7A, 0x45);
    private static final Color SLATE = new Color(0x64, 0x74, 0x8B);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SectionDefRepository sectionDefRepository;
    private final SectionResponseRepository sectionResponseRepository;
    private final GroupSectionStatusRepository groupSectionStatusRepository;
    private final SectionExportRenderer sectionExportRenderer;
    private final PdfBlockEmitter pdfBlockEmitter;
    private final ObjectMapper objectMapper;

    public byte[] exportGroupRecap(WorkGroup group) {
        try {
            Document document = new Document(PageSize.A4, 40, 40, 60, 50);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, baos);
            document.open();

            addCoverPage(document, group);

            List<SectionDef> sections = sectionDefRepository.findAllByOrderByOrderAsc();
            for (SectionDef section : sections) {
                document.newPage();
                addSectionPage(document, section, group);
            }

            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Erreur de generation du PDF", e);
        }
    }

    private void addCoverPage(Document document, WorkGroup group) throws DocumentException {
        Font titleFont = new Font(Font.HELVETICA, 22, Font.BOLD, PRIMARY);
        Font subtitleFont = new Font(Font.HELVETICA, 14, Font.NORMAL, SLATE);
        Font metaFont = new Font(Font.HELVETICA, 11, Font.NORMAL, SLATE);

        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(120);
        document.add(spacer);

        Paragraph title = new Paragraph("SENICO SA — Plan Stratégique", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph subtitle = new Paragraph("Plan Stratégique de Développement (PSD) 2027-2031", subtitleFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingBefore(10);
        document.add(subtitle);

        Paragraph groupName = new Paragraph(group.getName(), new Font(Font.HELVETICA, 16, Font.BOLD, Color.DARK_GRAY));
        groupName.setAlignment(Element.ALIGN_CENTER);
        groupName.setSpacingBefore(40);
        document.add(groupName);

        Paragraph meta = new Paragraph(
                "Export généré le " + java.time.LocalDateTime.now().format(DATE_FORMAT),
                metaFont);
        meta.setAlignment(Element.ALIGN_CENTER);
        meta.setSpacingBefore(10);
        document.add(meta);
    }

    private void addSectionPage(Document document, SectionDef section, WorkGroup group) throws DocumentException {
        Font headerFont = new Font(Font.HELVETICA, 16, Font.BOLD, PRIMARY);

        Paragraph header = new Paragraph(section.getCode() + " — " + section.getTitle(), headerFont);
        header.setSpacingAfter(4);
        document.add(header);

        LineSeparator separator = new LineSeparator();
        separator.setLineColor(PRIMARY);
        document.add(new Chunk(separator));
        document.add(new Paragraph(" "));

        List<ExportBlock> blocks = sectionExportRenderer.render(loadExportData(group.getId(), section));
        pdfBlockEmitter.emit(document, blocks);
    }

    private ExportSectionData loadExportData(Long groupId, SectionDef section) {
        SectionResponse response = sectionResponseRepository.findByGroupIdAndSectionId(groupId, section.getId()).orElse(null);
        GroupSectionStatus status = groupSectionStatusRepository.findByGroupIdAndSectionId(groupId, section.getId()).orElse(null);
        JsonNode content = response != null ? parseJson(response.getContentJson()) : objectMapper.createObjectNode();
        Integer version = response != null ? response.getVersion() : 0;
        return new ExportSectionData(section, content, version, status);
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }
}
