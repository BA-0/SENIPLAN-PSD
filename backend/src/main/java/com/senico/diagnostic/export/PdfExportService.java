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
import com.senico.diagnostic.domain.NarrativeBlockKey;
import com.senico.diagnostic.domain.PsdNarrativeBlock;
import com.senico.diagnostic.domain.SectionDef;
import com.senico.diagnostic.domain.SectionResponse;
import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.export.PsdDocumentStructure.Entry;
import com.senico.diagnostic.export.PsdDocumentStructure.MajorHeading;
import com.senico.diagnostic.export.PsdDocumentStructure.NarrativeEntry;
import com.senico.diagnostic.export.PsdDocumentStructure.SectionEntry;
import com.senico.diagnostic.repository.GroupSectionStatusRepository;
import com.senico.diagnostic.repository.PsdNarrativeBlockRepository;
import com.senico.diagnostic.repository.SectionDefRepository;
import com.senico.diagnostic.repository.SectionResponseRepository;
import com.senico.diagnostic.repository.WorkGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private static final String LOGO_RESOURCE = "/branding/logo-senico.png";

    private byte[] logoBytes;

    private final SectionDefRepository sectionDefRepository;
    private final SectionResponseRepository sectionResponseRepository;
    private final GroupSectionStatusRepository groupSectionStatusRepository;
    private final WorkGroupRepository workGroupRepository;
    private final PsdNarrativeBlockRepository psdNarrativeBlockRepository;
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

    public byte[] exportPsdFinalDocument() {
        try {
            Document document = new Document(PageSize.A4, 40, 40, 60, 50);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, baos);
            document.open();

            List<WorkGroup> groups = workGroupRepository.findAll();
            List<Entry> entries = PsdDocumentStructure.entries();

            Map<String, SectionResponse> responsesByKey = sectionResponseRepository.findAll().stream()
                    .collect(Collectors.toMap(r -> key(r.getGroup().getId(), r.getSection().getId()), r -> r));
            Map<String, GroupSectionStatus> statusesByKey = groupSectionStatusRepository.findAllWithGroupAndSection().stream()
                    .collect(Collectors.toMap(s -> key(s.getGroup().getId(), s.getSection().getId()), s -> s));
            Map<NarrativeBlockKey, PsdNarrativeBlock> narrativeByKey = psdNarrativeBlockRepository.findAll().stream()
                    .collect(Collectors.toMap(PsdNarrativeBlock::getKey, b -> b));

            addPsdFinalCoverPage(document);
            document.newPage();
            addPsdFinalSommairePage(document, entries, groups);

            for (Entry entry : entries) {
                document.newPage();
                if (entry instanceof MajorHeading heading) {
                    addPsdMajorHeadingPage(document, heading);
                } else if (entry instanceof NarrativeEntry narrative) {
                    addPsdNarrativePage(document, narrative, narrativeByKey.get(narrative.key()));
                } else if (entry instanceof SectionEntry sectionEntry) {
                    addPsdSectionEntryPage(document, sectionEntry, groups, responsesByKey, statusesByKey);
                }
            }

            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Erreur de generation du document final PSD", e);
        }
    }

    private void addPsdFinalCoverPage(Document document) throws DocumentException {
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

        Paragraph docTitle = new Paragraph("Document final", new Font(Font.HELVETICA, 16, Font.BOLD, Color.DARK_GRAY));
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

    private void addPsdFinalSommairePage(Document document, List<Entry> entries, List<WorkGroup> groups) throws DocumentException {
        Font headerFont = new Font(Font.HELVETICA, 16, Font.BOLD, PRIMARY);
        Paragraph header = new Paragraph("Sommaire", headerFont);
        header.setSpacingAfter(4);
        document.add(header);

        LineSeparator separator = new LineSeparator();
        separator.setLineColor(PRIMARY);
        document.add(new Chunk(separator));
        document.add(new Paragraph(" "));

        Font majorFont = new Font(Font.HELVETICA, 11, Font.BOLD, Color.DARK_GRAY);
        Font itemFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY);
        for (Entry entry : entries) {
            Paragraph p;
            if (entry instanceof MajorHeading heading) {
                p = new Paragraph(heading.title(), majorFont);
                p.setSpacingBefore(8);
            } else if (entry instanceof NarrativeEntry narrative) {
                p = new Paragraph("• " + narrative.label(), itemFont);
                p.setIndentationLeft(15);
            } else {
                SectionEntry sectionEntry = (SectionEntry) entry;
                p = new Paragraph("• " + sectionEntry.label(), itemFont);
                p.setIndentationLeft(15);
            }
            document.add(p);
        }

        Paragraph legendTitle = new Paragraph("Légende des directions", new Font(Font.HELVETICA, 13, Font.BOLD, PRIMARY));
        legendTitle.setSpacingBefore(20);
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

    private void addPsdMajorHeadingPage(Document document, MajorHeading heading) throws DocumentException {
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(150);
        document.add(spacer);

        Paragraph title = new Paragraph(heading.title(), new Font(Font.HELVETICA, 24, Font.BOLD, PRIMARY));
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
    }

    private void addPsdNarrativePage(Document document, NarrativeEntry narrative, PsdNarrativeBlock block) throws DocumentException {
        Font headerFont = new Font(Font.HELVETICA, 16, Font.BOLD, PRIMARY);
        Paragraph header = new Paragraph(narrative.label(), headerFont);
        header.setSpacingAfter(4);
        document.add(header);

        LineSeparator separator = new LineSeparator();
        separator.setLineColor(PRIMARY);
        document.add(new Chunk(separator));
        document.add(new Paragraph(" "));

        String content = block != null && block.getContent() != null ? block.getContent() : "";
        Font bodyFont = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.DARK_GRAY);
        if (content.isBlank()) {
            document.add(new Paragraph("(contenu à renseigner)", new Font(Font.HELVETICA, 11, Font.ITALIC, SLATE)));
            return;
        }
        for (String line : content.split("\n")) {
            Paragraph p = new Paragraph(line, bodyFont);
            p.setSpacingAfter(6);
            document.add(p);
        }
    }

    private static final String[] PESTEL_AXES = {
            "POLITIQUE", "ECONOMIQUE", "SOCIAL_CULTUREL", "TECHNOLOGIQUE", "ENVIRONNEMENTAL", "LEGAL"
    };

    private void addPsdSectionEntryPage(Document document, SectionEntry sectionEntry, List<WorkGroup> groups,
                                         Map<String, SectionResponse> responsesByKey,
                                         Map<String, GroupSectionStatus> statusesByKey) throws DocumentException {
        Font headerFont = new Font(Font.HELVETICA, 16, Font.BOLD, PRIMARY);
        Paragraph header = new Paragraph(sectionEntry.label(), headerFont);
        header.setSpacingAfter(4);
        document.add(header);

        LineSeparator separator = new LineSeparator();
        separator.setLineColor(PRIMARY);
        document.add(new Chunk(separator));
        document.add(new Paragraph(" "));

        for (String code : sectionEntry.sectionCodes()) {
            SectionDef section = sectionDefRepository.findByCode(code)
                    .orElseThrow(() -> new IllegalStateException("Section introuvable : " + code));

            if (sectionEntry.sectionCodes().size() > 1) {
                Paragraph subHeader = new Paragraph(section.getCode() + " — " + section.getTitle(),
                        new Font(Font.HELVETICA, 13, Font.BOLD, SLATE));
                subHeader.setSpacingBefore(10);
                subHeader.setSpacingAfter(6);
                document.add(subHeader);
            }

            switch (code) {
                case "S01" -> addPsdMergedStakeholders(document, section, groups, responsesByKey);
                case "S04" -> addPsdMergedSwot(document, section, groups, responsesByKey);
                case "S03" -> addPsdMergedPestel(document, section, groups, responsesByKey);
                default -> addPsdPerGroupSection(document, section, groups, responsesByKey, statusesByKey);
            }
        }
    }

    /** Rendu historique : un bloc complet par direction (utilise pour les sections qui restent propres a chaque direction : axes, budget, plan d'actions...). */
    private void addPsdPerGroupSection(Document document, SectionDef section, List<WorkGroup> groups,
                                        Map<String, SectionResponse> responsesByKey,
                                        Map<String, GroupSectionStatus> statusesByKey) throws DocumentException {
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

    /**
     * Rendu fusionne : les diagnostics des directions (parties prenantes, SWOT, PESTEL) se
     * recoupent souvent (ex. plusieurs directions citent "electricite" comme menace) ; on
     * n'affiche chaque element qu'une seule fois, avec un carre de couleur par direction
     * contributrice, au lieu de repeter le tableau complet de chaque direction.
     */
    private void addPsdMergedStakeholders(Document document, SectionDef section, List<WorkGroup> groups,
                                           Map<String, SectionResponse> responsesByKey) throws DocumentException {
        String[] headers = {"Catégorie", "Portée", "Rôles", "Attentes", "Stratégie d'adaptation", "Importance", "Influence", "Actions", "Directions"};
        float[] widths = {9, 7, 13, 13, 13, 7, 7, 14, 17};

        Map<String, String[]> rowValuesByKey = new LinkedHashMap<>();
        Map<String, List<WorkGroup>> contributorsByKey = new LinkedHashMap<>();

        for (WorkGroup group : groups) {
            JsonNode content = contentFor(group, section, responsesByKey);
            for (JsonNode row : JsonUtil.arr(content, "rows")) {
                String[] values = {
                        SectionLabels.stakeholderCategory(JsonUtil.text(row, "category")),
                        SectionLabels.stakeholderScope(JsonUtil.text(row, "scope")),
                        JsonUtil.text(row, "roles"),
                        JsonUtil.text(row, "expectations"),
                        JsonUtil.text(row, "adaptationStrategy"),
                        JsonUtil.text(row, "importance"),
                        JsonUtil.text(row, "influence"),
                        JsonUtil.text(row, "actions")
                };
                String key = PsdCrossGroupMerge.normalize(String.join("|", values));
                if (key.isBlank()) {
                    continue;
                }
                rowValuesByKey.putIfAbsent(key, values);
                List<WorkGroup> contributors = contributorsByKey.computeIfAbsent(key, k -> new ArrayList<>());
                if (!contributors.contains(group)) {
                    contributors.add(group);
                }
            }
        }

        if (rowValuesByKey.isEmpty()) {
            document.add(new Paragraph("Aucune donnée saisie pour cette section.", new Font(Font.HELVETICA, 10, Font.ITALIC, SLATE)));
            return;
        }

        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        table.setSpacingBefore(4);
        table.setSpacingAfter(4);
        addTableHeaderRow(table, headers);

        for (Map.Entry<String, String[]> entry : rowValuesByKey.entrySet()) {
            for (String value : entry.getValue()) {
                table.addCell(bodyCell(value, Element.ALIGN_LEFT, Color.WHITE));
            }
            Paragraph dots = new Paragraph();
            appendContributorDots(dots, contributorsByKey.get(entry.getKey()));
            PdfPCell dotsCell = new PdfPCell(dots);
            dotsCell.setBorderColor(BORDER);
            dotsCell.setPadding(5);
            table.addCell(dotsCell);
        }
        document.add(table);
        addMergeCaption(document);
    }

    private void addPsdMergedSwot(Document document, SectionDef section, List<WorkGroup> groups,
                                   Map<String, SectionResponse> responsesByKey) throws DocumentException {
        String[] fields = {"strengths", "weaknesses", "opportunities", "threats"};
        String[] labels = {"Forces", "Faiblesses", "Opportunités", "Menaces"};
        Color[] tints = {
                new Color(0xDC, 0xFC, 0xE7), new Color(0xFF, 0xED, 0xD5),
                new Color(0xDB, 0xEA, 0xFE), new Color(0xFE, 0xE2, 0xE2)
        };

        Map<WorkGroup, JsonNode> contentByGroup = new LinkedHashMap<>();
        for (WorkGroup group : groups) {
            contentByGroup.put(group, contentFor(group, section, responsesByKey));
        }

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(4);

        for (int i = 0; i < fields.length; i++) {
            List<Map.Entry<WorkGroup, String>> entries = new ArrayList<>();
            for (WorkGroup group : groups) {
                for (String item : JsonUtil.strList(contentByGroup.get(group), fields[i])) {
                    entries.add(Map.entry(group, item));
                }
            }
            List<PsdCrossGroupMerge.MergedItem> merged = PsdCrossGroupMerge.merge(entries, false);

            Paragraph cellContent = new Paragraph();
            cellContent.add(new Chunk(labels[i] + "\n", new Font(Font.HELVETICA, 10, Font.BOLD, Color.DARK_GRAY)));
            if (merged.isEmpty()) {
                cellContent.add(new Chunk("Aucun élément.", new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY)));
            } else {
                for (PsdCrossGroupMerge.MergedItem item : merged) {
                    cellContent.add(new Chunk("•  " + item.text(), new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY)));
                    appendContributorDots(cellContent, item.contributors());
                    cellContent.add(Chunk.NEWLINE);
                }
            }
            PdfPCell cell = new PdfPCell(cellContent);
            cell.setBackgroundColor(tints[i]);
            cell.setBorderColor(BORDER);
            cell.setPadding(8);
            table.addCell(cell);
        }
        document.add(table);
        addMergeCaption(document);
    }

    private void addPsdMergedPestel(Document document, SectionDef section, List<WorkGroup> groups,
                                     Map<String, SectionResponse> responsesByKey) throws DocumentException {
        Map<WorkGroup, JsonNode> contentByGroup = new LinkedHashMap<>();
        for (WorkGroup group : groups) {
            contentByGroup.put(group, contentFor(group, section, responsesByKey));
        }

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{15, 28, 28, 29});
        table.setSpacingBefore(4);
        table.setSpacingAfter(4);
        addTableHeaderRow(table, "Axe", "Menaces", "Opportunités", "Actions");

        String[] columns = {"threats", "opportunities", "actions"};
        for (String axisCode : PESTEL_AXES) {
            PdfPCell axisCell = bodyCell(SectionLabels.pestel(axisCode), Element.ALIGN_LEFT, Color.WHITE);
            axisCell.setVerticalAlignment(Element.ALIGN_TOP);
            table.addCell(axisCell);

            for (String column : columns) {
                List<Map.Entry<WorkGroup, String>> entries = new ArrayList<>();
                for (WorkGroup group : groups) {
                    for (JsonNode row : JsonUtil.arr(contentByGroup.get(group), "rows")) {
                        if (axisCode.equals(JsonUtil.text(row, "axis"))) {
                            String text = JsonUtil.text(row, column);
                            if (!text.isBlank()) {
                                entries.add(Map.entry(group, text));
                            }
                        }
                    }
                }
                List<PsdCrossGroupMerge.MergedItem> merged = PsdCrossGroupMerge.merge(entries, true);

                Paragraph cellContent = new Paragraph();
                if (merged.isEmpty()) {
                    cellContent.add(new Chunk("—", new Font(Font.HELVETICA, 9, Font.NORMAL, SLATE)));
                } else {
                    for (PsdCrossGroupMerge.MergedItem item : merged) {
                        cellContent.add(new Chunk("•  " + item.text(), new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY)));
                        appendContributorDots(cellContent, item.contributors());
                        cellContent.add(Chunk.NEWLINE);
                    }
                }
                PdfPCell cell = new PdfPCell(cellContent);
                cell.setBorderColor(BORDER);
                cell.setPadding(6);
                table.addCell(cell);
            }
        }
        document.add(table);
        addMergeCaption(document);
    }

    private void appendContributorDots(Paragraph content, List<WorkGroup> contributors) {
        for (WorkGroup group : contributors) {
            content.add(new Chunk(" ■", new Font(Font.HELVETICA, 8, Font.NORMAL, hexToColor(group.getColor()))));
        }
    }

    private void addMergeCaption(Document document) throws DocumentException {
        Paragraph caption = new Paragraph(
                "■ = direction(s) ayant mentionné cet élément (voir légende des directions au sommaire).",
                new Font(Font.HELVETICA, 8, Font.ITALIC, SLATE));
        caption.setSpacingAfter(10);
        document.add(caption);
    }

    private JsonNode contentFor(WorkGroup group, SectionDef section, Map<String, SectionResponse> responsesByKey) {
        SectionResponse response = responsesByKey.get(key(group.getId(), section.getId()));
        return response != null ? parseJson(response.getContentJson()) : objectMapper.createObjectNode();
    }

    private String key(Long groupId, Integer sectionId) {
        return groupId + ":" + sectionId;
    }

    private Image senicoLogo() throws DocumentException {
        if (logoBytes == null) {
            try (InputStream in = getClass().getResourceAsStream(LOGO_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("Logo SENICO introuvable sur le classpath: " + LOGO_RESOURCE);
                }
                logoBytes = in.readAllBytes();
            } catch (IOException e) {
                throw new IllegalStateException("Impossible de charger le logo SENICO", e);
            }
        }
        try {
            Image logo = Image.getInstance(logoBytes);
            logo.scaleToFit(190, 70);
            logo.setAlignment(Element.ALIGN_CENTER);
            return logo;
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de charger le logo SENICO", e);
        }
    }

    private void addConsolidatedCoverPage(Document document) throws DocumentException {
        Paragraph topSpacer = new Paragraph(" ");
        topSpacer.setSpacingAfter(70);
        document.add(topSpacer);

        document.add(senicoLogo());

        Paragraph logoSpacer = new Paragraph(" ");
        logoSpacer.setSpacingAfter(28);
        document.add(logoSpacer);

        LineSeparator rule = new LineSeparator();
        rule.setLineColor(PRIMARY);
        rule.setLineWidth(1.5f);
        document.add(new Chunk(rule));

        Font titleFont = new Font(Font.HELVETICA, 22, Font.BOLD, PRIMARY);
        Paragraph title = new Paragraph("SENICO SA — Plan Stratégique", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(28);
        document.add(title);

        Font subtitleFont = new Font(Font.HELVETICA, 14, Font.NORMAL, SLATE);
        Paragraph subtitle = new Paragraph("Plan Stratégique de Développement (PSD) 2027-2031", subtitleFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingBefore(10);
        document.add(subtitle);

        PdfPTable badge = new PdfPTable(1);
        badge.setWidthPercentage(62);
        badge.setSpacingBefore(46);
        badge.setHorizontalAlignment(Element.ALIGN_CENTER);
        PdfPCell badgeCell = new PdfPCell(new Paragraph("DOCUMENT DE CONSOLIDATION",
                new Font(Font.HELVETICA, 14, Font.BOLD, Color.WHITE)));
        badgeCell.setBackgroundColor(PRIMARY);
        badgeCell.setPadding(14);
        badgeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badge.addCell(badgeCell);
        document.add(badge);

        Paragraph description = new Paragraph(
                "Toutes les réponses de toutes les directions, réunies dans un seul document — "
                        + "code couleur par direction — pour permettre de trancher directement.",
                new Font(Font.HELVETICA, 11, Font.ITALIC, SLATE));
        description.setAlignment(Element.ALIGN_CENTER);
        description.setSpacingBefore(24);
        description.setIndentationLeft(50);
        description.setIndentationRight(50);
        document.add(description);

        Paragraph meta = new Paragraph(
                "Export généré le " + java.time.LocalDateTime.now().format(DATE_FORMAT),
                new Font(Font.HELVETICA, 11, Font.NORMAL, SLATE));
        meta.setAlignment(Element.ALIGN_CENTER);
        meta.setSpacingBefore(60);
        document.add(meta);

        Paragraph confidential = new Paragraph("Document interne — confidentiel",
                new Font(Font.HELVETICA, 9, Font.ITALIC, SLATE));
        confidential.setAlignment(Element.ALIGN_CENTER);
        confidential.setSpacingBefore(6);
        document.add(confidential);
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
