package com.senico.diagnostic.export;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.IOException;
import java.util.List;

/**
 * Traduit une List&lt;ExportBlock&gt; en elements OpenPDF (Paragraph, PdfPTable...).
 * Seule classe d'export qui touche l'API com.lowagie.text, avec PdfExportService.
 */
@Component
public class PdfBlockEmitter {

    /** Prefixe des marqueurs poses sur les intertitres, releves a la pagination pour le sommaire. */
    static final String TOC_TAG = "toc:";

    private static final Color PRIMARY = new Color(0x2D, 0x7A, 0x45);
    private static final Color PRIMARY_DARK = new Color(0x1F, 0x5C, 0x33);
    private static final Color INK = new Color(0x1F, 0x29, 0x37);
    private static final Color SLATE = new Color(0x64, 0x74, 0x8B);
    private static final Color BORDER = new Color(0xE2, 0xE8, 0xF0);
    private static final Color CALLOUT_BG = new Color(0xF3, 0xF9, 0xF5);
    private static final Color WARNING_ACCENT = new Color(0xD9, 0x77, 0x06);
    private static final Color WARNING_BG = new Color(0xFF, 0xFB, 0xEB);
    private static final Color WARNING_TEXT = new Color(0x92, 0x40, 0x0E);
    private static final Color TILE_BG = new Color(0xF8, 0xFA, 0xFC);
    /** Trame une ligne sur deux : sur un tableau de dix lignes, l'oeil ne saute plus de ligne. */
    private static final Color ZEBRA = new Color(0xFA, 0xFB, 0xFC);

    /** Hauteur minimale a trouver sous un intertitre : un titre seul en bas de page n'introduit rien. */
    private static final float MIN_SPACE_UNDER_HEADING = 110;

    public void emit(Document document, List<ExportBlock> blocks) throws DocumentException {
        emit(document, null, blocks);
    }

    /**
     * Variante qui connait le writer : elle peut alors eviter un intertitre orphelin en bas de
     * page. Sans writer, la position courante n'est pas connue et le titre reste ou il tombe.
     */
    public void emit(Document document, PdfWriter writer, List<ExportBlock> blocks) throws DocumentException {
        for (int i = 0; i < blocks.size(); i++) {
            ExportBlock block = blocks.get(i);
            ExportBlock next = i + 1 < blocks.size() ? blocks.get(i + 1) : null;
            if (writer != null && block instanceof ExportBlock.Table t) {
                emitTable(document, writer, t, next instanceof ExportBlock.Callout analysis ? analysis : null);
            } else {
                emitOne(document, writer, block);
            }
        }
    }

    /**
     * Tableau pose en connaissant la position sur la page. Il est coupe a chaque bandeau
     * (« AXE 2 : ... », « Statut ») en tableaux accoles : un bandeau qui ne tient plus en bas de
     * page avec sa premiere ligne passe a la suivante, au lieu d'y rester seul. Et ses dernieres
     * lignes accompagnent l'encadre d'analyse qui le suit, plutot que de le laisser seul sur une
     * page blanche.
     */
    private void emitTable(Document document, PdfWriter writer, ExportBlock.Table t, ExportBlock.Callout analysis)
            throws DocumentException {
        float width = document.right() - document.left();
        float pageBody = document.top() - document.bottom();
        List<List<ExportBlock.TableRow>> chunks = chunksAtBands(t.rows());
        for (int i = 0; i < chunks.size(); i++) {
            boolean first = i == 0;
            boolean last = i == chunks.size() - 1;
            List<ExportBlock.TableRow> rows = chunks.get(i);
            PdfPTable pdf = measured(table(withRows(t, rows)), width, first, last);

            float lead = pdf.spacingBefore() + (first ? pdf.getHeaderHeight() : 0) + rowsHeight(pdf, leadingRows(rows));
            // Un tableau a lignes hautes commence sur la page en cours (cf. table) : le renvoyer
            // entier a la page suivante laisserait le blanc que cette regle evite.
            if (pdf.isSplitLate() && writer.getVerticalPosition(true) - document.bottom() < lead) {
                // Sur la nouvelle page, le tableau reprend ses en-tetes.
                document.newPage();
            } else if (!first) {
                pdf.setSkipFirstHeader(true);
            }

            if (last && analysis != null && pdf.isSplitLate()) {
                int tail = trailingRows(rows);
                float remaining = remainingAfter(pdf, writer.getVerticalPosition(true) - document.bottom(), pageBody,
                        pdf.isSkipFirstHeader());
                if (remaining < calloutHeight(analysis, width) && rows.size() - tail >= leadingRows(rows)
                        && splittable(rows, tail)) {
                    PdfPTable head = measured(table(withRows(t, rows.subList(0, rows.size() - tail))), width, first, false);
                    head.setSkipFirstHeader(pdf.isSkipFirstHeader());
                    document.add(head);
                    document.newPage();
                    document.add(measured(table(withRows(t, rows.subList(rows.size() - tail, rows.size()))), width, false, true));
                    return;
                }
            }
            document.add(pdf);
        }
    }

