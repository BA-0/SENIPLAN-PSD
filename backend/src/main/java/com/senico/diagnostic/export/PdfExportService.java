package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import java.awt.Color;
import com.senico.diagnostic.domain.SectionDef;
import com.senico.diagnostic.domain.WorkGroup;
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

        Paragraph title = new Paragraph("SENICO SA — Diagnostic Stratégique", titleFont);
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
        Font bodyFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY);
        Font emptyFont = new Font(Font.HELVETICA, 10, Font.ITALIC, SLATE);

        Paragraph header = new Paragraph(section.getCode() + " — " + section.getTitle(), headerFont);
        header.setSpacingAfter(4);
        document.add(header);

        LineSeparator separator = new LineSeparator();
        separator.setLineColor(PRIMARY);
        document.add(new Chunk(separator));
        document.add(new Paragraph(" "));

        JsonNode content = loadContent(group.getId(), section.getId());
        List<JsonContentRenderer.Line> lines = JsonContentRenderer.render(content);

        if (lines.isEmpty()) {
            document.add(new Paragraph("Aucune donnée saisie pour cette section.", emptyFont));
            return;
        }

        for (JsonContentRenderer.Line line : lines) {
            Paragraph p = new Paragraph(line.text(), bodyFont);
            p.setIndentationLeft(line.indent() * 16f);
            p.setSpacingAfter(2);
            document.add(p);
        }
    }

    private JsonNode loadContent(Long groupId, Integer sectionId) {
        return sectionResponseRepository.findByGroupIdAndSectionId(groupId, sectionId)
                .map(r -> {
                    try {
                        return objectMapper.readTree(r.getContentJson());
                    } catch (Exception e) {
                        return objectMapper.createObjectNode();
                    }
                })
                .orElseGet(objectMapper::createObjectNode);
    }
}
