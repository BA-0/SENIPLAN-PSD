package com.senico.diagnostic.export;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Rend un {@link ExportBlock.Chart} en image PNG, inseree telle quelle dans le PDF et le Word :
 * les deux formats montrent ainsi exactement le meme graphique.
 *
 * <p>Mise en forme sobre, a la maniere d'un rapport publie : traits fins, quadrillage discret,
 * segments empiles separes par un liseré blanc plutot que par un contour, extremite arrondie du
 * cote de la valeur, legende toujours presente et etiquettes limitees au total de chaque colonne.
 * Le texte reste dans les encres neutres ; seule la marque porte la couleur de la serie.</p>
 */
final class ChartImageRenderer {

    /** Resolution de rendu : l'image reste nette une fois reduite a la largeur de la page. */
    private static final float SCALE = 2.5f;
    private static final int WIDTH = 860;

    private static final Color SURFACE = Color.WHITE;
    private static final Color INK = new Color(0x1F, 0x29, 0x37);
    private static final Color INK_SECONDARY = new Color(0x52, 0x51, 0x4E);
    private static final Color GRID = new Color(0xE1, 0xE0, 0xD9);
    private static final Color BASELINE = new Color(0xC3, 0xC2, 0xB7);

    private ChartImageRenderer() {
    }

    static byte[] render(ExportBlock.Chart chart) {
        return switch (chart.kind()) {
            case STACKED_COLUMNS -> stackedColumns(chart);
            case STACKED_BAR -> stackedBar(chart);
        };
    }

    // ---- Colonnes empilees ----

    private static byte[] stackedColumns(ExportBlock.Chart chart) {
        int height = 400;
        Canvas canvas = new Canvas(WIDTH, height);
        Graphics2D g = canvas.g;
        int top = drawTitle(g, chart);

        int legendHeight = legendHeight(g, chart.series(), WIDTH - 40);
        int left = 70;
        int right = WIDTH - 20;
        int bottom = height - 34 - legendHeight;
        int plotTop = top + 24;

        List<Double> totals = new ArrayList<>();
        for (int c = 0; c < chart.categories().size(); c++) {
            double sum = 0;
            for (ExportBlock.ChartSeries series : chart.series()) {
                sum += value(series, c);
            }
            totals.add(sum);
        }
        double max = totals.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double step = niceStep(max / 4);
        double axisMax = step * Math.max(1, Math.ceil(max / step));

        // Quadrillage et graduations en millions : c'est l'unite dans laquelle se lit un budget.
        g.setFont(PdfFonts.awt(false, 11));
        FontMetrics metrics = g.getFontMetrics();
        for (double tick = 0; tick <= axisMax + step / 2; tick += step) {
            int y = (int) Math.round(bottom - (tick / axisMax) * (bottom - plotTop));
            g.setColor(tick == 0 ? BASELINE : GRID);
            g.setStroke(new BasicStroke(1f));
            g.drawLine(left, y, right, y);
            String label = JsonUtil.formatNumber(tick / 1_000_000d);
            g.setColor(INK_SECONDARY);
            g.drawString(label, left - 10 - metrics.stringWidth(label), y + metrics.getAscent() / 2 - 1);
        }

        int count = chart.categories().size();
        double slot = (right - left) / (double) Math.max(1, count);
        int barWidth = (int) Math.min(72, slot * 0.46);
        for (int c = 0; c < count; c++) {
            int x = (int) Math.round(left + slot * c + (slot - barWidth) / 2);
            double base = 0;
            int lastVisible = -1;
            for (int s = 0; s < chart.series().size(); s++) {
                if (value(chart.series().get(s), c) > 0) {
                    lastVisible = s;
                }
            }
            for (int s = 0; s < chart.series().size(); s++) {
                ExportBlock.ChartSeries series = chart.series().get(s);
                double v = value(series, c);
                if (v <= 0) {
                    continue;
                }
                int y0 = (int) Math.round(bottom - (base / axisMax) * (bottom - plotTop));
                int y1 = (int) Math.round(bottom - ((base + v) / axisMax) * (bottom - plotTop));
                // Liseré blanc de 2 px entre deux segments : c'est l'espace, pas un trait, qui separe.
                int gap = base > 0 ? 2 : 0;
                int segmentHeight = Math.max(1, y0 - y1 - gap);
                g.setColor(color(series.colorHex()));
                if (s == lastVisible) {
                    g.fill(topRounded(x, y1, barWidth, segmentHeight, 4));
                } else {
                    g.fillRect(x, y1, barWidth, segmentHeight);
                }
                base += v;
            }

            // Une seule etiquette par colonne, le total, posee au-dessus : jamais un nombre par segment.
            String total = JsonUtil.formatNumber(totals.get(c) / 1_000_000d);
            g.setFont(PdfFonts.awt(true, 11));
            FontMetrics bold = g.getFontMetrics();
            int yTop = (int) Math.round(bottom - (totals.get(c) / axisMax) * (bottom - plotTop));
            g.setColor(INK);
            g.drawString(total, x + (barWidth - bold.stringWidth(total)) / 2, yTop - 6);

            g.setFont(PdfFonts.awt(false, 12));
            FontMetrics regular = g.getFontMetrics();
            String category = chart.categories().get(c);
            g.setColor(INK_SECONDARY);
            g.drawString(category, x + (barWidth - regular.stringWidth(category)) / 2, bottom + 18);
        }

        drawLegend(g, chart.series(), 20, height - legendHeight - 6, WIDTH - 40);
        return canvas.png();
    }