    private static ExportBlock.Table withRows(ExportBlock.Table t, List<ExportBlock.TableRow> rows) {
        return new ExportBlock.Table(t.columnHeaders(), rows, t.widths(), t.bands());
    }

    /** Largeur fixee pour pouvoir mesurer les lignes avant de poser le tableau ; accole a ses voisins. */
    private static PdfPTable measured(PdfPTable pdf, float width, boolean first, boolean last) {
        pdf.setTotalWidth(width);
        pdf.setLockedWidth(true);
        if (!first) {
            pdf.setSpacingBefore(0);
        }
        if (!last) {
            pdf.setSpacingAfter(0);
        }
        return pdf;
    }

    /** Les lignes d'un tableau, regroupees sous chaque bandeau ; des bandeaux consecutifs restent ensemble. */
    private static List<List<ExportBlock.TableRow>> chunksAtBands(List<ExportBlock.TableRow> rows) {
        List<List<ExportBlock.TableRow>> chunks = new java.util.ArrayList<>();
        List<ExportBlock.TableRow> current = new java.util.ArrayList<>();
        for (ExportBlock.TableRow row : rows) {
            if (row.band() && current.stream().anyMatch(previous -> !previous.band())) {
                chunks.add(current);
                current = new java.util.ArrayList<>();
            }
            current.add(row);
        }
        chunks.add(current);
        return chunks;
    }

    /** Bandeaux de tete, intitules repetes dessous, et la premiere ligne qu'ils introduisent. */
    private static int leadingRows(List<ExportBlock.TableRow> rows) {
        int count = 0;
        while (count < rows.size() && rows.get(count).band()) {
            count++;
        }
        if (count > 0 && count < rows.size() && rows.get(count).emphasized()) {
            count++;
        }
        return Math.min(rows.size(), count + 1);
    }

    /** Lignes de total de fin, et au moins une ligne de contenu avec elles. */
    private static int trailingRows(List<ExportBlock.TableRow> rows) {
        int count = 0;
        while (count < rows.size() && rows.get(rows.size() - 1 - count).emphasized()
                && !rows.get(rows.size() - 1 - count).band()) {
            count++;
        }
        return Math.min(rows.size(), Math.max(2, count + 1));
    }

    /** Coupe possible avant les {@code tail} dernieres lignes : aucune cellule fusionnee ne l'enjambe. */
    private static boolean splittable(List<ExportBlock.TableRow> rows, int tail) {
        ExportBlock.TableRow firstOfTail = rows.get(rows.size() - tail);
        return !firstOfTail.band() && firstOfTail.cells().stream().noneMatch(ExportBlock.Cell::isCovered);
    }

    private static float rowsHeight(PdfPTable pdf, int count) {
        float height = 0;
        for (int r = pdf.getHeaderRows(); r < Math.min(pdf.size(), pdf.getHeaderRows() + count); r++) {
            height += pdf.getRowHeight(r);
        }
        return height;
    }

