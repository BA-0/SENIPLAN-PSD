package com.senico.diagnostic.export;

import java.util.List;

/**
 * Representation intermediaire, independante d'OpenPDF/POI, d'un contenu de section a exporter.
 * SectionExportRenderer produit une List&lt;ExportBlock&gt; par section ; PdfBlockEmitter et
 * WordBlockEmitter la traduisent chacun dans leur API de mise en page respective.
 */
public sealed interface ExportBlock {

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

    record Cell(String text, boolean bold, Align align, Background background) {
        public Cell(String text) {
            this(text, false, Align.LEFT, Background.NONE);
        }

        public Cell(String text, Background background) {
            this(text, false, Align.LEFT, background);
        }
    }

    record TableRow(List<Cell> cells, boolean emphasized, Background rowBackground) {
        public TableRow(List<Cell> cells) {
            this(cells, false, Background.NONE);
        }

        public TableRow(List<Cell> cells, boolean emphasized) {
            this(cells, emphasized, Background.NONE);
        }
    }

    record Table(List<String> columnHeaders, List<TableRow> rows) implements ExportBlock {
    }

    record QuadrantCell(String title, List<String> items) {
    }

    record Quadrant(List<QuadrantCell> cells) implements ExportBlock {
    }

    enum Align { LEFT, CENTER, RIGHT }

    enum Background { NONE, RED, ORANGE, GREEN, GREY, PRIMARY_LIGHT }
}
