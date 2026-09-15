package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.NarrativeBlockKey;
import com.senico.diagnostic.domain.SectionDef;
import com.senico.diagnostic.domain.SectionResponse;
import com.senico.diagnostic.domain.SectionStatus;
import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.validation.DefaultSectionContentFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Compose la "Note de synthese" : le Plan Strategique de Developpement de SENICO presente sur le
 * plan d'un PSD publie, a partir des contributions des directions et des textes arretes par la
 * Direction Generale.
 *
 * <p>Le plan suit celui d'un PSD de reference (sigles, mot du DG, contexte, approche
 * methodologique, presentation, parties prenantes, diagnostic, bilan, enjeux, facteurs cles,
 * cadre strategique, mise en oeuvre, pilotage, synthese du cadre strategique, conclusion,
 * annexes). Chaque tableau chiffre est suivi de la lecture qu'on en attend (« Analyse : ... »)
 * et les budgets sont accompagnes d'un graphique.</p>
 *
 * <p>Deux sources, deux voix. Les directions apportent le diagnostic et la programmation (SWOT,
 * PESTEL, risques, budget, indicateurs...) : ces constats restent attribues par la couleur de la
 * direction qui les a ecrits. La Direction Generale arrete ce qu'un PSD n'affiche qu'une fois pour
 * toute l'entreprise — mot du DG, vision, mission, valeurs, axes strategiques, dispositif de
 * pilotage — sous forme de blocs narratifs. Tant qu'un de ces blocs n'est pas redige, la note le
 * signale a l'endroit concerne plutot que d'aligner les vingt visions des directions.</p>
 *
 * <p>La note ne tronque aucune liste : elle resume en fusionnant ce que les directions disent en
 * commun, pas en coupant les elements cites.</p>
 *
 * <p>Perimetre : les sections approuvees par le DG. La note sert de base au Conseil
 * d'Administration et au comite de pilotage : elle n'a pas a s'appuyer sur des contributions que
 * la Direction Generale n'a pas encore arbitrees, ni a annoncer des chiffres absents des documents
 * consolides.</p>
 */
@Component
@RequiredArgsConstructor
class PsdBriefBuilder {

    static final String SIGLES = "SIGLES ET ABRÉVIATIONS";
    static final String MOT_DU_DG = "MOT DU DIRECTEUR GÉNÉRAL";
    static final String ESSENTIEL = "L'ESSENTIEL DU PLAN";
    static final String CONTEXTE = "I. CONTEXTE ET JUSTIFICATION";
    static final String METHODE = "II. APPROCHE MÉTHODOLOGIQUE";
    static final String PRESENTATION = "III. PRÉSENTATION DE SENICO";
    static final String PARTIES_PRENANTES = "IV. ANALYSE DES PARTIES PRENANTES";
    // Revue client du 15/09/2026 : le bilan des performances passees precede le diagnostic.
    static final String BILAN = "V. BILAN DES PERFORMANCES DES ANNÉES PRÉCÉDENTES";
    static final String DIAGNOSTIC = "VI. DIAGNOSTIC STRATÉGIQUE";
    static final String ENJEUX = "VII. PRINCIPAUX ENJEUX ET DÉFIS";
    static final String FACTEURS = "VIII. FACTEURS CLÉS DE RÉUSSITE ET D'ÉCHEC";
    static final String CADRE = "IX. CADRE STRATÉGIQUE";
    static final String MISE_EN_OEUVRE = "X. CADRE DE MISE EN ŒUVRE";
    static final String PILOTAGE = "XI. CADRE DE PILOTAGE ET DE SUIVI-ÉVALUATION";
    static final String SYNTHESE = "XII. SYNTHÈSE DU CADRE STRATÉGIQUE";
    static final String CONCLUSION = "XIII. CONCLUSION";
    static final String ANNEXES = "ANNEXES";

    /** Intitules des parties, dans l'ordre. Le sommaire des exports reprend en outre les sous-parties. */
    static List<String> partTitles() {
        return List.of(SIGLES, MOT_DU_DG, ESSENTIEL, CONTEXTE, METHODE, PRESENTATION, PARTIES_PRENANTES,
                BILAN, DIAGNOSTIC, ENJEUX, FACTEURS, CADRE, MISE_EN_OEUVRE, PILOTAGE, SYNTHESE, CONCLUSION, ANNEXES);
    }

    /**
     * Couleurs des axes de l'entreprise dans les graphiques, dans cet ordre et jamais recyclees :
     * ordre valide pour la distinction des teintes adjacentes, y compris pour les daltoniens.
     * Un axe ne prend jamais la couleur d'une direction : ce n'est pas la meme chose.
     */
    private static final List<String> AXIS_COLORS =
            List.of("#2A78D6", "#EB6834", "#1BAF7A", "#EDA100", "#E87BA4", "#008300", "#4A3AA7", "#E34948");
    private static final String UNLINKED_COLOR = "#A8A29E";
    private static final String COVERED_COLOR = "#2D7A45";
    private static final String GAP_COLOR = "#D6D3CB";

    private static final String[] YEARS = SectionLabels.YEARS;
    private static final String FIRST_YEAR = YEARS[0];
    private static final String LAST_YEAR = YEARS[YEARS.length - 1];
    private static final String PERIOD = FIRST_YEAR + "-" + LAST_YEAR;
    private static final String EDIT_SCREEN = "l'écran « Plan Stratégique de SENICO »";
    private static final String UNLINKED_AXES = "AXES NON RATTACHÉS";
    /** Exercice dont les directions ont analyse les performances (S01B). */
    private static final String REVIEW_YEAR = "2026";
    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");

    private final ExportContentReader exportContentReader;

    List<ExportBlock> build(List<WorkGroup> groups, Map<String, SectionDef> sectionsByCode,
                            Map<String, SectionResponse> responsesByKey,
                            Map<String, GroupSectionStatus> statusesByKey,
                            Map<NarrativeBlockKey, String> narratives) {
        return build(groups, sectionsByCode, responsesByKey, statusesByKey, narratives, LocalDate.now(DAKAR));
    }

    /**
     * @param today date de la note : tant que l'exercice analyse n'est pas clos, ses realisations
     *              sont des estimations et la note les presente comme telles
     */
    List<ExportBlock> build(List<WorkGroup> groups, Map<String, SectionDef> sectionsByCode,
                            Map<String, SectionResponse> responsesByKey,
                            Map<String, GroupSectionStatus> statusesByKey,
                            Map<NarrativeBlockKey, String> narratives, LocalDate today) {
        Context ctx = new Context(groups, sectionsByCode, responsesByKey, statusesByKey, narratives, today);
        List<ExportBlock> b = new ArrayList<>();

        part(b, SIGLES);
        b.add(acronyms());

        part(b, MOT_DU_DG);
        narrative(b, ctx, NarrativeBlockKey.MOT_DU_DG);

        part(b, ESSENTIEL);
        b.addAll(essentials(ctx));

        part(b, CONTEXTE);
        b.addAll(context(ctx));

        part(b, METHODE);
        narrative(b, ctx, NarrativeBlockKey.APPROCHE_METHODOLOGIQUE);

        part(b, PRESENTATION);
        sub(b, "III.1 Historique");
        narrative(b, ctx, NarrativeBlockKey.HISTORIQUE);
        sub(b, "III.2 Missions");
        narrative(b, ctx, NarrativeBlockKey.MISSIONS);
        sub(b, "III.3 Gouvernance");
        narrative(b, ctx, NarrativeBlockKey.GOUVERNANCE);
        sub(b, "III.4 Organisation");
        narrative(b, ctx, NarrativeBlockKey.ORGANISATION);
        sub(b, "III.5 Ressources");
        narrative(b, ctx, NarrativeBlockKey.RESSOURCES);

        part(b, PARTIES_PRENANTES);
        b.addAll(stakeholders(ctx));

        // L'exercice 2026 est le dernier des exercices passes : ses performances ouvrent le bilan, avant le diagnostic.
        part(b, BILAN);
        narrative(b, ctx, NarrativeBlockKey.BILAN_PSD_PRECEDENT);
        sub(b, "V.1 Performances de l'exercice " + REVIEW_YEAR);
        b.addAll(performances(ctx));

        part(b, DIAGNOSTIC);
        b.addAll(diagnostic(ctx));

        part(b, ENJEUX);
        sub(b, "VII.1 Enjeux");
        narrative(b, ctx, NarrativeBlockKey.ENJEUX);
        sub(b, "VII.2 Défis à relever");
        narrative(b, ctx, NarrativeBlockKey.DEFIS_A_RELEVER);

        part(b, FACTEURS);
        narrative(b, ctx, NarrativeBlockKey.FACTEURS_CLES);

        part(b, CADRE);
        b.addAll(strategicFramework(ctx));

        part(b, MISE_EN_OEUVRE);
        b.addAll(implementation(ctx));

        part(b, PILOTAGE);
        b.addAll(monitoring(ctx));

        part(b, SYNTHESE);
        b.addAll(strategicSummary(ctx));

        part(b, CONCLUSION);
        b.addAll(conclusion(ctx));

        part(b, ANNEXES);
        b.addAll(annexes(ctx));
        return b;
    }

    /**
     * Axes strategiques de l'entreprise et leur budget, pour la page « Axes stratégiques de
     * SENICO » du Plan Strategique complet : meme lecture que la partie IX de la note.
     */
    List<ExportBlock> strategicAxes(List<WorkGroup> groups, Map<String, SectionDef> sectionsByCode,
                                    Map<String, SectionResponse> responsesByKey,
                                    Map<String, GroupSectionStatus> statusesByKey,
                                    Map<NarrativeBlockKey, String> narratives) {
        Context ctx = new Context(groups, sectionsByCode, responsesByKey, statusesByKey, narratives, LocalDate.now(DAKAR));
        List<ExportBlock> b = new ArrayList<>(axesBlocks(ctx));
        b.add(new ExportBlock.Heading("Budget par axe stratégique", 3));
        b.addAll(budgetBlocks(ctx));
        return b;
    }

    /** Sections du canevas que le Plan Strategique complet presente par axe de l'entreprise (cf. {@link #planSection}). */
    static final Set<String> PLAN_SECTIONS = Set.of("S06", "S07", "S08", "S09", "S10", "S11", "S12", "S17");

    /**
     * Rubrique du Plan Strategique complet qui se lit toutes directions confondues, rendue avec les
     * tableaux du canevas que reprend la note : analyse causale (S06) et inventaire du diagnostic (S07),
     * puis, par axe de l'entreprise, axes d'intervention des directions (S08), cadre logique (S09),
     * plan d'actions (S10), budget detaille (S11), cadre de mesure de rendement (S12) et synthese du
     * cadre strategique (S17). Le client tient les tableaux par axe pour les parties les plus importantes
     * du plan : le Plan les repetait direction par direction, sous des axes numerotes de 1 a 4 dans
     * chaque direction, la ou la note les consolidait deja sous les axes de l'entreprise.
     *
     * @return vide pour une section que le Plan continue de rendre direction par direction
     */
    Optional<List<ExportBlock>> planSection(String sectionCode, List<WorkGroup> groups,
                                            Map<String, SectionDef> sectionsByCode,
                                            Map<String, SectionResponse> responsesByKey,
                                            Map<String, GroupSectionStatus> statusesByKey,
                                            Map<NarrativeBlockKey, String> narratives) {
        if (!PLAN_SECTIONS.contains(sectionCode)) {
            return Optional.empty();
        }
        Context ctx = new Context(groups, sectionsByCode, responsesByKey, statusesByKey, narratives, LocalDate.now(DAKAR));
        List<ExportBlock> blocks = switch (sectionCode) {
            case "S06" -> causalAnalysis(ctx);
            case "S07" -> inventory(ctx);
            case "S08" -> directionAxesTables(ctx);
            // La synthese du cadre logique a sa propre rubrique dans le Plan (S09B).
            case "S09" -> logicalFrameworks(ctx);
            case "S10" -> actionPlans(ctx);
            case "S11" -> {
                List<ExportBlock> budgets = detailedBudgets(ctx, true);
                yield budgets.isEmpty()
                        ? List.<ExportBlock>of(new ExportBlock.Paragraph("Aucun budget n'est encore approuvé.", true, false))
                        : budgets;
            }
            case "S12" -> performanceFrameworks(ctx);
            default -> {
                // Le recapitulatif des axes a ete retire a la revue client du 15/09/2026 : le budget et
                // l'objectif de chaque action se lisent desormais dans le tableau de synthese.
                List<ExportBlock> b = new ArrayList<>();
                b.add(new ExportBlock.Heading("Tableau de synthèse du cadre stratégique", 3));
                b.addAll(strategicSummaryTable(ctx));
                yield b;
            }
        };
        return Optional.of(blocks);
    }

    /**
     * Nombre d'axes strategiques de l'entreprise, ou a defaut des axes formules par les directions : le
     * chiffre que la synthese du Plan annonce, et que le cadre strategique retrouve quelques pages plus loin.
     */
    int strategicAxisCount(List<WorkGroup> groups, Map<String, SectionDef> sectionsByCode,
                           Map<String, SectionResponse> responsesByKey,
                           Map<String, GroupSectionStatus> statusesByKey,
                           Map<NarrativeBlockKey, String> narratives) {
        return new Context(groups, sectionsByCode, responsesByKey, statusesByKey, narratives, LocalDate.now(DAKAR)).axisCount();
    }

    // ---- Sigles ----

    private ExportBlock.Table acronyms() {
        String[][] entries = {
                {"CA", "Conseil d'Administration"},
                {"CODIR", "Comité de direction"},
                {"COPIL", "Comité de pilotage du Plan Stratégique"},
                {"CRM", "Outil de gestion de la relation client"},
                {"DC", "Direction Ventes Locales et Marketing"},
                {"DFC", "Direction Financière et Contrôle de Gestion"},
                {"DG", "Directeur Général / Direction Générale"},
                {"DRH", "Direction des Ressources Humaines"},
                {"DSI", "Direction des Systèmes d'Information"},
                {"DTE", "Direction Technique"},
                {"EPI", "Équipement de protection individuelle"},
                {"FCFA", "Franc de la Communauté Financière Africaine"},
                {"IA", "Intelligence artificielle"},
                {"IOV", "Indicateur objectivement vérifiable"},
                {"KPI", "Indicateur clé de performance"},
                {"M FCFA", "Millions de francs CFA"},
                {"OS", "Orientation stratégique"},
                {"PESTEL", "Analyse des facteurs politiques, économiques, socioculturels, technologiques, environnementaux et légaux"},
                {"PMO", "Cellule de coordination du Plan Stratégique (Project Management Office)"},
                {"PTF", "Partenaires techniques et financiers"},
                {"QHSE", "Qualité, hygiène, sécurité et environnement"},
                {"R&D", "Recherche et développement"},
                {"RH", "Ressources humaines"},
                {"SI", "Système d'information"},
                {"SWOT", "Forces, faiblesses, opportunités et menaces"},
                {"TOWS", "Matrice de confrontation des facteurs internes et externes"}};
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        for (String[] entry : entries) {
            rows.add(new ExportBlock.TableRow(List.of(
                    new ExportBlock.Cell(entry[0], true, ExportBlock.Align.LEFT, ExportBlock.Background.NONE),
                    new ExportBlock.Cell(entry[1]))));
        }
        return new ExportBlock.Table(List.of("Sigle", "Signification"), rows, List.of(18, 82));
    }

    // ---- L'essentiel du plan ----

    /** Les directions dont le plan reunit les contributions : toutes n'ont pas forcement encore fait approuver les leurs. */
    private static String contributions(PsdKeyFigures f) {
        return f.allContribute()
                ? "les contributions des " + f.directions() + " directions de l'entreprise, approuvées par la Direction Générale"
                : "les contributions, approuvées par la Direction Générale, de " + f.contributingDirections() + " des "
                        + f.directions() + " directions et services de l'entreprise";
    }

    private List<ExportBlock> essentials(Context ctx) {
        PsdKeyFigures f = ctx.figures;
        List<ExportBlock> b = new ArrayList<>();

        // Sans cet avertissement, une note ou tout vaut zero se lit comme un PSD sans ambition,
        // alors qu'elle ne fait que refleter un perimetre d'approbation encore vide.
        if (f.sectionsCovered() == 0) {
            b.add(new ExportBlock.Callout(
                    ctx.awaitingApproval > 0
                            ? "Aucune section n'a encore été approuvée par la Direction Générale : la note "
                                    + "est vide et tous les chiffres qui suivent restent à zéro. " + ctx.awaitingApproval
                                    + " section(s) validée(s) par le comité de pilotage attendent cet arbitrage."
                            : "Aucune section approuvée par la Direction Générale à ce jour : la note est vide "
                                    + "et tous les chiffres qui suivent restent à zéro.",
                    ExportBlock.Tone.WARNING));
        }

        int axes = ctx.axisCount();
        int orientations = ctx.orientationCount();
        b.add(new ExportBlock.Paragraph("Le Plan Stratégique " + PERIOD + " de SENICO SA réunit "
                + contributions(f) + ". Il s'articule autour de " + axes + " axes stratégiques, déclinés en "
                + f.specificObjectives() + " objectifs spécifiques, " + orientations + " orientations stratégiques (OS) et "
                + f.actions() + " actions programmées, pour un budget global prévisionnel de "
                + JsonUtil.formatAmountLabel(f.budget()) + " sur " + YEARS.length + " exercices."));

        String vision = ctx.narrative(NarrativeBlockKey.VISION);
        if (!vision.isBlank()) {
            b.add(new ExportBlock.Callout("Vision " + LAST_YEAR + " — « " + unquote(vision) + " »"));
        }

        String coverage = f.budget() > 0 ? " (" + JsonUtil.formatPercent(f.financing() / f.budget() * 100) + " du budget)" : "";
        b.add(new ExportBlock.MetricGrid(List.of(
                new ExportBlock.Metric("Axes stratégiques", String.valueOf(axes)),
                new ExportBlock.Metric("Objectifs spécifiques", String.valueOf(f.specificObjectives())),
                new ExportBlock.Metric("Orientations stratégiques (OS)", String.valueOf(orientations)),
                new ExportBlock.Metric("Actions programmées", String.valueOf(f.actions())),
                new ExportBlock.Metric("Budget global prévisionnel " + PERIOD, JsonUtil.formatAmountLabel(f.budget())),
                new ExportBlock.Metric("Financement identifié" + coverage, JsonUtil.formatAmountLabel(f.financing())),
                new ExportBlock.Metric("Effectifs prévus " + FIRST_YEAR + " – " + LAST_YEAR, f.staffEvolutionLabel()),
                new ExportBlock.Metric("Indicateurs de suivi", String.valueOf(ctx.indicators)))));
        b.add(analysis(coverageSentence(f)));

        if (ctx.plan.consolidated() && !ctx.plan.axes().isEmpty()) {
            b.add(new ExportBlock.Heading("Les axes du plan", 3));
            List<ExportBlock.TableRow> rows = new ArrayList<>();
            double total = 0;
            for (int i = 0; i < ctx.plan.axes().size(); i++) {
                PlanAxis axis = ctx.plan.axes().get(i);
                double amount = sum(ctx.budgetOf(axis.keys()));
                total += amount;
                rows.add(new ExportBlock.TableRow(List.of(
                        new ExportBlock.Cell("Axe " + (i + 1) + " : " + axis.title()),
                        right(JsonUtil.formatMillions(amount)),
                        right(f.budget() > 0 ? JsonUtil.formatPercent(amount / f.budget() * 100) : "—"))));
            }
            rows.add(totalRow("Total", JsonUtil.formatMillions(total), f.budget() > 0 ? "100 %" : "—"));
            b.add(new ExportBlock.Table(List.of("Axe stratégique", "Budget (M FCFA)", "Part"), rows, List.of(64, 22, 14)));
        }
        return b;
    }

