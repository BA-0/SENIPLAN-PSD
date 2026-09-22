package com.senico.diagnostic.export;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
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
            if (writer != null && block instanceof ExportBlock.Heading h && h.level() > 1) {
                keepWithTable(document, writer, blocks, i);
            }
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

            if (!first && i == chunks.size() - 2 && pdf.isSplitLate()) {
                keepCompanyForLastChunk(document, writer, t, pdf, chunks.get(i + 1), width, pageBody);
            }

            float lead = pdf.spacingBefore() + (first ? pdf.getHeaderHeight() : 0) + rowsHeight(pdf, leadingRows(rows));
            // Apres un tableau, OpenPDF ouvre de lui-meme une nouvelle page quand il ne reste plus la
            // hauteur d'une ligne : le morceau suivant commence alors en haut de page et doit y
            // reprendre ses en-tetes, faute de quoi la page s'ouvre sur un bandeau « EFFET » nu.
            boolean atPageTop = writer.getVerticalPosition(true) >= document.top() - 0.5f;
            // Un tableau a lignes hautes commence sur la page en cours (cf. table) : le renvoyer
            // entier a la page suivante laisserait le blanc que cette regle evite.
            if (pdf.isSplitLate() && writer.getVerticalPosition(true) - document.bottom() < lead) {
                // Sur la nouvelle page, le tableau reprend ses en-tetes.
                document.newPage();
            } else if (!first && !atPageTop) {
                pdf.setSkipFirstHeader(true);
            }

            int ownHeader = ownHeaderStart(t, rows);
            if (ownHeader >= 0 && !(last && analysis != null)) {
                emitWithOwnHeader(document, writer, t, rows, ownHeader, width, first, last);
                continue;
            }

            if (last && analysis == null && pdf.isSplitLate()
                    && avoidLoneLastRow(document, writer, t, pdf, rows, width, pageBody, first)) {
                return;
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
            boolean firstChunk = first;
            boolean lastChunk = last;
            addUnbroken(document, writer, rows, pdf.isSkipFirstHeader(), (part, firstPart, lastPart) ->
                    measured(table(withRows(t, part)), width, firstChunk && firstPart, lastChunk && lastPart));
        }
    }

    /** Place a trouver en bas de page pour y commencer une ligne haute : en deca, elle ouvre la page suivante. */
    private static final float MIN_SPACE_TO_START_TALL_ROW = 120;
    /** Marge basse d'une cellule (cf. textCell) et epaisseur de filet : ce qui peut deborder sans texte. */
    private static final float ROW_BOTTOM_SLACK = 6;

    /** Un morceau de tableau, reconstruit sur une partie de ses lignes, en-tetes compris. */
    @FunctionalInterface
    private interface TablePart {
        PdfPTable build(List<ExportBlock.TableRow> rows, boolean firstPart, boolean lastPart);
    }

    /**
     * Pose des lignes de tableau en ne coupant, au bas d'une page, qu'entre deux groupes de lignes : une ligne
     * courte ne se partage plus entre deux pages (« Recherche et » en bas de l'une, « développement » en haut de
     * la suivante), ni les lignes que coiffe une cellule fusionnee (l'objectif d'une direction coupe au milieu
     * d'une phrase, sous ses OS). Seul un groupe trop haut pour passer entier a la page suivante sans y laisser un
     * grand blanc commence sur la page en cours et s'y coupe.
     */
    private void addUnbroken(Document document, PdfWriter writer, List<ExportBlock.TableRow> rows, boolean skipFirstHeader,
                             TablePart parts) throws DocumentException {
        float pageBody = document.top() - document.bottom();
        List<int[]> groups = rowGroups(rows);
        int from = 0;
        boolean firstPart = true;
        boolean skip = skipFirstHeader;
        while (from < groups.size()) {
            int offset = groups.get(from)[0];
            PdfPTable pdf = parts.build(rows.subList(offset, rows.size()), firstPart, true);
            pdf.setSkipFirstHeader(skip);
            boolean atPageTop = writer.getVerticalPosition(true) >= document.top() - 0.5f;
            // Deux points de jeu : l'arrondi des hauteurs ne doit pas laisser OpenPDF couper la ou le calcul voyait de la place.
            float remaining = writer.getVerticalPosition(true) - document.bottom() - pdf.spacingBefore()
                    - (skip ? 0 : pdf.getHeaderHeight()) - 2;
            int stop = groups.size();
            boolean breakPage = false;
            // Ligne haute dont seule la marge basse deborde : OpenPDF la couperait en laissant une ligne vide en haut
            // de la page suivante. Sa hauteur est ramenee a la place restante, le texte tenant deja sur la page.
            int trimmedRow = -1;
            float trimmedHeight = 0;
            for (int g = from; g < groups.size(); g++) {
                int[] group = groups.get(g);
                float height = 0;
                for (int r = group[0]; r < group[1]; r++) {
                    height += pdf.getRowHeight(pdf.getHeaderRows() + r - offset);
                }
                if (height <= remaining) {
                    remaining -= height;
                    continue;
                }
                boolean merged = group[1] - group[0] > 1;
                boolean tall = height > (merged ? pageBody / 2 : pageBody / 5);
                float overflow = height - (remaining + 2);
                if (tall && !merged && overflow > 0 && overflow <= ROW_BOTTOM_SLACK) {
                    stop = g + 1;
                    trimmedRow = group[0] - offset;
                    trimmedHeight = height - overflow - 0.5f;
                } else if (tall && remaining >= MIN_SPACE_TO_START_TALL_ROW) {
                    stop = g + 1;
                } else {
                    // Une ligne courte passe entiere a la page suivante ; une ligne haute aussi quand il ne resterait
                    // en bas de page que la place d'une ou deux lignes de texte, coupees de leur intitule.
                    stop = g;
                    breakPage = true;
                }
                break;
            }
            if (stop == groups.size()) {
                if (trimmedRow >= 0) {
                    pdf.getRow(pdf.getHeaderRows() + trimmedRow).setMaxHeights(trimmedHeight);
                }
                document.add(pdf);
                return;
            }
            if (stop == from) {
                if (atPageTop) {
                    document.add(pdf);
                    return;
                }
                document.newPage();
                skip = false;
                continue;
            }
            PdfPTable part = parts.build(rows.subList(offset, stop < groups.size() ? groups.get(stop)[0] : rows.size()),
                    firstPart, false);
            part.setSkipFirstHeader(skip);
            if (trimmedRow >= 0) {
                part.getRow(part.getHeaderRows() + trimmedRow).setMaxHeights(trimmedHeight);
            }
            document.add(part);
            if (breakPage) {
                document.newPage();
            }
            from = stop;
            firstPart = false;
            skip = writer.getVerticalPosition(true) < document.top() - 0.5f;
        }
    }

    /**
     * Groupes de lignes qui ne se separent pas d'une page a l'autre : une ligne dont une cellule est recouverte par
     * une fusion reste avec celle du dessus, un bandeau et les intitules qu'il coiffe avec la ligne qu'ils introduisent.
     */
    private static List<int[]> rowGroups(List<ExportBlock.TableRow> rows) {
        List<int[]> groups = new java.util.ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            boolean joins = r > 0 && (rows.get(r).cells().stream().anyMatch(ExportBlock.Cell::isCovered)
                    || rows.get(r - 1).band()
                    || (r > 1 && rows.get(r - 1).emphasized() && rows.get(r - 2).band()));
            if (joins) {
                groups.get(groups.size() - 1)[1] = r + 1;
            } else {
                groups.add(new int[]{r, r + 1});
            }
        }
        return groups;
    }

    /**
     * Un dernier morceau court (un bandeau « EFFET » et sa ligne) qui ne tiendrait plus sous l'avant-dernier
     * ouvrirait seul une page presque vide : l'avant-dernier passe avec lui a la page suivante.
     */
    private void keepCompanyForLastChunk(Document document, PdfWriter writer, ExportBlock.Table t, PdfPTable current,
                                         List<ExportBlock.TableRow> lastRows, float width, float pageBody) {
        if (lastRows.stream().filter(row -> !row.band()).count() > 1
                || writer.getVerticalPosition(true) >= document.top() - 0.5f) {
            return;
        }
        PdfPTable next = measured(table(withRows(t, lastRows)), width, false, true);
        float available = writer.getVerticalPosition(true) - document.bottom();
        float here = current.spacingBefore() + bodyHeight(current);
        float both = here + bodyHeight(next) + next.spacingAfter();
        if (here <= available && both > available && current.getHeaderHeight() + both <= pageBody) {
            document.newPage();
        }
    }

    /**
     * La derniere ligne d'un tableau ne passe pas seule sur une nouvelle page, sous ses en-tetes repetes : les
     * deux dernieres lignes y passent ensemble, ou le morceau entier quand le couper laisserait son bandeau
     * sans ligne. Sans cela, la vision de la Direction Logistique occupait seule une page.
     *
     * @return vrai si le morceau a ete pose
     */
    private boolean avoidLoneLastRow(Document document, PdfWriter writer, ExportBlock.Table t, PdfPTable pdf,
                                     List<ExportBlock.TableRow> rows, float width, float pageBody, boolean first)
            throws DocumentException {
        float available = writer.getVerticalPosition(true) - document.bottom();
        if (rowsOnLastPage(pdf, available, pageBody, pdf.isSkipFirstHeader()) != 1) {
            return false;
        }
        int tail = 2;
        if (rows.size() - tail >= leadingRows(rows) && splittable(rows, tail)) {
            PdfPTable head = measured(table(withRows(t, rows.subList(0, rows.size() - tail))), width, first, false);
            head.setSkipFirstHeader(pdf.isSkipFirstHeader());
            document.add(head);
            document.newPage();
            document.add(measured(table(withRows(t, rows.subList(rows.size() - tail, rows.size()))), width, false, true));
            return true;
        }
        if (!first && pdf.getTotalHeight() <= pageBody) {
            document.newPage();
            pdf.setSkipFirstHeader(false);
        }
        return false;
    }

    /** Lignes posees sur la derniere page d'un tableau qui deborde ; {@code Integer.MAX_VALUE} s'il tient ici. */
    private static int rowsOnLastPage(PdfPTable pdf, float available, float pageBody, boolean skipFirstHeader) {
        float header = pdf.getHeaderHeight();
        float remaining = available - pdf.spacingBefore() - (skipFirstHeader ? 0 : header);
        int onLastPage = Integer.MAX_VALUE;
        for (int r = pdf.getHeaderRows(); r < pdf.size(); r++) {
            float height = pdf.getRowHeight(r);
            if (height <= remaining) {
                remaining -= height;
                if (onLastPage != Integer.MAX_VALUE) {
                    onLastPage++;
                }
            } else {
                remaining = pageBody - header - height;
                onLastPage = 1;
            }
        }
        return onLastPage;
    }

    private static float bodyHeight(PdfPTable pdf) {
        return pdf.getTotalHeight() - pdf.getHeaderHeight();
    }

    /**
     * Un quadrant (SWOT, TOWS) dont les cases tiennent sur une demi-page ne coupe plus une case entre deux
     * pages : « Opportunités » et « Menaces » restaient seuls en bas de page, leurs puces sur la suivante.
     * Des cases plus hautes continuent de commencer sur la page en cours, pour ne pas laisser un grand blanc.
     */
    private static PdfPTable keepCellsWhole(PdfPTable quadrant, Document document) {
        quadrant.setTotalWidth(document.right() - document.left());
        quadrant.setLockedWidth(true);
        float tallest = 0;
        for (int r = 0; r < quadrant.size(); r++) {
            tallest = Math.max(tallest, quadrant.getRowHeight(r));
        }
        quadrant.setSplitLate(tallest <= (document.top() - document.bottom()) / 2);
        return quadrant;
    }

    /**
     * Debut des intitules propres a un morceau de tableau : son dernier bandeau de tete, suivi d'une ligne
     * d'intitules de colonnes, dans un tableau sans ligne d'en-tete (synthese du cadre strategique, ou les
     * intitules se repetent sous le bandeau de chaque axe). -1 si le morceau n'en porte pas.
     */
    private static int ownHeaderStart(ExportBlock.Table t, List<ExportBlock.TableRow> rows) {
        int bands = 0;
        while (bands < rows.size() && rows.get(bands).band()) {
            bands++;
        }
        if (t.showsHeaders() || bands == 0 || bands + 1 >= rows.size() || !rows.get(bands).emphasized()) {
            return -1;
        }
        return bands - 1;
    }

    /**
     * Pose un morceau dont le bandeau (« AXE 2 : ... ») et les intitules de colonnes deviennent l'en-tete :
     * sur la page suivante, les lignes reprennent sous ce bandeau et ces intitules, et non sous le seul titre
     * du tableau, qui laissait le lecteur deviner a quelle colonne et a quel axe il avait affaire. Ce qui
     * precede le bandeau (la vision, sous le titre du tableau) est pose juste avant, sans se repeter.
     */
    private void emitWithOwnHeader(Document document, PdfWriter writer, ExportBlock.Table t, List<ExportBlock.TableRow> rows,
                                   int start, float width, boolean first, boolean last) throws DocumentException {
        List<ExportBlock.TableRow> head = rows.subList(start, start + 2);
        // Rien avant le bandeau : le titre du tableau reste en tete, et se repete avec lui.
        boolean keepTitle = first && start == 0;
        if (!keepTitle && start > 0) {
            ExportBlock.Table prefix = first ? withRows(t, rows.subList(0, start))
                    : new ExportBlock.Table(t.columnHeaders(), rows.subList(0, start), t.widths(), List.of());
            document.add(measured(table(prefix), width, first, false));
        }
        addUnbroken(document, writer, rows.subList(start + 2, rows.size()), false, (part, firstPart, lastPart) -> {
            List<ExportBlock.TableRow> all = new java.util.ArrayList<>(head);
            all.addAll(part);
            PdfPTable table = keepTitle
                    ? measured(table(withRows(t, all)), width, firstPart, last && lastPart)
                    : measured(table(new ExportBlock.Table(t.columnHeaders(), all, t.widths(), List.of())), width, false,
                            last && lastPart);
            table.setHeaderRows(table.getHeaderRows() + 2);
            return table;
        });
    }

    /**
     * Un intertitre, et les paragraphes qui l'introduisent, ne restent pas en bas de page quand le
     * tableau qu'ils annoncent ne peut pas commencer dessous : ils passent avec lui a la page
     * suivante. Sans cela, « Axe 4 » et son objectif finissaient seuls au pied de la page.
     */
    private void keepWithTable(Document document, PdfWriter writer, List<ExportBlock> blocks, int start) {
        float width = document.right() - document.left();
        float needed = 0;
        int j = start;
        for (; j < blocks.size(); j++) {
            ExportBlock block = blocks.get(j);
            Paragraph text;
            if (block instanceof ExportBlock.Heading h && h.level() > 1) {
                text = headingParagraph(h);
            } else if (block instanceof ExportBlock.Paragraph p) {
                text = paragraph(p);
            } else {
                break;
            }
            needed += text.getSpacingBefore() + height(text, width) + text.getSpacingAfter();
        }
        ExportBlock target = j < blocks.size() ? blocks.get(j) : null;
        if (target instanceof ExportBlock.Quadrant || target instanceof ExportBlock.AttributedQuadrant) {
            // Une matrice SWOT ne coupe pas ses cases : si la premiere rangee ne tient pas sous « V.4 Analyse
            // SWOT », le titre restait seul au pied de la page et la matrice ouvrait la suivante.
            PdfPTable pdf = keepCellsWhole(target instanceof ExportBlock.Quadrant q ? quadrant(q)
                    : attributedQuadrant((ExportBlock.AttributedQuadrant) target), document);
            float firstRow = pdf.size() == 0 ? 0 : pdf.getRowHeight(0);
            needed += pdf.spacingBefore() + (pdf.isSplitLate() ? firstRow : Math.min(firstRow, 72));
        } else if (target instanceof ExportBlock.Table t && !t.rows().isEmpty()) {
            List<ExportBlock.TableRow> firstRows = chunksAtBands(t.rows()).get(0);
            PdfPTable pdf = measured(table(withRows(t, firstRows)), width, true, false);
            float rows = rowsHeight(pdf, leadingRows(firstRows));
            // Un tableau a lignes hautes se coupe dans la ligne : quelques lignes de texte suffisent a l'amorcer.
            needed += pdf.spacingBefore() + pdf.getHeaderHeight() + (pdf.isSplitLate() ? rows : Math.min(rows, 72));
        } else {
            return;
        }
        float available = writer.getVerticalPosition(true) - document.bottom();
        if (available < needed && needed < document.top() - document.bottom()) {
            document.newPage();
        }
    }

    /** Hauteur d'un paragraphe pose sur la largeur utile, espacements non compris. */
    private static float height(Paragraph paragraph, float width) {
        ColumnText column = new ColumnText(null);
        column.setSimpleColumn(0, 0, width, 10_000);
        column.addElement(paragraph);
        try {
            column.go(true);
        } catch (DocumentException e) {
            return 0;
        }
        return 10_000 - column.getYLine();
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
            case ExportBlock.BulletList b -> emitBulletList(document, writer, b);
            case ExportBlock.Table t -> document.add(table(t));
            case ExportBlock.Quadrant q -> document.add(keepCellsWhole(quadrant(q), document));
            case ExportBlock.Callout c -> document.add(callout(c));
            case ExportBlock.MetricGrid m -> document.add(metricGrid(m));
            case ExportBlock.AttributedList a -> emitAttributedList(document, writer, a);
            case ExportBlock.AttributedQuadrant a -> document.add(keepCellsWhole(attributedQuadrant(a), document));
            case ExportBlock.ColorLegend l -> document.add(colorLegend(l));
            case ExportBlock.Chart c -> emitChart(document, writer, c);
        }
    }

    /**
     * Le niveau 1 est le titre de partie d'un document redige (« I. CONTEXTE... ») : il ouvre une
     * page, en grand, souligne d'un filet, comme dans un PSD publie. Les intertitres ne portent plus
     * de marqueur de pagination : la note n'a plus de sommaire (revue du 22/09/2026) et le Plan
     * numerote le sien depuis ses propres titres de rubrique (cf. PdfExportService#PLAN_TAG).
     */
    private void emitHeading(Document document, PdfWriter writer, ExportBlock.Heading h) throws DocumentException {
        if (h.level() == 1) {
            document.newPage();
            Paragraph title = titled(h.text(), PdfFonts.font(18, Font.BOLD, PRIMARY));
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
        document.add(headingParagraph(h));
    }

    /** Intertitre de niveau 2 et plus, tel qu'il est pose et mesure (cf. keepWithTable). */
    private Paragraph headingParagraph(ExportBlock.Heading h) {
        Paragraph p;
        switch (h.level()) {
            case 2 -> {
                p = titled(h.text(), PdfFonts.font(13, Font.BOLD, PRIMARY));
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
        return p;
    }

    private void ensureSpace(Document document, PdfWriter writer) {
        if (writer != null && writer.getVerticalPosition(true) - document.bottom() < MIN_SPACE_UNDER_HEADING) {
            document.newPage();
        }
    }

    /** Titre de partie ou de sous-partie, a l'interligne serre d'un titre. */
    private Paragraph titled(String text, Font font) {
        Paragraph paragraph = new Paragraph(PdfFonts.phrase(text, font));
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

    private void emitBulletList(Document document, PdfWriter writer, ExportBlock.BulletList b) throws DocumentException {
        List<ExportBlock.Attribution> items = b.items().stream().map(ExportBlock.Attribution::new).toList();
        emitListTitle(document, writer, b.title(), items);
        emitItems(document, writer, items);
    }

    private void emitAttributedList(Document document, PdfWriter writer, ExportBlock.AttributedList a) throws DocumentException {
        emitListTitle(document, writer, a.title(), a.items());
        emitItems(document, writer, a.items());
    }

    /** Au-dela, une liste peut se partager entre deux pages ; en deca, elle reste avec son titre. */
    private static final int SHORT_LIST = 4;

    /**
     * Titre de liste (« Défis prioritaires »). Quand la position sur la page est connue, il ne reste
     * pas seul en bas de page : s'il n'y tient pas avec ses deux premieres puces (avec toutes, pour
     * une liste courte comme une mission), il passe avec elles a la page suivante.
     */
    private void emitListTitle(Document document, PdfWriter writer, String title, List<ExportBlock.Attribution> items)
            throws DocumentException {
        if (title != null && !title.isBlank()) {
            Paragraph p = new Paragraph(PdfFonts.phrase(title, PdfFonts.font(11, Font.BOLD, PRIMARY_DARK)));
            p.setSpacingBefore(8);
            p.setSpacingAfter(3);
            if (writer != null) {
                float width = document.right() - document.left();
                float needed = p.getSpacingBefore() + height(p, width) + p.getSpacingAfter();
                if (items.isEmpty()) {
                    needed += 20;
                }
                float atLeastTwo = needed;
                for (int i = 0; i < items.size(); i++) {
                    Paragraph item = item(items, i);
                    needed += height(item, width) + item.getSpacingAfter();
                    if (i < 2) {
                        atLeastTwo = needed;
                    }
                }
                if (items.size() > SHORT_LIST || needed > document.top() - document.bottom()) {
                    needed = atLeastTwo;
                }
                if (writer.getVerticalPosition(true) - document.bottom() < needed) {
                    document.newPage();
                }
            }
            document.add(p);
        }
    }

    /**
     * Mise en page unique des puces, attribuees ou non. La derniere puce ne commence pas seule une
     * page : quand elle n'y tient plus, l'avant-derniere passe avec elle a la page suivante.
     */
    private void emitItems(Document document, PdfWriter writer, List<ExportBlock.Attribution> items) throws DocumentException {
        if (items.isEmpty()) {
            document.add(paragraph(new ExportBlock.Paragraph("Aucun élément.", true, false)));
            return;
        }
        float width = document.right() - document.left();
        for (int i = 0; i < items.size(); i++) {
            Paragraph item = item(items, i);
            if (writer != null && i > 0 && i == items.size() - 2) {
                Paragraph last = item(items, i + 1);
                float own = height(item, width) + item.getSpacingAfter();
                float pair = own + height(last, width) + last.getSpacingAfter();
                float room = writer.getVerticalPosition(true) - document.bottom();
                if (room >= own && room < pair) {
                    document.newPage();
                }
            }
            document.add(item);
        }
    }

    /** La puce {@code i} d'une liste, telle qu'elle est posee et mesuree (cf. emitListTitle). */
    private Paragraph item(List<ExportBlock.Attribution> items, int i) {
        Paragraph p = bullet(items.get(i), 10);
        p.setIndentationLeft(22);
        p.setFirstLineIndent(-10);
        // L'espace sous la liste est porte par sa derniere puce : un paragraphe blanc a part
        // pouvait deborder seul sur une page, que le titre de partie suivant laissait vide.
        p.setSpacingAfter(i == items.size() - 1 ? 9 : 3);
        return p;
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
        // A partir de huit colonnes (effectifs, budget detaille, cadre de mesure de rendement, matrice
        // des risques), le corps de texte habituel ne tient plus « 1 188,7 » ni « environnementales »
        // sur une ligne et coupe les mots : le tableau se resserre.
        boolean dense = columns > 7;
        float fontSize = dense ? 7.5f : 8.5f;
        // Un tableau qui deborde sur la page suivante y reprend ses en-tetes : sans cela, la
        // seconde moitie d'un budget sur cinq exercices devient illisible.
        table.setHeaderRows((banded ? 1 : 0) + (headed ? 1 : 0));
        // Une ligne courte qui ne tient plus en bas de page passe entiere a la suivante : coupee,
        // elle laissait le nom d'une direction sur une page et ses chiffres sur l'autre. Seul un
        // tableau a lignes hautes (listes de constats) les commence sur la page en cours, pour ne
        // pas laisser un grand blanc au-dessus d'elles.
        table.setSplitLate(!hasTallRows(t));
        boolean givenWidths = t.widths() != null && t.widths().size() == columns;
        float[] widths = new float[columns];
        for (int i = 0; i < columns; i++) {
            widths[i] = givenWidths ? t.widths().get(i) : 1;
        }
        try {
            table.setWidths(fitLongestWords(t, widths, fontSize, dense));
        } catch (DocumentException ignored) {
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

    /** Largeur utile la plus etroite des documents (marges de la note de synthese) : un mot qui y tient tient partout. */
    private static final float NARROWEST_BODY = 595 - 2 * 48;

    /**
     * Largeurs de colonnes ou chaque mot tient sur sa ligne. OpenPDF coupe en plein milieu un mot plus large
     * que sa colonne (« d'autofinancemen / t ») : la colonne s'elargit de ce qui lui manque, pris aux
     * colonnes qui ont de la place de reste, au prorata de cette place.
     */
    private static float[] fitLongestWords(ExportBlock.Table t, float[] relative, float fontSize, boolean dense) {
        int columns = relative.length;
        float sum = 0;
        for (float w : relative) {
            sum += w;
        }
        float[] width = new float[columns];
        float[] needed = new float[columns];
        for (int c = 0; c < columns; c++) {
            width[c] = relative[c] / sum * NARROWEST_BODY;
        }
        if (t.showsHeaders()) {
            Font header = PdfFonts.font(fontSize, Font.BOLD, INK);
            for (int c = 0; c < columns && c < t.columnHeaders().size(); c++) {
                needed[c] = Math.max(needed[c], longestWord(t.columnHeaders().get(c), header));
            }
        }
        Font attributed = PdfFonts.font(8.5f, Font.NORMAL, INK);
        for (ExportBlock.TableRow row : t.rows()) {
            if (row.band()) {
                continue;
            }
            for (int c = 0; c < columns && c < row.cells().size(); c++) {
                ExportBlock.Cell cell = row.cells().get(c);
                if (cell.attributions().isEmpty()) {
                    Font font = PdfFonts.font(fontSize, cell.bold() || row.emphasized() ? Font.BOLD : Font.NORMAL, INK);
                    needed[c] = Math.max(needed[c], longestWord(cell.text(), font));
                } else {
                    float indent = cell.attributions().size() > 1 ? 9 : 0;
                    for (ExportBlock.Attribution item : cell.attributions()) {
                        needed[c] = Math.max(needed[c], indent + longestWord(item.text(), attributed));
                    }
                }
            }
        }
        // Marges interieures de la cellule, et un point de jeu pour l'arrondi des largeurs.
        float padding = (dense ? 4 : 10) + 1;
        float missing = 0;
        float spare = 0;
        for (int c = 0; c < columns; c++) {
            float need = needed[c] + padding;
            if (need > width[c]) {
                missing += need - width[c];
            } else {
                spare += width[c] - need;
            }
        }
        if (missing == 0 || spare == 0) {
            return relative;
        }
        float share = Math.min(missing, spare);
        float[] fitted = new float[columns];
        for (int c = 0; c < columns; c++) {
            float need = needed[c] + padding;
            fitted[c] = need > width[c]
                    ? width[c] + (need - width[c]) * share / missing
                    : width[c] - (width[c] - need) * share / spare;
        }
        return fitted;
    }

    /** Largeur du plus long fragment insecable, coupe la ou OpenPDF coupe : aux blancs et apres un tiret. */
    private static float longestWord(String text, Font font) {
        float longest = 0;
        for (String word : PdfFonts.typography(text == null ? "" : text).split("[\\x00-\\x20]+|(?<=[-‐])")) {
            longest = Math.max(longest, font.getBaseFont().getWidthPoint(word, font.getSize()));
        }
        return longest;
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
     * Legende d'attribution en grille de deux colonnes, sous un bandeau de titre : chaque direction dans sa case,
     * marquee a gauche d'un filet de sa couleur. Posee a la suite en une seule phrase, elle alignait mal pastilles et
     * noms, et ses lignes s'arretaient a des longueurs inegales. D'un seul tenant, elle ne se coupe pas entre deux pages.
     */
    private PdfPTable colorLegend(ExportBlock.ColorLegend legend) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setSpacingAfter(12);
        table.setKeepTogether(true);
        if (legend.title() != null && !legend.title().isBlank()) {
            PdfPCell title = textCell(legend.title(), PdfFonts.font(9.5f, Font.BOLD, PRIMARY_DARK), Element.ALIGN_LEFT, CALLOUT_BG);
            title.setColspan(2);
            title.setBorder(Rectangle.NO_BORDER);
            title.setPaddingTop(5);
            title.setPaddingBottom(8);
            title.setPaddingLeft(10);
            table.addCell(title);
        }
        List<ExportBlock.Attribution> entries = legend.entries();
        for (ExportBlock.Attribution entry : entries) {
            PdfPCell cell = textCell(entry.text(), PdfFonts.font(9, Font.NORMAL, INK), Element.ALIGN_LEFT, TILE_BG);
            cell.setUseVariableBorders(true);
            cell.setBorderWidthLeft(4f);
            cell.setBorderColorLeft(hexToColor(entry.colorHexes().isEmpty() ? "#64748B" : entry.colorHexes().get(0)));
            // Des filets blancs separent les cases, sur le blanc de la page.
            cell.setBorderWidthTop(2f);
            cell.setBorderWidthBottom(2f);
            cell.setBorderWidthRight(3f);
            cell.setBorderColorTop(Color.WHITE);
            cell.setBorderColorBottom(Color.WHITE);
            cell.setBorderColorRight(Color.WHITE);
            cell.setPaddingTop(4);
            cell.setPaddingBottom(7);
            cell.setPaddingLeft(9);
            table.addCell(cell);
        }
        if (entries.size() % 2 == 1) {
            PdfPCell filler = new PdfPCell(new Paragraph(" "));
            filler.setBorder(Rectangle.NO_BORDER);
            table.addCell(filler);
        }
        return table;
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
