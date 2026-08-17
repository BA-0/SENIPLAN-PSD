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
import java.util.List;
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

    public byte[] exportConsolidated() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle wrapStyle = buildWrapStyle(workbook);

            List<WorkGroup> groups = workGroupRepository.findAll();
            List<SectionDef> sections = sectionDefRepository.findAllByOrderByOrderAsc();

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
                    Row row = sheet.createRow(rowIndex++);
                    row.setHeightInPoints(Math.max(60, 14 * countLines(group.getId(), section.getId())));

                    Cell groupCell = row.createCell(0);
                    groupCell.setCellValue(group.getName());
                    groupCell.setCellStyle(wrapStyle);

                    Cell statusCell = row.createCell(1);
                    statusCell.setCellValue(statusLabel(group.getId(), section.getId()));
                    statusCell.setCellStyle(wrapStyle);

                    Cell contentCell = row.createCell(2);
                    contentCell.setCellValue(renderContent(group.getId(), section.getId()));
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

    private CellStyle buildWrapStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
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

    private String renderContent(Long groupId, Integer sectionId) {
        JsonNode content = sectionResponseRepository.findByGroupIdAndSectionId(groupId, sectionId)
                .map(r -> {
                    try {
                        return objectMapper.readTree(r.getContentJson());
                    } catch (Exception e) {
                        return objectMapper.createObjectNode();
                    }
                })
                .orElseGet(objectMapper::createObjectNode);
        String text = JsonContentRenderer.renderAsText(content).trim();
        return text.isBlank() ? "(aucune donnée)" : text;
    }

    private int countLines(Long groupId, Integer sectionId) {
        return Math.max(1, renderContent(groupId, sectionId).split("\n").length);
    }

    private String sanitizeSheetName(String name) {
        String cleaned = name.replaceAll("[\\[\\]:*?/\\\\]", " ");
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }
}
