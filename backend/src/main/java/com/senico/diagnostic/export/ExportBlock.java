package com.senico.diagnostic.export;

import java.util.List;

/**
 * Representation intermediaire, independante d'OpenPDF/POI, d'un contenu de section a exporter.
 * SectionExportRenderer produit une List&lt;ExportBlock&gt; par section ; PdfBlockEmitter et
 * WordBlockEmitter la traduisent chacun dans leur API de mise en page respective.
 */
public sealed interface ExportBlock {

    /**
     * Intertitre. Le niveau 1 ouvre une partie numerotee (« I. CONTEXTE ») et commence une
     * nouvelle page, comme dans un PSD publie ; les niveaux 1 et 2 alimentent le sommaire.
     */
    record Heading(String text, int level) implements ExportBlock {
        public Heading(String text) {
            this(text, 2);
        }
    }

    record Paragraph(String text, boolean italic, boolean bold) implements ExportBlock {
        public Paragraph(String text) {
            this(text, false, false);
        }
    }

    record KeyValue(String label, String value) {
    }

    record KeyValueList(String title, List<KeyValue> pairs, boolean boxed) implements ExportBlock {
    }

    record BulletList(String title, List<String> items) implements ExportBlock {
    }

    /**
     * Un element du document et les directions qui l'ont ecrit, par leur couleur.
     *
     * <p>Une seule couleur : le texte prend celle de la direction, et le lecteur sait d'un
     * coup d'oeil d'ou vient la phrase. Plusieurs : le constat est partage, aucune direction
     * ne peut se l'approprier — le texte reste neutre et porte une pastille par contributrice.
     * C'est precisement l'information qu'un document consolide doit rendre visible.</p>
     */
    record Attribution(String text, List<String> colorHexes) {
        public Attribution(String text) {
            this(text, List.of());
        }
    }

    /** Liste a puces dont chaque element porte la couleur de la direction qui l'a ecrit. */
    record AttributedList(String title, List<Attribution> items) implements ExportBlock {
    }

    /** Case d'un cadran : un titre, une ligne de lecture facultative (« À impliquer... »), les elements. */
    record AttributedQuadrantCell(String title, String caption, List<Attribution> items) {
        public AttributedQuadrantCell(String title, List<Attribution> items) {
            this(title, null, items);
        }
    }

    /**
     * Cadran de quatre cases (SWOT, matrice des parties prenantes, orientations croisees), chaque
     * element portant l'attribution de sa ou ses directions. Les teintes suivent l'ordre des cases ;
     * sans teintes, celles du SWOT (vert, orange, bleu, rouge).
     */
    record AttributedQuadrant(List<AttributedQuadrantCell> cells, List<Background> tints) implements ExportBlock {
        public AttributedQuadrant(List<AttributedQuadrantCell> cells) {
            this(cells, List.of());
        }
    }

    /** Legende d'attribution : une pastille de couleur par direction. */
    record ColorLegend(String title, List<Attribution> entries) implements ExportBlock {
    }

    /**
     * Cellule de tableau. Quand {@code attributions} n'est pas vide, la cellule rend ces
     * elements en puces coloriees par direction plutot que son {@code text} : c'est le meme
     * contenu, mais on voit qui l'a ecrit.
     *
     * <p>{@code rowSpan} fusionne la cellule avec celles du dessous, comme l'OS qui coiffe ses
     * actions dans le modele client. Les lignes qu'elle recouvre gardent a sa place une cellule
     * {@link #covered()}, que les emetteurs ne rendent pas : chaque ligne conserve ainsi autant de
     * cellules que de colonnes, et la position de chacune reste lisible.</p>
     */
    record Cell(String text, boolean bold, Align align, Background background,
                List<Attribution> attributions, int rowSpan) {
        public Cell(String text) {
            this(text, false, Align.LEFT, Background.NONE, List.of(), 1);
        }

        public Cell(String text, Background background) {
            this(text, false, Align.LEFT, background, List.of(), 1);
        }

        public Cell(String text, boolean bold, Align align, Background background) {
            this(text, bold, align, background, List.of(), 1);
        }

        public Cell(List<Attribution> attributions) {
            this("", false, Align.LEFT, Background.NONE, attributions, 1);
        }

        /** Place tenue par une cellule fusionnee d'une ligne superieure. */
        public static Cell covered() {
            return new Cell("", false, Align.LEFT, Background.NONE, List.of(), 0);
        }

        public boolean isCovered() {
            return rowSpan == 0;
        }

        public Cell spanning(int rows) {
            return new Cell(text, bold, align, background, attributions, Math.max(1, rows));
        }

        public Cell withBackground(Background tint) {
            return new Cell(text, bold, align, tint, attributions, rowSpan);
        }
    }

