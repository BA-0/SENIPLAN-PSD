package com.senico.diagnostic.export;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTVMerge;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.List;

/**
 * Traduit une List&lt;ExportBlock&gt; en elements Apache POI XWPF (XWPFParagraph, XWPFTable...).
 * Seule classe d'export qui touche l'API org.apache.poi.xwpf.
 */
@Component
public class WordBlockEmitter {

    private static final String PRIMARY_HEX = "2D7A45";
    private static final String PRIMARY_DARK_HEX = "1F5C33";
    private static final String SLATE_HEX = "64748B";
    private static final String DARK_HEX = "1E293B";
    private static final String BOX_BG_HEX = "F8FAFC";
    private static final String CALLOUT_BG_HEX = "F3F9F5";
    private static final String WARNING_BG_HEX = "FFFBEB";
    private static final String WARNING_TEXT_HEX = "92400E";
    private static final String TILE_BG_HEX = "F8FAFC";
    /** Trame une ligne sur deux : sur un tableau de dix lignes, l'oeil ne saute plus de ligne. */
    private static final String ZEBRA_HEX = "FAFBFC";
    /** Largeur utile d'une page A4 aux marges par defaut de Word, en points. */
    private static final int CHART_WIDTH_PT = 450;

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
            case ExportBlock.Callout c -> callout(doc, c);
            case ExportBlock.MetricGrid m -> metricGrid(doc, m);
            case ExportBlock.AttributedList a -> attributedList(doc, a);
            case ExportBlock.AttributedQuadrant a -> attributedQuadrant(doc, a);
            case ExportBlock.ColorLegend l -> colorLegend(doc, l);
            case ExportBlock.Chart c -> chart(doc, c);
        }
    }

    /**
     * Le niveau 1 est le titre de partie d'un document redige (« I. CONTEXTE... ») : il ouvre une
     * page, plus grand, souligne d'un filet, comme dans un PSD publie. Les niveaux suivants
     * restent les intertitres courants des sections.
     */
    private void heading(XWPFDocument doc, ExportBlock.Heading h) {
        int size = switch (h.level()) {
            case 1 -> 18;
            case 2 -> 13;
            case 3 -> 11;
            default -> 10;
        };
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(h.level() == 1 ? 0 : 200);
        p.setSpacingAfter(h.level() == 1 ? 200 : 80);
        if (h.level() == 1) {
            p.setPageBreak(true);
            p.setBorderBottom(Borders.SINGLE);
        }
        p.setKeepNext(true);
        XWPFRun run = p.createRun();
        run.setText(h.text());
        run.setBold(true);
        run.setFontSize(size);
        run.setColor(h.level() <= 2 ? PRIMARY_HEX : h.level() == 3 ? PRIMARY_DARK_HEX : DARK_HEX);
    }

    private void paragraph(XWPFDocument doc, ExportBlock.Paragraph p) {
        XWPFParagraph para = doc.createParagraph();
        para.setAlignment(ParagraphAlignment.BOTH);
        para.setSpacingAfter(100);
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
        listTitle(doc, b.title());
        emitItems(doc, b.items().stream().map(ExportBlock.Attribution::new).toList());
    }

    private void attributedList(XWPFDocument doc, ExportBlock.AttributedList a) {
        listTitle(doc, a.title());
        emitItems(doc, a.items());
    }

    private void listTitle(XWPFDocument doc, String title) {
        if (title != null && !title.isBlank()) {
            XWPFParagraph p = doc.createParagraph();
            p.setSpacingBefore(120);
            p.setKeepNext(true);
            XWPFRun run = p.createRun();
            run.setText(title);
            run.setBold(true);
            run.setFontSize(11);
            run.setColor(PRIMARY_DARK_HEX);
        }
    }

    /** Mise en page unique des puces, attribuees ou non. */
    private void emitItems(XWPFDocument doc, List<ExportBlock.Attribution> items) {
        if (items.isEmpty()) {
            paragraph(doc, new ExportBlock.Paragraph("Aucun élément.", true, false));
            return;
        }
        for (ExportBlock.Attribution item : items) {
            XWPFParagraph p = doc.createParagraph();
            p.setIndentationLeft(360);
            p.setIndentationHanging(200);
            p.setSpacingAfter(40);
            writeBullet(p, item, 10);
        }
    }

    /**
     * Une puce attribuee : le texte prend la couleur de la direction quand elle est seule,
     * reste neutre et porte une pastille par direction quand elles sont plusieurs.
     */
    private void writeBullet(XWPFParagraph p, ExportBlock.Attribution item, int size) {
        List<String> colors = item.colorHexes();
        XWPFRun run = p.createRun();
        run.setText("•  " + item.text());
        run.setFontSize(size);
        run.setColor(colors.size() == 1 ? cleanHex(colors.get(0)) : DARK_HEX);
        if (colors.size() > 1) {
            for (String hex : colors) {
                XWPFRun marker = p.createRun();
                marker.setText(" ■");
                marker.setFontSize(size - 1);
                marker.setColor(cleanHex(hex));
            }
        }
    }

    private void attributedQuadrant(XWPFDocument doc, ExportBlock.AttributedQuadrant q) {
        int rows = (int) Math.ceil(q.cells().size() / 2.0);
        XWPFTable table = doc.createTable(rows, 2);
        table.setWidth("100%");
        List<ExportBlock.Background> defaults = List.of(ExportBlock.Background.GREEN, ExportBlock.Background.ORANGE,
                ExportBlock.Background.BLUE, ExportBlock.Background.RED);
        List<ExportBlock.Background> tints = q.tints() == null || q.tints().isEmpty() ? defaults : q.tints();
        for (int i = 0; i < q.cells().size(); i++) {
            ExportBlock.AttributedQuadrantCell cell = q.cells().get(i);
            XWPFTableCell tableCell = table.getRow(i / 2).getCell(i % 2);
            tableCell.setColor(hex(tints.get(i % tints.size())));
            XWPFRun titleRun = tableCell.getParagraphs().get(0).createRun();
            titleRun.setText(cell.title());
            titleRun.setBold(true);
            titleRun.setFontSize(10);
            titleRun.setColor(DARK_HEX);
            if (cell.caption() != null && !cell.caption().isBlank()) {
                addRun(tableCell.addParagraph(), cell.caption(), false, true);
            }
            if (cell.items().isEmpty()) {
                addRun(tableCell.addParagraph(), "Aucun élément.", false, true);
            }
            for (ExportBlock.Attribution item : cell.items()) {
                writeBullet(tableCell.addParagraph(), item, 9);
            }
        }
        doc.createParagraph().setSpacingAfter(80);
    }

    /** Legende d'attribution : son titre, puis chaque direction precedee de sa pastille, a la suite (cf. PDF). */
    private void colorLegend(XWPFDocument doc, ExportBlock.ColorLegend legend) {
        if (legend.entries().isEmpty()) {
            return;
        }
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(160);
        if (legend.title() != null && !legend.title().isBlank()) {
            XWPFRun title = p.createRun();
            title.setText(legend.title() + " :   ");
            title.setBold(true);
            title.setFontSize(9);
            title.setColor(DARK_HEX);
        }
        for (int i = 0; i < legend.entries().size(); i++) {
            ExportBlock.Attribution entry = legend.entries().get(i);
            XWPFRun swatch = p.createRun();
            swatch.setText("■ ");
            swatch.setFontSize(11);
            swatch.setColor(entry.colorHexes().isEmpty() ? SLATE_HEX : cleanHex(entry.colorHexes().get(0)));
            XWPFRun name = p.createRun();
            name.setText(entry.text() + (i < legend.entries().size() - 1 ? "     " : ""));
            name.setFontSize(9);
            name.setColor(DARK_HEX);
        }
    }

    /** Word veut une couleur sans dièse ; une valeur absente ou invalide retombe sur le gris du corps. */
    private String cleanHex(String hex) {
        return hex != null && hex.matches("#[0-9A-Fa-f]{6}") ? hex.substring(1) : DARK_HEX;
    }

    /**
     * Encadre a filet lateral : le lecteur distingue au premier coup d'oeil la lecture d'un
     * tableau (vert) de l'avertissement sur le perimetre du document (ambre). Word n'offrant
     * pas de filet gauche colore simple sur un paragraphe, l'encadre est un tableau d'une cellule.
     */
    private void callout(XWPFDocument doc, ExportBlock.Callout c) {
        boolean warning = c.tone() == ExportBlock.Tone.WARNING;
        XWPFTable table = doc.createTable(1, 1);
        table.setWidth("100%");
        XWPFTableCell cell = table.getRow(0).getCell(0);
        cell.setColor(warning ? WARNING_BG_HEX : CALLOUT_BG_HEX);
        XWPFParagraph p = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        XWPFRun run = p.createRun();
        run.setText(c.text());
        run.setItalic(!warning);
        run.setBold(warning);
        run.setFontSize(10);
        run.setColor(warning ? WARNING_TEXT_HEX : DARK_HEX);
        doc.createParagraph().setSpacingAfter(80);
    }

    /**
     * Tuiles de chiffres cles, deux par ligne : intitule discret au-dessus, valeur en grand.
     * Une derniere tuile orpheline laisse sa voisine vide plutot que de s'etaler a moitie.
     */
    private void metricGrid(XWPFDocument doc, ExportBlock.MetricGrid grid) {
        List<ExportBlock.Metric> metrics = grid.metrics();
        if (metrics.isEmpty()) {
            return;
        }
        int rows = (int) Math.ceil(metrics.size() / 2.0);
        XWPFTable table = doc.createTable(rows, 2);
        table.setWidth("100%");
        for (int i = 0; i < rows * 2; i++) {
            XWPFTableCell cell = table.getRow(i / 2).getCell(i % 2);
            cell.setColor(TILE_BG_HEX);
            if (i >= metrics.size()) {
                continue;
            }
            ExportBlock.Metric metric = metrics.get(i);
            XWPFParagraph labelParagraph = cell.getParagraphs().isEmpty()
                    ? cell.addParagraph() : cell.getParagraphs().get(0);
            XWPFRun labelRun = labelParagraph.createRun();
            labelRun.setText(metric.label());
            labelRun.setBold(true);
            labelRun.setFontSize(8);
            labelRun.setColor(SLATE_HEX);
            XWPFRun valueRun = cell.addParagraph().createRun();
            valueRun.setText(metric.value());
            valueRun.setBold(true);
            valueRun.setFontSize(15);
            valueRun.setColor(PRIMARY_HEX);
        }
        doc.createParagraph().setSpacingAfter(80);
    }

    private void table(XWPFDocument doc, ExportBlock.Table t) {
        int cols = Math.max(1, t.columnHeaders().size());
        boolean banded = t.bands() != null && !t.bands().isEmpty();
        boolean headed = t.showsHeaders();
        int offset = banded ? 1 : 0;
        int firstBodyRow = offset + (headed ? 1 : 0);
        // Meme regle que le PDF : au-dela de dix colonnes, le tableau se resserre.
        boolean dense = cols > 10;
        int headerFont = dense ? 7 : 8;
        int bodyFont = dense ? 7 : 9;
        XWPFTable table = doc.createTable(firstBodyRow + t.rows().size(), cols);
        table.setWidth("100%");
        if (dense) {
            table.setCellMargins(0, 30, 0, 30);
        }
        boolean sized = t.widths() != null && t.widths().size() == cols;
        int totalWidth = sized ? t.widths().stream().mapToInt(Integer::intValue).sum() : 0;

        if (banded) {
            XWPFTableRow bandRow = table.getRow(0);
            bandRow.setRepeatHeader(true);
            int count = Math.min(t.bands().size(), cols);
            for (int b = 0; b < count; b++) {
                ExportBlock.HeaderBand band = t.bands().get(b);
                XWPFTableCell cell = bandRow.getCell(b);
                setCell(cell, band.label(), true, ParagraphAlignment.CENTER, "FFFFFF", PRIMARY_DARK_HEX, headerFont);
                span(cell, band.span());
            }
            keepCells(bandRow, count);
        }

        if (headed) {
            XWPFTableRow headerRow = table.getRow(offset);
            headerRow.setRepeatHeader(true);
            for (int c = 0; c < cols; c++) {
                String header = c < t.columnHeaders().size() ? t.columnHeaders().get(c) : "";
                setCell(headerRow.getCell(c), header, true, banded ? ParagraphAlignment.CENTER : ParagraphAlignment.LEFT,
                        "FFFFFF", PRIMARY_HEX, headerFont);
                if (sized && totalWidth > 0) {
                    headerRow.getCell(c).setWidth(Math.round(t.widths().get(c) * 100f / totalWidth) + "%");
                }
            }
        }

        // Lignes restant a recouvrir, par colonne, sous une cellule fusionnee vers le bas.
        int[] covered = new int[cols];
        int zebra = 0;
        for (int r = 0; r < t.rows().size(); r++) {
            ExportBlock.TableRow row = t.rows().get(r);
            XWPFTableRow tableRow = table.getRow(firstBodyRow + r);
            if (row.band()) {
                String text = row.cells().isEmpty() ? "" : row.cells().get(0).text();
                String bg = row.rowBackground() != ExportBlock.Background.NONE
                        ? hex(row.rowBackground()) : hex(ExportBlock.Background.GREY);
                String ink = row.rowBackground() == ExportBlock.Background.PRIMARY_DARK ? "FFFFFF" : DARK_HEX;
                setCell(tableRow.getCell(0), text, true, ParagraphAlignment.LEFT, ink, bg, bodyFont);
                span(tableRow.getCell(0), cols);
                keepCells(tableRow, 1);
                zebra = 0;
                continue;
            }
            zebra++;
            for (int c = 0; c < cols; c++) {
                if (sized && totalWidth > 0) {
                    tableRow.getCell(c).setWidth(Math.round(t.widths().get(c) * 100f / totalWidth) + "%");
                }
                if (covered[c] > 0) {
                    covered[c]--;
                    verticalMerge(tableRow.getCell(c), false);
                    continue;
                }
                ExportBlock.Cell cell = c < row.cells().size() ? row.cells().get(c) : new ExportBlock.Cell("");
                if (cell.rowSpan() > 1) {
                    verticalMerge(tableRow.getCell(c), true);
                    covered[c] = cell.rowSpan() - 1;
                }
                String bg = cell.background() != ExportBlock.Background.NONE ? hex(cell.background())
                        : (row.rowBackground() != ExportBlock.Background.NONE ? hex(row.rowBackground())
                        : (row.emphasized() ? hex(ExportBlock.Background.GREY)
                        : (zebra % 2 == 0 ? ZEBRA_HEX : null)));
                ParagraphAlignment align = switch (cell.align()) {
                    case CENTER -> ParagraphAlignment.CENTER;
                    case RIGHT -> ParagraphAlignment.RIGHT;
                    default -> ParagraphAlignment.LEFT;
                };
                if (cell.attributions().isEmpty()) {
                    setCell(tableRow.getCell(c), cell.text(), cell.bold() || row.emphasized(), align, DARK_HEX, bg, bodyFont);
                } else {
                    XWPFTableCell tableCell = tableRow.getCell(c);
                    if (bg != null) {
                        tableCell.setColor(bg);
                    }
                    boolean premier = true;
                    for (ExportBlock.Attribution item : cell.attributions()) {
                        XWPFParagraph p = premier && !tableCell.getParagraphs().isEmpty()
                                ? tableCell.getParagraphs().get(0) : tableCell.addParagraph();
                        if (cell.attributions().size() == 1) {
                            XWPFRun run = p.createRun();
                            run.setText(item.text());
                            run.setFontSize(9);
                            run.setColor(item.colorHexes().size() == 1 ? cleanHex(item.colorHexes().get(0)) : DARK_HEX);
                        } else {
                            writeBullet(p, item, 9);
                        }
                        premier = false;
                    }
                }
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

    /** Meme image que dans le PDF (cf. ChartImageRenderer), a la largeur utile de la page. */
    private void chart(XWPFDocument doc, ExportBlock.Chart chart) {
        byte[] png = ChartImageRenderer.render(chart);
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(120);
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            int heightPt = Math.round(CHART_WIDTH_PT * image.getHeight() / (float) image.getWidth());
            p.createRun().addPicture(new ByteArrayInputStream(png), XWPFDocument.PICTURE_TYPE_PNG, "graphique.png",
                    Units.toEMU(CHART_WIDTH_PT), Units.toEMU(heightPt));
        } catch (Exception e) {
            // Un graphique illisible ne doit pas priver l'utilisateur du document : le tableau
            // des memes chiffres, qui l'accompagne toujours, reste en place.
            addRun(p, "(graphique indisponible)", false, true);
        }
    }

    private void addRun(XWPFParagraph p, String text, boolean bold, boolean italic) {
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setItalic(italic);
        run.setFontSize(9);
        run.setColor(italic ? SLATE_HEX : DARK_HEX);
    }

    /** Etend la cellule sur {@code span} colonnes de la grille du tableau. */
    private void span(XWPFTableCell cell, int span) {
        if (span <= 1) {
            return;
        }
        CTTcPr properties = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        properties.addNewGridSpan().setVal(BigInteger.valueOf(span));
    }

    /** Fusion verticale : la premiere cellule ouvre la fusion, celles du dessous la prolongent. */
    private void verticalMerge(XWPFTableCell cell, boolean start) {
        CTTcPr properties = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTVMerge merge = properties.isSetVMerge() ? properties.getVMerge() : properties.addNewVMerge();
        merge.setVal(start ? STMerge.RESTART : STMerge.CONTINUE);
    }

    /** Retire les cellules devenues inutiles d'une ligne dont les premieres ont ete etendues. */
    private void keepCells(XWPFTableRow row, int count) {
        while (row.getTableCells().size() > count) {
            row.removeCell(row.getTableCells().size() - 1);
        }
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
            case BLUE -> "DBEAFE";
            case GREEN -> "DCFCE7";
            case GREY -> "F1F5F9";
            case PRIMARY_LIGHT -> "E3F3E8";
            case VIOLET -> "EDE9FE";
            case PRIMARY_DARK -> PRIMARY_DARK_HEX;
            case NONE -> null;
        };
    }
}
