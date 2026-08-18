package com.senico.diagnostic.export;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.List;

/**
 * Traduit une List&lt;ExportBlock&gt; en elements OpenPDF (Paragraph, PdfPTable...).
 * Seule classe d'export qui touche l'API com.lowagie.text.
 */
@Component
public class PdfBlockEmitter {

    private static final Color PRIMARY = new Color(0x2D, 0x7A, 0x45);
    private static final Color SLATE = new Color(0x64, 0x74, 0x8B);
    private static final Color BORDER = new Color(0xE2, 0xE8, 0xF0);

    public void emit(Document document, List<ExportBlock> blocks) throws DocumentException {
        for (ExportBlock block : blocks) {
            emitOne(document, block);
        }
    }

    private void emitOne(Document document, ExportBlock block) throws DocumentException {
        switch (block) {
            case ExportBlock.Heading h -> document.add(heading(h));
            case ExportBlock.Paragraph p -> document.add(paragraph(p));
            case ExportBlock.KeyValueList kv -> document.add(keyValueTable(kv));
            case ExportBlock.BulletList b -> emitBulletList(document, b);
            case ExportBlock.Table t -> document.add(table(t));
            case ExportBlock.Quadrant q -> document.add(quadrant(q));
        }
    }

    private Paragraph heading(ExportBlock.Heading h) {
        int size = switch (h.level()) {
            case 2 -> 14;
            case 3 -> 12;
            default -> 11;
        };
        Paragraph p = new Paragraph(h.text(), new Font(Font.HELVETICA, size, Font.BOLD, PRIMARY));
        p.setSpacingBefore(10);
        p.setSpacingAfter(4);
        return p;
    }

    private Paragraph paragraph(ExportBlock.Paragraph p) {
        int style = (p.bold() ? Font.BOLD : Font.NORMAL) | (p.italic() ? Font.ITALIC : Font.NORMAL);
        Color color = p.italic() ? SLATE : Color.DARK_GRAY;
        Paragraph para = new Paragraph(p.text(), new Font(Font.HELVETICA, 10, style, color));
        para.setSpacingAfter(4);
        return para;
    }

    private PdfPTable keyValueTable(ExportBlock.KeyValueList kv) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(10);
        try {
            table.setWidths(new float[]{35, 65});
        } catch (DocumentException ignored) {
        }
        Color bg = kv.boxed() ? new Color(0xF8, 0xFA, 0xFC) : Color.WHITE;
        for (ExportBlock.KeyValue pair : kv.pairs()) {
            PdfPCell labelCell = textCell(pair.label(), new Font(Font.HELVETICA, 9, Font.BOLD, SLATE), Element.ALIGN_LEFT, bg);
            PdfPCell valueCell = textCell(pair.value(), new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY), Element.ALIGN_LEFT, bg);
            labelCell.setBorder(kv.boxed() ? Rectangle.BOX : Rectangle.NO_BORDER);
            labelCell.setBorderColor(BORDER);
            valueCell.setBorder(kv.boxed() ? Rectangle.BOX : Rectangle.NO_BORDER);
            valueCell.setBorderColor(BORDER);
            table.addCell(labelCell);
            table.addCell(valueCell);
        }
        return table;
    }

    private void emitBulletList(Document document, ExportBlock.BulletList b) throws DocumentException {
        if (b.title() != null && !b.title().isBlank()) {
            Paragraph title = new Paragraph(b.title(), new Font(Font.HELVETICA, 11, Font.BOLD, PRIMARY));
            title.setSpacingBefore(8);
            title.setSpacingAfter(2);
            document.add(title);
        }
        if (b.items().isEmpty()) {
            document.add(paragraph(new ExportBlock.Paragraph("Aucun élément.", true, false)));
            return;
        }
        for (String item : b.items()) {
            Paragraph p = new Paragraph("•  " + item, new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY));
            p.setIndentationLeft(14);
            p.setSpacingAfter(2);
            document.add(p);
        }
    }

    private PdfPTable table(ExportBlock.Table t) {
        PdfPTable table = new PdfPTable(Math.max(1, t.columnHeaders().size()));
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(10);

        for (String header : t.columnHeaders()) {
            PdfPCell cell = textCell(header, new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE), Element.ALIGN_LEFT, PRIMARY);
            cell.setBorderColor(BORDER);
            table.addCell(cell);
        }

        for (ExportBlock.TableRow row : t.rows()) {
            for (ExportBlock.Cell cell : row.cells()) {
                Color bg = cell.background() != ExportBlock.Background.NONE
                        ? awtColor(cell.background())
                        : (row.rowBackground() != ExportBlock.Background.NONE ? awtColor(row.rowBackground())
                        : (row.emphasized() ? awtColor(ExportBlock.Background.GREY) : Color.WHITE));
                Font font = new Font(Font.HELVETICA, 9, (cell.bold() || row.emphasized()) ? Font.BOLD : Font.NORMAL, Color.DARK_GRAY);
                int align = switch (cell.align()) {
                    case CENTER -> Element.ALIGN_CENTER;
                    case RIGHT -> Element.ALIGN_RIGHT;
                    default -> Element.ALIGN_LEFT;
                };
                PdfPCell pdfCell = textCell(cell.text(), font, align, bg);
                pdfCell.setBorderColor(BORDER);
                table.addCell(pdfCell);
            }
        }
        return table;
    }

    private PdfPTable quadrant(ExportBlock.Quadrant q) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(10);
        Color[] tints = {
                new Color(0xDC, 0xFC, 0xE7), new Color(0xFF, 0xED, 0xD5),
                new Color(0xDB, 0xEA, 0xFE), new Color(0xFE, 0xE2, 0xE2)
        };
        int i = 0;
        for (ExportBlock.QuadrantCell cell : q.cells()) {
            Color bg = tints[i % tints.length];
            StringBuilder sb = new StringBuilder();
            for (String item : cell.items()) {
                sb.append("•  ").append(item).append('\n');
            }
            if (cell.items().isEmpty()) {
                sb.append("Aucun élément.");
            }
            Paragraph content = new Paragraph();
            content.add(new Chunk(cell.title() + "\n", new Font(Font.HELVETICA, 10, Font.BOLD, Color.DARK_GRAY)));
            content.add(new Chunk(sb.toString(), new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY)));
            PdfPCell pdfCell = new PdfPCell(content);
            pdfCell.setBackgroundColor(bg);
            pdfCell.setBorderColor(BORDER);
            pdfCell.setPadding(8);
            table.addCell(pdfCell);
            i++;
        }
        return table;
    }

    private PdfPCell textCell(String text, Font font, int align, Color bg) {
        PdfPCell cell = new PdfPCell(new Paragraph(text == null ? "" : text, font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(5);
        if (bg != null) {
            cell.setBackgroundColor(bg);
        }
        return cell;
    }

    private Color awtColor(ExportBlock.Background bg) {
        return switch (bg) {
            case RED -> new Color(0xFE, 0xE2, 0xE2);
            case ORANGE -> new Color(0xFF, 0xED, 0xD5);
            case GREEN -> new Color(0xDC, 0xFC, 0xE7);
            case GREY -> new Color(0xF1, 0xF5, 0xF9);
            case PRIMARY_LIGHT -> new Color(0xE3, 0xF3, 0xE8);
            case NONE -> Color.WHITE;
        };
    }
}
