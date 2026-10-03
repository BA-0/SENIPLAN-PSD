package com.senico.diagnostic.export;

import java.util.ArrayList;
import java.util.List;

/**
 * Fusion a trois voies de la note de synthese corrigee.
 *
 * <p>Deux situations y conduisent. Une section approuvee apres une correction doit entrer dans la
 * note sans effacer les corrections deja faites. Et plusieurs personnes corrigent la note en meme
 * temps, depuis des postes differents et souvent le meme compte : chacune doit retrouver ses
 * corrections, et celles des autres, apres avoir enregistre.</p>
 *
 * <p>On dispose de trois versions : celle dont on est parti ({@code base}), la sienne
 * ({@code ours}) et celle qui s'est imposee entre-temps ({@code theirs}). Ce qu'un seul cote a
 * change est repris ; ce que les deux ont change differemment est un conflit, tranche en faveur du
 * cote {@code winner}. La fusion descend jusqu'aux lignes et aux cellules des tableaux : deux
 * personnes qui corrigent deux cellules du meme tableau gardent toutes deux leur correction.</p>
 */
final class SynthesisNoteMerge {

    /** Cote retenu quand les deux ont modifie differemment le meme element. */
    enum Winner { OURS, THEIRS }

    /** @param conflicts nombre d'elements modifies des deux cotes, tranches en faveur du gagnant */
    record Result(List<ExportBlock> blocks, int conflicts) {
    }

    @FunctionalInterface
    private interface Merger<T> {
        /** Appele quand les deux cotes ont modifie l'element, differemment. */
        T merge(T base, T ours, T theirs, Context ctx);
    }

    private static final class Context {
        private final Winner winner;
        private int conflicts;

        private Context(Winner winner) {
            this.winner = winner;
        }

        private <T> T conflict(T ours, T theirs) {
            conflicts++;
            return winner == Winner.OURS ? ours : theirs;
        }
    }

    private SynthesisNoteMerge() {
    }

    static Result merge(List<ExportBlock> base, List<ExportBlock> ours, List<ExportBlock> theirs, Winner winner) {
        Context ctx = new Context(winner);
        List<ExportBlock> blocks = mergeLists(base, ours, theirs, SynthesisNoteMerge::mergeBlock, ctx);
        return new Result(blocks, ctx.conflicts);
    }

    private static ExportBlock mergeBlock(ExportBlock base, ExportBlock ours, ExportBlock theirs, Context ctx) {
        if (base instanceof ExportBlock.Table b && ours instanceof ExportBlock.Table o && theirs instanceof ExportBlock.Table t) {
            return new ExportBlock.Table(
                    mergeSameSize(b.columnHeaders(), o.columnHeaders(), t.columnHeaders(), ctx),
                    mergeLists(b.rows(), o.rows(), t.rows(), SynthesisNoteMerge::mergeRow, ctx),
                    merge3(b.widths(), o.widths(), t.widths(), (x, y, z, c) -> c.conflict(y, z), ctx),
                    mergeSameSize(b.bands(), o.bands(), t.bands(), ctx));
        }
        return ctx.conflict(ours, theirs);
    }

    private static ExportBlock.TableRow mergeRow(ExportBlock.TableRow base, ExportBlock.TableRow ours,
                                                 ExportBlock.TableRow theirs, Context ctx) {
        if (base.band() != ours.band() || base.band() != theirs.band()) {
            return ctx.conflict(ours, theirs);
        }
        ExportBlock.TableRow layout = ctx.winner == Winner.OURS ? ours : theirs;
        return new ExportBlock.TableRow(mergeSameSize(base.cells(), ours.cells(), theirs.cells(), ctx),
                layout.emphasized(), layout.rowBackground(), layout.band());
    }

    /** Listes de meme longueur fusionnees element par element ; sinon, une seule modification ou un conflit. */
    private static <T> List<T> mergeSameSize(List<T> base, List<T> ours, List<T> theirs, Context ctx) {
        if (base.size() == ours.size() && base.size() == theirs.size()) {
            List<T> merged = new ArrayList<>(base.size());
            for (int k = 0; k < base.size(); k++) {
                merged.add(merge3(base.get(k), ours.get(k), theirs.get(k), (x, y, z, c) -> c.conflict(y, z), ctx));
            }
            return merged;
        }
        return merge3(base, ours, theirs, (x, y, z, c) -> c.conflict(y, z), ctx);
    }

    private static <T> T merge3(T base, T ours, T theirs, Merger<T> merger, Context ctx) {
        if (ours.equals(base)) {
            return theirs;
        }
        if (theirs.equals(base) || ours.equals(theirs)) {
            return ours;
        }
        return merger.merge(base, ours, theirs, ctx);
    }

    /**
     * Fusion de deux listes derivees d'une meme liste (diff3) : les elements que ni l'un ni l'autre
     * n'a touches servent d'ancres ; entre deux ancres, ce qu'un seul cote a change est repris.
     */
    private static <T> List<T> mergeLists(List<T> base, List<T> ours, List<T> theirs, Merger<T> merger, Context ctx) {
        if (ours.equals(base)) {
            return theirs;
        }
        if (theirs.equals(base) || ours.equals(theirs)) {
            return ours;
        }
        int[] toOurs = matches(base, ours);
        int[] toTheirs = matches(base, theirs);
        List<T> merged = new ArrayList<>(Math.max(ours.size(), theirs.size()));
        int i0 = 0;
        int o0 = 0;
        int t0 = 0;
        for (int i = 0; i <= base.size(); i++) {
            boolean end = i == base.size();
            if (!end && (toOurs[i] < 0 || toTheirs[i] < 0)) {
                continue;
            }
            int oEnd = end ? ours.size() : toOurs[i];
            int tEnd = end ? theirs.size() : toTheirs[i];
            mergeChunk(base.subList(i0, i), ours.subList(o0, oEnd), theirs.subList(t0, tEnd), merger, ctx, merged);
            if (!end) {
                merged.add(ours.get(oEnd));
                i0 = i + 1;
                o0 = oEnd + 1;
                t0 = tEnd + 1;
            }
        }
        return merged;
    }

