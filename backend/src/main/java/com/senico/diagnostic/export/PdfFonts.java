package com.senico.diagnostic.export;

import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.FontSelector;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;

/**
 * Polices des exports PDF : Roboto, embarquee dans le document.
 *
 * <p>Les exports utilisaient les polices standard du PDF (Helvetica), limitees au jeu de
 * caracteres WinAnsi. Un intitule contenant un caractere hors de ce jeu (« → », « ■ ») etait
 * rendu tout entier dans une police de substitution : c'est ce qui faisait sortir
 * « VI. CADRE DE MISE EN ŒUVRE » en maigre au milieu de titres gras. Une police embarquee en
 * Identity-H couvre tout le francais et s'affiche a l'identique sur n'importe quel poste, y
 * compris le serveur Linux de production qui n'a pas les polices de Windows.</p>
 *
 * <p>Roboto est distribuee sous licence Apache 2.0 (cf. resources/fonts/LICENSE-Roboto.txt).
 * Elle n'a ni fleches ni formes geometriques : les rares symboles concernes passent par la
 * police ZapfDingbats en repli (cf. {@link #phrase}).</p>
 */
final class PdfFonts {

    private static final String DIRECTORY = "/fonts/";

    private static volatile BaseFont regular;
    private static volatile BaseFont bold;
    private static volatile BaseFont italic;
    private static volatile BaseFont boldItalic;
    private static volatile java.awt.Font awtRegular;
    private static volatile java.awt.Font awtBold;

    private PdfFonts() {
    }

    /**
     * Police Roboto de la graisse demandee. Le style est porte par la fonte elle-meme : laisser
     * OpenPDF simuler le gras sur la fonte normale epaissit le trait sans en changer le dessin.
     */
    static Font font(float size, int style, Color color) {
        boolean isBold = (style & Font.BOLD) != 0;
        boolean isItalic = (style & Font.ITALIC) != 0;
        BaseFont base = isBold && isItalic ? boldItalic() : isBold ? bold() : isItalic ? italic() : regular();
        int decorations = style & (Font.UNDERLINE | Font.STRIKETHRU);
        return new Font(base, size, decorations, color);
    }

    /**
     * Texte decoupe par police : Roboto pour tout ce qu'elle sait dessiner, ZapfDingbats puis
     * Symbol pour le reste (coche, fleche). Sans ce repli, ces caracteres disparaitraient
     * silencieusement du document.
     */
    static Phrase phrase(String text, Font font) {
        FontSelector selector = new FontSelector();
        selector.addFont(font);
        selector.addFont(new Font(Font.ZAPFDINGBATS, font.getSize(), Font.NORMAL, font.getColor()));
        selector.addFont(new Font(Font.SYMBOL, font.getSize(), Font.NORMAL, font.getColor()));
        Phrase phrase = selector.process(text == null ? "" : text);
        phrase.setLeading(font.getSize() * 1.35f);
        return phrase;
    }

    /** Meme police pour les graphiques rendus en image : le document garde une seule typographie. */
    static java.awt.Font awt(boolean isBold, float size) {
        if (isBold) {
            if (awtBold == null) {
                awtBold = loadAwt("Roboto-Bold.ttf");
            }
            return awtBold.deriveFont(size);
        }
        if (awtRegular == null) {
            awtRegular = loadAwt("Roboto-Regular.ttf");
        }
        return awtRegular.deriveFont(size);
    }

    private static BaseFont regular() {
        if (regular == null) {
            regular = load("Roboto-Regular.ttf");
        }
        return regular;
    }

    private static BaseFont bold() {
        if (bold == null) {
            bold = load("Roboto-Bold.ttf");
        }
        return bold;
    }

    private static BaseFont italic() {
        if (italic == null) {
            italic = load("Roboto-Italic.ttf");
        }
        return italic;
    }

    private static BaseFont boldItalic() {
        if (boldItalic == null) {
            boldItalic = load("Roboto-BoldItalic.ttf");
        }
        return boldItalic;
    }

    private static BaseFont load(String file) {
        try {
            return BaseFont.createFont(file, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes(file), null);
        } catch (Exception e) {
            throw new IllegalStateException("Police introuvable ou illisible : " + file, e);
        }
    }

    private static java.awt.Font loadAwt(String file) {
        try (InputStream in = PdfFonts.class.getResourceAsStream(DIRECTORY + file)) {
            if (in == null) {
                throw new IllegalStateException("Police introuvable sur le classpath : " + file);
            }
            return java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            throw new IllegalStateException("Police illisible : " + file, e);
        }
    }

    private static byte[] bytes(String file) throws IOException {
        try (InputStream in = PdfFonts.class.getResourceAsStream(DIRECTORY + file)) {
            if (in == null) {
                throw new IOException("Police introuvable sur le classpath : " + file);
            }
            return in.readAllBytes();
        }
    }
}
