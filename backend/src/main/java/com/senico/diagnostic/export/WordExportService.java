package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senico.diagnostic.domain.SectionDef;
import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.repository.SectionDefRepository;
import com.senico.diagnostic.repository.SectionResponseRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WordExportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String PRIMARY_HEX = "2D7A45";

    private final SectionDefRepository sectionDefRepository;
    private final SectionResponseRepository sectionResponseRepository;
    private final ObjectMapper objectMapper;

    public byte[] exportGroupRecap(WorkGroup group) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            addCoverPage(doc, group);

            List<SectionDef> sections = sectionDefRepository.findAllByOrderByOrderAsc();
            for (SectionDef section : sections) {
                doc.createParagraph().setPageBreak(true);
                addSection(doc, section, group);
            }

            doc.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Erreur de generation du document Word", e);
        }
    }

    private void addCoverPage(XWPFDocument doc, WorkGroup group) {
        XWPFParagraph title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        title.setSpacingBefore(2000);
        XWPFRun titleRun = title.createRun();
        titleRun.setText("SENICO SA — Diagnostic Stratégique");
        titleRun.setBold(true);
        titleRun.setFontSize(22);
        titleRun.setColor(PRIMARY_HEX);

        XWPFParagraph subtitle = doc.createParagraph();
        subtitle.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun subtitleRun = subtitle.createRun();
        subtitleRun.setText("Plan Stratégique de Développement (PSD) 2027-2031");
        subtitleRun.setFontSize(14);
        subtitleRun.setColor("64748B");

        XWPFParagraph groupName = doc.createParagraph();
        groupName.setAlignment(ParagraphAlignment.CENTER);
        groupName.setSpacingBefore(600);
        XWPFRun groupRun = groupName.createRun();
        groupRun.setText(group.getName());
        groupRun.setBold(true);
        groupRun.setFontSize(16);

        XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun metaRun = meta.createRun();
        metaRun.setText("Export généré le " + java.time.LocalDateTime.now().format(DATE_FORMAT));
        metaRun.setFontSize(11);
        metaRun.setColor("64748B");
    }

    private void addSection(XWPFDocument doc, SectionDef section, WorkGroup group) {
        XWPFParagraph header = doc.createParagraph();
        XWPFRun headerRun = header.createRun();
        headerRun.setText(section.getCode() + " — " + section.getTitle());
        headerRun.setBold(true);
        headerRun.setFontSize(16);
        headerRun.setColor(PRIMARY_HEX);
        header.setBorderBottom(Borders.SINGLE);

        JsonNode content = loadContent(group.getId(), section.getId());
        List<JsonContentRenderer.Line> lines = JsonContentRenderer.render(content);

        if (lines.isEmpty()) {
            XWPFParagraph empty = doc.createParagraph();
            XWPFRun emptyRun = empty.createRun();
            emptyRun.setText("Aucune donnée saisie pour cette section.");
            emptyRun.setItalic(true);
            emptyRun.setColor("64748B");
            return;
        }

        for (JsonContentRenderer.Line line : lines) {
            XWPFParagraph p = doc.createParagraph();
            p.setIndentationLeft(line.indent() * 300);
            XWPFRun run = p.createRun();
            run.setText(line.text());
            run.setFontSize(10);
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
