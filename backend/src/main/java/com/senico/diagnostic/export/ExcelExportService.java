package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.SectionDef;
import com.senico.diagnostic.domain.SectionResponse;
import com.senico.diagnostic.domain.SectionStatus;
import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.repository.GroupSectionStatusRepository;
import com.senico.diagnostic.repository.SectionDefRepository;
import com.senico.diagnostic.repository.SectionResponseRepository;
import com.senico.diagnostic.repository.WorkGroupRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Export Excel consolide : une feuille par section, toutes les reponses de tous les groupes.
 */
@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private final SectionDefRepository sectionDefRepository;
    private final WorkGroupRepository workGroupRepository;
    private final SectionResponseRepository sectionResponseRepository;
    private final GroupSectionStatusRepository groupSectionStatusRepository;
    private final ObjectMapper objectMapper;
    private final ExportContentReader exportContentReader;

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] exportConsolidated(int months, LocalDate referenceDate) {
        LocalDateTime periodStart = referenceDate.minusMonths(months).atStartOfDay();
        LocalDateTime periodEnd = referenceDate.atTime(23, 59, 59);
        String periodLabel = "Période : " + periodStart.toLocalDate().format(PERIOD_FORMAT)
                + " au " + periodEnd.toLocalDate().format(PERIOD_FORMAT)
                + (months == 1 ? " (mensuel)" : " (" + months + " mois)");

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle periodStyle = buildPeriodStyle(workbook);
            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle wrapStyle = buildWrapStyle(workbook);

            List<WorkGroup> groups = workGroupRepository.findAll();
            List<SectionDef> sections = sectionDefRepository.findAllByOrderByOrderAsc();

            Map<Long, CellStyle> groupStyles = new HashMap<>();
            for (WorkGroup group : groups) {
                groupStyles.put(group.getId(), buildGroupStyle(workbook, group.getColor()));
            }

            for (SectionDef section : sections) {
                Sheet sheet = workbook.createSheet(sanitizeSheetName(section.getCode() + " " + section.getTitle()));
                sheet.setColumnWidth(0, 28 * 256);
                sheet.setColumnWidth(1, 16 * 256);
                sheet.setColumnWidth(2, 100 * 256);

                Row periodRow = sheet.createRow(0);
                Cell periodCell = periodRow.createCell(0);
                periodCell.setCellValue(periodLabel);
                periodCell.setCellStyle(periodStyle);

                Row header = sheet.createRow(1);
                writeHeaderCell(header, 0, "Groupe", headerStyle);
                writeHeaderCell(header, 1, "Statut", headerStyle);
                writeHeaderCell(header, 2, "Contenu sur la période", headerStyle);

                int rowIndex = 2;
                for (WorkGroup group : groups) {
                    Row row = sheet.createRow(rowIndex++);
                    String content = renderContentForPeriod(section, group.getId(), periodStart, periodEnd);
                    row.setHeightInPoints(Math.max(60, 14 * Math.max(1, content.split("\n").length)));

                    Cell groupCell = row.createCell(0);
                    groupCell.setCellValue(group.getName());
                    groupCell.setCellStyle(groupStyles.getOrDefault(group.getId(), wrapStyle));

                    Cell statusCell = row.createCell(1);
                    statusCell.setCellValue(statusLabel(group.getId(), section.getId()));
                    statusCell.setCellStyle(wrapStyle);

                    Cell contentCell = row.createCell(2);
                    contentCell.setCellValue(content);
                    contentCell.setCellStyle(wrapStyle);
                }
            }

            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Erreur de generation du fichier Excel", e);
        }
    }

    private static final Map<SectionStatus, String> STATUS_COLORS = Map.of(
            SectionStatus.NOT_STARTED, "#F1F5F9",
            SectionStatus.IN_PROGRESS, "#DBEAFE",
            SectionStatus.SUBMITTED, "#E3F3E8",
            SectionStatus.VALIDATED, "#DCFCE7",
            SectionStatus.REVISION_REQUESTED, "#FFEDD5"
    );

    /**
     * Export Excel consolide complet : feuille "Sommaire" (legende des directions + avancement
     * par section), puis une feuille par section avec le contenu integral de toutes les directions,
     * sans filtre de periode.
     */
    public byte[] exportConsolidatedFull() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle wrapStyle = buildWrapStyle(workbook);
            CellStyle titleStyle = buildPeriodStyle(workbook);

            List<WorkGroup> groups = workGroupRepository.findAll();
            List<SectionDef> sections = sectionDefRepository.findAllByOrderByOrderAsc();

            Map<Long, CellStyle> groupStyles = new HashMap<>();
            for (WorkGroup group : groups) {
                groupStyles.put(group.getId(), buildGroupStyle(workbook, group.getColor()));
            }

            Map<String, SectionResponse> responsesByKey = sectionResponseRepository.findAll().stream()
                    .collect(Collectors.toMap(r -> key(r.getGroup().getId(), r.getSection().getId()), r -> r));
            Map<String, GroupSectionStatus> statusesByKey = groupSectionStatusRepository.findAllWithGroupAndSection().stream()
                    .collect(Collectors.toMap(s -> key(s.getGroup().getId(), s.getSection().getId()), s -> s));

            buildSommaireSheet(workbook, groups, sections, statusesByKey, headerStyle, wrapStyle, titleStyle, groupStyles);

            for (SectionDef section : sections) {
                Sheet sheet = workbook.createSheet(sanitizeSheetName(section.getCode() + " " + section.getTitle()));
                sheet.setColumnWidth(0, 28 * 256);
                sheet.setColumnWidth(1, 16 * 256);
                sheet.setColumnWidth(2, 100 * 256);

                Row header = sheet.createRow(0);
                writeHeaderCell(header, 0, "Groupe", headerStyle);
                writeHeaderCell(header, 1, "Statut", headerStyle);
                writeHeaderCell(header, 2, "Contenu", headerStyle);

                int rowIndex = 1;
                for (WorkGroup group : groups) {
                    String k = key(group.getId(), section.getId());
                    Row row = sheet.createRow(rowIndex++);
                    String content = renderFullContent(section, responsesByKey.get(k));
                    row.setHeightInPoints(Math.max(60, 14 * Math.max(1, content.split("\n").length)));

                    Cell groupCell = row.createCell(0);
                    groupCell.setCellValue(group.getName());
                    groupCell.setCellStyle(groupStyles.getOrDefault(group.getId(), wrapStyle));

                    GroupSectionStatus status = statusesByKey.get(k);
                    SectionStatus statusEnum = status != null ? status.getStatus() : SectionStatus.NOT_STARTED;
                    Cell statusCell = row.createCell(1);
                    statusCell.setCellValue(SectionExportRenderer.statusLabel(statusEnum.name()));
                    statusCell.setCellStyle(wrapStyle);

                    Cell contentCell = row.createCell(2);
                    contentCell.setCellValue(content);
                    contentCell.setCellStyle(wrapStyle);
                }
            }

            workbook.setActiveSheet(0);
            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Erreur de generation du fichier Excel consolide", e);
        }
    }

    private void buildSommaireSheet(XSSFWorkbook workbook, List<WorkGroup> groups, List<SectionDef> sections,
                                     Map<String, GroupSectionStatus> statusesByKey, CellStyle headerStyle,
                                     CellStyle wrapStyle, CellStyle titleStyle, Map<Long, CellStyle> groupStyles) {
        Sheet sheet = workbook.createSheet("Sommaire");
        sheet.setColumnWidth(0, 40 * 256);
        for (int i = 1; i <= groups.size(); i++) {
            sheet.setColumnWidth(i, 26 * 256);
        }

        int r = 0;
        Row titleRow = sheet.createRow(r++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Document de consolidation — Diagnostic stratégique");
        titleCell.setCellStyle(titleStyle);

        Row metaRow = sheet.createRow(r++);
        metaRow.createCell(0).setCellValue("Généré le " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        r++;
        Row legendHeaderRow = sheet.createRow(r++);
        writeHeaderCell(legendHeaderRow, 0, "Légende des directions", headerStyle);
        for (WorkGroup group : groups) {
            Row legendRow = sheet.createRow(r++);
            Cell cell = legendRow.createCell(0);
            cell.setCellValue(group.getName());
            cell.setCellStyle(groupStyles.getOrDefault(group.getId(), wrapStyle));
        }

        r++;
        Row matrixHeader = sheet.createRow(r++);
        writeHeaderCell(matrixHeader, 0, "Avancement par section", headerStyle);
        for (int i = 0; i < groups.size(); i++) {
            writeHeaderCell(matrixHeader, i + 1, groups.get(i).getName(), headerStyle);
        }

        for (SectionDef section : sections) {
            Row row = sheet.createRow(r++);
            Cell sectionCell = row.createCell(0);
            sectionCell.setCellValue(section.getCode() + " — " + section.getTitle());
            sectionCell.setCellStyle(wrapStyle);

            for (int i = 0; i < groups.size(); i++) {
                WorkGroup group = groups.get(i);
                GroupSectionStatus status = statusesByKey.get(key(group.getId(), section.getId()));
                SectionStatus statusEnum = status != null ? status.getStatus() : SectionStatus.NOT_STARTED;
                Cell statusCell = row.createCell(i + 1);
                statusCell.setCellValue(SectionExportRenderer.statusLabel(statusEnum.name()));
                statusCell.setCellStyle(buildStatusStyle(workbook, statusEnum));
            }
        }
    }

    private CellStyle buildStatusStyle(XSSFWorkbook workbook, SectionStatus status) {
        CellStyle style = buildWrapStyle(workbook);
        String hex = STATUS_COLORS.get(status);
        if (hex != null) {
            byte[] rgb = {
                    (byte) Integer.parseInt(hex.substring(1, 3), 16),
                    (byte) Integer.parseInt(hex.substring(3, 5), 16),
                    (byte) Integer.parseInt(hex.substring(5, 7), 16)
            };
            style.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(rgb, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return style;
    }

    private String key(Long groupId, Integer sectionId) {
        return groupId + ":" + sectionId;
    }

    private String renderFullContent(SectionDef section, SectionResponse response) {
        if (response == null) {
            return "(aucune donnée)";
        }
        JsonNode content = exportContentReader.read(response.getGroup().getId(), section, response);
        String text = JsonContentRenderer.renderAsText(content).trim();
        return text.isBlank() ? "(aucune donnée)" : text;
    }

    private CellStyle buildHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{0x2D, 0x7A, 0x45}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle buildPeriodStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setItalic(true);
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle buildWrapStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private CellStyle buildGroupStyle(XSSFWorkbook workbook, String hexColor) {
        CellStyle style = buildWrapStyle(workbook);
        if (hexColor != null && hexColor.matches("#[0-9A-Fa-f]{6}")) {
            byte[] rgb = {
                    (byte) Integer.parseInt(hexColor.substring(1, 3), 16),
                    (byte) Integer.parseInt(hexColor.substring(3, 5), 16),
                    (byte) Integer.parseInt(hexColor.substring(5, 7), 16)
            };
            style.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(rgb, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return style;
    }

    private void writeHeaderCell(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private String statusLabel(Long groupId, Integer sectionId) {
        Optional<GroupSectionStatus> status = groupSectionStatusRepository.findByGroupIdAndSectionId(groupId, sectionId);
        return status.map(s -> s.getStatus().name()).orElse("NOT_STARTED");
    }

    private String renderContentForPeriod(SectionDef section, Long groupId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        return sectionResponseRepository.findByGroupIdAndSectionId(groupId, section.getId())
                .map(r -> {
                    if (r.getUpdatedAt() == null || r.getUpdatedAt().isBefore(periodStart) || r.getUpdatedAt().isAfter(periodEnd)) {
                        return "(aucune mise à jour sur la période)";
                    }
                    JsonNode content = exportContentReader.read(groupId, section, r);
                    String text = JsonContentRenderer.renderAsText(content).trim();
                    return text.isBlank() ? "(aucune donnée)" : text;
                })
                .orElse("(aucune donnée)");
    }

    private String sanitizeSheetName(String name) {
        String cleaned = name.replaceAll("[\\[\\]:*?/\\\\]", " ");
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }
}
