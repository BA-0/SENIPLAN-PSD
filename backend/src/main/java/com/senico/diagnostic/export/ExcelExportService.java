package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.SectionDef;
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
                    String content = renderContentForPeriod(group.getId(), section.getId(), periodStart, periodEnd);
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

    private String renderContentForPeriod(Long groupId, Integer sectionId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        return sectionResponseRepository.findByGroupIdAndSectionId(groupId, sectionId)
                .map(r -> {
                    if (r.getUpdatedAt() == null || r.getUpdatedAt().isBefore(periodStart) || r.getUpdatedAt().isAfter(periodEnd)) {
                        return "(aucune mise à jour sur la période)";
                    }
                    JsonNode content;
                    try {
                        content = objectMapper.readTree(r.getContentJson());
                    } catch (Exception e) {
                        content = objectMapper.createObjectNode();
                    }
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