    // ---- I. Contexte et justification ----

    private List<ExportBlock> context(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();
        narrative(b, ctx, NarrativeBlockKey.INTRODUCTION);

        sub(b, "I.1 Objet et périmètre du document");
        if (!ctx.narrative(NarrativeBlockKey.PREAMBULE).isBlank()) {
            b.addAll(PsdNarrativeText.blocks(ctx.narrative(NarrativeBlockKey.PREAMBULE)));
        }
        b.add(new ExportBlock.KeyValueList(null, List.of(
                new ExportBlock.KeyValue("Structure", "SENICO SA"),
                new ExportBlock.KeyValue("Document", "Plan Stratégique " + PERIOD),
                new ExportBlock.KeyValue("Nature", "Note de synthèse — consolidation de l'ensemble des directions"),
                new ExportBlock.KeyValue("Horizon", FIRST_YEAR + " – " + LAST_YEAR + " (" + YEARS.length + " exercices)"),
                new ExportBlock.KeyValue("Directions contributrices", ctx.figures.contributorsLabel()),
                new ExportBlock.KeyValue("Périmètre retenu", "Sections validées par le comité de pilotage puis approuvées par la Direction Générale"),
                new ExportBlock.KeyValue("Destinataires", "Conseil d'Administration, Direction Générale, comité de pilotage")
        ), true));

        // Sans legende, une phrase bleue ne dit rien : la couleur n'attribue que si le lecteur
        // dispose de la cle, et il doit l'avoir avant d'aborder le diagnostic.
        sub(b, "I.2 Lecture du document");
        b.add(new ExportBlock.Paragraph(
                "Les constats issus du diagnostic des directions portent la couleur de la direction qui les a "
                        + "formulés. Un constat partagé par plusieurs directions reste en gris et porte une pastille "
                        + "par direction : il est commun, aucune ne peut se l'approprier. Les textes arrêtés par la "
                        + "Direction Générale — vision, mission, valeurs, axes stratégiques — engagent l'entreprise "
                        + "entière et ne portent pas de couleur."));
        b.add(new ExportBlock.ColorLegend("Code couleur des directions", ctx.groups.stream()
                .map(group -> new ExportBlock.Attribution(group.getName(), List.of(colorOf(group, ctx.groups))))
                .toList()));
        return b;
    }

    // ---- IV. Parties prenantes ----

    private List<ExportBlock> stakeholders(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();
        b.add(new ExportBlock.Paragraph(
                "SENICO SA évolue dans un écosystème d'acteurs institutionnels, économiques, financiers et sociaux "
                        + "dont les attentes influencent directement sa performance et sa trajectoire. La matrice "
                        + "ci-dessous classe les parties prenantes identifiées par les directions selon leur intérêt "
                        + "pour le plan et leur pouvoir d'influence sur sa mise en œuvre : elle indique avec qui "
                        + "concerter, qui consulter et qui tenir informé."));

        List<Contributions> cells = List.of(new Contributions(ctx.groups), new Contributions(ctx.groups),
                new Contributions(ctx.groups), new Contributions(ctx.groups));
        for (WorkGroup group : ctx.groups) {
            for (JsonNode row : JsonUtil.arr(ctx.content(group, "S01"), "rows")) {
                String actor = actorOf(row);
                if (actor.isEmpty()) {
                    continue;
                }
                boolean interest = "FORT".equalsIgnoreCase(JsonUtil.text(row, "importance"));
                boolean power = "FORT".equalsIgnoreCase(JsonUtil.text(row, "influence"));
                int index = interest ? (power ? 1 : 0) : (power ? 3 : 2);
                cells.get(index).add(group, actor);
            }
        }
        int total = cells.stream().mapToInt(Contributions::size).sum();
        if (total == 0) {
            b.add(new ExportBlock.Paragraph("Aucune partie prenante n'est encore renseignée dans les sections approuvées.", true, false));
            return b;
        }

        b.add(new ExportBlock.AttributedQuadrant(List.of(
                new ExportBlock.AttributedQuadrantCell("INTÉRÊT FORT — POUVOIR MODÉRÉ",
                        "À informer, consulter et satisfaire régulièrement.", cells.get(0).toAttributions()),
                new ExportBlock.AttributedQuadrantCell("INTÉRÊT FORT — POUVOIR FORT",
                        "À impliquer étroitement dans la gouvernance, le pilotage et la mise en œuvre du plan.",
                        cells.get(1).toAttributions()),
                new ExportBlock.AttributedQuadrantCell("INTÉRÊT MODÉRÉ — POUVOIR MODÉRÉ",
                        "À suivre avec un effort proportionné, en maintenant une relation de proximité.",
                        cells.get(2).toAttributions()),
                new ExportBlock.AttributedQuadrantCell("INTÉRÊT MODÉRÉ — POUVOIR FORT",
                        "À maintenir satisfaits et à mobiliser sur les sujets à fort enjeu.", cells.get(3).toAttributions())),
                List.of(ExportBlock.Background.GREEN, ExportBlock.Background.ORANGE,
                        ExportBlock.Background.VIOLET, ExportBlock.Background.BLUE)));

        b.add(analysis("les directions identifient " + total + " parties prenantes, dont " + cells.get(1).size()
                + " à impliquer étroitement : leur intérêt et leur pouvoir d'influence sont tous deux forts. "
                + "L'intérêt reprend l'importance et le pouvoir l'influence cotées par les directions ; une "
                + "cotation moyenne est rangée avec les cotations faibles. Les attentes et les stratégies "
                + "d'adaptation figurent en annexe (Tableau 1)."));
        return b;
    }

    /** Nom de la partie prenante ; a defaut, le debut de son role (« Direction Commerciale : exprime... »). */
    private static String actorOf(JsonNode row) {
        String actor = JsonUtil.text(row, "actor").trim();
        if (!actor.isEmpty()) {
            return actor;
        }
        String roles = JsonUtil.text(row, "roles").trim();
        int colon = roles.indexOf(" : ");
        return colon > 0 ? roles.substring(0, colon).trim() : roles;
    }

    /**
     * Role d'une partie prenante, sans le nom que {@link #actorOf} y a deja pris. La Direction Logistique a
     * saisi ses acteurs dans la case des roles (« Direction Commerciale : exprime les besoins... », ou le seul
     * nom) : l'annexe les ecrivait deux fois, dans la colonne de l'acteur et dans celle des roles.
     */
    static String rolesOf(JsonNode row) {
        String roles = JsonUtil.text(row, "roles").trim();
        if (!JsonUtil.text(row, "actor").isBlank()) {
            return roles;
        }
        int colon = roles.indexOf(" : ");
        return colon > 0 ? capitalize(roles.substring(colon + 3).trim()) : "";
    }

    /**
     * Categorie d'une partie prenante, precisee de son nom quand la saisie en porte un. Le formulaire du
     * canevas ne demande que la categorie, mais les saisies anterieures nomment l'acteur : « Autre » seul
     * ne disait pas s'il s'agissait des grands comptes, des concurrents ou du personnel.
     */
    static String stakeholderLabel(JsonNode row) {
        String category = SectionLabels.stakeholderCategory(JsonUtil.text(row, "category"));
        String actor = actorOf(row);
        return actor.isEmpty() ? category : category + " — " + actor;
    }

    // ---- V. Diagnostic strategique ----

    private List<ExportBlock> diagnostic(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();
        // Revue client du 15/09/2026 : les performances 2026 passent au bilan (partie V) et l'inventaire
        // du diagnostic est retire.
        b.add(new ExportBlock.Paragraph(
                "Le diagnostic consolide les analyses conduites par chaque direction : ressources et compétences, "
                        + "environnement externe (PESTEL), forces et faiblesses internes confrontées aux opportunités "
                        + "et menaces (SWOT), mise en relation de ces facteurs, analyse causale et risques associés. "
                        + "Un constat formulé par plusieurs directions n'y figure qu'une fois."));

        sub(b, "VI.1 Analyse des ressources et des compétences");
        b.addAll(resources(ctx));
        b.addAll(resourcesSynthesis(ctx));

        sub(b, "VI.2 Analyse PESTEL");
        b.addAll(pestel(ctx));

        sub(b, "VI.3 Analyse SWOT");
        b.add(new ExportBlock.Paragraph("Les forces et les faiblesses relèvent de l'organisation elle-même ; les "
                + "opportunités et les menaces, de son environnement."));
        b.add(mergedSwot(ctx));

        sub(b, "VI.4 Mise en relation du diagnostic stratégique");
        b.addAll(crossedStrategies(ctx));

        sub(b, "VI.5 Analyse causale");
        b.addAll(causalAnalysis(ctx));

        sub(b, "VI.6 Cartographie des risques");
        b.addAll(risks(ctx));
        return b;
    }

    private List<ExportBlock> performances(Context ctx) {
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        for (WorkGroup group : ctx.groups) {
            for (JsonNode row : JsonUtil.arr(ctx.content(group, "S01B"), "rows")) {
                String indicator = JsonUtil.text(row, "indicator").trim();
                if (indicator.isEmpty()) {
                    continue;
                }
                JsonNode rate = row.get("rate");
                rows.add(new ExportBlock.TableRow(List.of(
                        new ExportBlock.Cell(JsonUtil.text(row, "domain")),
                        single(indicator, group, ctx),
                        right(JsonUtil.formatDecimal(JsonUtil.num(row, "target2026"))),
                        right(JsonUtil.formatDecimal(JsonUtil.num(row, "achieved2026"))),
                        right(rate == null || rate.isNull() ? "—" : JsonUtil.formatRate(rate.asDouble())),
                        new ExportBlock.Cell(JsonUtil.dash(JsonUtil.text(row, "comment").trim())))));
            }
        }
        if (rows.isEmpty()) {
            return List.of(new ExportBlock.Paragraph("Aucune analyse des performances " + REVIEW_YEAR
                    + " n'est encore approuvée.", true, false));
        }
        // Une note datee avant la cloture de l'exercice ne peut pas presenter ses chiffres comme
        // realises : ce sont des estimations a date, que la cloture confirmera.
        boolean closed = ctx.reviewYearClosed;
        return List.of(
                new ExportBlock.Paragraph(closed
                        ? "Les directions ont mesuré leurs réalisations de l'exercice " + REVIEW_YEAR + " au regard des "
                                + "cibles fixées ; ces écarts fondent le diagnostic qui suit."
                        : "L'exercice " + REVIEW_YEAR + " n'étant pas clos, les directions ont estimé à date leurs "
                                + "réalisations au regard des cibles fixées ; ces écarts, que la clôture de l'exercice "
                                + "confirmera, fondent le diagnostic qui suit."),
                new ExportBlock.Table(List.of("Domaine", "Indicateur", "Cible " + REVIEW_YEAR,
                        (closed ? "Réalisé " : "Estimation ") + REVIEW_YEAR, "Taux", "Écart / commentaire"),
                        rows, List.of(15, 22, 9, 12, 9, 33)),
                // Le taux d'un indicateur a plafond est calcule a l'envers (cible / realise, cf. DerivedFieldsService) :
                // l'encadre annoncait l'inverse, et un delai de 3,8 jours pour 3 vises, affiche a 78,9 %, s'y lisait
                // comme une bonne performance.
                analysis("le taux rapporte " + (closed ? "le réalisé" : "l'estimation") + " à la cible. Pour un délai, "
                        + "un coût, un écart ou un nombre d'incidents, dont la cible est un plafond, il rapporte la cible "
                        + (closed ? "au réalisé" : "à l'estimation") + " : quel que soit l'indicateur, un taux inférieur "
                        + "à 100 % signale une cible non atteinte, et un taux supérieur, une cible dépassée."));
    }

    /**
     * Matrice des ressources et des competences, sur le modele transmis par le client : une ligne
     * par ressource, les forces, faiblesses et defis de toutes les directions fondus dans chaque
     * case, chacun a la couleur de la direction qui l'a releve.
     */
    private List<ExportBlock> resources(Context ctx) {
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        boolean any = false;
        for (String resource : DefaultSectionContentFactory.RESOURCE_KEYS) {
            Contributions strengths = new Contributions(ctx.groups);
            Contributions weaknesses = new Contributions(ctx.groups);
            Contributions challenges = new Contributions(ctx.groups);
            for (WorkGroup group : ctx.groups) {
                for (JsonNode row : JsonUtil.arr(ctx.content(group, "S02"), "rows")) {
                    if (!resource.equals(JsonUtil.text(row, "resourceKey"))) {
                        continue;
                    }
                    strengths.addLines(group, JsonUtil.text(row, "strengths"));
                    weaknesses.addLines(group, JsonUtil.text(row, "weaknesses"));
                    challenges.addLines(group, JsonUtil.text(row, "challenges"));
                }
            }
            any |= !(strengths.isEmpty() && weaknesses.isEmpty() && challenges.isEmpty());
            rows.add(new ExportBlock.TableRow(List.of(
                    new ExportBlock.Cell(SectionLabels.resource(resource), true, ExportBlock.Align.LEFT, ExportBlock.Background.GREY),
                    attributedCell(strengths), attributedCell(weaknesses), attributedCell(challenges))));
        }
        if (!any) {
            return List.of(new ExportBlock.Paragraph("Aucune matrice des ressources et des compétences n'est encore approuvée.", true, false));
        }
        return List.of(
                new ExportBlock.Paragraph("La matrice d'analyse des ressources et des compétences confronte, pour chaque "
                        + "ressource de l'entreprise, les forces et acquis sur lesquels le plan peut s'appuyer, les "
                        + "faiblesses qu'il doit corriger et les défis qui en découlent."),
                new ExportBlock.Table(List.of("Ressources", "Forces / Acquis", "Faiblesses", "Défis à relever"),
                        rows, List.of(22, 26, 26, 26)));
    }

    /**
     * Synthese de l'analyse des ressources (S03B) : les forces majeures, faiblesses majeures et defis
     * prioritaires que chaque direction retient de sa matrice, puis sa note de synthese.
     */
    private List<ExportBlock> resourcesSynthesis(Context ctx) {
        Contributions strengths = new Contributions(ctx.groups);
        Contributions weaknesses = new Contributions(ctx.groups);
        Contributions challenges = new Contributions(ctx.groups);
        List<ExportBlock.TableRow> notes = new ArrayList<>();
        for (WorkGroup group : ctx.groups) {
            JsonNode content = ctx.content(group, "S03B");
            JsonUtil.strList(content, "majorStrengths").forEach(item -> strengths.add(group, item));
            JsonUtil.strList(content, "majorWeaknesses").forEach(item -> weaknesses.add(group, item));
            JsonUtil.strList(content, "priorityChallenges").forEach(item -> challenges.add(group, item));
            String note = JsonUtil.text(content, "synthesisNote").trim();
            if (!note.isEmpty()) {
                notes.add(new ExportBlock.TableRow(List.of(single(group.getName(), group, ctx), single(note, group, ctx))));
            }
        }
        boolean lists = !(strengths.isEmpty() && weaknesses.isEmpty() && challenges.isEmpty());
        if (!lists && notes.isEmpty()) {
            return List.of();
        }
        List<ExportBlock> b = new ArrayList<>();
        b.add(new ExportBlock.Heading("Synthèse de l'analyse des ressources", 3));
        b.add(new ExportBlock.Paragraph("De cette matrice, chaque direction retient ses forces majeures, ses faiblesses "
                + "majeures et ses défis prioritaires."));
        if (lists) {
            b.add(new ExportBlock.Table(List.of("Forces majeures", "Faiblesses majeures", "Défis prioritaires"),
                    List.of(new ExportBlock.TableRow(List.of(attributedCell(strengths), attributedCell(weaknesses),
                            attributedCell(challenges)))), List.of(33, 33, 34)));
        }
        if (!notes.isEmpty()) {
            b.add(new ExportBlock.Table(List.of("Direction", "Synthèse de l'analyse des ressources"), notes, List.of(22, 78)));
        }
        return b;
    }