    /**
     * Place restant sous le tableau une fois pose : les lignes remplissent la page, et celle qui ne
     * tient plus passe entiere a la suivante, sous les en-tetes repetes.
     */
    private static float remainingAfter(PdfPTable pdf, float available, float pageBody, boolean skipFirstHeader) {
        float header = pdf.getHeaderHeight();
        float remaining = available - pdf.spacingBefore() - (skipFirstHeader ? 0 : header);
        for (int r = pdf.getHeaderRows(); r < pdf.size(); r++) {
            float height = pdf.getRowHeight(r);
            remaining = height <= remaining ? remaining - height : pageBody - header - height;
        }
        return remaining - pdf.spacingAfter();
    }

    private float calloutHeight(ExportBlock.Callout c, float width) {
        PdfPTable box = callout(c);
        box.setTotalWidth(width);
        box.setLockedWidth(true);
        return box.spacingBefore() + box.getTotalHeight();
    }

    private void emitOne(Document document, PdfWriter writer, ExportBlock block) throws DocumentException {
        switch (block) {
            case ExportBlock.Heading h -> emitHeading(document, writer, h);
            case ExportBlock.Paragraph p -> document.add(paragraph(p));
            case ExportBlock.KeyValueList kv -> document.add(keyValueTable(kv));
            case ExportBlock.BulletList b -> emitBulletList(document, b);
            case ExportBlock.Table t -> document.add(table(t));
            case ExportBlock.Quadrant q -> document.add(quadrant(q));
            case ExportBlock.Callout c -> document.add(callout(c));
            case ExportBlock.MetricGrid m -> document.add(metricGrid(m));
            case ExportBlock.AttributedList a -> emitAttributedList(document, a);
            case ExportBlock.AttributedQuadrant a -> document.add(attributedQuadrant(a));
            case ExportBlock.ColorLegend l -> document.add(colorLegend(l));
            case ExportBlock.Chart c -> emitChart(document, writer, c);
        }
    }

    /**
     * Le niveau 1 est le titre de partie d'un document redige (« I. CONTEXTE... ») : il ouvre une
     * page, en grand, souligne d'un filet, comme dans un PSD publie. Les niveaux 1 et 2 portent un
     * marqueur que la pagination releve pour numeroter le sommaire.
     */
    private void emitHeading(Document document, PdfWriter writer, ExportBlock.Heading h) throws DocumentException {
        if (h.level() == 1) {
            document.newPage();
            Paragraph title = tagged(h.text(), PdfFonts.font(18, Font.BOLD, PRIMARY), TOC_TAG + h.text());
            title.setSpacingAfter(4);
            document.add(title);
            LineSeparator rule = new LineSeparator();
            rule.setLineColor(PRIMARY);
            rule.setLineWidth(1.2f);
            document.add(new Chunk(rule));
            Paragraph spacer = new Paragraph(" ", PdfFonts.font(6, Font.NORMAL, INK));
            spacer.setSpacingAfter(4);
            document.add(spacer);
            return;
        }
        ensureSpace(document, writer);
        Paragraph p;
        switch (h.level()) {
            case 2 -> {
                p = tagged(h.text(), PdfFonts.font(13, Font.BOLD, PRIMARY), TOC_TAG + h.text());
                p.setSpacingBefore(14);
                p.setSpacingAfter(6);
            }
            case 3 -> {
                p = new Paragraph(PdfFonts.phrase(h.text(), PdfFonts.font(11, Font.BOLD, PRIMARY_DARK)));
                p.setSpacingBefore(10);
                p.setSpacingAfter(4);
            }
            default -> {
                p = new Paragraph(PdfFonts.phrase(h.text(), PdfFonts.font(10, Font.BOLD, INK)));
                p.setSpacingBefore(8);
                p.setSpacingAfter(3);
            }
        }
        document.add(p);
    }

    private void ensureSpace(Document document, PdfWriter writer) {
        if (writer != null && writer.getVerticalPosition(true) - document.bottom() < MIN_SPACE_UNDER_HEADING) {
            document.newPage();
        }
    }

