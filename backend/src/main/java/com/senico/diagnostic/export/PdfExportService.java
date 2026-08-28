package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
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
import com.senico.diagnostic.repository.WorkGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PdfExportService {

    private static final Color PRIMARY = new Color(0x2D, 0x7A, 0x45);
    private static final Color SLATE = new Color(0x64, 0x74, 0x8B);
    private static final Color BORDER = new Color(0xE2, 0xE8, 0xF0);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SectionDefRepository sectionDefRepository;
    private final SectionResponseRepository sectionResponseRepository;
    private final GroupSectionStatusRepository groupSectionStatusRepository;
    private final WorkGroupRepository workGroupRepository;
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

    public byte[] exportConsolidated() {
        try {
            Document document = new Document(PageSize.A4, 40, 40, 60, 50);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, baos);
            document.open();

            List<WorkGroup> groups = workGroupRepository.findAll();
            List<SectionDef> sections = sectionDefRepository.findAllByOrderByOrderAsc();

            Map<String, SectionResponse> responsesByKey = sectionResponseRepository.findAll().stream()
                    .collect(Collectors.toMap(r -> key(r.getGroup().getId(), r.getSection().getId()), r -> r));
            Map<String, GroupSectionStatus> statusesByKey = groupSectionStatusRepository.findAllWithGroupAndSection().stream()
                    .collect(Collectors.toMap(s -> key(s.getGroup().getId(), s.getSection().getId()), s -> s));

            addConsolidatedCoverPage(document);
            document.newPage();
            addSommairePage(document, sections, groups);

            for (SectionDef section : sections) {
                document.newPage();
                addConsolidatedSectionPage(document, section, groups, responsesByKey, statusesByKey);
            }

            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Erreur de generation du PDF consolide", e);
        }
    }

    private String key(Long groupId, Integer sectionId) {
        return groupId + ":" + sectionId;
    }

    private void addConsolidatedCoverPage(Document document) throws DocumentException {
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

        Paragraph docTitle = new Paragraph("Document de consolidation", new Font(Font.HELVETICA, 16, Font.BOLD, Color.DARK_GRAY));
        docTitle.setAlignment(Element.ALIGN_CENTER);
        docTitle.setSpacingBefore(40);
        document.add(docTitle);

        Paragraph meta = new Paragraph(
                "Export généré le " + java.time.LocalDateTime.now().format(DATE_FORMAT),
                metaFont);
        meta.setAlignment(Element.ALIGN_CENTER);
        meta.setSpacingBefore(10);
        document.add(meta);
    }

    private void addSommairePage(Document document, List<SectionDef> sections, List<WorkGroup> groups) throws DocumentException {
        Font headerFont = new Font(Font.HELVETICA, 16, Font.BOLD, PRIMARY);
        Paragraph header = new Paragraph("Sommaire", headerFont);
        header.setSpacingAfter(4);
        document.add(header);

        LineSeparator separator = new LineSeparator();
        separator.setLineColor(PRIMARY);
        document.add(new Chunk(separator));
        document.add(new Paragraph(" "));

        PdfPTable toc = new PdfPTable(2);
        toc.setWidthPercentage(100);
        toc.setWidths(new float[]{15, 85});
        toc.setSpacingAfter(20);
        addTableHeaderRow(toc, "Code", "Section");
        for (SectionDef section : sections) {
            toc.addCell(bodyCell(section.getCode(), Element.ALIGN_LEFT, Color.WHITE));
            toc.addCell(bodyCell(section.getTitle(), Element.ALIGN_LEFT, Color.WHITE));
        }
        document.add(toc);

        Paragraph legendTitle = new Paragraph("Légende des directions", new Font(Font.HELVETICA, 13, Font.BOLD, PRIMARY));
        legendTitle.setSpacingBefore(10);
        legendTitle.setSpacingAfter(6);
        document.add(legendTitle);

        PdfPTable legend = new PdfPTable(2);
        legend.setWidthPercentage(100);
        legend.setWidths(new float[]{30, 70});
        addTableHeaderRow(legend, "Couleur", "Direction");
        for (WorkGroup group : groups) {
            PdfPCell colorCell = new PdfPCell(new Paragraph(" "));
            colorCell.setBackgroundColor(hexToColor(group.getColor()));
            colorCell.setFixedHeight(20);
            colorCell.setBorderColor(BORDER);
            legend.addCell(colorCell);
            legend.addCell(bodyCell(group.getName(), Element.ALIGN_LEFT, Color.WHITE));
        }
        document.add(legend);
    }

    private void addConsolidatedSectionPage(Document document, SectionDef section, List<WorkGroup> groups,
                                             Map<String, SectionResponse> responsesByKey,
                                             Map<String, GroupSectionStatus> statusesByKey) throws DocumentException {
        Font headerFont = new Font(Font.HELVETICA, 16, Font.BOLD, PRIMARY);

        Paragraph header = new Paragraph(section.getCode() + " — " + section.getTitle(), headerFont);
        header.setSpacingAfter(4);
        document.add(header);

        LineSeparator separator = new LineSeparator();
        separator.setLineColor(PRIMARY);
        document.add(new Chunk(separator));
        document.add(new Paragraph(" "));

        for (WorkGroup group : groups) {
            document.add(groupBanner(group));

            ExportSectionData data = loadExportData(group, section, responsesByKey, statusesByKey);
            List<ExportBlock> blocks = sectionExportRenderer.render(data);
            pdfBlockEmitter.emit(document, blocks);

            Paragraph spacing = new Paragraph(" ");
            spacing.setSpacingAfter(10);
            document.add(spacing);
        }
    }

    private PdfPTable groupBanner(WorkGroup group) throws DocumentException {
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        banner.setSpacingBefore(6);
        banner.setSpacingAfter(6);
        PdfPCell cell = new PdfPCell(new Paragraph(group.getName(), new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE)));
        cell.setBackgroundColor(hexToColor(group.getColor()));
        cell.setPadding(6);
        cell.setBorder(Rectangle.NO_BORDER);
        banner.addCell(cell);
        return banner;
    }

    private void addTableHeaderRow(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Paragraph(h, new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE)));
            cell.setBackgroundColor(PRIMARY);
            cell.setPadding(5);
            cell.setBorderColor(BORDER);
            table.addCell(cell);
        }
    }

    private PdfPCell bodyCell(String text, int align, Color bg) {
        PdfPCell cell = new PdfPCell(new Paragraph(text == null ? "" : text, new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY)));
        cell.setHorizontalAlignment(align);
        cell.setPadding(5);
        cell.setBorderColor(BORDER);
        cell.setBackgroundColor(bg);
        return cell;
    }

    private Color hexToColor(String hex) {
        if (hex == null || !hex.matches("#[0-9A-Fa-f]{6}")) {
            return SLATE;
        }
        return new Color(
                Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16)
        );
    }

    private ExportSectionData loadExportData(WorkGroup group, SectionDef section,
                                              Map<String, SectionResponse> responsesByKey,
                                              Map<String, GroupSectionStatus> statusesByKey) {
        String k = key(group.getId(), section.getId());
        SectionResponse response = responsesByKey.get(k);
        GroupSectionStatus status = statusesByKey.get(k);
        JsonNode content = response != null ? parseJson(response.getContentJson()) : objectMapper.createObjectNode();
        Integer version = response != null ? response.getVersion() : 0;
        return new ExportSectionData(section, content, version, status);
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