    private List<ExportBlock> pestel(Context ctx) {
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        boolean any = false;
        for (String axis : DefaultSectionContentFactory.PESTEL_AXES) {
            Contributions opportunities = new Contributions(ctx.groups);
            Contributions threats = new Contributions(ctx.groups);
            Contributions actions = new Contributions(ctx.groups);
            for (WorkGroup group : ctx.groups) {
                for (JsonNode row : JsonUtil.arr(ctx.content(group, "S03"), "rows")) {
                    if (!axis.equals(JsonUtil.text(row, "axis"))) {
                        continue;
                    }
                    opportunities.addLines(group, JsonUtil.text(row, "opportunities"));
                    threats.addLines(group, JsonUtil.text(row, "threats"));
                    actions.addLines(group, JsonUtil.text(row, "actions"));
                }
            }
            any |= !(opportunities.isEmpty() && threats.isEmpty() && actions.isEmpty());
            rows.add(new ExportBlock.TableRow(List.of(
                    new ExportBlock.Cell(SectionLabels.pestel(axis), true, ExportBlock.Align.LEFT, ExportBlock.Background.GREY),
                    attributedCell(threats), attributedCell(opportunities), attributedCell(actions))));
        }
        if (!any) {
            return List.of(new ExportBlock.Paragraph("Aucune analyse PESTEL n'est encore approuvée.", true, false));
        }
        return List.of(
                new ExportBlock.Paragraph("L'analyse PESTEL recense, pour chaque facteur de l'environnement, les "
                        + "menaces à anticiper, les opportunités à saisir et les actions que les directions proposent pour "
                        + "atténuer les unes et saisir les autres."),
                new ExportBlock.Table(List.of("Items", "Menaces", "Opportunités",
                        "Actions pour atténuer les menaces ou saisir les opportunités"),
                        rows, List.of(16, 28, 28, 28)));
    }

    /** SWOT de toutes les directions fondu en un seul cadran : un element cite plusieurs fois n'apparait qu'une. */
    private ExportBlock.AttributedQuadrant mergedSwot(Context ctx) {
        List<ExportBlock.AttributedQuadrantCell> cells = new ArrayList<>();
        for (String[] field : new String[][]{
                {"strengths", "FORCES", "Atouts internes"}, {"weaknesses", "FAIBLESSES", "Contraintes internes"},
                {"opportunities", "OPPORTUNITÉS", "Atouts de l'environnement"}, {"threats", "MENACES", "Contraintes de l'environnement"}}) {
            Contributions items = new Contributions(ctx.groups);
            for (WorkGroup group : ctx.groups) {
                for (String item : JsonUtil.strList(ctx.content(group, "S04"), field[0])) {
                    items.add(group, item);
                }
            }
            cells.add(new ExportBlock.AttributedQuadrantCell(field[1], field[2], items.toAttributions()));
        }
        return new ExportBlock.AttributedQuadrant(cells);
    }

    /**
     * Mise en relation du diagnostic strategique, sur le modele du canevas : les facteurs internes en
     * colonnes, les facteurs externes en lignes, et a chaque croisement ce que les directions en tirent.
     */
    private List<ExportBlock> crossedStrategies(Context ctx) {
        String[] fields = {"maximizeStrengths", "minimizeWeaknesses", "strengthsControlWeaknesses",
                "maximizeOpportunities", "strengthsForOpportunities", "correctWeaknessesViaOpportunities",
                "minimizeThreats", "strengthsReduceThreats", "minimizeWeaknessesAndThreats", "opportunitiesMinimizeThreats"};
        Map<String, Contributions> answers = new LinkedHashMap<>();
        for (String field : fields) {
            answers.put(field, new Contributions(ctx.groups));
        }
        for (WorkGroup group : ctx.groups) {
            JsonNode content = ctx.content(group, "S05");
            answers.forEach((field, items) -> items.addLines(group, JsonUtil.text(content, field)));
        }
        if (answers.values().stream().allMatch(Contributions::isEmpty)) {
            return List.of(new ExportBlock.Paragraph("Aucune matrice de confrontation n'est encore approuvée.", true, false));
        }
        ExportBlock.Cell none = new ExportBlock.Cell("", ExportBlock.Background.GREY);
        List<ExportBlock.TableRow> rows = List.of(
                new ExportBlock.TableRow(List.of(rowLabel("Approche interne"), none,
                        attributedCell(answers.get("maximizeStrengths")), attributedCell(answers.get("minimizeWeaknesses")),
                        attributedCell(answers.get("strengthsControlWeaknesses")))),
                new ExportBlock.TableRow(List.of(rowLabel("Opportunités"), attributedCell(answers.get("maximizeOpportunities")),
                        attributedCell(answers.get("strengthsForOpportunities")),
                        attributedCell(answers.get("correctWeaknessesViaOpportunities")), none)),
                new ExportBlock.TableRow(List.of(rowLabel("Menaces"), attributedCell(answers.get("minimizeThreats")),
                        attributedCell(answers.get("strengthsReduceThreats")),
                        attributedCell(answers.get("minimizeWeaknessesAndThreats")), none)),
                new ExportBlock.TableRow(List.of(rowLabel("En quoi les opportunités permettent de minimiser les menaces"),
                        attributedCell(answers.get("opportunitiesMinimizeThreats")), none, none, none)));
        return List.of(
                new ExportBlock.Paragraph("La mise en relation du diagnostic croise les facteurs internes (forces, "
                        + "faiblesses) et externes (opportunités, menaces). À chaque croisement figure ce que les directions "
                        + "en tirent : utiliser les forces pour saisir les opportunités ou réduire les menaces, corriger les "
                        + "faiblesses grâce aux opportunités, minimiser à la fois faiblesses et menaces. Ces orientations ont "
                        + "nourri la formulation des axes stratégiques."),
                new ExportBlock.Table(List.of("Approche externe", "Comment maximiser les opportunités / minimiser les menaces ?",
                        "Forces : comment les maximiser et s'en servir ?", "Faiblesses : comment les minimiser et les corriger ?",
                        "En quoi les forces permettent de maîtriser les faiblesses"), rows, List.of(15, 20, 22, 22, 21)));
    }

    /**
     * Analyse causale, sur le modele du canevas : des manifestations des problemes aux causes
     * immediates, sous-jacentes et profondes, puis aux solutions, toutes directions confondues.
     */
    private List<ExportBlock> causalAnalysis(Context ctx) {
        Map<String, Contributions> bySource = new LinkedHashMap<>();
        for (String source : DefaultSectionContentFactory.CAUSAL_SOURCES) {
            bySource.put(source, new Contributions(ctx.groups));
        }
        for (WorkGroup group : ctx.groups) {
            for (JsonNode row : JsonUtil.arr(ctx.content(group, "S06"), "rows")) {
                Contributions items = bySource.get(JsonUtil.text(row, "source"));
                if (items != null) {
                    JsonUtil.strList(row, "items").forEach(item -> items.add(group, item));
                }
            }
        }
        if (bySource.values().stream().allMatch(Contributions::isEmpty)) {
            return List.of(new ExportBlock.Paragraph("Aucune analyse causale n'est encore approuvée.", true, false));
        }
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        bySource.forEach((source, items) -> rows.add(new ExportBlock.TableRow(List.of(
                rowLabel(SectionLabels.causal(source)), attributedCell(items)))));
        return List.of(
                new ExportBlock.Paragraph("L'analyse causale remonte des manifestations des problèmes relevés par les "
                        + "directions à leurs causes immédiates, sous-jacentes et profondes, puis aux solutions qu'elles appellent."),
                new ExportBlock.Table(List.of("Sources", "Analyse"), rows, List.of(28, 72)));
    }

    /** Inventaire du diagnostic : ce que chaque direction retient de ses analyses, dans sa couleur. */
    private List<ExportBlock> inventory(Context ctx) {
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        for (WorkGroup group : ctx.groups) {
            String note = JsonUtil.text(ctx.content(group, "S07"), "synthesisNote").trim();
            if (!note.isEmpty()) {
                rows.add(new ExportBlock.TableRow(List.of(single(group.getName(), group, ctx), single(note, group, ctx))));
            }
        }
        if (rows.isEmpty()) {
            return List.of(new ExportBlock.Paragraph("Aucun inventaire du diagnostic n'est encore approuvé.", true, false));
        }
        return List.of(
                new ExportBlock.Paragraph("L'inventaire récapitule ce que chaque direction retient de ses analyses — SWOT, "
                        + "PESTEL, parties prenantes et analyse causale, dont le détail consolidé figure ci-dessus."),
                new ExportBlock.Table(List.of("Direction",
                        "Synthèse de l'inventaire (SWOT, PESTEL, parties prenantes, analyse causale)"), rows, List.of(22, 78)));
    }

    private static ExportBlock.Cell rowLabel(String text) {
        return new ExportBlock.Cell(text, true, ExportBlock.Align.LEFT, ExportBlock.Background.GREY);
    }

    private List<ExportBlock> risks(Context ctx) {
        List<Risk> risks = ctx.risks(false);
        int absent = ctx.risks(true).size() - risks.size();
        if (risks.isEmpty()) {
            return List.of(new ExportBlock.Paragraph("Aucune matrice des risques n'est encore approuvée.", true, false));
        }
        List<Risk> high = risks.stream().filter(risk -> risk.criticality() >= 6).toList();
        List<ExportBlock> b = new ArrayList<>();
        b.add(new ExportBlock.Paragraph("Chaque risque est coté par la direction qui l'a identifié selon son niveau "
                + "(N, de 1 à 3) et l'importance de son impact (Q, de 1 à 3), et rapporté aux domaines d'activités "
                + "qu'il affecterait. La criticité N × Q les classe : élevée de 6 à 9, moyenne de 3 à 4, faible de 1 "
                + "à 2. Les risques de criticité élevée, qui appellent une action prioritaire, sont présentés ci-dessous."));
        if (!high.isEmpty()) {
            List<ExportBlock.TableRow> rows = new ArrayList<>();
            for (Risk risk : high) {
                rows.add(riskRow(risk, ctx, false));
            }
            b.add(new ExportBlock.Table(
                    List.of("Catégorie", "Risque", "Impact sur les activités", "N × Q", "Criticité", "Actions d'atténuation"),
                    rows, List.of(15, 23, 17, 8, 10, 27)));
        }
        b.add(analysis("les directions identifient " + risks.size() + " risques, dont " + high.size()
                + " de criticité élevée. La matrice complète figure en annexe (Tableau 2)"
                + (absent > 0 ? ", y compris " + (absent == 1 ? "le risque examiné mais jugé absent" : "les " + absent
                        + " risques examinés mais jugés absents") : "") + "."));
        return b;
    }

    private ExportBlock.TableRow riskRow(Risk risk, Context ctx, boolean full) {
        ExportBlock.Cell criticality = new ExportBlock.Cell(SectionLabels.criticality(risk.label()), true,
                ExportBlock.Align.CENTER, SectionLabels.criticalityBackground(risk.label()));
        ExportBlock.Cell impact = new ExportBlock.Cell(JsonUtil.dash(risk.impact()));
        if (full) {
            return new ExportBlock.TableRow(List.of(
                    new ExportBlock.Cell(risk.category()),
                    center(risk.present() ? "Oui" : "Non"),
                    single(risk.details(), risk.group(), ctx),
                    center(String.valueOf(risk.level())),
                    impact,
                    center(String.valueOf(risk.quotation())),
                    criticality,
                    new ExportBlock.Cell(risk.mitigation())));
        }
        return new ExportBlock.TableRow(List.of(
                new ExportBlock.Cell(risk.category()),
                single(risk.details(), risk.group(), ctx),
                impact,
                center(risk.level() + " × " + risk.quotation()),
                criticality,
                new ExportBlock.Cell(risk.mitigation())));
    }

    // ---- IX. Cadre strategique ----

    private List<ExportBlock> strategicFramework(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();

        sub(b, "IX.1 Vision");
        String vision = ctx.narrative(NarrativeBlockKey.VISION);
        if (vision.isBlank()) {
            b.add(pendingArbitration("La vision de l'entreprise"));
            Contributions visions = new Contributions(ctx.groups);
            ctx.groups.forEach(group -> visions.add(group, JsonUtil.text(ctx.content(group, "S07B"), "vision")));
            b.add(new ExportBlock.AttributedList("Visions proposées par les directions", visions.toAttributions()));
        } else {
            b.add(new ExportBlock.Paragraph("La vision exprime l'ambition de SENICO SA à l'horizon " + LAST_YEAR
                    + " : elle guide les choix stratégiques du plan et le sens de sa mise en œuvre."));
            b.add(new ExportBlock.Callout("« " + unquote(vision) + " »"));
        }

        sub(b, "IX.2 Mission");
        String mission = ctx.narrative(NarrativeBlockKey.MISSION);
        if (mission.isBlank()) {
            b.add(pendingArbitration("La mission de l'entreprise"));
            Contributions missions = new Contributions(ctx.groups);
            ctx.groups.forEach(group -> JsonUtil.strList(ctx.content(group, "S07B"), "mission")
                    .forEach(item -> missions.add(group, item)));
            b.add(new ExportBlock.AttributedList("Missions proposées par les directions", missions.toAttributions()));
        } else {
            b.add(new ExportBlock.Paragraph("La mission exprime la raison d'être de SENICO SA : ce qu'elle produit et "
                    + "offre, et ceux au service de qui elle le fait."));
            List<String> lines = PsdNarrativeText.lines(mission);
            if (lines.size() == 1) {
                b.add(new ExportBlock.Callout("« " + unquote(lines.get(0)) + " »"));
            } else {
                b.addAll(PsdNarrativeText.blocks(mission));
            }
        }

        sub(b, "IX.3 Valeurs");
        String values = ctx.narrative(NarrativeBlockKey.VALEURS);
        if (values.isBlank()) {
            b.add(pendingArbitration("Les valeurs de l'entreprise"));
            Contributions proposals = new Contributions(ctx.groups);
            ctx.groups.forEach(group -> JsonUtil.strList(ctx.content(group, "S07B"), "values")
                    .forEach(item -> proposals.add(group, item)));
            b.add(new ExportBlock.AttributedList("Valeurs proposées par les directions", proposals.toAttributions()));
        } else {
            b.add(new ExportBlock.Paragraph("Les valeurs de SENICO SA orientent les comportements, les décisions et les "
                    + "pratiques de ses équipes au quotidien."));
            List<String> plain = new ArrayList<>();
            for (String line : PsdNarrativeText.lines(values)) {
                int separator = line.indexOf(':');
                if (separator > 0 && separator < 60) {
                    b.add(new ExportBlock.Heading(line.substring(0, separator).trim(), 4));
                    b.add(new ExportBlock.Paragraph(capitalize(line.substring(separator + 1).trim())));
                } else {
                    plain.add(line);
                }
            }
            if (!plain.isEmpty()) {
                b.add(new ExportBlock.BulletList(null, plain));
            }
        }

        sub(b, "IX.4 Orientations et axes stratégiques");
        b.addAll(axesBlocks(ctx));
        return b;
    }

    private List<ExportBlock> axesBlocks(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();
        AxisPlan plan = ctx.plan;
        if (!plan.consolidated()) {
            b.add(new ExportBlock.Callout("Les axes stratégiques de l'entreprise ne sont pas encore arrêtés par la "
                    + "Direction Générale : figurent ci-dessous les axes proposés par chaque direction, sans "
                    + "regroupement. Le regroupement en axes communs se saisit sur " + EDIT_SCREEN + ".",
                    ExportBlock.Tone.WARNING));
            Contributions titles = new Contributions(ctx.groups);
            Contributions objectives = new Contributions(ctx.groups);
            for (PlanAxis axis : plan.axes()) {
                for (DirectionAxis member : axis.members()) {
                    titles.add(member.group(), member.title());
                    member.objectives().forEach(objective -> objectives.add(member.group(), objective));
                }
            }
            b.add(new ExportBlock.AttributedList("Axes proposés par les directions", titles.toAttributions()));
            b.add(new ExportBlock.AttributedList("Objectifs spécifiques", objectives.toAttributions()));
            return b;
        }

        List<String> summary = new ArrayList<>();
        for (int i = 0; i < plan.axes().size(); i++) {
            summary.add("Axe " + (i + 1) + " : " + plan.axes().get(i).title());
        }
        b.add(new ExportBlock.Paragraph("Le plan s'articule autour de " + plan.axes().size() + " axes stratégiques "
                + "communs à l'ensemble de l'entreprise. Chacun regroupe un ou plusieurs des axes que les directions "
                + "ont formulés dans leur diagnostic, identifiés par la couleur de la direction qui les porte."));
        b.add(new ExportBlock.BulletList(null, summary));
        // Le tableau des axes strategiques du canevas : un axe par colonne, ses objectifs specifiques dessous.
        // Au-dela de six axes, les colonnes deviendraient illisibles : la liste detaillee qui suit suffit.
        if (plan.axes().size() <= 6) {
            List<String> headers = new ArrayList<>();
            List<ExportBlock.Cell> titles = new ArrayList<>();
            List<ExportBlock.Cell> objectives = new ArrayList<>();
            for (int i = 0; i < plan.axes().size(); i++) {
                PlanAxis axis = plan.axes().get(i);
                headers.add("Axe " + (i + 1));
                titles.add(rowLabel(axis.title()));
                Contributions items = new Contributions(ctx.groups);
                axis.members().forEach(member -> member.objectives().forEach(objective -> items.add(member.group(), objective)));
                objectives.add(attributedCell(items));
            }
            b.add(new ExportBlock.Table(headers, List.of(new ExportBlock.TableRow(titles), new ExportBlock.TableRow(objectives))));
        }

        for (int i = 0; i < plan.axes().size(); i++) {
            PlanAxis axis = plan.axes().get(i);
            b.add(new ExportBlock.Heading("Axe " + (i + 1) + " : " + axis.title(), 3));
            if (!axis.objective().isBlank()) {
                b.add(new ExportBlock.Paragraph(axis.objective()));
            }
            if (axis.members().isEmpty()) {
                b.add(new ExportBlock.Paragraph("Aucun axe de direction n'est encore rattaché à cet axe.", true, false));
                continue;
            }
            Contributions objectives = new Contributions(ctx.groups);
            Contributions members = new Contributions(ctx.groups);
            for (DirectionAxis member : axis.members()) {
                member.objectives().forEach(objective -> objectives.add(member.group(), objective));
                members.add(member.group(), member.title());
            }
            b.add(new ExportBlock.AttributedList("Objectifs spécifiques", objectives.toAttributions()));
            b.add(new ExportBlock.AttributedList("Axes des directions regroupés", members.toAttributions()));
        }

        if (!plan.unlinked().isEmpty()) {
            List<String> labels = plan.unlinked().stream()
                    .map(axis -> axis.group().getName() + " — " + axis.title())
                    .toList();
            b.add(new ExportBlock.Callout("Axes de direction non rattachés à un axe stratégique de l'entreprise : "
                    + String.join(" ; ", labels) + ". Leur budget et leurs actions sont présentés à part ; le "
                    + "rattachement se saisit sur " + EDIT_SCREEN + ".", ExportBlock.Tone.WARNING));
        }
        return b;
    }