    private Paragraph tagged(String text, Font font, String tag) {
        Phrase phrase = PdfFonts.phrase(text, font);
        for (Object element : phrase.getChunks()) {
            ((Chunk) element).setGenericTag(tag);
        }
        Paragraph paragraph = new Paragraph(phrase);
        paragraph.setLeading(font.getSize() * 1.25f);
        return paragraph;
    }

    private Paragraph paragraph(ExportBlock.Paragraph p) {
        int style = (p.bold() ? Font.BOLD : Font.NORMAL) | (p.italic() ? Font.ITALIC : Font.NORMAL);
        Color color = p.italic() ? SLATE : INK;
        Paragraph para = new Paragraph(PdfFonts.phrase(p.text(), PdfFonts.font(10, style, color)));
        // Corps justifie et interligne aere : la mise en page d'un PSD publie, pas celle d'un
        // formulaire. Le drapeau a droite trahissait un document genere.
        para.setAlignment(Element.ALIGN_JUSTIFIED);
        para.setLeading(14.5f);
        para.setSpacingAfter(6);
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
        Color bg = kv.boxed() ? TILE_BG : Color.WHITE;
        for (ExportBlock.KeyValue pair : kv.pairs()) {
            PdfPCell labelCell = textCell(pair.label(), PdfFonts.font(9, Font.BOLD, SLATE), Element.ALIGN_LEFT, bg);
            PdfPCell valueCell = textCell(pair.value(), PdfFonts.font(9, Font.NORMAL, INK), Element.ALIGN_LEFT, bg);
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
        emitListTitle(document, b.title());
        emitItems(document, b.items().stream().map(ExportBlock.Attribution::new).toList());
    }

    private void emitAttributedList(Document document, ExportBlock.AttributedList a) throws DocumentException {
        emitListTitle(document, a.title());
        emitItems(document, a.items());
    }

    private void emitListTitle(Document document, String title) throws DocumentException {
        if (title != null && !title.isBlank()) {
            Paragraph p = new Paragraph(PdfFonts.phrase(title, PdfFonts.font(11, Font.BOLD, PRIMARY_DARK)));
            p.setSpacingBefore(8);
            p.setSpacingAfter(3);
            document.add(p);
        }
    }

    /** Mise en page unique des puces, attribuees ou non. */
    private void emitItems(Document document, List<ExportBlock.Attribution> items) throws DocumentException {
        if (items.isEmpty()) {
            document.add(paragraph(new ExportBlock.Paragraph("Aucun élément.", true, false)));
            return;
        }
        for (ExportBlock.Attribution item : items) {
            Paragraph p = bullet(item, 10);
            p.setIndentationLeft(22);
            p.setFirstLineIndent(-10);
            p.setSpacingAfter(3);
            document.add(p);
        }
        Paragraph after = new Paragraph(" ", PdfFonts.font(4, Font.NORMAL, INK));
        document.add(after);
    }

    /**
     * Une puce attribuee : le texte prend la couleur de la direction quand elle est seule,
     * reste neutre et porte une pastille par direction quand elles sont plusieurs.
     */
    private Paragraph bullet(ExportBlock.Attribution item, float size) {
        List<String> colors = item.colorHexes();
        Color textColor = colors.size() == 1 ? hexToColor(colors.get(0)) : INK;
        Font font = PdfFonts.font(size, Font.NORMAL, textColor);
        Paragraph p = new Paragraph();
        p.add(new Chunk("•  ", PdfFonts.font(size, Font.BOLD, colors.size() == 1 ? textColor : SLATE)));
        p.add(PdfFonts.phrase(item.text(), font));
        if (colors.size() > 1) {
            for (String hex : colors) {
                p.add(new Chunk(" ", font));
                p.add(swatch(hex, size));
            }
        }
        p.setLeading(size * 1.4f);
        return p;
    }

    /**
     * Pastille de couleur dessinee comme un fond de texte plutot qu'avec le glyphe « ■ » : aucune
     * police n'est alors necessaire, et la pastille a la meme taille partout.
     */
    static Chunk swatch(String hex, float size) {
        Chunk chunk = new Chunk("   ", PdfFonts.font(size * 0.62f, Font.NORMAL, Color.WHITE));
        chunk.setBackground(hexToColor(hex), 0.3f, 0.2f, 0.3f, 0.6f);
        return chunk;
    }

    /**
     * Encadre a filet lateral : le lecteur distingue au premier coup d'oeil la lecture d'un
     * tableau chiffre (vert) de l'avertissement sur le perimetre du document (ambre).
     */
    private PdfPTable callout(ExportBlock.Callout c) {
        boolean warning = c.tone() == ExportBlock.Tone.WARNING;
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(12);

        Font font = PdfFonts.font(9.5f, warning ? Font.BOLD : Font.ITALIC, warning ? WARNING_TEXT : INK);
        Paragraph text = new Paragraph(PdfFonts.phrase(c.text(), font));
        text.setLeading(13.5f);
        PdfPCell cell = new PdfPCell(text);
        cell.setBackgroundColor(warning ? WARNING_BG : CALLOUT_BG);
        cell.setBorder(Rectangle.LEFT);
        cell.setBorderColorLeft(warning ? WARNING_ACCENT : PRIMARY);
        cell.setBorderWidthLeft(3f);
        cell.setPaddingLeft(11);
        cell.setPaddingRight(11);
        cell.setPaddingTop(7);
        cell.setPaddingBottom(9);
        table.addCell(cell);
        return table;
    }

    /**
     * Tuiles de chiffres cles, deux par ligne : intitule discret au-dessus, valeur en grand.
     * Une derniere tuile orpheline prend toute la largeur plutot que de laisser un trou.
     */
    private PdfPTable metricGrid(ExportBlock.MetricGrid grid) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setSpacingAfter(12);

        List<ExportBlock.Metric> metrics = grid.metrics();
        for (int i = 0; i < metrics.size(); i++) {
            ExportBlock.Metric metric = metrics.get(i);
            Paragraph content = new Paragraph();
            content.add(PdfFonts.phrase(metric.label().toUpperCase(java.util.Locale.FRENCH), PdfFonts.font(7.5f, Font.BOLD, SLATE)));
            content.add(Chunk.NEWLINE);
            content.add(PdfFonts.phrase(metric.value(), PdfFonts.font(15, Font.BOLD, PRIMARY)));
            content.setLeading(19);

            PdfPCell cell = new PdfPCell(content);
            cell.setBackgroundColor(TILE_BG);
            cell.setBorder(Rectangle.BOX);
            cell.setBorderColor(BORDER);
            cell.setPaddingTop(8);
            cell.setPaddingBottom(11);
            cell.setPaddingLeft(11);
            cell.setPaddingRight(11);
            if (i == metrics.size() - 1 && metrics.size() % 2 == 1) {
                cell.setColspan(2);
            }
            table.addCell(cell);
        }
        return table;
    }

