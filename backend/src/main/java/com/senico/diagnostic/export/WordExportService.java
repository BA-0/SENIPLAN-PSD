package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WordExportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String PRIMARY_HEX = "2D7A45";
    private static final String DARK_HEX = "1E293B";

    private final SectionDefRepository sectionDefRepository;
    private final SectionResponseRepository sectionResponseRepository;
    private final GroupSectionStatusRepository groupSectionStatusRepository;
    private final WorkGroupRepository workGroupRepository;
    private final PsdNarrativeBlockRepository psdNarrativeBlockRepository;
    private final SectionExportRenderer sectionExportRenderer;
    private final ExportContentReader exportContentReader;
    private final WordBlockEmitter wordBlockEmitter;
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

    public byte[] exportPsdFinalDocument() {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            List<WorkGroup> groups = workGroupRepository.findAll();
            List<Entry> entries = PsdDocumentStructure.entries();

            Map<String, SectionResponse> responsesByKey = sectionResponseRepository.findAll().stream()
                    .collect(Collectors.toMap(r -> r.getGroup().getId() + ":" + r.getSection().getId(), r -> r));
            Map<String, GroupSectionStatus> statusesByKey = groupSectionStatusRepository.findAllWithGroupAndSection().stream()
                    .collect(Collectors.toMap(s -> s.getGroup().getId() + ":" + s.getSection().getId(), s -> s));
            Map<NarrativeBlockKey, PsdNarrativeBlock> narrativeByKey = psdNarrativeBlockRepository.findAll().stream()
                    .collect(Collectors.toMap(PsdNarrativeBlock::getKey, b -> b));

            // La consolidation ne reprend que ce que la direction a valide (cf. PsdValidatedContent).
            responsesByKey = PsdValidatedContent.validatedOnly(responsesByKey, statusesByKey);

            addPsdFinalCoverPage(doc);
            doc.createParagraph().setPageBreak(true);
            addPsdFinalSommaire(doc, entries, groups);

            for (Entry entry : entries) {
                doc.createParagraph().setPageBreak(true);
                if (entry instanceof MajorHeading heading) {
                    addPsdMajorHeading(doc, heading);
                } else if (entry instanceof NarrativeEntry narrative) {
                    addPsdNarrative(doc, narrative, narrativeByKey.get(narrative.key()));
                } else if (entry instanceof SectionEntry sectionEntry) {
                    addPsdSectionEntry(doc, sectionEntry, groups, responsesByKey, statusesByKey);
                }
            }

            doc.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Erreur de generation du document final PSD", e);
        }
    }

    private void addPsdFinalCoverPage(XWPFDocument doc) {
        XWPFParagraph title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        title.setSpacingBefore(2000);
        XWPFRun titleRun = title.createRun();
        titleRun.setText("SENICO SA — Plan Stratégique");
        titleRun.setBold(true);
        titleRun.setFontSize(22);
        titleRun.setColor(PRIMARY_HEX);

        XWPFParagraph subtitle = doc.createParagraph();
        subtitle.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun subtitleRun = subtitle.createRun();
        subtitleRun.setText("Plan Stratégique de Développement (PSD) 2027-2031");
        subtitleRun.setFontSize(14);
        subtitleRun.setColor("64748B");

        XWPFParagraph docTitle = doc.createParagraph();
        docTitle.setAlignment(ParagraphAlignment.CENTER);
        docTitle.setSpacingBefore(600);
        XWPFRun docTitleRun = docTitle.createRun();
        docTitleRun.setText("PLAN STRATÉGIQUE DE SENICO");
        docTitleRun.setBold(true);
        docTitleRun.setFontSize(16);

        XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun metaRun = meta.createRun();
        metaRun.setText("Export généré le " + java.time.LocalDateTime.now().format(DATE_FORMAT));
        metaRun.setFontSize(11);
        metaRun.setColor("64748B");
    }

    private void addPsdFinalSommaire(XWPFDocument doc, List<Entry> entries, List<WorkGroup> groups) {
        XWPFParagraph header = doc.createParagraph();
        XWPFRun headerRun = header.createRun();
        headerRun.setText("Sommaire");
        headerRun.setBold(true);
        headerRun.setFontSize(16);
        headerRun.setColor(PRIMARY_HEX);
        header.setBorderBottom(Borders.SINGLE);

        for (Entry entry : entries) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun run = p.createRun();
            if (entry instanceof MajorHeading heading) {
                p.setSpacingBefore(160);
                run.setText(heading.title());
                run.setBold(true);
                run.setFontSize(12);
                run.setColor(DARK_HEX);
            } else {
                String label = entry instanceof NarrativeEntry n ? n.label() : ((SectionEntry) entry).label();
                p.setIndentationLeft(300);
                run.setText("• " + label);
                run.setFontSize(10);
                run.setColor(DARK_HEX);
            }
        }

        XWPFParagraph legendTitle = doc.createParagraph();
        legendTitle.setSpacingBefore(300);
        XWPFRun legendTitleRun = legendTitle.createRun();
        legendTitleRun.setText("Légende des directions");
        legendTitleRun.setBold(true);
        legendTitleRun.setFontSize(13);
        legendTitleRun.setColor(PRIMARY_HEX);

        for (WorkGroup group : groups) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun swatch = p.createRun();
            swatch.setText("■ ");
            swatch.setColor(hexToWordColor(group.getColor()));
            XWPFRun label = p.createRun();
            label.setText(group.getName());
            label.setColor(DARK_HEX);
            label.setFontSize(10);
        }
    }

    private void addPsdMajorHeading(XWPFDocument doc, MajorHeading heading) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingBefore(3000);
        XWPFRun run = p.createRun();
        run.setText(heading.title());
        run.setBold(true);
        run.setFontSize(24);
        run.setColor(PRIMARY_HEX);
    }

    private void addPsdNarrative(XWPFDocument doc, NarrativeEntry narrative, PsdNarrativeBlock block) {
        addWordSectionHeader(doc, narrative.label());

        String content = block != null && block.getContent() != null ? block.getContent() : "";
        if (content.isBlank()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun run = p.createRun();
            run.setText("(contenu à renseigner)");
            run.setItalic(true);
            run.setColor("64748B");
            return;
        }
        for (String line : content.split("\n")) {
            XWPFParagraph p = doc.createParagraph();
            p.setSpacingAfter(120);
            XWPFRun run = p.createRun();
            run.setText(line);
            run.setFontSize(11);
            run.setColor(DARK_HEX);
        }
    }

    private static final String[] PESTEL_AXES = {
            "POLITIQUE", "ECONOMIQUE", "SOCIAL_CULTUREL", "TECHNOLOGIQUE", "ENVIRONNEMENTAL", "LEGAL"
    };

    private void addPsdSectionEntry(XWPFDocument doc, SectionEntry sectionEntry, List<WorkGroup> groups,
                                     Map<String, SectionResponse> responsesByKey,
                                     Map<String, GroupSectionStatus> statusesByKey) {
        addWordSectionHeader(doc, sectionEntry.label());

        for (String code : sectionEntry.sectionCodes()) {
            SectionDef section = sectionDefRepository.findByCode(code)
                    .orElseThrow(() -> new IllegalStateException("Section introuvable : " + code));

            if (sectionEntry.sectionCodes().size() > 1) {
                XWPFParagraph subHeader = doc.createParagraph();
                subHeader.setSpacingBefore(160);
                XWPFRun run = subHeader.createRun();
                run.setText(section.getCode() + " — " + section.getTitle());
                run.setBold(true);
                run.setFontSize(13);
                run.setColor("64748B");
            }

            switch (code) {
                case "S01" -> addPsdMergedStakeholders(doc, section, groups, responsesByKey);
                case "S04" -> addPsdMergedSwot(doc, section, groups, responsesByKey);
                case "S03" -> addPsdMergedPestel(doc, section, groups, responsesByKey);
                default -> addPsdPerGroupSection(doc, section, groups, responsesByKey, statusesByKey);
            }
        }
    }

    /** Rendu historique : un bloc complet par direction (sections qui restent propres a chaque direction : axes, budget, plan d'actions...). */
    private void addPsdPerGroupSection(XWPFDocument doc, SectionDef section, List<WorkGroup> groups,
                                        Map<String, SectionResponse> responsesByKey,
                                        Map<String, GroupSectionStatus> statusesByKey) {
        for (WorkGroup group : groups) {
            addWordGroupBanner(doc, group);

            String key = group.getId() + ":" + section.getId();
            SectionResponse response = responsesByKey.get(key);
            GroupSectionStatus status = statusesByKey.get(key);
            JsonNode content = exportContentReader.read(group.getId(), section, response);
            Integer version = response != null ? response.getVersion() : 0;

            // Reponses deja filtrees sur les sections validees : une reponse absente alors que le
            // statut n'est pas NOT_STARTED signale un contenu retenu, pas une section vide.
            boolean withheld = !PsdValidatedContent.isValidated(status);
            List<ExportBlock> blocks = sectionExportRenderer.render(
                    new ExportSectionData(section, content, version, status, withheld));
            wordBlockEmitter.emit(doc, blocks);
        }
    }

    /**
     * Rendu fusionne : les diagnostics des directions (parties prenantes, SWOT, PESTEL) se
     * recoupent souvent (ex. plusieurs directions citent "electricite" comme menace) ; chaque
     * element n'apparait qu'une seule fois, avec un carre de couleur par direction contributrice.
     */
    private void addPsdMergedStakeholders(XWPFDocument doc, SectionDef section, List<WorkGroup> groups,
                                           Map<String, SectionResponse> responsesByKey) {
        String[] headers = {"Catégorie", "Portée", "Rôles", "Attentes", "Stratégie d'adaptation", "Importance", "Influence", "Actions", "Directions"};

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
            XWPFParagraph p = doc.createParagraph();
            XWPFRun run = p.createRun();
            run.setText("Aucune donnée saisie pour cette section.");
            run.setItalic(true);
            run.setColor("64748B");
            return;
        }

        XWPFTable table = doc.createTable(rowValuesByKey.size() + 1, headers.length);
        table.setWidth("100%");
        for (int c = 0; c < headers.length; c++) {
            setCell(table.getRow(0).getCell(c), headers[c], true, ParagraphAlignment.LEFT, "FFFFFF", PRIMARY_HEX, 9);
        }
        int r = 1;
        for (Map.Entry<String, String[]> entry : rowValuesByKey.entrySet()) {
            XWPFTableRow row = table.getRow(r++);
            String[] values = entry.getValue();
            for (int c = 0; c < values.length; c++) {
                setCell(row.getCell(c), values[c], false, ParagraphAlignment.LEFT, DARK_HEX, null, 9);
            }
            setDotsCell(row.getCell(headers.length - 1), contributorsByKey.get(entry.getKey()));
        }
        doc.createParagraph().setSpacingAfter(80);
        addMergeCaption(doc);
    }

    private void addPsdMergedSwot(XWPFDocument doc, SectionDef section, List<WorkGroup> groups,
                                   Map<String, SectionResponse> responsesByKey) {
        String[] fields = {"strengths", "weaknesses", "opportunities", "threats"};
        String[] labels = {"Forces", "Faiblesses", "Opportunités", "Menaces"};
        String[] tints = {"DCFCE7", "FFEDD5", "DBEAFE", "FEE2E2"};

        Map<WorkGroup, JsonNode> contentByGroup = new LinkedHashMap<>();
        for (WorkGroup group : groups) {
            contentByGroup.put(group, contentFor(group, section, responsesByKey));
        }

        XWPFTable table = doc.createTable(2, 2);
        table.setWidth("100%");

        for (int i = 0; i < fields.length; i++) {
            List<Map.Entry<WorkGroup, String>> entries = new ArrayList<>();
            for (WorkGroup group : groups) {
                for (String item : JsonUtil.strList(contentByGroup.get(group), fields[i])) {
                    entries.add(Map.entry(group, item));
                }
            }
            List<PsdCrossGroupMerge.MergedItem> merged = PsdCrossGroupMerge.merge(entries, false);

            XWPFTableCell cell = table.getRow(i / 2).getCell(i % 2);
            cell.setColor(tints[i]);
            XWPFParagraph titleParagraph = cell.getParagraphs().get(0);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText(labels[i]);
            titleRun.setBold(true);
            titleRun.setFontSize(10);
            titleRun.setColor(DARK_HEX);

            if (merged.isEmpty()) {
                XWPFRun run = cell.addParagraph().createRun();
                run.setText("Aucun élément.");
                run.setFontSize(9);
                run.setColor(DARK_HEX);
            } else {
                for (PsdCrossGroupMerge.MergedItem item : merged) {
                    XWPFParagraph p = cell.addParagraph();
                    XWPFRun run = p.createRun();
                    run.setText("•  " + item.text());
                    run.setFontSize(9);
                    run.setColor(DARK_HEX);
                    appendContributorDots(p, item.contributors());
                }
            }
        }
        doc.createParagraph().setSpacingAfter(80);
        addMergeCaption(doc);
    }

    private void addPsdMergedPestel(XWPFDocument doc, SectionDef section, List<WorkGroup> groups,
                                     Map<String, SectionResponse> responsesByKey) {
        Map<WorkGroup, JsonNode> contentByGroup = new LinkedHashMap<>();
        for (WorkGroup group : groups) {
            contentByGroup.put(group, contentFor(group, section, responsesByKey));
        }

        XWPFTable table = doc.createTable(PESTEL_AXES.length + 1, 4);
        table.setWidth("100%");
        String[] headers = {"Axe", "Menaces", "Opportunités", "Actions"};
        for (int c = 0; c < headers.length; c++) {
            setCell(table.getRow(0).getCell(c), headers[c], true, ParagraphAlignment.LEFT, "FFFFFF", PRIMARY_HEX, 9);
        }

        String[] columns = {"threats", "opportunities", "actions"};
        for (int a = 0; a < PESTEL_AXES.length; a++) {
            String axisCode = PESTEL_AXES[a];
            XWPFTableRow row = table.getRow(a + 1);
            setCell(row.getCell(0), SectionLabels.pestel(axisCode), false, ParagraphAlignment.LEFT, DARK_HEX, null, 9);

            for (int c = 0; c < columns.length; c++) {
                List<Map.Entry<WorkGroup, String>> entries = new ArrayList<>();
                for (WorkGroup group : groups) {
                    for (JsonNode contentRow : JsonUtil.arr(contentByGroup.get(group), "rows")) {
                        if (axisCode.equals(JsonUtil.text(contentRow, "axis"))) {
                            String text = JsonUtil.text(contentRow, columns[c]);
                            if (!text.isBlank()) {
                                entries.add(Map.entry(group, text));
                            }
                        }
                    }
                }
                List<PsdCrossGroupMerge.MergedItem> merged = PsdCrossGroupMerge.merge(entries, true);

                XWPFTableCell cell = row.getCell(c + 1);
                XWPFParagraph first = cell.getParagraphs().get(0);
                if (merged.isEmpty()) {
                    XWPFRun run = first.createRun();
                    run.setText("—");
                    run.setFontSize(9);
                    run.setColor("64748B");
                } else {
                    boolean firstItem = true;
                    for (PsdCrossGroupMerge.MergedItem item : merged) {
                        XWPFParagraph p = firstItem ? first : cell.addParagraph();
                        firstItem = false;
                        XWPFRun run = p.createRun();
                        run.setText("•  " + item.text());
                        run.setFontSize(9);
                        run.setColor(DARK_HEX);
                        appendContributorDots(p, item.contributors());
                    }
                }
            }
        }
        doc.createParagraph().setSpacingAfter(80);
        addMergeCaption(doc);
    }

    private void setCell(XWPFTableCell cell, String text, boolean bold, ParagraphAlignment align, String colorHex, String bgHex, int fontSize) {
        if (bgHex != null) {
            cell.setColor(bgHex);
        }
        XWPFParagraph p = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        p.setAlignment(align);
        XWPFRun run = p.createRun();
        run.setText(text == null ? "" : text);
        run.setBold(bold);
        run.setFontSize(fontSize);
        run.setColor(colorHex);
    }

    private void appendContributorDots(XWPFParagraph p, List<WorkGroup> contributors) {
        for (WorkGroup group : contributors) {
            XWPFRun dot = p.createRun();
            dot.setText(" ■");
            dot.setColor(hexToWordColor(group.getColor()));
        }
    }

    private void setDotsCell(XWPFTableCell cell, List<WorkGroup> contributors) {
        XWPFParagraph p = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        appendContributorDots(p, contributors);
    }

    private void addMergeCaption(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(160);
        XWPFRun run = p.createRun();
        run.setText("■ = direction(s) ayant mentionné cet élément (voir légende des directions au sommaire).");
        run.setItalic(true);
        run.setFontSize(8);
        run.setColor("64748B");
    }

    private JsonNode contentFor(WorkGroup group, SectionDef section, Map<String, SectionResponse> responsesByKey) {
        SectionResponse response = responsesByKey.get(group.getId() + ":" + section.getId());
        return exportContentReader.read(group.getId(), section, response);
    }

    private void addWordSectionHeader(XWPFDocument doc, String label) {
        XWPFParagraph header = doc.createParagraph();
        XWPFRun headerRun = header.createRun();
        headerRun.setText(label);
        headerRun.setBold(true);
        headerRun.setFontSize(16);
        headerRun.setColor(PRIMARY_HEX);
        header.setBorderBottom(Borders.SINGLE);
    }

    private void addWordGroupBanner(XWPFDocument doc, WorkGroup group) {
        XWPFTable table = doc.createTable(1, 1);
        table.setWidth("100%");
        XWPFTableCell cell = table.getRow(0).getCell(0);
        cell.setColor(hexToWordColor(group.getColor()));
        XWPFParagraph p = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        XWPFRun run = p.createRun();
        run.setText(group.getName());
        run.setBold(true);
        run.setColor("FFFFFF");
        doc.createParagraph().setSpacingAfter(80);
    }

    private String hexToWordColor(String hex) {
        if (hex == null || !hex.matches("#[0-9A-Fa-f]{6}")) {
            return "64748B";
        }
        return hex.substring(1);
    }

    private void addCoverPage(XWPFDocument doc, WorkGroup group) {
        XWPFParagraph title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        title.setSpacingBefore(2000);
        XWPFRun titleRun = title.createRun();
        titleRun.setText("SENICO SA — Plan Stratégique");
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

        List<ExportBlock> blocks = sectionExportRenderer.render(loadExportData(group.getId(), section));
        wordBlockEmitter.emit(doc, blocks);
    }

    private ExportSectionData loadExportData(Long groupId, SectionDef section) {
        SectionResponse response = sectionResponseRepository.findByGroupIdAndSectionId(groupId, section.getId()).orElse(null);
        GroupSectionStatus status = groupSectionStatusRepository.findByGroupIdAndSectionId(groupId, section.getId()).orElse(null);
        JsonNode content = exportContentReader.read(groupId, section, response);
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