    // ---- X. Cadre de mise en oeuvre ----

    /**
     * Cadre de mise en oeuvre dans l'ordre du canevas du client : cadre logique, operationnalisation
     * (plan d'actions), budget, plan de financement, puis effectifs.
     */
    private List<ExportBlock> implementation(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();
        // Revue client du 15/09/2026 : les tableaux par axe (cadre logique, plan d'actions, budget) sont reportes en
        // annexe ; le corps du document garde les textes qui les introduisent et la synthese du cadre logique.
        sub(b, "X.1 Cadre logique");
        b.addAll(introOnly(logicalFrameworks(ctx), "Tableau 5 : Cadre logique"));
        b.addAll(logframeSyntheses(ctx));
        sub(b, "X.2 Opérationnalisation : plan d'actions " + PERIOD);
        b.addAll(introOnly(actionPlans(ctx), "Tableau 6 : Planification"));
        sub(b, "X.3 Budget du plan");
        b.addAll(budgetBlocks(ctx, false));
        b.addAll(introOnly(detailedBudgets(ctx, false), "Tableau 4 : Budget du plan"));
        sub(b, "X.4 Plan de financement");
        b.addAll(financing(ctx));
        sub(b, "X.5 Plan d'évolution des effectifs");
        b.addAll(staff(ctx));
        return b;
    }

    // ---- Tableaux du canevas, axe par axe ----

    /** Un axe tel que les tableaux du canevas le presentent : son intitule, son objectif, les axes de direction qu'il regroupe. */
    private record AxisView(String title, String objective, Collection<String> keys) {
    }

    private List<AxisView> axisViews(Context ctx) {
        List<AxisView> views = new ArrayList<>();
        for (int i = 0; i < ctx.plan.axes().size(); i++) {
            PlanAxis axis = ctx.plan.axes().get(i);
            views.add(new AxisView(ctx.plan.consolidated() ? "Axe " + (i + 1) + " : " + axis.title() : axis.title(),
                    axis.objective(), axis.keys()));
        }
        List<String> rest = ctx.unlinkedKeys();
        if (!rest.isEmpty()) {
            views.add(new AxisView("Axes non rattachés", "", rest));
        }
        return views;
    }

    /**
     * Axes d'intervention des directions (S08) sous l'axe de l'entreprise qui les regroupe : l'axe de
     * la direction, son objectif et ses objectifs specifiques, a la couleur de la direction. Rendus
     * direction par direction, ils s'empilaient sous des intertitres « Axe 1 » a « Axe 4 » vides.
     */
    private List<ExportBlock> directionAxesTables(Context ctx) {
        record AxisRows(AxisView axis, List<List<ExportBlock.Cell>> rows) {
        }
        List<AxisRows> axes = new ArrayList<>();
        boolean anyObjective = false;
        for (AxisView axis : axisViews(ctx)) {
            List<List<ExportBlock.Cell>> rows = new ArrayList<>();
            for (String key : axis.keys()) {
                WorkGroup group = ctx.groupOf(key);
                if (group == null) {
                    continue;
                }
                String color = colorOf(group, ctx.groups);
                for (JsonNode entry : ctx.axisEntries(group, "S08", key)) {
                    String title = JsonUtil.text(entry, "title").trim();
                    if (title.isEmpty()) {
                        continue;
                    }
                    String objective = JsonUtil.text(entry, "objective").trim();
                    anyObjective |= !objective.isEmpty();
                    List<ExportBlock.Attribution> objectives = JsonUtil.strList(entry, "specificObjectives").stream()
                            .map(String::trim).filter(specific -> !specific.isEmpty())
                            .map(specific -> new ExportBlock.Attribution(specific, List.of(color)))
                            .toList();
                    rows.add(List.of(
                            single(title, group, ctx),
                            new ExportBlock.Cell(JsonUtil.dash(objective)),
                            objectives.isEmpty() ? new ExportBlock.Cell("—") : new ExportBlock.Cell(objectives)));
                }
            }
            if (!rows.isEmpty()) {
                axes.add(new AxisRows(axis, rows));
            }
        }
        if (axes.isEmpty()) {
            return List.of(new ExportBlock.Paragraph("Aucun axe d'intervention n'est encore approuvé.", true, false));
        }
        // Aucune direction n'a formule l'objectif de son axe : une colonne de tirets n'apprend rien au
        // lecteur. La decision vaut pour tous les axes, pour que leurs tableaux gardent la meme forme.
        boolean withObjective = anyObjective;
        List<String> headers = withObjective
                ? List.of("Axe d'intervention de la direction", "Objectif de l'axe", "Objectifs spécifiques")
                : List.of("Axe d'intervention de la direction", "Objectifs spécifiques");
        List<Integer> widths = withObjective ? List.of(26, 34, 40) : List.of(35, 65);

        List<ExportBlock> b = new ArrayList<>();
        b.add(new ExportBlock.Paragraph("Sous chaque axe stratégique de l'entreprise figurent les axes d'intervention "
                + "formulés par les directions qu'il regroupe, " + (withObjective ? "leur objectif et " : "")
                + "leurs objectifs spécifiques, chacun à la couleur de la direction qui le porte."));
        for (AxisRows entry : axes) {
            b.add(new ExportBlock.Heading(entry.axis().title(), 3));
            if (!entry.axis().objective().isBlank()) {
                b.add(new ExportBlock.Paragraph("Objectif : " + entry.axis().objective()));
            }
            List<ExportBlock.TableRow> rows = entry.rows().stream()
                    .map(cells -> new ExportBlock.TableRow(withObjective ? cells : List.of(cells.get(0), cells.get(2))))
                    .toList();
            b.add(new ExportBlock.Table(headers, rows, widths));
        }
        return b;
    }

    /**
     * Cadre logique de chaque axe, sur le modele du canevas : de l'impact aux ressources, la logique
     * d'intervention, ses indicateurs objectivement verifiables, leurs moyens de verification et les
     * conditions critiques. Sous un axe de l'entreprise, chaque case reunit les cadres logiques des
     * directions qu'il regroupe, chacun a sa couleur ; une formulation commune n'y figure qu'une fois.
     * La synthese du cadre logique de chaque direction (S09B) se lit a part (cf. {@link #logframeSyntheses}).
     */
    private List<ExportBlock> logicalFrameworks(Context ctx) {
        String[] fields = {"interventionLogic", "iov", "verificationMeans", "assumptions"};
        List<ExportBlock> tables = new ArrayList<>();
        for (AxisView axis : axisViews(ctx)) {
            Map<String, List<Contributions>> cellsByLevel = new LinkedHashMap<>();
            for (String level : DefaultSectionContentFactory.LOGFRAME_LEVELS) {
                cellsByLevel.put(level, Stream.generate(() -> new Contributions(ctx.groups)).limit(fields.length).toList());
            }
            Contributions objectives = new Contributions(ctx.groups);
            for (String key : axis.keys()) {
                WorkGroup group = ctx.groupOf(key);
                if (group == null) {
                    continue;
                }
                for (JsonNode direction : ctx.axisEntries(group, "S09", key)) {
                    objectives.add(group, JsonUtil.text(direction, "objective"));
                    for (JsonNode row : JsonUtil.arr(direction, "rows")) {
                        List<Contributions> cells = cellsByLevel.get(JsonUtil.text(row, "level"));
                        if (cells == null) {
                            continue;
                        }
                        for (int f = 0; f < fields.length; f++) {
                            cells.get(f).addLines(group, JsonUtil.text(row, fields[f]));
                        }
                    }
                }
            }
            if (cellsByLevel.values().stream().flatMap(List::stream).allMatch(Contributions::isEmpty)) {
                continue;
            }
            tables.add(new ExportBlock.Heading(axis.title(), 3));
            if (!axis.objective().isBlank()) {
                tables.add(new ExportBlock.Paragraph("Objectif : " + axis.objective()));
            } else if (!objectives.isEmpty()) {
                tables.add(new ExportBlock.AttributedList("Objectif", objectives.toAttributions()));
            }
            List<ExportBlock.TableRow> rows = new ArrayList<>();
            cellsByLevel.forEach((level, cells) -> rows.add(new ExportBlock.TableRow(List.of(
                    rowLabel(SectionLabels.logframe(level)), attributedCell(cells.get(0)), attributedCell(cells.get(1)),
                    attributedCell(cells.get(2)), attributedCell(cells.get(3))))));
            tables.add(new ExportBlock.Table(List.of("Logique d'intervention", "Énoncé",
                    "Indicateurs objectivement vérifiables (IOV)", "Moyens et sources de vérification",
                    "Conditions critiques / hypothèses"), rows, List.of(17, 24, 21, 19, 19)));
        }
        if (tables.isEmpty()) {
            return List.of(new ExportBlock.Paragraph("Aucun cadre logique n'est encore approuvé.", true, false));
        }
        List<ExportBlock> b = new ArrayList<>();
        b.add(new ExportBlock.Paragraph("Le cadre logique décline, pour chaque axe stratégique, la chaîne des résultats "
                + "attendus, de l'impact visé aux ressources mobilisées, les indicateurs objectivement vérifiables qui les "
                + "mesurent, leurs moyens et sources de vérification et les conditions critiques de leur atteinte. Sous "
                + "chaque axe sont réunis les cadres logiques des directions qu'il regroupe, chacun à sa couleur."));
        b.addAll(tables);
        return b;
    }

    /**
     * Synthese du cadre logique (S09B) : la lecture d'ensemble que chaque direction en donne. Le client la garde
     * dans le corps du document, en texte sous le nom de la direction, et en a retire le tableau.
     */
    private List<ExportBlock> logframeSyntheses(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();
        for (WorkGroup group : ctx.groups) {
            String note = JsonUtil.text(ctx.content(group, "S09B"), "synthesisNote").trim();
            if (note.isEmpty()) {
                continue;
            }
            if (b.isEmpty()) {
                b.add(new ExportBlock.Heading("Synthèse du cadre logique", 3));
            }
            b.add(new ExportBlock.Heading(group.getName(), 4));
            b.add(new ExportBlock.Paragraph(note));
        }
        return b;
    }

    /**
     * Texte d'une rubrique dont les tableaux sont reportes en annexe : son introduction, puis le renvoi a
     * l'annexe. Sans tableau, la rubrique garde son seul message (« Aucun ... n'est encore approuvé »).
     */
    private static List<ExportBlock> introOnly(List<ExportBlock> blocks, String annex) {
        List<ExportBlock> intro = new ArrayList<>();
        for (ExportBlock block : blocks) {
            if (!(block instanceof ExportBlock.Paragraph)) {
                break;
            }
            intro.add(block);
        }
        if (blocks.stream().anyMatch(ExportBlock.Table.class::isInstance)) {
            intro.add(new ExportBlock.Paragraph("Les tableaux par axe figurent en annexe (" + annex + ")."));
        }
        return intro;
    }

    /** Tableaux d'une rubrique reportes en annexe, sous leurs intertitres, sans l'introduction deja lue dans le corps. */
    private static List<ExportBlock> withoutIntro(List<ExportBlock> blocks) {
        if (blocks.stream().noneMatch(ExportBlock.Table.class::isInstance)) {
            return blocks;
        }
        int start = 0;
        while (start < blocks.size() && blocks.get(start) instanceof ExportBlock.Paragraph) {
            start++;
        }
        return blocks.subList(start, blocks.size());
    }

    /**
     * Plan d'actions de chaque axe, sur le modele du canevas : sous chaque effet (OS), les extrants,
     * les activites, les exercices ou elles sont programmees et les responsables. Les OS portent le
     * numero de la synthese du cadre strategique.
     */
    private List<ExportBlock> actionPlans(Context ctx) {
        List<ExportBlock> tables = new ArrayList<>();
        for (AxisView axis : axisViews(ctx)) {
            List<ExportBlock.TableRow> rows = new ArrayList<>();
            int os = 0;
            for (String key : axis.keys()) {
                for (PlanOrientation orientation : ctx.orientationsOf(key)) {
                    os++;
                    if (orientation.programmed().isEmpty()) {
                        continue;
                    }
                    rows.add(effectBand(os, orientation));
                    for (ActionRow action : orientation.programmed()) {
                        List<ExportBlock.Cell> cells = new ArrayList<>();
                        cells.add(extrantCell(action, ctx));
                        cells.add(single(action.activity(), action.group(), ctx));
                        for (String year : YEARS) {
                            cells.add(center(ctx.scheduled(action, year) ? "✓" : ""));
                        }
                        cells.add(new ExportBlock.Cell(JsonUtil.dash(action.responsible())));
                        rows.add(new ExportBlock.TableRow(cells));
                    }
                }
            }
            if (rows.isEmpty()) {
                continue;
            }
            List<String> headers = new ArrayList<>(List.of("Extrants", "Activités pour atteindre les résultats"));
            headers.addAll(List.of(YEARS));
            headers.add("Responsables");
            List<Integer> widths = new ArrayList<>(List.of(19, 29));
            widths.addAll(Collections.nCopies(YEARS.length, 7));
            widths.add(17);
            tables.add(new ExportBlock.Heading(axis.title(), 3));
            tables.add(new ExportBlock.Table(headers, rows, widths));
        }
        if (tables.isEmpty()) {
            return List.of(new ExportBlock.Paragraph("Aucun plan d'actions n'est encore approuvé.", true, false));
        }
        List<ExportBlock> b = new ArrayList<>();
        b.add(new ExportBlock.Paragraph("Le plan d'actions " + PERIOD + " opérationnalise chaque axe : pour chaque "
                + "orientation stratégique (OS), numérotée comme dans la synthèse du cadre stratégique, les extrants "
                + "attendus, les activités qui les produisent, les exercices où elles sont programmées (✓) et les "
                + "structures responsables."));
        b.addAll(tables);
        return b;
    }

