package com.senico.diagnostic.export;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Traduit une List&lt;ExportBlock&gt; en elements Apache POI XWPF (XWPFParagraph, XWPFTable...).
 * Seule classe d'export qui touche l'API org.apache.poi.xwpf.
 */
@Component
public class WordBlockEmitter {

    private static final String PRIMARY_HEX = "2D7A45";
    private static final String SLATE_HEX = "64748B";
    private static final String DARK_HEX = "1E293B";
    private static final String BOX_BG_HEX = "F8FAFC";

    public void emit(XWPFDocument doc, List<ExportBlock> blocks) {
        for (ExportBlock block : blocks) {
            emitOne(doc, block);
        }
    }

    private void emitOne(XWPFDocument doc, ExportBlock block) {
        switch (block) {
            case ExportBlock.Heading h -> heading(doc, h);
            case ExportBlock.Paragraph p -> paragraph(doc, p);
            case ExportBlock.KeyValueList kv -> keyValueTable(doc, kv);
            case ExportBlock.BulletList b -> bulletList(doc, b);
            case ExportBlock.Table t -> table(doc, t);
            case ExportBlock.Quadrant q -> quadrant(doc, q);
        }
    }

    private void heading(XWPFDocument doc, ExportBlock.Heading h) {
        int size = switch (h.level()) {
            case 2 -> 14;
            case 3 -> 12;
            default -> 11;
        };
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(160);
        p.setSpacingAfter(60);
        XWPFRun run = p.createRun();
        run.setText(h.text());
        run.setBold(true);
        run.setFontSize(size);
        run.setColor(PRIMARY_HEX);
    }

    private void paragraph(XWPFDocument doc, ExportBlock.Paragraph p) {
        XWPFParagraph para = doc.createParagraph();
        para.setSpacingAfter(60);
        XWPFRun run = para.createRun();
        run.setText(p.text());
        run.setBold(p.bold());
        run.setItalic(p.italic());
        run.setFontSize(10);
        run.setColor(p.italic() ? SLATE_HEX : DARK_HEX);
    }

    private void keyValueTable(XWPFDocument doc, ExportBlock.KeyValueList kv) {
        if (kv.pairs().isEmpty()) {
            return;
        }
        XWPFTable table = doc.createTable(kv.pairs().size(), 2);
        table.setWidth("100%");
        for (int i = 0; i < kv.pairs().size(); i++) {
            ExportBlock.KeyValue pair = kv.pairs().get(i);
            XWPFTableRow row = table.getRow(i);
            setCell(row.getCell(0), pair.label(), true, ParagraphAlignment.LEFT, SLATE_HEX, kv.boxed() ? BOX_BG_HEX : null, 9);
            setCell(row.getCell(1), pair.value(), false, ParagraphAlignment.LEFT, DARK_HEX, kv.boxed() ? BOX_BG_HEX : null, 9);
        }
        doc.createParagraph().setSpacingAfter(80);
    }

    private void bulletList(XWPFDocument doc, ExportBlock.BulletList b) {
        if (b.title() != null && !b.title().isBlank()) {
            XWPFParagraph title = doc.createParagraph();
            title.setSpacingBefore(120);
            XWPFRun run = title.createRun();
            run.setText(b.title());
            run.setBold(true);
            run.setFontSize(11);
            run.setColor(PRIMARY_HEX);
        }
        if (b.items().isEmpty()) {
            paragraph(doc, new ExportBlock.Paragraph("Aucun élément.", true, false));
            return;
        }
        for (String item : b.items()) {
            XWPFParagraph p = doc.createParagraph();
            p.setIndentationLeft(300);
            XWPFRun run = p.createRun();
            run.setText("•  " + item);
            run.setFontSize(10);
            run.setColor(DARK_HEX);
        }
    }

    private void table(XWPFDocument doc, ExportBlock.Table t) {
        int cols = Math.max(1, t.columnHeaders().size());
        XWPFTable table = doc.createTable(1 + t.rows().size(), cols);
        table.setWidth("100%");

        XWPFTableRow headerRow = table.getRow(0);
        for (int c = 0; c < cols; c++) {
            String header = c < t.columnHeaders().size() ? t.columnHeaders().get(c) : "";
            setCell(headerRow.getCell(c), header, true, ParagraphAlignment.LEFT, "FFFFFF", PRIMARY_HEX, 8);
        }

        for (int r = 0; r < t.rows().size(); r++) {
            ExportBlock.TableRow row = t.rows().get(r);
            XWPFTableRow tableRow = table.getRow(r + 1);
            for (int c = 0; c < cols; c++) {
                ExportBlock.Cell cell = c < row.cells().size() ? row.cells().get(c) : new ExportBlock.Cell("");
                String bg = cell.background() != ExportBlock.Background.NONE ? hex(cell.background())
                        : (row.rowBackground() != ExportBlock.Background.NONE ? hex(row.rowBackground())
                        : (row.emphasized() ? hex(ExportBlock.Background.GREY) : null));
                ParagraphAlignment align = switch (cell.align()) {
                    case CENTER -> ParagraphAlignment.CENTER;
                    case RIGHT -> ParagraphAlignment.RIGHT;
                    default -> ParagraphAlignment.LEFT;
                };
                setCell(tableRow.getCell(c), cell.text(), cell.bold() || row.emphasized(), align, DARK_HEX, bg, 9);
            }
        }
        doc.createParagraph().setSpacingAfter(80);
    }

    private void quadrant(XWPFDocument doc, ExportBlock.Quadrant q) {
        int rows = (int) Math.ceil(q.cells().size() / 2.0);
        XWPFTable table = doc.createTable(rows, 2);
        table.setWidth("100%");
        String[] tints = {"DCFCE7", "FFEDD5", "DBEAFE", "FEE2E2"};
        for (int i = 0; i < q.cells().size(); i++) {
            ExportBlock.QuadrantCell cell = q.cells().get(i);
            XWPFTableCell tableCell = table.getRow(i / 2).getCell(i % 2);
            tableCell.setColor(tints[i % tints.length]);
            XWPFParagraph titleParagraph = tableCell.getParagraphs().get(0);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText(cell.title());
            titleRun.setBold(true);
            titleRun.setFontSize(10);
            titleRun.setColor(DARK_HEX);
            if (cell.items().isEmpty()) {
                addRun(tableCell.addParagraph(), "Aucun élément.", false, true);
            } else {
                for (String item : cell.items()) {
                    addRun(tableCell.addParagraph(), "•  " + item, false, false);
                }
            }
        }
        doc.createParagraph().setSpacingAfter(80);
    }

    private void addRun(XWPFParagraph p, String text, boolean bold, boolean italic) {
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setItalic(italic);
        run.setFontSize(9);
        run.setColor(italic ? SLATE_HEX : DARK_HEX);
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

    private String hex(ExportBlock.Background bg) {
        return switch (bg) {
            case RED -> "FEE2E2";
            case ORANGE -> "FFEDD5";
            case GREEN -> "DCFCE7";
            case GREY -> "F1F5F9";
            case PRIMARY_LIGHT -> "E3F3E8";
            case NONE -> null;
        };
    }
}