    private PdfPTable table(ExportBlock.Table t) {
        int columns = Math.max(1, t.columnHeaders().size());
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(10);
        boolean banded = t.bands() != null && !t.bands().isEmpty();
        boolean headed = t.showsHeaders();
        // Au-dela de dix colonnes (effectifs : M, F et total sur cinq exercices), le corps de texte
        // habituel ne tient plus « 1 568 » sur une ligne : le tableau se resserre.
        boolean dense = columns > 10;
        float fontSize = dense ? 7.5f : 8.5f;
        // Un tableau qui deborde sur la page suivante y reprend ses en-tetes : sans cela, la
        // seconde moitie d'un budget sur cinq exercices devient illisible.
        table.setHeaderRows((banded ? 1 : 0) + (headed ? 1 : 0));
        // Une ligne courte qui ne tient plus en bas de page passe entiere a la suivante : coupee,
        // elle laissait le nom d'une direction sur une page et ses chiffres sur l'autre. Seul un
        // tableau a lignes hautes (listes de constats) les commence sur la page en cours, pour ne
        // pas laisser un grand blanc au-dessus d'elles.
        table.setSplitLate(!hasTallRows(t));
        if (t.widths() != null && t.widths().size() == columns) {
            float[] widths = new float[columns];
            for (int i = 0; i < columns; i++) {
                widths[i] = t.widths().get(i);
            }
            try {
                table.setWidths(widths);
            } catch (DocumentException ignored) {
            }
        }

        if (banded) {
            for (ExportBlock.HeaderBand band : t.bands()) {
                PdfPCell cell = textCell(band.label(), PdfFonts.font(fontSize, Font.BOLD, Color.WHITE), Element.ALIGN_CENTER, PRIMARY_DARK);
                cell.setColspan(Math.max(1, band.span()));
                cell.setBorderColor(PRIMARY_DARK);
                table.addCell(cell);
            }
        }
        if (headed) {
            for (String header : t.columnHeaders()) {
                PdfPCell cell = textCell(header, PdfFonts.font(fontSize, Font.BOLD, Color.WHITE),
                        banded ? Element.ALIGN_CENTER : Element.ALIGN_LEFT, PRIMARY);
                cell.setBorderColor(PRIMARY);
                cell.setPaddingTop(5);
                cell.setPaddingBottom(6);
                if (dense) {
                    cell.setPaddingLeft(2);
                    cell.setPaddingRight(2);
                }
                table.addCell(cell);
            }
        }

        // Lignes restant a recouvrir, par colonne, sous une cellule fusionnee vers le bas.
        int[] covered = new int[columns];
        int rowIndex = 0;
        for (ExportBlock.TableRow row : t.rows()) {
            if (row.band()) {
                String text = row.cells().isEmpty() ? "" : row.cells().get(0).text();
                Color bg = row.rowBackground() != ExportBlock.Background.NONE
                        ? awtColor(row.rowBackground()) : awtColor(ExportBlock.Background.GREY);
                Color ink = row.rowBackground() == ExportBlock.Background.PRIMARY_DARK ? Color.WHITE : INK;
                PdfPCell cell = textCell(text, PdfFonts.font(fontSize + 0.5f, Font.BOLD, ink), Element.ALIGN_LEFT, bg);
                cell.setColspan(columns);
                cell.setBorderColor(BORDER);
                table.addCell(cell);
                rowIndex = 0;
                continue;
            }
            // Le tramage ne vaut que pour les lignes sans couleur propre : une ligne de total
            // ou une ligne coloree par le metier garde la sienne.
            Color defaultBg = rowIndex % 2 == 1 ? ZEBRA : Color.WHITE;
            for (int c = 0; c < columns; c++) {
                if (covered[c] > 0) {
                    covered[c]--;
                    continue;
                }
                ExportBlock.Cell cell = c < row.cells().size() ? row.cells().get(c) : new ExportBlock.Cell("");
                Color bg = cell.background() != ExportBlock.Background.NONE
                        ? awtColor(cell.background())
                        : (row.rowBackground() != ExportBlock.Background.NONE ? awtColor(row.rowBackground())
                        : (row.emphasized() ? awtColor(ExportBlock.Background.GREY) : defaultBg));
                Font font = PdfFonts.font(fontSize, (cell.bold() || row.emphasized()) ? Font.BOLD : Font.NORMAL, INK);
                int align = switch (cell.align()) {
                    case CENTER -> Element.ALIGN_CENTER;
                    case RIGHT -> Element.ALIGN_RIGHT;
                    default -> Element.ALIGN_LEFT;
                };
                PdfPCell pdfCell = cell.attributions().isEmpty()
                        ? textCell(cell.text(), font, align, bg)
                        : attributedCell(cell.attributions(), bg);
                pdfCell.setBorderColor(BORDER);
                if (dense) {
                    pdfCell.setPaddingLeft(2);
                    pdfCell.setPaddingRight(2);
                }
                if (cell.rowSpan() > 1) {
                    pdfCell.setRowspan(cell.rowSpan());
                    covered[c] = cell.rowSpan() - 1;
                }
                table.addCell(pdfCell);
            }
            rowIndex++;
        }
        return table;
    }