    // ---- Barre horizontale unique ----

    private static byte[] stackedBar(ExportBlock.Chart chart) {
        Canvas probe = new Canvas(WIDTH, 10);
        int legendHeight = legendHeight(probe.g, chart.series(), WIDTH - 40);
        int height = 150 + legendHeight;

        Canvas canvas = new Canvas(WIDTH, height);
        Graphics2D g = canvas.g;
        int top = drawTitle(g, chart);

        double total = chart.series().stream().mapToDouble(series -> value(series, 0)).sum();
        int left = 20;
        int right = WIDTH - 20;
        int barTop = top + 26;
        int barHeight = 34;
        double x = left;
        int visible = (int) chart.series().stream().filter(series -> value(series, 0) > 0).count();
        int drawn = 0;
        for (ExportBlock.ChartSeries series : chart.series()) {
            double v = value(series, 0);
            if (v <= 0 || total <= 0) {
                continue;
            }
            double w = (right - left) * v / total;
            int gap = drawn < visible - 1 ? 2 : 0;
            g.setColor(color(series.colorHex()));
            boolean first = drawn == 0;
            boolean last = drawn == visible - 1;
            g.fill(sideRounded((int) Math.round(x), barTop, (int) Math.max(1, Math.round(w) - gap), barHeight, 4, first, last));
            x += w;
            drawn++;
        }

        drawLegend(g, chart.series(), 20, barTop + barHeight + 22, WIDTH - 40);
        return canvas.png();
    }

    // ---- Elements communs ----

    private static int drawTitle(Graphics2D g, ExportBlock.Chart chart) {
        int y = 26;
        g.setColor(INK);
        g.setFont(PdfFonts.awt(true, 15));
        g.drawString(chart.title() == null ? "" : chart.title(), 20, y);
        if (chart.subtitle() != null && !chart.subtitle().isBlank()) {
            y += 20;
            g.setColor(INK_SECONDARY);
            g.setFont(PdfFonts.awt(false, 12));
            g.drawString(chart.subtitle(), 20, y);
        }
        return y;
    }

    private static int legendHeight(Graphics2D g, List<ExportBlock.ChartSeries> series, int width) {
        return legendRows(g, series, width).size() * 22;
    }