    /**
     * Budget detaille de chaque axe, sur le modele du canevas : sous chaque effet (OS), le cout de
     * chaque activite par exercice, son total et son responsable, puis le total de l'axe.
     *
     * @param withGrandTotal clot les tableaux par le total general du canevas, axe par axe et par
     *                       exercice ; la note l'omet, sa rubrique Budget s'ouvrant deja sur ce tableau
     */
    private List<ExportBlock> detailedBudgets(Context ctx, boolean withGrandTotal) {
        List<ExportBlock> tables = new ArrayList<>();
        List<ExportBlock.TableRow> grandRows = new ArrayList<>();
        double[] grandYears = new double[YEARS.length];
        double grandTotal = 0;
        for (AxisView axis : axisViews(ctx)) {
            List<ExportBlock.TableRow> rows = new ArrayList<>();
            double[] axisYears = new double[YEARS.length];
            double axisTotal = 0;
            int os = 0;
            for (String key : axis.keys()) {
                for (PlanOrientation orientation : ctx.orientationsOf(key)) {
                    os++;
                    if (orientation.programmed().isEmpty()) {
                        continue;
                    }
                    rows.add(effectBand(os, orientation));
                    for (ActionRow action : orientation.programmed()) {
                        List<ExportBlock.Cell> cells = new ArrayList<>();
                        cells.add(extrantCell(action, ctx));
                        cells.add(single(action.activity(), action.group(), ctx));
                        double rowTotal = 0;
                        for (int y = 0; y < YEARS.length; y++) {
                            double amount = amount(action, YEARS[y]);
                            axisYears[y] += amount;
                            rowTotal += amount;
                            cells.add(right(amount > 0 ? JsonUtil.formatMillions(amount) : "—"));
                        }
                        if (rowTotal <= 0) {
                            rowTotal = action.cost();
                        }
                        axisTotal += rowTotal;
                        cells.add(new ExportBlock.Cell(rowTotal > 0 ? JsonUtil.formatMillions(rowTotal) : "—", true,
                                ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
                        cells.add(new ExportBlock.Cell(JsonUtil.dash(action.responsible())));
                        rows.add(new ExportBlock.TableRow(cells));
                    }
                }
            }
            if (rows.isEmpty()) {
                continue;
            }
            List<ExportBlock.Cell> total = new ArrayList<>();
            total.add(new ExportBlock.Cell("Total de l'axe", true, ExportBlock.Align.LEFT, ExportBlock.Background.NONE));
            total.add(new ExportBlock.Cell(""));
            for (double yearTotal : axisYears) {
                total.add(new ExportBlock.Cell(JsonUtil.formatMillions(yearTotal), true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
            }
            total.add(new ExportBlock.Cell(JsonUtil.formatMillions(axisTotal), true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
            total.add(new ExportBlock.Cell(""));
            rows.add(new ExportBlock.TableRow(total, true, ExportBlock.Background.PRIMARY_LIGHT));

            List<String> headers = new ArrayList<>(List.of("Extrants", "Activités pour atteindre les résultats"));
            headers.addAll(List.of(YEARS));
            headers.addAll(List.of("Totaux", "Responsable"));
            List<Integer> widths = new ArrayList<>(List.of(14, 20));
            widths.addAll(Collections.nCopies(YEARS.length, 8));
            widths.addAll(List.of(10, 16));
            tables.add(new ExportBlock.Heading("Budget détaillé — " + axis.title(), 3));
            tables.add(new ExportBlock.Table(headers, rows, widths));

            List<ExportBlock.Cell> recap = new ArrayList<>();
            recap.add(new ExportBlock.Cell(axis.title()));
            for (int y = 0; y < YEARS.length; y++) {
                grandYears[y] += axisYears[y];
                recap.add(right(JsonUtil.formatMillions(axisYears[y])));
            }
            recap.add(new ExportBlock.Cell(JsonUtil.formatMillions(axisTotal), true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
            grandRows.add(new ExportBlock.TableRow(recap));
            grandTotal += axisTotal;
        }
        if (tables.isEmpty()) {
            return List.of();
        }
        if (withGrandTotal) {
            List<ExportBlock.Cell> grand = new ArrayList<>();
            grand.add(new ExportBlock.Cell("TOTAL GÉNÉRAL", true, ExportBlock.Align.LEFT, ExportBlock.Background.NONE));
            for (double yearTotal : grandYears) {
                grand.add(new ExportBlock.Cell(JsonUtil.formatMillions(yearTotal), true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
            }
            grand.add(new ExportBlock.Cell(JsonUtil.formatMillions(grandTotal), true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
            grandRows.add(new ExportBlock.TableRow(grand, true, ExportBlock.Background.PRIMARY_LIGHT));

            List<String> headers = new ArrayList<>(List.of("Axe stratégique"));
            headers.addAll(List.of(YEARS));
            headers.add("Totaux");
            List<Integer> widths = new ArrayList<>(List.of(35));
            widths.addAll(Collections.nCopies(YEARS.length, 11));
            widths.add(10);
            tables.add(new ExportBlock.Heading("Total général du budget", 3));
            tables.add(new ExportBlock.Table(headers, grandRows, widths));
        }
        List<ExportBlock> b = new ArrayList<>();
        b.add(new ExportBlock.Paragraph("Le budget détaillé reprend, pour chaque axe et chaque orientation stratégique, "
                + "le coût prévisionnel de chaque activité par exercice, en millions de FCFA, et la structure qui en a la charge."));
        b.addAll(tables);
        return b;
    }

    /**
     * Cadre de mesure de rendement de chaque axe, sur le modele du canevas : pour chaque niveau de
     * resultat, l'indicateur, sa reference, ses cibles annuelles et le responsable de son suivi.
     */
    private List<ExportBlock> performanceFrameworks(Context ctx) {
        List<ExportBlock> tables = new ArrayList<>();
        for (AxisView axis : axisViews(ctx)) {
            List<ExportBlock.TableRow> rows = new ArrayList<>();
            for (String level : DefaultSectionContentFactory.LOGFRAME_LEVELS) {
                List<ExportBlock.TableRow> levelRows = new ArrayList<>();
                Set<String> seenRows = new java.util.HashSet<>();
                for (String key : axis.keys()) {
                    WorkGroup group = ctx.groupOf(key);
                    if (group == null) {
                        continue;
                    }
                    for (JsonNode direction : ctx.axisEntries(group, "S12", key)) {
                        for (JsonNode performanceGroup : JsonUtil.arr(direction, "groups")) {
                            if (!level.equals(JsonUtil.text(performanceGroup, "level"))) {
                                continue;
                            }
                            for (JsonNode row : JsonUtil.arr(performanceGroup, "rows")) {
                                String result = JsonUtil.text(row, "resultOrExtrant").trim();
                                String indicator = JsonUtil.text(row, "indicator").trim();
                                if (result.isEmpty() && indicator.isEmpty()) {
                                    continue;
                                }
                                List<ExportBlock.Cell> cells = new ArrayList<>();
                                cells.add(result.isEmpty() ? new ExportBlock.Cell("—") : single(result, group, ctx));
                                cells.add(indicator.isEmpty() ? new ExportBlock.Cell("—") : single(indicator, group, ctx));
                                cells.add(new ExportBlock.Cell(JsonUtil.dash(JsonUtil.text(row, "ref2026").trim())));
                                JsonNode years = row.get("years");
                                for (String year : YEARS) {
                                    cells.add(center(JsonUtil.dash(JsonUtil.text(years, year).trim())));
                                }
                                cells.add(new ExportBlock.Cell(JsonUtil.dash(JsonUtil.text(row, "responsible").trim())));
                                // Les axes d'une meme direction regroupes sous un axe de l'entreprise portent
                                // souvent la meme ligne generique (« Realisations et livrables de l'axe ») :
                                // repetee a l'identique, elle n'apprend rien et n'est ecrite qu'une fois.
                                // Le resultat et l'indicateur sont des cellules attribuees : leur texte est
                                // dans les attributions, pas dans text(). Les ignorer confondrait deux lignes
                                // distinctes aux memes cibles.
                                String signature = group.getName() + "|" + cells.stream()
                                        .map(cell -> cell.text() + "/" + (cell.attributions() == null ? "" : cell.attributions()
                                                .stream().map(ExportBlock.Attribution::text)
                                                .collect(java.util.stream.Collectors.joining("/"))))
                                        .collect(java.util.stream.Collectors.joining("|"));
                                if (seenRows.add(signature)) {
                                    levelRows.add(new ExportBlock.TableRow(cells));
                                }
                            }
                        }
                    }
                }
                if (!levelRows.isEmpty()) {
                    rows.add(ExportBlock.TableRow.band(performanceBand(level), ExportBlock.Background.GREY));
                    rows.addAll(levelRows);
                }
            }
            if (rows.isEmpty()) {
                continue;
            }
            List<String> headers = new ArrayList<>(List.of("Résultat / extrant", "Indicateur (IOV)", "Réf. " + REVIEW_YEAR));
            headers.addAll(List.of(YEARS));
            headers.add("Responsables");
            List<Integer> widths = new ArrayList<>(List.of(21, 21, 10));
            widths.addAll(Collections.nCopies(YEARS.length, 7));
            widths.add(13);
            List<ExportBlock.HeaderBand> bands = List.of(new ExportBlock.HeaderBand("Résultats et indicateurs", 3),
                    new ExportBlock.HeaderBand("Cibles", YEARS.length), new ExportBlock.HeaderBand("", 1));
            tables.add(new ExportBlock.Heading(axis.title(), 3));
            tables.add(new ExportBlock.Table(headers, rows, widths, bands));
        }
        if (tables.isEmpty()) {
            return List.of(new ExportBlock.Paragraph("Aucun cadre de mesure de rendement n'est encore approuvé.", true, false));
        }
        List<ExportBlock> b = new ArrayList<>();
        b.add(new ExportBlock.Paragraph("Le cadre de mesure de rendement fixe, pour chaque niveau de résultat de chaque "
                + "axe, l'indicateur retenu, sa valeur de référence " + REVIEW_YEAR + ", les cibles annuelles jusqu'en "
                + LAST_YEAR + " et la structure responsable de son suivi."));
        b.addAll(tables);
        return b;
    }

    private static String performanceBand(String level) {
        return switch (level) {
            case "IMPACT" -> "IMPACT (Finalité) — horizon " + LAST_YEAR;
            case "EFFET" -> "EFFET (Objectif spécifique)";
            case "EFFETS_IMMEDIATS" -> "EFFETS IMMÉDIATS (Résultats immédiats par OS)";
            case "EXTRANTS" -> "EXTRANTS (Produits)";
            case "RESSOURCES_INTRANTS" -> "RESSOURCES / INTRANTS (Moyens)";
            default -> SectionLabels.logframe(level);
        };
    }

    private static ExportBlock.TableRow effectBand(int os, PlanOrientation orientation) {
        return ExportBlock.TableRow.band("EFFET " + os + " — OS" + os + " : " + JsonUtil.dash(orientation.label()),
                ExportBlock.Background.PRIMARY_LIGHT);
    }

    private static ExportBlock.Cell extrantCell(ActionRow action, Context ctx) {
        return action.extrant().isEmpty() ? new ExportBlock.Cell("—") : single(action.extrant(), action.group(), ctx);
    }

    /** Montant budgete d'une action pour un exercice ; le plan d'actions (S10) ne porte que des coches. */
    private static double amount(ActionRow action, String year) {
        JsonNode value = action.years() == null ? null : action.years().get(year);
        return value == null || value.isNull() || value.isBoolean() ? 0 : value.asDouble();
    }

    /** Une ligne du budget : un axe de l'entreprise, ou une direction quand les axes ne sont pas arretes. */
    private record BudgetLine(String label, String shortLabel, String color, double[] years) {
    }

    private List<BudgetLine> budgetLines(Context ctx) {
        List<BudgetLine> lines = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        if (ctx.plan.consolidated()) {
            for (int i = 0; i < ctx.plan.axes().size(); i++) {
                PlanAxis axis = ctx.plan.axes().get(i);
                covered.addAll(axis.keys());
                lines.add(new BudgetLine("Axe " + (i + 1) + " : " + axis.title(), "Axe " + (i + 1), axis.color(),
                        ctx.budgetOf(axis.keys())));
            }
            List<String> rest = ctx.budgetByKey.keySet().stream().filter(key -> !covered.contains(key)).toList();
            double[] unlinked = ctx.budgetOf(rest);
            if (sum(unlinked) > 0) {
                lines.add(new BudgetLine("Axes non rattachés", "Non rattachés", UNLINKED_COLOR, unlinked));
            }
            return lines;
        }
        for (WorkGroup group : ctx.groups) {
            List<String> keys = ctx.budgetByKey.keySet().stream()
                    .filter(key -> ctx.groupByKey.get(key) == group).toList();
            double[] years = ctx.budgetOf(keys);
            if (sum(years) > 0) {
                lines.add(new BudgetLine(group.getName(), group.getName(), colorOf(group, ctx.groups), years));
            }
        }
        return lines;
    }

    private List<ExportBlock> budgetBlocks(Context ctx) {
        return budgetBlocks(ctx, true);
    }

    /**
     * @param withTable pose le tableau par exercice ; la note le reporte en annexe (Tableau 4) et garde le
     *                  texte, le graphique et sa lecture
     */
    private List<ExportBlock> budgetBlocks(Context ctx, boolean withTable) {
        List<ExportBlock> b = new ArrayList<>();
        List<BudgetLine> lines = budgetLines(ctx);
        double total = lines.stream().mapToDouble(line -> sum(line.years())).sum();
        if (total <= 0) {
            b.add(new ExportBlock.Paragraph("Le budget consolidé n'est pas encore renseigné dans les sections approuvées.", true, false));
            return b;
        }
        String dimension = ctx.plan.consolidated() ? "axe stratégique" : "direction";
        b.add(new ExportBlock.Paragraph("Le budget prévisionnel du plan s'élève à " + JsonUtil.formatAmountLabel(total)
                + " sur la période " + PERIOD + ". " + (withTable ? "Il est réparti ci-dessous" : "Sa répartition est illustrée ci-dessous")
                + " par " + dimension + " et par exercice, en millions de FCFA."));

        List<String> headers = new ArrayList<>();
        headers.add(ctx.plan.consolidated() ? "Axe stratégique" : "Direction");
        headers.addAll(List.of(YEARS));
        headers.add("Total");
        headers.add("Part");
        List<Integer> widths = new ArrayList<>(List.of(29));
        widths.addAll(java.util.Collections.nCopies(YEARS.length, 9));
        widths.add(11);
        widths.add(8);

        double[] yearTotals = new double[YEARS.length];
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        for (BudgetLine line : lines) {
            List<ExportBlock.Cell> cells = new ArrayList<>();
            cells.add(new ExportBlock.Cell(line.label()));
            for (int y = 0; y < YEARS.length; y++) {
                cells.add(right(JsonUtil.formatMillions(line.years()[y])));
                yearTotals[y] += line.years()[y];
            }
            cells.add(new ExportBlock.Cell(JsonUtil.formatMillions(sum(line.years())), true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
            cells.add(right(JsonUtil.formatPercent(sum(line.years()) / total * 100)));
            rows.add(new ExportBlock.TableRow(cells));
        }
        List<ExportBlock.Cell> totalCells = new ArrayList<>();
        totalCells.add(new ExportBlock.Cell("TOTAL", true, ExportBlock.Align.LEFT, ExportBlock.Background.NONE));
        for (double yearTotal : yearTotals) {
            totalCells.add(new ExportBlock.Cell(JsonUtil.formatMillions(yearTotal), true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
        }
        totalCells.add(new ExportBlock.Cell(JsonUtil.formatMillions(total), true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
        totalCells.add(new ExportBlock.Cell("100 %", true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
        rows.add(new ExportBlock.TableRow(totalCells, true, ExportBlock.Background.PRIMARY_LIGHT));
        if (withTable) {
            b.add(new ExportBlock.Table(headers, rows, widths));
        }

        List<ExportBlock.ChartSeries> series = new ArrayList<>();
        for (BudgetLine line : lines) {
            List<Double> values = new ArrayList<>();
            for (double value : line.years()) {
                values.add(value);
            }
            series.add(new ExportBlock.ChartSeries(ctx.plan.consolidated() ? line.label() : line.shortLabel(), line.color(), values));
        }
        b.add(new ExportBlock.Chart(ExportBlock.ChartKind.STACKED_COLUMNS,
                "Répartition annuelle du budget par " + dimension, "En millions de FCFA — total par exercice au-dessus de chaque colonne",
                List.of(YEARS), series));

        BudgetLine top = lines.stream().max(Comparator.comparingDouble(line -> sum(line.years()))).orElseThrow();
        String topLabel = ctx.plan.consolidated() ? "l'axe « " + top.label().replaceFirst("^Axe \\d+ : ", "") + " »" : top.label();
        StringBuilder reading = new StringBuilder(topLabel + " concentre "
                + JsonUtil.formatPercent(sum(top.years()) / total * 100) + " de l'enveloppe.");
        if (yearTotals[0] > 0) {
            double change = (yearTotals[YEARS.length - 1] - yearTotals[0]) / yearTotals[0] * 100;
            reading.append(" Le budget annuel prévu passera de ").append(JsonUtil.formatMillions(yearTotals[0]))
                    .append(" millions FCFA en ").append(FIRST_YEAR).append(" à ")
                    .append(JsonUtil.formatMillions(yearTotals[YEARS.length - 1])).append(" millions FCFA en ")
                    .append(LAST_YEAR).append(" (").append(change >= 0 ? "+" : "")
                    .append(JsonUtil.formatPercent(change)).append(").");
        }
        b.add(analysis(lowerFirst(reading.toString())));
        return b;
    }

    private List<ExportBlock> financing(Context ctx) {
        PsdKeyFigures f = ctx.figures;
        Map<String, Double> bySource = new LinkedHashMap<>();
        for (String source : DefaultSectionContentFactory.FINANCING_SOURCES) {
            bySource.put(source, 0d);
        }
        // Modalites, periode et responsables du canevas, par source, dans la couleur de chaque direction.
        Map<String, List<Contributions>> detailsBySource = new LinkedHashMap<>();
        for (WorkGroup group : ctx.groups) {
            for (JsonNode row : JsonUtil.arr(ctx.content(group, "S15"), "rows")) {
                String source = JsonUtil.text(row, "source");
                bySource.merge(source, JsonUtil.num(row, "amount"), Double::sum);
                List<Contributions> details = detailsBySource.computeIfAbsent(source, k -> List.of(
                        new Contributions(ctx.groups), new Contributions(ctx.groups), new Contributions(ctx.groups)));
                details.get(0).addLines(group, JsonUtil.text(row, "modalities"));
                details.get(1).addLines(group, JsonUtil.text(row, "period"));
                details.get(2).addLines(group, JsonUtil.text(row, "responsible"));
            }
        }
        double financingTotal = bySource.values().stream().mapToDouble(Double::doubleValue).sum();
        if (financingTotal <= 0) {
            return List.of(new ExportBlock.Paragraph("Le plan de financement n'est pas encore renseigné dans les sections approuvées.", true, false));
        }
        double budget = f.budget();
        List<ExportBlock> b = new ArrayList<>();
        b.add(new ExportBlock.Paragraph("Le financement identifié par les directions s'élève à "
                + JsonUtil.formatAmountLabel(financingTotal) + ". Le tableau le détaille par source et le rapporte au "
                + "budget du plan (montants en millions de FCFA)."));

        List<ExportBlock.TableRow> rows = new ArrayList<>();
        Map.Entry<String, Double> top = null;
        for (Map.Entry<String, Double> entry : bySource.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            if (top == null || entry.getValue() > top.getValue()) {
                top = entry;
            }
            List<Contributions> details = detailsBySource.get(entry.getKey());
            rows.add(new ExportBlock.TableRow(List.of(
                    new ExportBlock.Cell(SectionLabels.financing(entry.getKey())),
                    right(JsonUtil.formatMillions(entry.getValue())),
                    right(JsonUtil.formatPercent(entry.getValue() / financingTotal * 100)),
                    right(budget > 0 ? JsonUtil.formatPercent(entry.getValue() / budget * 100) : "—"),
                    attributedCell(details == null ? null : details.get(0)),
                    attributedCell(details == null ? null : details.get(1)),
                    attributedCell(details == null ? null : details.get(2)))));
        }
        rows.add(new ExportBlock.TableRow(List.of(
                new ExportBlock.Cell("Total du financement identifié", true, ExportBlock.Align.LEFT, ExportBlock.Background.NONE),
                new ExportBlock.Cell(JsonUtil.formatMillions(financingTotal), true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE),
                new ExportBlock.Cell("100 %", true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE),
                new ExportBlock.Cell(budget > 0 ? JsonUtil.formatPercent(financingTotal / budget * 100) : "—", true,
                        ExportBlock.Align.RIGHT, ExportBlock.Background.NONE)), true, ExportBlock.Background.GREY));
        if (budget > financingTotal) {
            double gap = budget - financingTotal;
            rows.add(new ExportBlock.TableRow(List.of(
                    new ExportBlock.Cell("Reste à mobiliser", true, ExportBlock.Align.LEFT, ExportBlock.Background.NONE),
                    new ExportBlock.Cell(JsonUtil.formatMillions(gap), true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE),
                    right("—"),
                    new ExportBlock.Cell(JsonUtil.formatPercent(gap / budget * 100), true, ExportBlock.Align.RIGHT,
                            ExportBlock.Background.NONE)), true, ExportBlock.Background.ORANGE));
        }
        if (budget > 0) {
            rows.add(totalRow("Budget du plan", JsonUtil.formatMillions(budget), "—", "100 %"));
        }
        b.add(new ExportBlock.Table(List.of("Sources de financement", "Montant (M FCFA)", "Pourcentage (%)", "Part du budget",
                "Modalités de mobilisation", "Période", "Responsables"), rows, List.of(16, 11, 13, 10, 20, 12, 18)));

        if (budget > 0) {
            double covered = Math.min(financingTotal, budget);
            double gap = Math.max(0, budget - financingTotal);
            b.add(new ExportBlock.Chart(ExportBlock.ChartKind.STACKED_BAR,
                    "Couverture du budget par le financement identifié",
                    "Budget du plan : " + JsonUtil.formatAmountLabel(budget),
                    List.of("Budget"),
                    List.of(new ExportBlock.ChartSeries("Financement identifié — "
                                    + JsonUtil.formatPercent(covered / budget * 100), COVERED_COLOR, List.of(covered)),
                            new ExportBlock.ChartSeries("Reste à mobiliser — "
                                    + JsonUtil.formatPercent(gap / budget * 100), GAP_COLOR, List.of(gap)))));
        }

        StringBuilder reading = new StringBuilder();
        if (top != null) {
            reading.append("les ").append(lowerFirst(SectionLabels.financing(top.getKey())))
                    .append(" constituent la première source, avec ")
                    .append(JsonUtil.formatPercent(top.getValue() / financingTotal * 100))
                    .append(" du financement identifié. ");
        }
        if (budget > financingTotal && budget > 0) {
            reading.append("Le financement identifié couvre ").append(JsonUtil.formatPercent(financingTotal / budget * 100))
                    .append(" du budget : ").append(JsonUtil.formatAmountLabel(budget - financingTotal))
                    .append(" restent à mobiliser, principal point de vigilance de la mise en œuvre.");
        } else if (budget > 0) {
            reading.append("Le financement identifié couvre l'intégralité du budget du plan.");
        }
        b.add(analysis(reading.toString().trim()));
        return b;
    }

    /**
     * Plan d'evolution des effectifs sur le modele du client : par hierarchie et par statut, hommes,
     * femmes et total de chaque exercice, toutes directions additionnees. Le TOTAUX suit la
     * hierarchie (cf. DerivedFieldsService) : les deux blocs ventilent les memes agents.
     */
    private List<ExportBlock> staff(Context ctx) {
        Map<String, String> categoryOf = new LinkedHashMap<>();
        Map<String, String> labelOf = new LinkedHashMap<>();
        Map<String, int[][]> valuesOf = new LinkedHashMap<>();
        int[][] totals = new int[YEARS.length][2];
        for (WorkGroup group : ctx.groups) {
            JsonNode content = ctx.content(group, "S14B");
            for (JsonNode row : JsonUtil.arr(content, "rows")) {
                String staffKey = JsonUtil.text(row, "staffKey").trim();
                String label = SectionLabels.staffRow(staffKey, JsonUtil.text(row, "label")).trim();
                if (label.isEmpty()) {
                    continue;
                }
                String category = JsonUtil.text(row, "category");
                // Une ligne du modele se reconnait a sa cle, une ligne libre a son intitule.
                String key = category + "|" + (staffKey.isEmpty() ? PsdCrossGroupMerge.normalize(label) : staffKey);
                categoryOf.putIfAbsent(key, category);
                labelOf.putIfAbsent(key, label);
                int[][] values = valuesOf.computeIfAbsent(key, k -> new int[YEARS.length][2]);
                JsonNode years = row.get("years");
                for (int y = 0; y < YEARS.length; y++) {
                    JsonNode cell = years == null ? null : years.get(YEARS[y]);
                    values[y][0] += (int) JsonUtil.num(cell, "male");
                    values[y][1] += (int) JsonUtil.num(cell, "female");
                }
            }
            JsonNode groupTotals = content.get("totals");
            for (int y = 0; y < YEARS.length; y++) {
                JsonNode cell = groupTotals == null ? null : groupTotals.get(YEARS[y]);
                totals[y][0] += (int) JsonUtil.num(cell, "male");
                totals[y][1] += (int) JsonUtil.num(cell, "female");
            }
        }
        boolean any = false;
        for (int[] year : totals) {
            any |= year[0] + year[1] > 0;
        }
        if (!any) {
            return List.of(new ExportBlock.Paragraph("Le plan d'évolution des effectifs n'est pas encore renseigné dans les sections approuvées.", true, false));
        }

        List<String> headers = new ArrayList<>(List.of("Effectifs"));
        List<ExportBlock.HeaderBand> bands = new ArrayList<>(List.of(new ExportBlock.HeaderBand("Années", 1)));
        List<Integer> widths = new ArrayList<>(List.of(16));
        for (String year : YEARS) {
            bands.add(new ExportBlock.HeaderBand(year, 3));
            headers.addAll(List.of("M", "F", "Total"));
            widths.addAll(List.of(5, 5, 6));
        }
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        for (String category : categoryOf.values().stream().distinct().toList()) {
            rows.add(ExportBlock.TableRow.band(category.isBlank() ? "Autres" : SectionLabels.staffCategory(category),
                    ExportBlock.Background.GREY));
            for (Map.Entry<String, String> entry : categoryOf.entrySet()) {
                if (entry.getValue().equals(category)) {
                    rows.add(staffLine(labelOf.get(entry.getKey()), valuesOf.get(entry.getKey()), false));
                }
            }
        }
        rows.add(staffLine("TOTAUX", totals, true));

        int last = YEARS.length - 1;
        int firstTotal = totals[0][0] + totals[0][1];
        int lastTotal = totals[last][0] + totals[last][1];
        double firstShare = firstTotal > 0 ? totals[0][1] * 100d / firstTotal : 0;
        double lastShare = lastTotal > 0 ? totals[last][1] * 100d / lastTotal : 0;
        String change = firstTotal > 0
                ? " (" + (lastTotal >= firstTotal ? "+" : "") + JsonUtil.formatPercent((lastTotal - firstTotal) * 100d / firstTotal) + ")"
                : "";
        return List.of(
                new ExportBlock.Paragraph("Le plan d'évolution des effectifs consolide, par hiérarchie et par statut, les "
                        + "effectifs prévus par les directions pour conduire les actions programmées (M : hommes, F : femmes)."),
                new ExportBlock.Table(headers, rows, widths, bands),
                analysis("les effectifs prévus passeront de " + JsonUtil.formatNumber(firstTotal) + " agents en " + FIRST_YEAR
                        + " à " + JsonUtil.formatNumber(lastTotal) + " en " + LAST_YEAR + change
                        + " ; la part des femmes passera de " + JsonUtil.formatDecimal(firstShare) + " % à "
                        + JsonUtil.formatDecimal(lastShare) + " %. Le total suit la ventilation par hiérarchie : la "
                        + "hiérarchie et le statut répartissent les mêmes agents."));
    }

    private ExportBlock.TableRow staffLine(String label, int[][] values, boolean total) {
        List<ExportBlock.Cell> cells = new ArrayList<>();
        cells.add(new ExportBlock.Cell(label, total, ExportBlock.Align.LEFT, ExportBlock.Background.NONE));
        for (int[] year : values) {
            cells.add(right(JsonUtil.formatNumber(year[0])));
            cells.add(right(JsonUtil.formatNumber(year[1])));
            cells.add(new ExportBlock.Cell(JsonUtil.formatNumber(year[0] + year[1]), true, ExportBlock.Align.RIGHT,
                    ExportBlock.Background.NONE));
        }
        return total ? new ExportBlock.TableRow(cells, true, ExportBlock.Background.PRIMARY_LIGHT) : new ExportBlock.TableRow(cells);
    }

    // ---- XI. Cadre de pilotage et de suivi-evaluation ----

    private List<ExportBlock> monitoring(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();
        sub(b, "XI.1 Dispositif de pilotage");
        narrative(b, ctx, NarrativeBlockKey.DISPOSITIF_PILOTAGE);

        sub(b, "XI.2 Cadre de mesure de rendement");
        b.addAll(introOnly(performanceFrameworks(ctx), "Tableau 7 : Cadre de mesure de rendement"));

        // Les tableaux de suivi des indicateurs sont reportes en annexe, avec la fiche des indicateurs (Tableau 3).
        sub(b, "XI.3 Indicateurs de suivi");
        b.addAll(indicatorFollowUp(ctx).stream().filter(block -> !(block instanceof ExportBlock.Table)).toList());
        return b;
    }

    /** Indicateurs de suivi : leur nombre, leur repartition par direction et par periodicite de remontee. */
    private List<ExportBlock> indicatorFollowUp(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();
        if (ctx.indicators == 0) {
            b.add(new ExportBlock.Paragraph("Aucun indicateur de suivi n'est encore renseigné dans les sections approuvées.", true, false));
            return b;
        }
        b.add(new ExportBlock.Paragraph("Le suivi du plan repose sur " + ctx.indicators + " indicateurs objectivement "
                + "vérifiables, définis par les directions. Chacun est remonté selon la périodicité retenue par la "
                + "structure qui en a la charge, puis consolidé par le comité de pilotage."));

        List<ExportBlock.TableRow> byDirection = new ArrayList<>();
        Map<String, String> periodicityLabels = new LinkedHashMap<>();
        Map<String, Integer> countByPeriodicity = new LinkedHashMap<>();
        for (WorkGroup group : ctx.groups) {
            int count = 0;
            Set<String> periodicities = new LinkedHashSet<>();
            Set<String> structures = new LinkedHashSet<>();
            for (JsonNode row : JsonUtil.arr(ctx.content(group, "S13"), "rows")) {
                if (JsonUtil.text(row, "indicatorTitle").isBlank()) {
                    continue;
                }
                count++;
                String periodicity = JsonUtil.text(row, "periodicity").trim();
                String key = periodicity.isEmpty() ? "" : PsdCrossGroupMerge.normalize(periodicity);
                periodicityLabels.putIfAbsent(key, periodicity.isEmpty() ? "Non précisée" : periodicity);
                countByPeriodicity.merge(key, 1, Integer::sum);
                if (!periodicity.isEmpty()) {
                    periodicities.add(periodicity.toLowerCase(java.util.Locale.FRENCH));
                }
                String structure = JsonUtil.text(row, "responsibleStructure").trim();
                if (!structure.isEmpty()) {
                    structures.add(structure);
                }
            }
            if (count == 0) {
                continue;
            }
            byDirection.add(new ExportBlock.TableRow(List.of(
                    single(group.getName(), group, ctx),
                    center(String.valueOf(count)),
                    new ExportBlock.Cell(capitalize(String.join(", ", periodicities.stream()
                            .sorted(Comparator.comparingInt(PsdBriefBuilder::periodicityRank)).toList()))),
                    new ExportBlock.Cell(String.join(" ; ", structures)))));
        }
        b.add(new ExportBlock.Table(List.of("Direction", "Indicateurs", "Périodicités de remontée", "Structures responsables"),
                byDirection, List.of(27, 11, 24, 38)));

        List<ExportBlock.TableRow> rows = new ArrayList<>();
        Map.Entry<String, Integer> top = null;
        for (Map.Entry<String, Integer> entry : countByPeriodicity.entrySet()) {
            if (top == null || entry.getValue() > top.getValue()) {
                top = entry;
            }
            rows.add(new ExportBlock.TableRow(List.of(
                    new ExportBlock.Cell(periodicityLabels.get(entry.getKey())),
                    right(String.valueOf(entry.getValue())),
                    right(JsonUtil.formatPercent(entry.getValue() * 100d / ctx.indicators)))));
        }
        rows.sort(Comparator.comparingInt((ExportBlock.TableRow row) -> -Integer.parseInt(row.cells().get(1).text())));
        rows.add(totalRow("TOTAL", String.valueOf(ctx.indicators), "100 %"));
        b.add(new ExportBlock.Table(List.of("Périodicité de remontée", "Indicateurs", "Part"), rows, List.of(50, 25, 25)));
        if (top != null) {
            b.add(analysis("la remontée " + periodicityLabels.get(top.getKey()).toLowerCase(java.util.Locale.FRENCH)
                    + " concerne " + top.getValue() + " indicateurs sur " + ctx.indicators + " ("
                    + JsonUtil.formatPercent(top.getValue() * 100d / ctx.indicators) + "). La fiche de chaque "
                    + "indicateur — mode de calcul, moyens de collecte, sources de vérification, structure responsable — "
                    + "figure en annexe (Tableau 3), avec la répartition des indicateurs par direction et par périodicité."));
        }
        return b;
    }

    // ---- XII. Synthese du cadre strategique ----

    private List<ExportBlock> strategicSummary(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();
        // Le recapitulatif des axes (ancien XII.2) a ete retire a la revue client du 15/09/2026.
        sub(b, "XII.1 Tableau de synthèse du cadre stratégique");
        b.addAll(strategicSummaryTable(ctx));
        return b;
    }

    private String axisBandTitle(Context ctx, int index) {
        PlanAxis axis = ctx.plan.axes().get(index);
        return ctx.plan.consolidated() ? "AXE " + (index + 1) + " : " + axis.title() : axis.title();
    }

    /**
     * Le tableau de synthese du cadre strategique, sur le modele transmis par le client : le titre et
     * la vision en tete, puis pour chaque axe son bandeau, les intitules de colonnes et ses
     * orientations strategiques (OS), chacune fusionnee sur les lignes de ses actions.
     *
     * <p>Les OS et leurs actions sont celles du budget, completees de ce que les directions n'ont
     * saisi qu'au tableau de synthese (S17), d'ou viennent aussi les contraintes. Le tableau compte ainsi
     * les memes OS, sous les memes numeros, que le plan d'actions et le budget : lu seul, le tableau de
     * synthese des directions en omettait une partie. Depuis la revue client du 15/09/2026, chaque action
     * porte son budget et chaque axe de direction son objectif specifique.</p>
     */
    private List<ExportBlock> strategicSummaryTable(Context ctx) {
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        for (int i = 0; i < ctx.plan.axes().size(); i++) {
            summaryRows(rows, axisBandTitle(ctx, i), ctx.plan.axes().get(i).keys(), ctx);
        }
        summaryRows(rows, UNLINKED_AXES, ctx.unlinkedKeys(), ctx);
        if (rows.isEmpty()) {
            return List.of(new ExportBlock.Paragraph("Aucun tableau de synthèse du cadre stratégique n'est encore approuvé.", true, false));
        }
        String vision = ctx.narrative(NarrativeBlockKey.VISION);
        if (!vision.isBlank()) {
            rows.add(0, ExportBlock.TableRow.band("Vision : « " + unquote(vision) + " »", ExportBlock.Background.PRIMARY_LIGHT));
        }
        return List.of(
                new ExportBlock.Paragraph("Le tableau présente, pour chaque axe stratégique, les orientations stratégiques "
                        + "(OS) formulées par les directions, les actions qui les déclinent et leur budget prévisionnel "
                        + PERIOD + " en millions de FCFA, l'objectif spécifique de l'axe de la direction qui les porte, et les "
                        + "contraintes à lever ou les opportunités à saisir pour les conduire. Chaque ligne porte la couleur de "
                        + "la direction qui l'a formulée ; un objectif ou une contrainte commun à plusieurs lignes voisines "
                        + "n'est écrit qu'une fois."),
                // Pas de ligne d'en-tete : comme dans le modele, les intitules de colonnes se repetent
                // sous le bandeau de chaque axe, et le titre du tableau reprend en haut de chaque page.
                new ExportBlock.Table(List.of("", "", "", "", ""), rows, List.of(20, 25, 9, 21, 25),
                        List.of(new ExportBlock.HeaderBand("SYNTHÈSE DU CADRE STRATÉGIQUE", 5))));
    }

    /**
     * Le bandeau de l'axe, les intitules de colonnes puis ses OS numerotees a la suite ; rien si
     * l'axe n'en compte aucune. Une contrainte que la direction repete a l'identique sur des
     * lignes voisines n'occupe qu'une cellule, fusionnee sur ces lignes.
     */
    private void summaryRows(List<ExportBlock.TableRow> rows, String title, Collection<String> keys, Context ctx) {
        List<ExportBlock.TableRow> axisRows = new ArrayList<>();
        int os = 0;
        for (String key : keys) {
            List<PlanOrientation> orientations = ctx.orientationsOf(key);
            if (orientations.isEmpty()) {
                continue;
            }
            WorkGroup group = orientations.get(0).group();
            List<List<ExportBlock.Cell>> lines = new ArrayList<>();
            List<String> constraints = new ArrayList<>();
            for (PlanOrientation orientation : orientations) {
                os++;
                ExportBlock.Cell osCell = single("OS" + os + " : " + JsonUtil.dash(orientation.label()), group, ctx)
                        .withBackground(ExportBlock.Background.GREY);
                List<SummaryAction> actions = orientation.actions();
                if (actions.isEmpty()) {
                    lines.add(new ArrayList<>(List.of(osCell, new ExportBlock.Cell("—"), right("—"))));
                    constraints.add("");
                    continue;
                }
                for (int a = 0; a < actions.size(); a++) {
                    SummaryAction action = actions.get(a);
                    lines.add(new ArrayList<>(List.of(
                            a == 0 ? osCell.spanning(actions.size()) : ExportBlock.Cell.covered(),
                            single("Action " + os + "." + (a + 1) + " : " + action.label(), group, ctx),
                            right(action.cost() > 0 ? JsonUtil.formatMillions(action.cost()) : "—"))));
                    constraints.add(action.constraint());
                }
            }
            // L'objectif specifique est celui de l'axe de la direction : commun a toutes ses OS, il n'est ecrit qu'une fois.
            ExportBlock.Cell objective = axisObjectiveCell(group, key, ctx);
            for (int index = 0; index < lines.size(); index++) {
                lines.get(index).add(index == 0 ? objective.spanning(lines.size()) : ExportBlock.Cell.covered());
            }
            int line = 0;
            while (line < lines.size()) {
                String constraint = constraints.get(line);
                int end = line + 1;
                while (!constraint.isEmpty() && end < lines.size() && constraints.get(end).equals(constraint)) {
                    end++;
                }
                lines.get(line).add(constraint.isEmpty()
                        ? new ExportBlock.Cell("—") : single(constraint, group, ctx).spanning(end - line));
                for (int covered = line + 1; covered < end; covered++) {
                    lines.get(covered).add(ExportBlock.Cell.covered());
                }
                line = end;
            }
            lines.forEach(cells -> axisRows.add(new ExportBlock.TableRow(cells)));
        }
        if (axisRows.isEmpty()) {
            return;
        }
        rows.add(ExportBlock.TableRow.band(title, ExportBlock.Background.PRIMARY_DARK));
        rows.add(new ExportBlock.TableRow(List.of(new ExportBlock.Cell("Orientation stratégique (OS)"),
                new ExportBlock.Cell("Actions"), new ExportBlock.Cell("Budget (M FCFA)"), new ExportBlock.Cell("Objectif"),
                new ExportBlock.Cell("Contraintes à lever ou opportunités à saisir")),
                true, ExportBlock.Background.PRIMARY_LIGHT));
        rows.addAll(axisRows);
    }

    /**
     * Objectif specifique de l'axe d'une direction, tel qu'elle l'a formule avec ses axes d'intervention (S08) ;
     * a defaut, l'objectif de son cadre logique (S09).
     */
    private ExportBlock.Cell axisObjectiveCell(WorkGroup group, String key, Context ctx) {
        Set<String> objectives = new LinkedHashSet<>();
        for (JsonNode axis : ctx.axisEntries(group, "S08", key)) {
            JsonUtil.strList(axis, "specificObjectives").stream().map(String::trim)
                    .filter(text -> !text.isEmpty()).forEach(objectives::add);
            if (objectives.isEmpty() && !JsonUtil.text(axis, "objective").isBlank()) {
                objectives.add(JsonUtil.text(axis, "objective").trim());
            }
        }
        if (objectives.isEmpty()) {
            for (JsonNode axis : ctx.axisEntries(group, "S09", key)) {
                if (!JsonUtil.text(axis, "objective").isBlank()) {
                    objectives.add(JsonUtil.text(axis, "objective").trim());
                }
            }
        }
        if (objectives.isEmpty()) {
            return new ExportBlock.Cell("—");
        }
        if (objectives.size() == 1) {
            return single(objectives.iterator().next(), group, ctx);
        }
        String color = colorOf(group, ctx.groups);
        return new ExportBlock.Cell(objectives.stream().map(text -> new ExportBlock.Attribution(text, List.of(color))).toList());
    }

    // ---- XIII. Conclusion ----

    private List<ExportBlock> conclusion(Context ctx) {
        PsdKeyFigures f = ctx.figures;
        List<ExportBlock> b = new ArrayList<>();
        narrative(b, ctx, NarrativeBlockKey.CONCLUSION);
        b.add(new ExportBlock.Paragraph("En chiffres, le plan prévoit un budget de " + JsonUtil.formatAmountLabel(f.budget())
                + " sur " + YEARS.length + " exercices, réparti entre " + ctx.axisCount() + " axes stratégiques, "
                + f.specificObjectives() + " objectifs spécifiques, " + ctx.orientationCount() + " orientations stratégiques et "
                + f.actions() + " actions programmées, portés par les "
                + (f.allContribute() ? f.directions() + " directions" : f.contributingDirections() + " directions contributrices")
                + " de SENICO SA et suivis au moyen de " + ctx.indicators + " indicateurs."));
        if (f.budget() > 0 && f.financing() < f.budget()) {
            b.add(new ExportBlock.Paragraph("À ce jour, le financement identifié en couvre "
                    + JsonUtil.formatPercent(f.financing() / f.budget() * 100) + ". La mobilisation des "
                    + JsonUtil.formatAmountLabel(f.budget() - f.financing()) + " restants conditionnera le rythme "
                    + "d'exécution du plan : c'est la première décision attendue des instances de gouvernance."));
        }
        b.add(new ExportBlock.Paragraph("Le détail propre à chaque direction figure dans son plan stratégique sectoriel.",
                true, false));
        return b;
    }

    // ---- Annexes ----

    private List<ExportBlock> annexes(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();
        sub(b, "Tableau 1 : Analyse des parties prenantes");
        b.addAll(stakeholdersTable(ctx));
        sub(b, "Tableau 2 : Matrice d'analyse des risques");
        b.addAll(riskTable(ctx));
        sub(b, "Tableau 3 : Fiche des indicateurs objectivement vérifiables");
        b.addAll(indicatorTable(ctx));
        List<ExportBlock> followUp = indicatorFollowUp(ctx).stream().filter(ExportBlock.Table.class::isInstance).toList();
        if (followUp.size() == 2) {
            b.add(new ExportBlock.Heading("Indicateurs de suivi par direction", 3));
            b.add(followUp.get(0));
            b.add(new ExportBlock.Heading("Indicateurs par périodicité de remontée", 3));
            b.add(followUp.get(1));
        }

        // Revue client du 15/09/2026 : les tableaux par axe du cadre de mise en oeuvre et du cadre de mesure de
        // rendement sont reportes ici, dans l'ordre demande ; le corps du document garde leurs textes.
        sub(b, "Tableau 4 : Budget du plan");
        List<ExportBlock> budget = budgetBlocks(ctx, true);
        List<ExportBlock> budgetTable = budget.stream().filter(ExportBlock.Table.class::isInstance).toList();
        if (budgetTable.isEmpty()) {
            b.addAll(budget);
        } else {
            b.add(new ExportBlock.Heading(ctx.plan.consolidated() ? "Budget par axe stratégique" : "Budget par direction", 3));
            b.addAll(budgetTable);
            b.addAll(withoutIntro(detailedBudgets(ctx, false)));
        }
        sub(b, "Tableau 5 : Cadre logique");
        b.addAll(withoutIntro(logicalFrameworks(ctx)));
        sub(b, "Tableau 6 : Planification");
        b.addAll(withoutIntro(actionPlans(ctx)));
        sub(b, "Tableau 7 : Cadre de mesure de rendement");
        b.addAll(withoutIntro(performanceFrameworks(ctx)));
        return b;
    }

    private List<ExportBlock> stakeholdersTable(Context ctx) {
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        for (WorkGroup group : ctx.groups) {
            for (JsonNode row : JsonUtil.arr(ctx.content(group, "S01"), "rows")) {
                String actor = actorOf(row);
                if (actor.isEmpty()) {
                    continue;
                }
                rows.add(new ExportBlock.TableRow(List.of(
                        single(actor, group, ctx),
                        new ExportBlock.Cell(JsonUtil.dash(rolesOf(row))),
                        new ExportBlock.Cell(JsonUtil.dash(JsonUtil.text(row, "expectations"))),
                        new ExportBlock.Cell(JsonUtil.dash(JsonUtil.text(row, "adaptationStrategy"))),
                        center(rating(JsonUtil.text(row, "importance"))),
                        center(rating(JsonUtil.text(row, "influence"))),
                        new ExportBlock.Cell(JsonUtil.dash(JsonUtil.text(row, "actions"))))));
            }
        }
        if (rows.isEmpty()) {
            return List.of(new ExportBlock.Paragraph("Aucune analyse des parties prenantes n'est encore approuvée.", true, false));
        }
        return List.of(new ExportBlock.Table(
                List.of("Acteur (PP)", "Rôles / responsabilités", "Attentes / intérêt / priorités", "Stratégie d'adaptation",
                        "Niveau importance", "Niveau influence", "Actions"), rows,
                List.of(14, 16, 16, 16, 11, 11, 16)));
    }

    private List<ExportBlock> riskTable(Context ctx) {
        List<Risk> risks = ctx.risks(true);
        if (risks.isEmpty()) {
            return List.of(new ExportBlock.Paragraph("Aucune matrice des risques n'est encore approuvée.", true, false));
        }
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        for (Risk risk : risks) {
            rows.add(riskRow(risk, ctx, true));
        }
        return List.of(
                new ExportBlock.Table(
                        List.of("Catégorie de risque", "Présence (Oui/Non)", "Risques (nature détaillée)", "Niveau de risque (N)",
                                "Impact sur les domaines d'activités", "Quotation (Q)", "Criticité (N × Q)",
                                "Actions de mitigation ou de contingence"),
                        rows, List.of(13, 9, 18, 9, 14, 10, 10, 17)),
                new ExportBlock.Heading("Méthodologie d'évaluation", 4),
                new ExportBlock.Table(List.of("Niveau de risque (N)", "Quotation / impact (Q)", "Criticité = N × Q"),
                        List.of(new ExportBlock.TableRow(List.of(
                                new ExportBlock.Cell(Stream.of("Élevé = 3", "Moyen = 2", "Faible = 1")
                                        .map(ExportBlock.Attribution::new).toList()),
                                new ExportBlock.Cell(Stream.of("Élevé = 3 (impact majeur)", "Moyen = 2 (impact modéré)",
                                        "Faible = 1 (impact mineur)").map(ExportBlock.Attribution::new).toList()),
                                new ExportBlock.Cell(Stream.of("6 à 9 : criticité élevée — action prioritaire",
                                        "3 à 4 : criticité moyenne — à surveiller", "1 à 2 : criticité faible — sous contrôle")
                                        .map(ExportBlock.Attribution::new).toList())))),
                        List.of(30, 33, 37)));
    }

    private List<ExportBlock> indicatorTable(Context ctx) {
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        for (WorkGroup group : ctx.groups) {
            for (JsonNode row : JsonUtil.arr(ctx.content(group, "S13"), "rows")) {
                String title = JsonUtil.text(row, "indicatorTitle").trim();
                if (title.isEmpty()) {
                    continue;
                }
                rows.add(new ExportBlock.TableRow(List.of(
                        single(title, group, ctx),
                        new ExportBlock.Cell(JsonUtil.text(row, "calculationMethod")),
                        new ExportBlock.Cell(JsonUtil.text(row, "periodicity")),
                        new ExportBlock.Cell(JsonUtil.text(row, "collectionSource")),
                        new ExportBlock.Cell(JsonUtil.text(row, "verificationSource")),
                        new ExportBlock.Cell(JsonUtil.text(row, "responsibleStructure")))));
            }
        }
        if (rows.isEmpty()) {
            return List.of(new ExportBlock.Paragraph("Aucune fiche d'indicateur n'est encore approuvée.", true, false));
        }
        return List.of(new ExportBlock.Table(
                List.of("Intitulés indicateurs", "Modes de calcul", "Périodicités", "Sources et moyens de collecte",
                        "Sources de vérification", "Structures responsables"),
                rows, List.of(18, 20, 14, 17, 15, 16)));
    }

    // ---- Helpers ----

    private void part(List<ExportBlock> blocks, String title) {
        blocks.add(new ExportBlock.Heading(title, 1));
    }

    private void sub(List<ExportBlock> blocks, String title) {
        blocks.add(new ExportBlock.Heading(title, 2));
    }

    /**
     * Texte arrete par la Direction Generale. Absent, la note le dit a l'endroit meme ou il manque :
     * une page blanche sous un titre se lirait comme un oubli de mise en page.
     */
    private void narrative(List<ExportBlock> blocks, Context ctx, NarrativeBlockKey key) {
        String content = ctx.narrative(key);
        if (content.isBlank()) {
            blocks.add(new ExportBlock.Callout("« " + key.getLabel() + " » n'est pas encore rédigé. Ce texte relève de la "
                    + "Direction Générale et se saisit sur " + EDIT_SCREEN + ".", ExportBlock.Tone.WARNING));
            return;
        }
        blocks.addAll(PsdNarrativeText.blocks(content));
    }

    private ExportBlock.Callout pendingArbitration(String subject) {
        return new ExportBlock.Callout(subject + " n'est pas encore arrêtée par la Direction Générale : figurent "
                + "ci-dessous les propositions des directions, à arbitrer sur " + EDIT_SCREEN + ".", ExportBlock.Tone.WARNING);
    }

    /**
     * Ecart entre le budget et le financement identifie : c'est la question que pose un comite de
     * pilotage devant deux totaux differents, autant y repondre des les chiffres cles.
     */
    private String coverageSentence(PsdKeyFigures f) {
        if (f.budget() <= 0) {
            return "Le budget consolidé n'est pas encore renseigné : la couverture du plan ne peut être appréciée.";
        }
        String head = "le financement identifié couvre "
                + JsonUtil.formatPercent(f.financing() / f.budget() * 100) + " du budget du plan ("
                + JsonUtil.formatAmountLabel(f.financing()) + " sur " + JsonUtil.formatAmountLabel(f.budget()) + ").";
        if (f.financing() >= f.budget()) {
            return "Sur le plan financier, " + head + " Le plan est intégralement couvert.";
        }
        return "Sur le plan financier, " + head + " Le solde de "
                + JsonUtil.formatAmountLabel(f.budget() - f.financing()) + " reste à mobiliser : c'est le "
                + "principal point de vigilance de la mise en œuvre.";
    }

    /** La lecture attendue d'un tableau chiffre, detachee du corps du texte (cf. ExportBlock.Callout). */
    private ExportBlock.Callout analysis(String text) {
        String sentence = text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
        return new ExportBlock.Callout("Analyse : " + sentence);
    }

    private static String unquote(String text) {
        return text.trim().replaceAll("^[«\"“\\s]+|[»\"”\\s]+$", "");
    }

    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Rang d'une periodicite, de la plus rapprochee a la plus espacee : « mensuelle, annuelle, trimestrielle »,
     * dans l'ordre ou les indicateurs avaient ete saisis, ne se lisait pas. Les autres suivent, dans l'ordre saisi.
     */
    private static int periodicityRank(String periodicity) {
        return switch (periodicity.toLowerCase(java.util.Locale.FRENCH).trim()) {
            case "hebdomadaire" -> 0;
            case "mensuelle" -> 1;
            case "trimestrielle" -> 2;
            case "semestrielle" -> 3;
            case "annuelle" -> 4;
            default -> 5;
        };
    }

    private static String rating(String level) {
        return switch (level == null ? "" : level.toUpperCase(java.util.Locale.ROOT)) {
            case "FORT" -> "Fort";
            case "MOYEN" -> "Moyen";
            case "FAIBLE" -> "Faible";
            default -> "—";
        };
    }

    private static double sum(double[] values) {
        double total = 0;
        for (double value : values) {
            total += value;
        }
        return total;
    }

    private static ExportBlock.Cell right(String text) {
        return new ExportBlock.Cell(text, false, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE);
    }

    private static ExportBlock.Cell center(String text) {
        return new ExportBlock.Cell(text, false, ExportBlock.Align.CENTER, ExportBlock.Background.NONE);
    }

    private static ExportBlock.TableRow totalRow(String label, String... values) {
        List<ExportBlock.Cell> cells = new ArrayList<>();
        cells.add(new ExportBlock.Cell(label, true, ExportBlock.Align.LEFT, ExportBlock.Background.NONE));
        for (String value : values) {
            cells.add(new ExportBlock.Cell(value, true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
        }
        return new ExportBlock.TableRow(cells, true, ExportBlock.Background.PRIMARY_LIGHT);
    }

    /** Cellule d'un texte ecrit par une seule direction, dans sa couleur. */
    private static ExportBlock.Cell single(String text, WorkGroup group, Context ctx) {
        return new ExportBlock.Cell(List.of(new ExportBlock.Attribution(text, List.of(colorOf(group, ctx.groups)))));
    }

    /** Le contenu d'une cellule, en puces coloriees par la direction qui les a ecrites. */
    private static ExportBlock.Cell attributedCell(Contributions items) {
        if (items == null || items.isEmpty()) {
            return new ExportBlock.Cell("—");
        }
        return new ExportBlock.Cell(items.toAttributions());
    }

    /** Teintes de repli, franchement distinctes les unes des autres. */
    private static final List<String> FALLBACK_COLORS =
            List.of("#2563EB", "#16A34A", "#D97706", "#7C3AED", "#DC2626", "#0891B2");

    private static boolean isUsableColor(String hex) {
        return hex != null && hex.matches("#[0-9A-Fa-f]{6}");
    }

    /**
     * Couleur d'attribution d'une direction. Celle choisie par l'admin prime. A defaut, la
     * direction recoit la teinte de repli la plus eloignee de celles deja prises : deux
     * directions rendues dans deux rouges voisins ne s'attribuent plus rien du tout, et
     * l'attribution par couleur perdrait son objet.
     */
    static String colorOf(WorkGroup group, List<WorkGroup> all) {
        if (isUsableColor(group.getColor())) {
            return group.getColor();
        }
        List<String> taken = all.stream()
                .map(WorkGroup::getColor)
                .filter(PsdBriefBuilder::isUsableColor)
                .toList();
        // Rang parmi les directions sans couleur : deux d'entre elles ne prennent pas la meme,
        // et le document reste identique d'une generation a l'autre.
        List<WorkGroup> uncolored = all.stream().filter(g -> !isUsableColor(g.getColor())).toList();
        int rank = Math.max(0, uncolored.indexOf(group));

        List<String> ranked = FALLBACK_COLORS.stream()
                .sorted(Comparator.comparingInt((String candidate) -> minDistance(candidate, taken)).reversed())
                .toList();
        return ranked.get(rank % ranked.size());
    }

    /** Distance RGB au plus proche des coloris deja pris ; grande = bien distinguable. */
    private static int minDistance(String candidate, List<String> taken) {
        int worst = Integer.MAX_VALUE;
        for (String other : taken) {
            int dr = channel(candidate, 1) - channel(other, 1);
            int dg = channel(candidate, 3) - channel(other, 3);
            int db = channel(candidate, 5) - channel(other, 5);
            worst = Math.min(worst, dr * dr + dg * dg + db * db);
        }
        return taken.isEmpty() ? Integer.MAX_VALUE : worst;
    }

    private static int channel(String hex, int offset) {
        return Integer.parseInt(hex.substring(offset, offset + 2), 16);
    }

    // ---- Modele ----

    /** Axe formule par une direction dans son canevas (S08). */
    private record DirectionAxis(WorkGroup group, String axisCode, String title, List<String> objectives) {
    }

    /** Axe presente dans la note : un axe de l'entreprise, ou un axe de direction quand rien n'est arrete. */
    private record PlanAxis(String title, String objective, String color, List<DirectionAxis> members, Set<String> keys) {
    }

    private record AxisPlan(boolean consolidated, List<PlanAxis> axes, List<DirectionAxis> unlinked) {
    }

    /** Action programmee d'un axe de direction ({@code key}), sous son objectif (l'effet du budget). */
    private record ActionRow(WorkGroup group, String key, String objective, String activity, String extrant, double cost,
                             String responsible, JsonNode years) {
    }

    /**
     * Action d'une OS telle que la synthese la presente : son intitule, la contrainte a lever pour la conduire et
     * son budget (0 pour une action saisie au seul tableau de synthese, sans ligne au budget).
     */
    private record SummaryAction(String label, String constraint, double cost) {
    }

    /**
     * Orientation strategique (OS) d'un axe de direction : son intitule, les actions du budget qui la
     * servent ({@code programmed}, qui portent cout et extrants) et les actions presentees
     * ({@code actions}), celles du budget completees de celles saisies au seul tableau de synthese.
     */
    private record PlanOrientation(WorkGroup group, String label, List<ActionRow> programmed, List<SummaryAction> actions) {
    }

    /** Meme intitule, a la formulation pres (cf. PsdCrossGroupMerge#similar). */
    private static boolean sameLabel(String a, String b) {
        String left = PsdCrossGroupMerge.normalize(a == null ? "" : a.trim());
        String right = PsdCrossGroupMerge.normalize(b == null ? "" : b.trim());
        return !left.isEmpty() && (left.equals(right) || PsdCrossGroupMerge.similar(left, right));
    }

    private static List<JsonNode> declaredActions(JsonNode orientation) {
        return JsonUtil.arr(orientation, "actions").stream()
                .filter(action -> !JsonUtil.text(action, "label").isBlank())
                .toList();
    }

    private static String constraintOf(JsonNode action) {
        return JsonUtil.text(action, "constraintsOrOpportunities").trim();
    }

    private record Risk(WorkGroup group, String category, String details, String impact, int level, int quotation,
                        String label, String mitigation, boolean present) {
        int criticality() {
            return level * quotation;
        }
    }

    /** Tout ce que la note lit, calcule une fois : perimetre, chiffres cles, budget et actions par axe. */
    private final class Context {
        private final List<WorkGroup> groups;
        private final Lookup lookup;
        private final Map<NarrativeBlockKey, String> narratives;
        private final int awaitingApproval;
        private final PsdKeyFigures figures;
        private final int indicators;
        /** L'exercice analyse (S01B) est clos a la date de la note : ses chiffres sont des realisations. */
        private final boolean reviewYearClosed;
        private final Map<String, double[]> budgetByKey = new LinkedHashMap<>();
        private final Map<String, List<ActionRow>> actionsByKey = new LinkedHashMap<>();
        /** Coches du plan d'actions (S10) par activite d'un axe de direction : les exercices ou elle est programmee. */
        private final Map<String, JsonNode> schedules = new LinkedHashMap<>();
        private final Map<String, WorkGroup> groupByKey = new LinkedHashMap<>();
        private final AxisPlan plan;

        private Context(List<WorkGroup> groups, Map<String, SectionDef> sectionsByCode,
                        Map<String, SectionResponse> responsesByKey,
                        Map<String, GroupSectionStatus> statusesByKey,
                        Map<NarrativeBlockKey, String> narratives, LocalDate today) {
            this.groups = groups;
            this.lookup = new Lookup(sectionsByCode, responsesByKey, statusesByKey);
            this.narratives = narratives == null ? Map.of() : narratives;
            this.reviewYearClosed = today.getYear() > Integer.parseInt(REVIEW_YEAR);
            int covered = (int) statusesByKey.values().stream().filter(PsdApprovedContent::isApproved).count();
            // Une note vide n'est pas une note fausse : c'est une note dont le perimetre est vide.
            // On compte donc ce qui attend l'arbitrage du DG pour pouvoir le dire au lecteur.
            this.awaitingApproval = (int) statusesByKey.values().stream()
                    .filter(status -> status != null && !PsdApprovedContent.isApproved(status))
                    .filter(status -> status.getStatus() == SectionStatus.VALIDATED)
                    .count();
            this.figures = PsdKeyFigures.compute(groups, lookup::content, covered);
            int count = 0;
            for (WorkGroup group : groups) {
                for (JsonNode row : JsonUtil.arr(content(group, "S13"), "rows")) {
                    if (!JsonUtil.text(row, "indicatorTitle").isBlank()) {
                        count++;
                    }
                }
            }
            this.indicators = count;
            readProgramming();
            this.plan = axisPlan();
        }

        private JsonNode content(WorkGroup group, String code) {
            return lookup.content(group, code);
        }

        private String narrative(NarrativeBlockKey key) {
            String value = narratives.get(key);
            return value == null ? "" : value.trim();
        }

        private int axisCount() {
            return plan.consolidated() ? plan.axes().size() : figures.axes();
        }

        /** Budget (S11) et actions par axe de direction ; le plan d'actions (S10) supplee un budget absent. */
        private void readProgramming() {
            for (WorkGroup group : groups) {
                for (JsonNode axis : JsonUtil.arr(content(group, "S11"), "axes")) {
                    String key = key(group, JsonUtil.text(axis, "axisCode"));
                    groupByKey.put(key, group);
                    double[] years = budgetByKey.computeIfAbsent(key, k -> new double[YEARS.length]);
                    for (JsonNode effect : JsonUtil.arr(axis, "effects")) {
                        JsonNode yearTotals = effect.get("yearTotals");
                        for (int y = 0; y < YEARS.length; y++) {
                            years[y] += JsonUtil.num(yearTotals, YEARS[y]);
                        }
                        readActions(group, key, effect, "rowTotal");
                    }
                }
                for (JsonNode axis : JsonUtil.arr(content(group, "S10"), "axes")) {
                    String key = key(group, JsonUtil.text(axis, "axisCode"));
                    for (JsonNode effect : JsonUtil.arr(axis, "effects")) {
                        for (JsonNode row : JsonUtil.arr(effect, "rows")) {
                            String activity = JsonUtil.text(row, "activities").trim();
                            if (activity.isEmpty()) {
                                activity = JsonUtil.text(row, "extrant").trim();
                            }
                            if (!activity.isEmpty()) {
                                schedules.putIfAbsent(key + "|" + PsdCrossGroupMerge.normalize(activity), row.get("years"));
                            }
                        }
                    }
                    if (actionsByKey.containsKey(key)) {
                        continue;
                    }
                    groupByKey.putIfAbsent(key, group);
                    for (JsonNode effect : JsonUtil.arr(axis, "effects")) {
                        readActions(group, key, effect, "budget");
                    }
                }
            }
        }

        private void readActions(WorkGroup group, String key, JsonNode effect, String costField) {
            String objective = JsonUtil.text(effect, "effectLabel").trim();
            for (JsonNode row : JsonUtil.arr(effect, "rows")) {
                String extrant = JsonUtil.text(row, "extrant").trim();
                String activity = JsonUtil.text(row, "activities").trim();
                if (activity.isEmpty()) {
                    activity = extrant;
                }
                if (activity.isEmpty()) {
                    continue;
                }
                actionsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(new ActionRow(group, key, objective, activity,
                        extrant, JsonUtil.num(row, costField), JsonUtil.text(row, "responsible").trim(), row.get("years")));
            }
        }

        /** Exercices ou l'action est programmee : les coches du plan d'actions (S10), a defaut les annees budgetees. */
        private boolean scheduled(ActionRow action, String year) {
            JsonNode plan = schedules.get(action.key() + "|" + PsdCrossGroupMerge.normalize(action.activity()));
            if (plan != null && plan.has(year)) {
                return plan.get(year).asBoolean();
            }
            return amount(action, year) > 0;
        }

        /** Les entrees d'une section par axe (S09, S12) propres a l'axe de direction {@code key}. */
        private List<JsonNode> axisEntries(WorkGroup group, String code, String key) {
            String axisCode = key.substring(key.indexOf(':') + 1);
            return JsonUtil.arr(content(group, code), "axes").stream()
                    .filter(axis -> axisCode.equals(JsonUtil.text(axis, "axisCode")))
                    .toList();
        }

        private WorkGroup groupOf(String key) {
            String id = key.substring(0, Math.max(0, key.indexOf(':')));
            for (WorkGroup group : groups) {
                if (String.valueOf(group.getId()).equals(id)) {
                    return group;
                }
            }
            return null;
        }

        private AxisPlan axisPlan() {
            List<DirectionAxis> directionAxes = new ArrayList<>();
            for (WorkGroup group : groups) {
                for (JsonNode axis : JsonUtil.arr(content(group, "S08"), "axes")) {
                    String title = JsonUtil.text(axis, "title").trim();
                    if (title.isEmpty()) {
                        continue;
                    }
                    List<String> objectives = JsonUtil.strList(axis, "specificObjectives").stream()
                            .map(String::trim).filter(objective -> !objective.isEmpty()).toList();
                    directionAxes.add(new DirectionAxis(group, JsonUtil.text(axis, "axisCode"), title, objectives));
                }
            }

            List<PsdConsolidatedAxes.Axis> config = PsdConsolidatedAxes.parse(narrative(NarrativeBlockKey.AXES_CONSOLIDES));
            if (config.isEmpty()) {
                List<PlanAxis> axes = directionAxes.stream()
                        .map(axis -> new PlanAxis(axis.title(), "", colorOf(axis.group(), groups), List.of(axis),
                                Set.of(key(axis.group(), axis.axisCode()))))
                        .toList();
                return new AxisPlan(false, axes, List.of());
            }

            Set<String> linked = new HashSet<>();
            List<PlanAxis> axes = new ArrayList<>();
            for (int i = 0; i < config.size(); i++) {
                PsdConsolidatedAxes.Axis axis = config.get(i);
                Set<String> keys = new LinkedHashSet<>();
                axis.links().forEach(link -> keys.add(link.groupId() + ":" + link.axisCode()));
                linked.addAll(keys);
                List<DirectionAxis> members = directionAxes.stream()
                        .filter(member -> keys.contains(key(member.group(), member.axisCode())))
                        .toList();
                axes.add(new PlanAxis(axis.title(), axis.objective(),
                        i < AXIS_COLORS.size() ? AXIS_COLORS.get(i) : UNLINKED_COLOR, members, keys));
            }
            List<DirectionAxis> unlinked = directionAxes.stream()
                    .filter(axis -> !linked.contains(key(axis.group(), axis.axisCode())))
                    .toList();
            return new AxisPlan(true, axes, unlinked);
        }

        private double[] budgetOf(Collection<String> keys) {
            double[] out = new double[YEARS.length];
            for (String key : keys) {
                double[] years = budgetByKey.get(key);
                if (years == null) {
                    continue;
                }
                for (int y = 0; y < YEARS.length; y++) {
                    out[y] += years[y];
                }
            }
            return out;
        }

        /**
         * Orientations strategiques (OS) d'un axe de direction, dans l'ordre du budget : chaque
         * objectif du budget (S11, a defaut du plan d'actions S10) et ses actions, puis les OS que
         * la direction n'a saisies qu'au tableau de synthese (S17). La contrainte d'une action vient
         * de ce tableau, rapprochee par l'intitule de l'action, a defaut commune a toute son OS.
         */
        private List<PlanOrientation> orientationsOf(String key) {
            WorkGroup group = groupOf(key);
            if (group == null) {
                return List.of();
            }
            String axisCode = key.substring(key.indexOf(':') + 1);
            List<JsonNode> declared = new ArrayList<>();
            for (JsonNode axis : JsonUtil.arr(content(group, "S17"), "axes")) {
                if (axisCode.equals(JsonUtil.text(axis, "axisCode"))) {
                    declared.addAll(JsonUtil.arr(axis, "orientations"));
                }
            }
            Map<String, List<ActionRow>> programmed = new LinkedHashMap<>();
            for (ActionRow action : actionsByKey.getOrDefault(key, List.of())) {
                programmed.computeIfAbsent(PsdCrossGroupMerge.normalize(action.objective()), k -> new ArrayList<>()).add(action);
            }

            Set<JsonNode> matched = Collections.newSetFromMap(new IdentityHashMap<>());
            List<PlanOrientation> orientations = new ArrayList<>();
            for (List<ActionRow> actions : programmed.values()) {
                String label = actions.get(0).objective();
                // L'OS du tableau de synthese se reconnait a son intitule ; renommee, a ses actions.
                JsonNode summary = declared.stream()
                        .filter(candidate -> !matched.contains(candidate))
                        .filter(candidate -> sameLabel(JsonUtil.text(candidate, "label"), label)
                                || declaredActions(candidate).stream().anyMatch(declaredAction -> actions.stream()
                                .anyMatch(action -> sameLabel(JsonUtil.text(declaredAction, "label"), action.activity()))))
                        .findFirst().orElse(null);
                List<JsonNode> declaredActions = summary == null ? List.of() : declaredActions(summary);
                if (summary != null) {
                    matched.add(summary);
                }
                List<String> distinct = declaredActions.stream().map(PsdBriefBuilder::constraintOf)
                        .filter(constraint -> !constraint.isEmpty()).distinct().toList();
                String common = distinct.size() == 1 ? distinct.get(0) : "";

                Set<JsonNode> used = Collections.newSetFromMap(new IdentityHashMap<>());
                List<SummaryAction> presented = new ArrayList<>();
                for (ActionRow action : actions) {
                    JsonNode declaredAction = declaredActions.stream()
                            .filter(candidate -> !used.contains(candidate) && sameLabel(JsonUtil.text(candidate, "label"), action.activity()))
                            .findFirst().orElse(null);
                    if (declaredAction != null) {
                        used.add(declaredAction);
                    }
                    String constraint = declaredAction == null ? "" : constraintOf(declaredAction);
                    presented.add(new SummaryAction(action.activity(), constraint.isEmpty() ? common : constraint, action.cost()));
                }
                declaredActions.stream().filter(extra -> !used.contains(extra)).forEach(extra ->
                        presented.add(new SummaryAction(JsonUtil.text(extra, "label").trim(), constraintOf(extra), 0)));
                orientations.add(new PlanOrientation(group, label, actions, presented));
            }
            for (JsonNode orientation : declared) {
                if (matched.contains(orientation)) {
                    continue;
                }
                String label = JsonUtil.text(orientation, "label").trim();
                List<SummaryAction> presented = declaredActions(orientation).stream()
                        .map(action -> new SummaryAction(JsonUtil.text(action, "label").trim(), constraintOf(action), 0))
                        .toList();
                if (!label.isEmpty() || !presented.isEmpty()) {
                    orientations.add(new PlanOrientation(group, label, List.of(), presented));
                }
            }
            return orientations;
        }

        /** Axes de direction hors des axes de l'entreprise : non rattaches, ou absents des axes saisis (S08). */
        private List<String> unlinkedKeys() {
            Set<String> covered = new HashSet<>();
            plan.axes().forEach(axis -> covered.addAll(axis.keys()));
            Set<String> rest = new LinkedHashSet<>();
            plan.unlinked().forEach(axis -> rest.add(key(axis.group(), axis.axisCode())));
            actionsByKey.keySet().stream().filter(key -> !covered.contains(key)).forEach(rest::add);
            return new ArrayList<>(rest);
        }

        /** Nombre d'OS du plan, tel que les tableaux de synthese les numerotent. */
        private int orientationCount() {
            Set<String> keys = new LinkedHashSet<>();
            plan.axes().forEach(axis -> keys.addAll(axis.keys()));
            keys.addAll(unlinkedKeys());
            return keys.stream().mapToInt(key -> orientationsOf(key).size()).sum();
        }

        /**
         * Risques du plus critique au moins critique ; l'ordre des directions departage. Le canevas
         * note aussi les risques examines mais juges absents : la matrice complete les reprend, apres
         * les risques presents.
         */
        private List<Risk> risks(boolean includeAbsent) {
            List<Risk> risks = new ArrayList<>();
            for (WorkGroup group : groups) {
                for (JsonNode row : JsonUtil.arr(content(group, "S14"), "rows")) {
                    boolean present = !row.has("present") || row.get("present").asBoolean();
                    String details = JsonUtil.text(row, "riskDetails").trim();
                    if ((!present && !includeAbsent) || details.isEmpty()) {
                        continue;
                    }
                    risks.add(new Risk(group, JsonUtil.text(row, "category"), details, JsonUtil.text(row, "impactAreas"),
                            (int) JsonUtil.num(row, "levelN"), (int) JsonUtil.num(row, "quotationQ"),
                            JsonUtil.text(row, "criticalityLabel"), JsonUtil.text(row, "mitigationActions"), present));
                }
            }
            risks.sort(Comparator.comparing((Risk risk) -> !risk.present())
                    .thenComparing(Comparator.comparingInt(Risk::criticality).reversed()));
            return risks;
        }
    }

    private static String key(WorkGroup group, String axisCode) {
        return group.getId() + ":" + axisCode;
    }

    /**
     * Collecte des textes libres en les dedupliquant, tout en gardant qui les a ecrits.
     * Deux directions qui formulent le meme constat n'occupent qu'une ligne, mais le document
     * doit pouvoir dire que toutes deux le portent : c'est l'interet d'un document consolide.
     */
    private static final class Contributions {

        private final Map<String, String> texts = new LinkedHashMap<>();
        private final Map<String, List<WorkGroup>> authors = new LinkedHashMap<>();
        private final List<WorkGroup> all;

        private Contributions(List<WorkGroup> all) {
            this.all = all;
        }

        private void add(WorkGroup group, String raw) {
            String text = raw == null ? "" : raw.trim();
            if (text.isEmpty()) {
                return;
            }
            String key = PsdCrossGroupMerge.matchingKey(texts.keySet(), PsdCrossGroupMerge.normalize(text));
            texts.putIfAbsent(key, text);
            List<WorkGroup> contributors = authors.computeIfAbsent(key, k -> new ArrayList<>());
            if (!contributors.contains(group)) {
                contributors.add(group);
            }
        }

        /** Un champ libre peut porter plusieurs constats, un par ligne (cellules PESTEL). */
        private void addLines(WorkGroup group, String raw) {
            if (raw == null) {
                return;
            }
            for (String line : raw.split("\\R")) {
                add(group, line);
            }
        }

        private boolean isEmpty() {
            return texts.isEmpty();
        }

        private int size() {
            return texts.size();
        }

        private List<ExportBlock.Attribution> toAttributions() {
            return texts.entrySet().stream()
                    .map(entry -> new ExportBlock.Attribution(entry.getValue(),
                            authors.get(entry.getKey()).stream()
                                    .map(author -> colorOf(author, all)).toList()))
                    .toList();
        }
    }

    /** Acces au contenu d'une section pour une direction, restreint aux sections approuvees par le DG. */
    private final class Lookup {
        private final Map<String, SectionDef> sectionsByCode;
        private final Map<String, SectionResponse> responsesByKey;
        private final Map<String, GroupSectionStatus> statusesByKey;

        Lookup(Map<String, SectionDef> sectionsByCode, Map<String, SectionResponse> responsesByKey,
               Map<String, GroupSectionStatus> statusesByKey) {
            this.sectionsByCode = sectionsByCode;
            this.responsesByKey = responsesByKey;
            this.statusesByKey = statusesByKey;
        }

        JsonNode content(WorkGroup group, String sectionCode) {
            SectionDef section = sectionsByCode.get(sectionCode);
            if (section == null) {
                return JsonUtil.emptyObject();
            }
            String key = group.getId() + ":" + section.getId();
            if (!PsdApprovedContent.isApproved(statusesByKey.get(key))) {
                return JsonUtil.emptyObject();
            }
            return exportContentReader.read(group.getId(), section, responsesByKey.get(key));
        }
    }
}