    /** Un tableau dont une cellule aligne plusieurs constats a des lignes trop hautes pour sauter de page. */
    private static boolean hasTallRows(ExportBlock.Table t) {
        return t.rows().stream()
                .flatMap(row -> row.cells().stream())
                .anyMatch(cell -> cell.attributions().size() > 3);
    }

    private PdfPTable quadrant(ExportBlock.Quadrant q) {
        return attributedQuadrant(new ExportBlock.AttributedQuadrant(q.cells().stream()
                .map(cell -> new ExportBlock.AttributedQuadrantCell(cell.title(),
                        cell.items().stream().map(ExportBlock.Attribution::new).toList()))
                .toList()));
    }

    private PdfPTable attributedQuadrant(ExportBlock.AttributedQuadrant q) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(10);
        table.setSplitLate(false);

        List<ExportBlock.Background> defaults = List.of(ExportBlock.Background.GREEN, ExportBlock.Background.ORANGE,
                ExportBlock.Background.BLUE, ExportBlock.Background.RED);
        List<ExportBlock.Background> tints = q.tints() == null || q.tints().isEmpty() ? defaults : q.tints();
        int i = 0;
        for (ExportBlock.AttributedQuadrantCell cell : q.cells()) {
            Paragraph content = new Paragraph();
            content.add(PdfFonts.phrase(cell.title(), PdfFonts.font(10, Font.BOLD, INK)));
            content.setLeading(13);
            PdfPCell pdfCell = new PdfPCell();
            pdfCell.addElement(content);
            if (cell.caption() != null && !cell.caption().isBlank()) {
                Paragraph caption = new Paragraph(PdfFonts.phrase(cell.caption(), PdfFonts.font(8.5f, Font.ITALIC, SLATE)));
                caption.setLeading(11.5f);
                caption.setSpacingAfter(2);
                pdfCell.addElement(caption);
            }
            if (cell.items().isEmpty()) {
                pdfCell.addElement(new Paragraph(PdfFonts.phrase("Aucun élément.", PdfFonts.font(9, Font.ITALIC, SLATE))));
            }
            for (ExportBlock.Attribution item : cell.items()) {
                Paragraph p = bullet(item, 9);
                p.setIndentationLeft(10);
                p.setFirstLineIndent(-10);
                p.setSpacingBefore(2);
                pdfCell.addElement(p);
            }
            pdfCell.setBackgroundColor(awtColor(tints.get(i % tints.size())));
            pdfCell.setBorderColor(Color.WHITE);
            pdfCell.setBorderWidth(2f);
            pdfCell.setPaddingTop(6);
            pdfCell.setPaddingBottom(10);
            pdfCell.setPaddingLeft(9);
            pdfCell.setPaddingRight(9);
            table.addCell(pdfCell);
            i++;
        }
        if (q.cells().size() % 2 == 1) {
            PdfPCell filler = new PdfPCell(new Paragraph(" "));
            filler.setBorder(Rectangle.NO_BORDER);
            table.addCell(filler);
        }
        return table;
    }

    /**
     * Legende d'attribution : son titre, puis chaque direction precedee de sa pastille, a la
     * suite. En tableau d'une ligne par direction, elle ne tenait plus sous le paragraphe qui
     * l'annonce et partait seule, sans titre, sur la page suivante.
     */
    private Paragraph colorLegend(ExportBlock.ColorLegend legend) {
        Font font = PdfFonts.font(9.5f, Font.NORMAL, INK);
        Paragraph p = new Paragraph();
        if (legend.title() != null && !legend.title().isBlank()) {
            p.add(PdfFonts.phrase(legend.title() + " :   ", PdfFonts.font(9.5f, Font.BOLD, INK)));
        }
        List<ExportBlock.Attribution> entries = legend.entries();
        for (int i = 0; i < entries.size(); i++) {
            ExportBlock.Attribution entry = entries.get(i);
            p.add(swatch(entry.colorHexes().isEmpty() ? "#64748B" : entry.colorHexes().get(0), 9.5f));
            p.add(PdfFonts.phrase(" " + entry.text() + (i < entries.size() - 1 ? "     " : ""), font));
        }
        p.setLeading(17);
        p.setSpacingBefore(2);
        p.setSpacingAfter(10);
        return p;
    }

    /**
     * Graphique insere en image, a la largeur utile de la page. S'il ne tient plus sur la page
     * en cours, il passe a la suivante : une image coupee n'est pas lisible.
     */
    private void emitChart(Document document, PdfWriter writer, ExportBlock.Chart chart) throws DocumentException {
        try {
            Image image = Image.getInstance(ChartImageRenderer.render(chart));
            float width = document.right() - document.left();
            image.scaleToFit(width, 360);
            image.setAlignment(Element.ALIGN_CENTER);
            image.setSpacingBefore(6);
            image.setSpacingAfter(8);
            if (writer != null && writer.getVerticalPosition(true) - document.bottom() < image.getScaledHeight() + 14) {
                document.newPage();
            }
            document.add(image);
        } catch (IOException e) {
            throw new DocumentException(e);
        }
    }

    static Color hexToColor(String hex) {
        if (hex == null || !hex.matches("#[0-9A-Fa-f]{6}")) {
            return SLATE;
        }
        return new Color(Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16));
    }

    /** Cellule dont le contenu est une liste de constats attribues a leurs directions. */
    private PdfPCell attributedCell(List<ExportBlock.Attribution> attributions, Color bg) {
        PdfPCell cell = new PdfPCell();
        boolean single = attributions.size() == 1;
        for (ExportBlock.Attribution item : attributions) {
            Paragraph p = single ? plainAttributed(item) : bullet(item, 8.5f);
            if (!single) {
                p.setIndentationLeft(9);
                p.setFirstLineIndent(-9);
            }
            p.setSpacingAfter(1.5f);
            cell.addElement(p);
        }
        cell.setPaddingTop(3);
        cell.setPaddingBottom(6);
        cell.setPaddingLeft(5);
        cell.setPaddingRight(5);
        cell.setBackgroundColor(bg);
        return cell;
    }

    /** Un element seul dans sa cellule n'a pas besoin de puce : sa couleur suffit a l'attribuer. */
    private Paragraph plainAttributed(ExportBlock.Attribution item) {
        List<String> colors = item.colorHexes();
        Font font = PdfFonts.font(8.5f, Font.NORMAL, colors.size() == 1 ? hexToColor(colors.get(0)) : INK);
        Paragraph p = new Paragraph(PdfFonts.phrase(item.text(), font));
        if (colors.size() > 1) {
            for (String hex : colors) {
                p.add(new Chunk(" ", font));
                p.add(swatch(hex, 8.5f));
            }
        }
        p.setLeading(11.5f);
        return p;
    }

    private PdfPCell textCell(String text, Font font, int align, Color bg) {
        Paragraph paragraph = new Paragraph(PdfFonts.phrase(text == null ? "" : text, font));
        paragraph.setLeading(font.getSize() * 1.3f);
        paragraph.setAlignment(align);
        PdfPCell cell = new PdfPCell();
        cell.addElement(paragraph);
        cell.setHorizontalAlignment(align);
        cell.setPaddingTop(2);
        cell.setPaddingBottom(5);
        cell.setPaddingLeft(5);
        cell.setPaddingRight(5);
        if (bg != null) {
            cell.setBackgroundColor(bg);
        }
        return cell;
    }

    private Color awtColor(ExportBlock.Background bg) {
        return switch (bg) {
            case RED -> new Color(0xFE, 0xE2, 0xE2);
            case ORANGE -> new Color(0xFF, 0xED, 0xD5);
            case BLUE -> new Color(0xDB, 0xEA, 0xFE);
            case GREEN -> new Color(0xDC, 0xFC, 0xE7);
            case GREY -> new Color(0xF1, 0xF5, 0xF9);
            case PRIMARY_LIGHT -> new Color(0xE3, 0xF3, 0xE8);
            case VIOLET -> new Color(0xED, 0xE9, 0xFE);
            case PRIMARY_DARK -> PRIMARY_DARK;
            case NONE -> Color.WHITE;
        };
    }
}