    /**
     * Ligne de tableau. Une ligne {@code band} est un intertitre qui occupe toute la largeur du
     * tableau (« AXE 1 : ... », « Hiérarchie ») : seule sa premiere cellule est rendue.
     */
    record TableRow(List<Cell> cells, boolean emphasized, Background rowBackground, boolean band) {
        public TableRow(List<Cell> cells) {
            this(cells, false, Background.NONE, false);
        }

        public TableRow(List<Cell> cells, boolean emphasized) {
            this(cells, emphasized, Background.NONE, false);
        }

        public TableRow(List<Cell> cells, boolean emphasized, Background rowBackground) {
            this(cells, emphasized, rowBackground, false);
        }

        public static TableRow band(String text, Background background) {
            return new TableRow(List.of(new Cell(text, true, Align.LEFT, Background.NONE)), true, background, true);
        }
    }

    /** Intitule qui coiffe {@code span} colonnes voisines (« 2027 » au-dessus de M, F, Total). */
    record HeaderBand(String label, int span) {
    }

    /**
     * Tableau. {@code widths} donne la largeur relative de chaque colonne ; vide, les colonnes
     * sont egales — ce qui convient a un tableau de chiffres, pas a un intitule suivi de montants.
     * {@code bands}, s'il n'est pas vide, ajoute au-dessus des en-tetes une ligne d'intitules
     * regroupant plusieurs colonnes ; la somme des {@code span} vaut le nombre de colonnes.
     * Des en-tetes tous vides fixent le nombre de colonnes sans ligne d'en-tete : c'est le cas
     * d'un tableau qui repete ses intitules sous chaque bandeau, comme la synthese du cadre
     * strategique du modele client.
     */
    record Table(List<String> columnHeaders, List<TableRow> rows, List<Integer> widths,
                 List<HeaderBand> bands) implements ExportBlock {
        public Table(List<String> columnHeaders, List<TableRow> rows) {
            this(columnHeaders, rows, List.of(), List.of());
        }

        public Table(List<String> columnHeaders, List<TableRow> rows, List<Integer> widths) {
            this(columnHeaders, rows, widths, List.of());
        }

        public boolean showsHeaders() {
            return columnHeaders.stream().anyMatch(header -> header != null && !header.isBlank());
        }
    }

    /**
     * Encadre de lecture : la phrase qu'on attend apres un tableau chiffre (« Analyse : ... »),
     * ou l'avertissement qui explique un document vide. En simple paragraphe italique, cette
     * phrase se noyait dans le corps du texte ; l'encadre la detache, comme dans un PSD publie.
     */
    record Callout(String text, Tone tone) implements ExportBlock {
        public Callout(String text) {
            this(text, Tone.ANALYSIS);
        }
    }

    /** Un chiffre cle et son intitule, rendus en tuile plutot qu'en ligne de tableau. */
    record Metric(String label, String value) {
    }

    /**
     * Les chiffres cles en tuiles sur deux colonnes : un comite lit « 28 820 000 000 FCFA »
     * d'un coup d'oeil, la ou une colonne « Valeur » oblige a suivre la ligne jusqu'au bout.
     */
    record MetricGrid(List<Metric> metrics) implements ExportBlock {
    }

    record QuadrantCell(String title, List<String> items) {
    }

    record Quadrant(List<QuadrantCell> cells) implements ExportBlock {
    }

    /** Une serie d'un graphique : son nom (legende), sa couleur, une valeur par categorie. */
    record ChartSeries(String name, String colorHex, List<Double> values) {
    }

    /**
     * Graphique, rendu en image par {@link ChartImageRenderer} pour les deux formats. Il accompagne
     * toujours le tableau des memes chiffres : le graphique donne la forme, le tableau les valeurs.
     */
    record Chart(ChartKind kind, String title, String subtitle, List<String> categories,
                 List<ChartSeries> series) implements ExportBlock {
    }

    /**
     * STACKED_COLUMNS : une colonne par categorie, empilee par serie (budget par exercice et par axe).
     * STACKED_BAR : une seule barre horizontale partagee entre les series (part couverte d'un total).
     */
    enum ChartKind { STACKED_COLUMNS, STACKED_BAR }

    enum Align { LEFT, CENTER, RIGHT }

    /** ANALYSIS = lecture d'un tableau chiffre ; WARNING = ce qu'il faut savoir avant de lire. */
    enum Tone { ANALYSIS, WARNING }

    /** PRIMARY_DARK est un fond fonce : le texte pose dessus passe en blanc (bandeau d'axe). */
    enum Background { NONE, RED, ORANGE, BLUE, GREEN, GREY, PRIMARY_LIGHT, VIOLET, PRIMARY_DARK }
}