    private static <T> void mergeChunk(List<T> base, List<T> ours, List<T> theirs, Merger<T> merger, Context ctx,
                                       List<T> out) {
        if (ours.equals(base)) {
            out.addAll(theirs);
        } else if (theirs.equals(base) || ours.equals(theirs)) {
            out.addAll(ours);
        } else if (base.size() == ours.size()) {
            alongEdited(base, ours, theirs, false, merger, ctx, out);
        } else if (base.size() == theirs.size()) {
            alongEdited(base, theirs, ours, true, merger, ctx, out);
        } else {
            out.addAll(ctx.conflict(ours, theirs));
        }
    }

    /**
     * Un cote ({@code edited}) a corrige les elements sur place, l'autre ({@code other}) en a ajoute,
     * retire ou modifie : on suit {@code other}, en reprenant les corrections sur place de
     * {@code edited} partout ou {@code other} a garde l'element, ou l'a modifie sans changer le compte.
     */
    private static <T> void alongEdited(List<T> base, List<T> edited, List<T> other, boolean editedIsTheirs,
                                        Merger<T> merger, Context ctx, List<T> out) {
        int[] toOther = matches(base, other);
        int b0 = 0;
        int x0 = 0;
        for (int k = 0; k <= base.size(); k++) {
            boolean end = k == base.size();
            if (!end && toOther[k] < 0) {
                continue;
            }
            int xEnd = end ? other.size() : toOther[k];
            if (xEnd - x0 == k - b0) {
                for (int g = 0; g < k - b0; g++) {
                    T b = base.get(b0 + g);
                    T e = edited.get(b0 + g);
                    T x = other.get(x0 + g);
                    out.add(editedIsTheirs ? merge3(b, x, e, merger, ctx) : merge3(b, e, x, merger, ctx));
                }
            } else {
                // {@code other} a ajoute ou retire des elements dans ce passage et modifie les autres :
                // on retrouve chacun par ressemblance (meme ligne de tableau), pour y reprendre les
                // corrections de {@code edited} au lieu de les perdre.
                int[] pairs = similar(base.subList(b0, k), other.subList(x0, xEnd));
                int next = 0;
                for (int g = 0; g < k - b0; g++) {
                    T b = base.get(b0 + g);
                    T e = edited.get(b0 + g);
                    if (pairs[g] < 0) {
                        if (!e.equals(b)) {
                            ctx.conflict(null, null);
                        }
                        continue;
                    }
                    while (next < pairs[g]) {
                        out.add(other.get(x0 + next++));
                    }
                    T x = other.get(x0 + pairs[g]);
                    out.add(editedIsTheirs ? merge3(b, x, e, merger, ctx) : merge3(b, e, x, merger, ctx));
                    next = pairs[g] + 1;
                }
                while (next < xEnd - x0) {
                    out.add(other.get(x0 + next++));
                }
            }
            if (!end) {
                out.add(edited.get(k));
                b0 = k + 1;
                x0 = xEnd + 1;
            }
        }
    }

    /**
     * Pour chaque element de {@code base}, l'element de {@code other} qui lui ressemble le plus, dans
     * l'ordre, ou -1. Seules les lignes de tableau se ressemblent : meme nombre de cellules, et les deux
     * premieres (extrant, activite) identiques ou la moitie des cellules au moins.
     */
    private static <T> int[] similar(List<T> base, List<T> other) {
        int[] pairs = new int[base.size()];
        java.util.Arrays.fill(pairs, -1);
        int from = 0;
        for (int g = 0; g < base.size(); g++) {
            int best = -1;
            int bestScore = 0;
            for (int j = from; j < other.size(); j++) {
                int score = resemblance(base.get(g), other.get(j));
                if (score > bestScore) {
                    best = j;
                    bestScore = score;
                }
            }
            if (best >= 0) {
                pairs[g] = best;
                from = best + 1;
            }
        }
        return pairs;
    }

    /** Nombre de cellules identiques de deux lignes de tableau comparables, 0 si elles ne se ressemblent pas. */
    private static int resemblance(Object a, Object b) {
        if (!(a instanceof ExportBlock.TableRow x) || !(b instanceof ExportBlock.TableRow y)
                || x.band() || y.band() || x.cells().size() != y.cells().size()) {
            return 0;
        }
        int same = 0;
        for (int c = 0; c < x.cells().size(); c++) {
            if (x.cells().get(c).equals(y.cells().get(c))) {
                same++;
            }
        }
        boolean sameKey = x.cells().size() >= 2
                && x.cells().get(0).equals(y.cells().get(0)) && x.cells().get(1).equals(y.cells().get(1))
                && !x.cells().get(1).text().isBlank();
        return sameKey || 2 * same >= x.cells().size() ? same : 0;
    }

    /** Pour chaque element de {@code base}, sa place dans {@code other} (plus longue sous-suite commune), ou -1. */
    private static <T> int[] matches(List<T> base, List<T> other) {
        int n = base.size();
        int m = other.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = base.get(i).equals(other.get(j))
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        int[] match = new int[n];
        java.util.Arrays.fill(match, -1);
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (base.get(i).equals(other.get(j))) {
                match[i++] = j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        return match;
    }
}