    /** Repartit les entrees de legende en lignes qui tiennent dans la largeur disponible. */
    private static List<List<ExportBlock.ChartSeries>> legendRows(Graphics2D g, List<ExportBlock.ChartSeries> series, int width) {
        g.setFont(PdfFonts.awt(false, 12));
        FontMetrics metrics = g.getFontMetrics();
        List<List<ExportBlock.ChartSeries>> rows = new ArrayList<>();
        List<ExportBlock.ChartSeries> current = new ArrayList<>();
        int used = 0;
        for (ExportBlock.ChartSeries entry : series) {
            int w = 18 + metrics.stringWidth(entry.name()) + 22;
            if (!current.isEmpty() && used + w > width) {
                rows.add(current);
                current = new ArrayList<>();
                used = 0;
            }
            current.add(entry);
            used += w;
        }
        if (!current.isEmpty()) {
            rows.add(current);
        }
        return rows;
    }

    private static void drawLegend(Graphics2D g, List<ExportBlock.ChartSeries> series, int x0, int y0, int width) {
        g.setFont(PdfFonts.awt(false, 12));
        FontMetrics metrics = g.getFontMetrics();
        int y = y0;
        for (List<ExportBlock.ChartSeries> row : legendRows(g, series, width)) {
            int x = x0;
            for (ExportBlock.ChartSeries entry : row) {
                g.setColor(color(entry.colorHex()));
                g.fill(new RoundRectangle2D.Float(x, y + 4, 11, 11, 3, 3));
                g.setColor(INK_SECONDARY);
                g.drawString(entry.name(), x + 18, y + 14);
                x += 18 + metrics.stringWidth(entry.name()) + 22;
            }
            y += 22;
        }
    }

    private static Path2D topRounded(int x, int y, int w, int h, int r) {
        int radius = Math.min(r, Math.min(w / 2, h));
        Path2D path = new Path2D.Float();
        path.moveTo(x, y + h);
        path.lineTo(x, y + radius);
        path.quadTo(x, y, x + radius, y);
        path.lineTo(x + w - radius, y);
        path.quadTo(x + w, y, x + w, y + radius);
        path.lineTo(x + w, y + h);
        path.closePath();
        return path;
    }

    private static Path2D sideRounded(int x, int y, int w, int h, int r, boolean roundLeft, boolean roundRight) {
        int radius = Math.min(r, Math.min(w / 2, h / 2));
        int rl = roundLeft ? radius : 0;
        int rr = roundRight ? radius : 0;
        Path2D path = new Path2D.Float();
        path.moveTo(x + rl, y);
        path.lineTo(x + w - rr, y);
        path.quadTo(x + w, y, x + w, y + rr);
        path.lineTo(x + w, y + h - rr);
        path.quadTo(x + w, y + h, x + w - rr, y + h);
        path.lineTo(x + rl, y + h);
        path.quadTo(x, y + h, x, y + h - rl);
        path.lineTo(x, y + rl);
        path.quadTo(x, y, x + rl, y);
        path.closePath();
        return path;
    }

    private static double value(ExportBlock.ChartSeries series, int index) {
        if (index >= series.values().size() || series.values().get(index) == null) {
            return 0;
        }
        return Math.max(0, series.values().get(index));
    }

    /** Pas « rond » (1, 2, 2,5 ou 5 fois une puissance de dix) : des graduations qu'on lit sans calcul. */
    static double niceStep(double raw) {
        if (raw <= 0) {
            return 1_000_000;
        }
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double normalized = raw / magnitude;
        double nice = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 2.5 ? 2.5 : normalized <= 5 ? 5 : 10;
        return nice * magnitude;
    }

    private static Color color(String hex) {
        if (hex == null || !hex.matches("#[0-9A-Fa-f]{6}")) {
            return BASELINE;
        }
        return new Color(Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16));
    }

    /** Surface de dessin en haute resolution ; les coordonnees restent exprimees en pixels logiques. */
    private static final class Canvas {
        private final BufferedImage image;
        private final Graphics2D g;

        private Canvas(int width, int height) {
            image = new BufferedImage(Math.round(width * SCALE), Math.round(height * SCALE), BufferedImage.TYPE_INT_RGB);
            g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(SURFACE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.scale(SCALE, SCALE);
        }

        private byte[] png() {
            g.dispose();
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", out);
                return out.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException("Rendu du graphique impossible", e);
            }
        }
    }
}
