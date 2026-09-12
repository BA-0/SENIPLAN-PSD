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
import java.util.Set;

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
    static final String DIAGNOSTIC = "V. DIAGNOSTIC STRATÉGIQUE";
    static final String BILAN = "VI. BILAN DES PERFORMANCES DES ANNÉES PRÉCÉDENTES";
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
                DIAGNOSTIC, BILAN, ENJEUX, FACTEURS, CADRE, MISE_EN_OEUVRE, PILOTAGE, SYNTHESE, CONCLUSION, ANNEXES);
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
    private static final String FULL_PLAN = "le document complet du Plan Stratégique de SENICO";
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
        sub(b, "III.1 Missions");
        narrative(b, ctx, NarrativeBlockKey.MISSIONS);
        sub(b, "III.2 Organisation");
        narrative(b, ctx, NarrativeBlockKey.ORGANISATION);
        sub(b, "III.3 Ressources");
        narrative(b, ctx, NarrativeBlockKey.RESSOURCES);

        part(b, PARTIES_PRENANTES);
        b.addAll(stakeholders(ctx));

        part(b, DIAGNOSTIC);
        b.addAll(diagnostic(ctx));

        part(b, BILAN);
        narrative(b, ctx, NarrativeBlockKey.BILAN_PSD_PRECEDENT);

        part(b, ENJEUX);
        sub(b, "VII.1 Enjeux");
        narrative(b, ctx, NarrativeBlockKey.ENJEUX);
        sub(b, "VII.2 Défis à relever");
        narrative(b, ctx, NarrativeBlockKey.DEFIS_A_RELEVER);
        sub(b, "VII.3 Synthèse des contraintes, enjeux, défis et priorités identifiés");
        b.addAll(challengesTable(ctx));

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

    // ---- Sigles ----

    private ExportBlock.Table acronyms() {
        String[][] entries = {
                {"CA", "Conseil d'Administration"},
                {"CODIR", "Comité de direction"},
                {"COPIL", "Comité de pilotage du Plan Stratégique"},
                {"CRM", "Outil de gestion de la relation client"},
                {"DC", "Direction Commerciale"},
                {"DFC", "Direction Financière et Comptable"},
                {"DG", "Directeur Général / Direction Générale"},
                {"DSI", "Direction des Systèmes d'Information"},
                {"DTE", "Direction Technique et Exploitation"},
                {"EPI", "Équipement de protection individuelle"},
                {"FCFA", "Franc de la Communauté Financière Africaine"},
                {"IA", "Intelligence artificielle"},
                {"KPI", "Indicateur clé de performance"},
                {"M FCFA", "Millions de francs CFA"},
                {"OS", "Orientation stratégique"},
                {"PESTEL", "Analyse des facteurs politiques, économiques, socioculturels, technologiques, environnementaux et légaux"},
                {"PMO", "Cellule de coordination du Plan Stratégique (Project Management Office)"},
                {"PTF", "Partenaires techniques et financiers"},
                {"QHSE", "Qualité, hygiène, sécurité et environnement"},
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
                + "les contributions des " + f.directions() + " directions de l'entreprise, approuvées par la "
                + "Direction Générale. Il s'articule autour de " + axes + " axes stratégiques, déclinés en "
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
                new ExportBlock.KeyValue("Directions contributrices", String.valueOf(ctx.figures.directions())),
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

    // ---- V. Diagnostic strategique ----

    private List<ExportBlock> diagnostic(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();
        b.add(new ExportBlock.Paragraph(
                "Le diagnostic consolide les analyses conduites par chaque direction : performances de l'exercice "
                        + "2026, ressources et compétences, environnement externe (PESTEL), forces et faiblesses "
                        + "internes confrontées aux opportunités et menaces (SWOT), stratégies qui en découlent et "
                        + "risques associés. Un constat formulé par plusieurs directions n'y figure qu'une fois."));

        sub(b, "V.1 Performances de l'exercice 2026");
        b.addAll(performances(ctx));

        sub(b, "V.2 Analyse des ressources et des compétences");
        b.addAll(resources(ctx));

        sub(b, "V.3 Analyse PESTEL");
        b.addAll(pestel(ctx));

        sub(b, "V.4 Analyse SWOT");
        b.add(new ExportBlock.Paragraph("Les forces et les faiblesses relèvent de l'organisation elle-même ; les "
                + "opportunités et les menaces, de son environnement."));
        b.add(mergedSwot(ctx));

        sub(b, "V.5 Orientations stratégiques croisées");
        b.addAll(crossedStrategies(ctx));

        sub(b, "V.6 Cartographie des risques");
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
                        right(rate == null || rate.isNull() ? "—" : JsonUtil.formatRate(rate.asDouble())))));
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
                        (closed ? "Réalisé " : "Estimation ") + REVIEW_YEAR, "Taux"),
                        rows, List.of(19, 41, 12, 15, 13)),
                analysis("le taux rapporte " + (closed ? "le réalisé" : "l'estimation") + " à la cible. Pour un délai, "
                        + "un coût, un écart ou un nombre d'incidents, un taux supérieur à 100 % traduit une "
                        + "contre-performance. Les commentaires d'écart de chaque direction figurent dans " + FULL_PLAN + "."));
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
                    attributedCell(opportunities), attributedCell(threats), attributedCell(actions))));
        }
        if (!any) {
            return List.of(new ExportBlock.Paragraph("Aucune analyse PESTEL n'est encore approuvée.", true, false));
        }
        return List.of(
                new ExportBlock.Paragraph("L'analyse PESTEL recense, pour chaque facteur de l'environnement, les "
                        + "opportunités à saisir, les menaces à anticiper et les actions de mise à niveau proposées par "
                        + "les directions."),
                new ExportBlock.Table(List.of("Facteur", "Opportunités", "Menaces et risques", "Actions de mise à niveau"),
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

    private List<ExportBlock> crossedStrategies(Context ctx) {
        String[][] fields = {
                {"strengthsForOpportunities", "SO — STRATÉGIES OFFENSIVES", "Forces × opportunités : s'appuyer sur les atouts pour saisir les opportunités."},
                {"strengthsReduceThreats", "ST — STRATÉGIES DÉFENSIVES", "Forces × menaces : mobiliser les atouts pour contenir les menaces."},
                {"correctWeaknessesViaOpportunities", "WO — STRATÉGIES DE REPOSITIONNEMENT", "Faiblesses × opportunités : tirer parti de l'environnement pour corriger les faiblesses."},
                {"minimizeWeaknessesAndThreats", "WT — STRATÉGIES DE CONSOLIDATION", "Faiblesses × menaces : réduire les vulnérabilités les plus exposées."}};
        List<ExportBlock.AttributedQuadrantCell> cells = new ArrayList<>();
        boolean any = false;
        for (String[] field : fields) {
            Contributions items = new Contributions(ctx.groups);
            for (WorkGroup group : ctx.groups) {
                items.add(group, JsonUtil.text(ctx.content(group, "S05"), field[0]));
            }
            any |= !items.isEmpty();
            cells.add(new ExportBlock.AttributedQuadrantCell(field[1], field[2], items.toAttributions()));
        }
        if (!any) {
            return List.of(new ExportBlock.Paragraph("Aucune matrice de confrontation n'est encore approuvée.", true, false));
        }
        return List.of(
                new ExportBlock.Paragraph("La confrontation des facteurs internes et externes dégage quatre familles "
                        + "d'orientations, qui ont nourri la formulation des axes stratégiques."),
                new ExportBlock.AttributedQuadrant(cells, List.of(ExportBlock.Background.GREEN, ExportBlock.Background.BLUE,
                        ExportBlock.Background.ORANGE, ExportBlock.Background.RED)));
    }

    private List<ExportBlock> risks(Context ctx) {
        List<Risk> risks = ctx.risks();
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
                + " de criticité élevée. La matrice complète figure en annexe (Tableau 2)."));
        return b;
    }

    private ExportBlock.TableRow riskRow(Risk risk, Context ctx, boolean full) {
        ExportBlock.Cell criticality = new ExportBlock.Cell(SectionLabels.criticality(risk.label()), true,
                ExportBlock.Align.CENTER, SectionLabels.criticalityBackground(risk.label()));
        ExportBlock.Cell impact = new ExportBlock.Cell(JsonUtil.dash(risk.impact()));
        if (full) {
            return new ExportBlock.TableRow(List.of(
                    new ExportBlock.Cell(risk.category()),
                    single(risk.details(), risk.group(), ctx),
                    impact,
                    center(String.valueOf(risk.level())),
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
            b.add(new ExportBlock.Paragraph("La mission exprime la raison d'être de SENICO SA : les services qu'elle "
                    + "rend et ceux au service de qui elle les rend."));
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

    private List<ExportBlock> implementation(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();
        sub(b, "X.1 Budget");
        b.addAll(budgetBlocks(ctx));
        sub(b, "X.2 Plan de financement");
        b.addAll(financing(ctx));
        sub(b, "X.3 Plan d'évolution des effectifs");
        b.addAll(staff(ctx));
        return b;
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
        List<ExportBlock> b = new ArrayList<>();
        List<BudgetLine> lines = budgetLines(ctx);
        double total = lines.stream().mapToDouble(line -> sum(line.years())).sum();
        if (total <= 0) {
            b.add(new ExportBlock.Paragraph("Le budget consolidé n'est pas encore renseigné dans les sections approuvées.", true, false));
            return b;
        }
        String dimension = ctx.plan.consolidated() ? "axe stratégique" : "direction";
        b.add(new ExportBlock.Paragraph("Le budget prévisionnel du plan s'élève à " + JsonUtil.formatAmountLabel(total)
                + " sur la période " + PERIOD + ". Il est réparti ci-dessous par " + dimension
                + " et par exercice, en millions de FCFA."));

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
        b.add(new ExportBlock.Table(headers, rows, widths));

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
        for (WorkGroup group : ctx.groups) {
            for (JsonNode row : JsonUtil.arr(ctx.content(group, "S15"), "rows")) {
                bySource.merge(JsonUtil.text(row, "source"), JsonUtil.num(row, "amount"), Double::sum);
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
            rows.add(new ExportBlock.TableRow(List.of(
                    new ExportBlock.Cell(SectionLabels.financing(entry.getKey())),
                    right(JsonUtil.formatMillions(entry.getValue())),
                    right(JsonUtil.formatPercent(entry.getValue() / financingTotal * 100)),
                    right(budget > 0 ? JsonUtil.formatPercent(entry.getValue() / budget * 100) : "—"))));
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
        b.add(new ExportBlock.Table(List.of("Source de financement", "Montant (M FCFA)", "Part du financement", "Part du budget"),
                rows, List.of(40, 20, 20, 20)));

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

        sub(b, "XI.2 Indicateurs de suivi");
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
                    new ExportBlock.Cell(capitalize(String.join(", ", periodicities))),
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
                    + "figure en annexe (Tableau 3)."));
        }
        return b;
    }

    // ---- XII. Synthese du cadre strategique ----

    private List<ExportBlock> strategicSummary(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();
        sub(b, "XII.1 Tableau de synthèse du cadre stratégique");
        b.addAll(strategicSummaryTable(ctx));
        sub(b, "XII.2 Récapitulatif des axes : orientations, actions, indicateurs, cibles et coûts");
        b.addAll(planRecap(ctx));
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
     * saisi qu'au tableau de synthese (S17), d'ou viennent aussi les contraintes. Le tableau et le
     * recapitulatif qui le suit comptent ainsi les memes OS, sous les memes numeros : lu seul, le
     * tableau de synthese des directions en omettait une partie.</p>
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
                        + "(OS) formulées par les directions, les actions qui les déclinent et les contraintes à lever ou les "
                        + "opportunités à saisir pour les conduire. Chaque ligne porte la couleur de la direction qui l'a "
                        + "formulée ; une contrainte que la direction attache à plusieurs OS voisines n'est écrite qu'une fois."),
                // Pas de ligne d'en-tete : comme dans le modele, les intitules de colonnes se repetent
                // sous le bandeau de chaque axe, et le titre du tableau reprend en haut de chaque page.
                new ExportBlock.Table(List.of("", "", ""), rows, List.of(30, 35, 35),
                        List.of(new ExportBlock.HeaderBand("SYNTHÈSE DU CADRE STRATÉGIQUE", 3))));
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
                    lines.add(new ArrayList<>(List.of(osCell, new ExportBlock.Cell("—"))));
                    constraints.add("");
                    continue;
                }
                for (int a = 0; a < actions.size(); a++) {
                    lines.add(new ArrayList<>(List.of(
                            a == 0 ? osCell.spanning(actions.size()) : ExportBlock.Cell.covered(),
                            single("Action " + os + "." + (a + 1) + " : " + actions.get(a).label(), group, ctx))));
                    constraints.add(actions.get(a).constraint());
                }
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
                new ExportBlock.Cell("Actions"), new ExportBlock.Cell("Contraintes à lever ou opportunités à saisir")),
                true, ExportBlock.Background.PRIMARY_LIGHT));
        rows.addAll(axisRows);
    }

    /**
     * Tout le plan en un seul tableau : pour chaque orientation strategique (OS), les actions qui la
     * servent, l'indicateur qui en mesure l'atteinte, sa cible finale et le cout prevu. L'indicateur
     * et la cible viennent du cadre de mesure de rendement (S12), rapproches de l'OS par son
     * intitule ; le cout, du budget (S11).
     */
    private List<ExportBlock> planRecap(Context ctx) {
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        double total = 0;
        for (int i = 0; i < ctx.plan.axes().size(); i++) {
            total += recapRows(rows, axisBandTitle(ctx, i), ctx.plan.axes().get(i).keys(), ctx);
        }
        total += recapRows(rows, UNLINKED_AXES, ctx.unlinkedKeys(), ctx);
        if (rows.isEmpty()) {
            return List.of(new ExportBlock.Paragraph("Aucune action programmée n'est encore approuvée.", true, false));
        }
        rows.add(new ExportBlock.TableRow(List.of(
                new ExportBlock.Cell("TOTAL DU PLAN", true, ExportBlock.Align.LEFT, ExportBlock.Background.NONE),
                new ExportBlock.Cell(""), new ExportBlock.Cell(""), new ExportBlock.Cell(""),
                new ExportBlock.Cell(JsonUtil.formatMillions(total), true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE)),
                true, ExportBlock.Background.PRIMARY_LIGHT));
        return List.of(
                new ExportBlock.Paragraph("Ce tableau récapitule l'ensemble du plan : pour chaque orientation stratégique (OS), "
                        + "numérotée comme dans le tableau de synthèse qui précède, les actions programmées, l'indicateur de "
                        + "suivi retenu, la cible visée en " + LAST_YEAR + " et le coût prévisionnel sur la période " + PERIOD
                        + ", en millions de FCFA. Chaque ligne porte la couleur de la direction qui la conduit."),
                new ExportBlock.Table(List.of("Orientation stratégique (OS)", "Actions", "Indicateur de suivi", "Cible " + LAST_YEAR,
                        "Coût (M FCFA)"), rows, List.of(22, 32, 24, 11, 11)),
                analysis("l'indicateur et la cible " + LAST_YEAR + " sont repris du cadre de mesure de rendement de la "
                        + "direction qui porte l'OS : l'indicateur propre à l'OS quand la direction en a fixé un, à défaut "
                        + "celui des extrants de son axe. « — » signale une OS à laquelle aucun indicateur n'est encore "
                        + "rattaché. Le coût additionne le budget prévisionnel des actions de l'OS."));
    }

    /** La bande de l'axe, une ligne par OS et le total de l'axe ; rend ce total. */
    private double recapRows(List<ExportBlock.TableRow> rows, String title, Collection<String> keys, Context ctx) {
        List<ExportBlock.TableRow> axisRows = new ArrayList<>();
        double axisTotal = 0;
        boolean axisCosted = false;
        int os = 0;
        for (String key : keys) {
            for (PlanOrientation orientation : ctx.orientationsOf(key)) {
                os++;
                List<ActionRow> programmed = orientation.programmed();
                double cost = programmed.stream().mapToDouble(ActionRow::cost).sum();
                boolean costed = programmed.stream().anyMatch(action -> action.cost() > 0);
                axisTotal += cost;
                axisCosted |= costed;
                List<Target> targets = ctx.targetsOf(key, orientation.label(),
                        programmed.stream().map(ActionRow::extrant).toList());
                String color = colorOf(orientation.group(), ctx.groups);
                axisRows.add(new ExportBlock.TableRow(List.of(
                        single("OS" + os + " : " + JsonUtil.dash(orientation.label()), orientation.group(), ctx),
                        orientation.actions().isEmpty() ? new ExportBlock.Cell("—") : new ExportBlock.Cell(orientation.actions().stream()
                                .map(action -> new ExportBlock.Attribution(action.label(), List.of(color)))
                                .toList()),
                        new ExportBlock.Cell(targets.isEmpty() ? "—" : String.join(" ; ", targets.stream().map(Target::indicator).toList())),
                        center(targets.isEmpty() ? "—" : String.join(" ; ", targets.stream().map(target -> JsonUtil.dash(target.value())).toList())),
                        right(costed ? JsonUtil.formatMillions(cost) : "—"))));
            }
        }
        if (axisRows.isEmpty()) {
            return 0;
        }
        rows.add(ExportBlock.TableRow.band(title, ExportBlock.Background.PRIMARY_DARK));
        rows.addAll(axisRows);
        rows.add(new ExportBlock.TableRow(List.of(
                new ExportBlock.Cell("Total de l'axe", true, ExportBlock.Align.LEFT, ExportBlock.Background.NONE),
                new ExportBlock.Cell(""), new ExportBlock.Cell(""), new ExportBlock.Cell(""),
                new ExportBlock.Cell(axisCosted ? JsonUtil.formatMillions(axisTotal) : "—", true, ExportBlock.Align.RIGHT,
                        ExportBlock.Background.NONE)),
                true, ExportBlock.Background.PRIMARY_LIGHT));
        return axisTotal;
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
                + f.directions() + " directions de SENICO SA et suivis au moyen de " + ctx.indicators + " indicateurs."));
        if (f.budget() > 0 && f.financing() < f.budget()) {
            b.add(new ExportBlock.Paragraph("À ce jour, le financement identifié en couvre "
                    + JsonUtil.formatPercent(f.financing() / f.budget() * 100) + ". La mobilisation des "
                    + JsonUtil.formatAmountLabel(f.budget() - f.financing()) + " restants conditionnera le rythme "
                    + "d'exécution du plan : c'est la première décision attendue des instances de gouvernance."));
        }
        b.add(new ExportBlock.Paragraph("Le détail des tableaux par direction — cadre logique, budget, "
                + "opérationnalisation, cadre de mesure de rendement — figure dans " + FULL_PLAN + ".",
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
        return b;
    }

    /**
     * Contraintes et defis regroupes par domaine d'activites, sur le modele du tableau de synthese
     * d'un PSD publie. Deux directions qui citent le meme domaine sont fusionnees : c'est bien la
     * meme activite, vue de deux endroits.
     */
    private List<ExportBlock> challengesTable(Context ctx) {
        Map<String, String> domainLabels = new LinkedHashMap<>();
        Map<String, Contributions> constraintsByDomain = new LinkedHashMap<>();
        Map<String, Contributions> challengesByDomain = new LinkedHashMap<>();

        for (WorkGroup group : ctx.groups) {
            for (JsonNode row : JsonUtil.arr(ctx.content(group, "S06B"), "rows")) {
                String domain = JsonUtil.text(row, "domain").trim();
                if (domain.isEmpty()) {
                    continue;
                }
                String key = PsdCrossGroupMerge.normalize(domain);
                domainLabels.putIfAbsent(key, domain);
                for (String item : JsonUtil.strList(row, "constraints")) {
                    constraintsByDomain.computeIfAbsent(key, k -> new Contributions(ctx.groups)).add(group, item);
                }
                for (String item : JsonUtil.strList(row, "challenges")) {
                    challengesByDomain.computeIfAbsent(key, k -> new Contributions(ctx.groups)).add(group, item);
                }
            }
        }
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        for (Map.Entry<String, String> domain : domainLabels.entrySet()) {
            Contributions constraints = constraintsByDomain.get(domain.getKey());
            Contributions challenges = challengesByDomain.get(domain.getKey());
            if ((constraints == null || constraints.isEmpty()) && (challenges == null || challenges.isEmpty())) {
                continue;
            }
            rows.add(new ExportBlock.TableRow(List.of(
                    new ExportBlock.Cell(domain.getValue(), true, ExportBlock.Align.LEFT, ExportBlock.Background.NONE),
                    attributedCell(constraints),
                    attributedCell(challenges))));
        }
        if (rows.isEmpty()) {
            return List.of(new ExportBlock.Paragraph("Aucune synthèse des enjeux et contraintes n'est encore approuvée.", true, false));
        }
        return List.of(
                new ExportBlock.Paragraph("Regroupées par domaine d'activités, les contraintes prioritaires et les défis "
                        + "et enjeux prioritaires relevés par les directions sont les suivants."),
                new ExportBlock.Table(
                        List.of("Domaines d'activités", "Contraintes prioritaires", "Défis et enjeux prioritaires"), rows,
                        List.of(20, 40, 40)));
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
                        new ExportBlock.Cell(SectionLabels.stakeholderScope(JsonUtil.text(row, "scope"))),
                        new ExportBlock.Cell(JsonUtil.text(row, "expectations")),
                        new ExportBlock.Cell(JsonUtil.text(row, "adaptationStrategy")),
                        center(rating(JsonUtil.text(row, "importance"))),
                        center(rating(JsonUtil.text(row, "influence"))))));
            }
        }
        if (rows.isEmpty()) {
            return List.of(new ExportBlock.Paragraph("Aucune analyse des parties prenantes n'est encore approuvée.", true, false));
        }
        return List.of(new ExportBlock.Table(
                List.of("Partie prenante", "Portée", "Attentes", "Stratégie d'adaptation", "Intérêt", "Pouvoir"), rows,
                List.of(22, 9, 26, 27, 8, 8)));
    }

    private List<ExportBlock> riskTable(Context ctx) {
        List<Risk> risks = ctx.risks();
        if (risks.isEmpty()) {
            return List.of(new ExportBlock.Paragraph("Aucune matrice des risques n'est encore approuvée.", true, false));
        }
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        for (Risk risk : risks) {
            rows.add(riskRow(risk, ctx, true));
        }
        return List.of(new ExportBlock.Table(
                List.of("Catégorie", "Risque", "Impact sur les domaines d'activités", "N", "Q", "Criticité", "Actions d'atténuation"),
                rows, List.of(16, 20, 17, 4, 4, 10, 29)));
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
                List.of("Indicateur", "Mode de calcul", "Périodicité", "Sources et moyens de collecte",
                        "Source de vérification", "Structure responsable"),
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
    private record ActionRow(WorkGroup group, String key, String objective, String activity, String extrant, double cost) {
    }

    /** Indicateur du cadre de mesure de rendement et sa cible au dernier exercice du plan. */
    private record Target(String indicator, String value) {
    }

    /** Action d'une OS telle que la synthese la presente : son intitule et la contrainte a lever pour la conduire. */
    private record SummaryAction(String label, String constraint) {
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

    /** Niveaux du cadre de mesure de rendement ou chercher l'indicateur d'un objectif, du plus proche au plus lointain. */
    private static final List<String> TARGET_LEVELS = List.of("EFFETS_IMMEDIATS", "EFFET", "EXTRANTS", "IMPACT", "RESSOURCES_INTRANTS");

    private static List<Target> matchingTargets(List<JsonNode> rows, List<String> labels) {
        List<String> wanted = labels.stream().map(String::trim).filter(label -> !label.isEmpty())
                .map(PsdCrossGroupMerge::normalize).toList();
        List<Target> found = new ArrayList<>();
        if (wanted.isEmpty()) {
            return found;
        }
        for (JsonNode row : rows) {
            String indicator = JsonUtil.text(row, "indicator").trim();
            String result = PsdCrossGroupMerge.normalize(JsonUtil.text(row, "resultOrExtrant").trim());
            if (indicator.isEmpty() || result.isEmpty()
                    || wanted.stream().noneMatch(label -> label.equals(result) || PsdCrossGroupMerge.similar(label, result))) {
                continue;
            }
            Target target = new Target(indicator, JsonUtil.text(row.get("years"), LAST_YEAR).trim());
            if (!found.contains(target)) {
                found.add(target);
            }
        }
        return found;
    }

    private record Risk(WorkGroup group, String category, String details, String impact, int level, int quotation,
                        String label, String mitigation) {
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
                        extrant, JsonUtil.num(row, costField)));
            }
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

        /**
         * Indicateur et cible finale d'un objectif, lus dans le cadre de mesure de rendement (S12) de
         * la direction : la ligne dont le resultat reprend l'intitule de l'objectif, a defaut celle
         * d'un extrant de ses actions. Le premier niveau qui repond l'emporte, pour ne pas citer deux
         * fois un objectif decline a la fois a l'effet et aux effets immediats.
         */
        private List<Target> targetsOf(String key, String objective, List<String> extrants) {
            WorkGroup group = groupOf(key);
            if (group == null) {
                return List.of();
            }
            String axisCode = key.substring(key.indexOf(':') + 1);
            Map<String, List<JsonNode>> rowsByLevel = new LinkedHashMap<>();
            TARGET_LEVELS.forEach(level -> rowsByLevel.put(level, new ArrayList<>()));
            for (JsonNode axis : JsonUtil.arr(content(group, "S12"), "axes")) {
                if (!axisCode.equals(JsonUtil.text(axis, "axisCode"))) {
                    continue;
                }
                for (JsonNode performanceGroup : JsonUtil.arr(axis, "groups")) {
                    List<JsonNode> rows = rowsByLevel.get(JsonUtil.text(performanceGroup, "level"));
                    if (rows != null) {
                        rows.addAll(JsonUtil.arr(performanceGroup, "rows"));
                    }
                }
            }
            for (List<String> labels : List.of(List.of(objective), extrants)) {
                for (List<JsonNode> rows : rowsByLevel.values()) {
                    List<Target> found = matchingTargets(rows, labels);
                    if (!found.isEmpty()) {
                        return found;
                    }
                }
            }
            // A defaut d'un indicateur propre a l'OS, celui des extrants de l'axe de la direction :
            // c'est lui qui mesure la realisation de l'ensemble des actions de l'axe. Laisser la
            // ligne vide faisait croire a une OS que personne ne suit.
            List<Target> fallback = new ArrayList<>();
            for (JsonNode row : rowsByLevel.get("EXTRANTS")) {
                String indicator = JsonUtil.text(row, "indicator").trim();
                Target target = new Target(indicator, JsonUtil.text(row.get("years"), LAST_YEAR).trim());
                if (!indicator.isEmpty() && !fallback.contains(target)) {
                    fallback.add(target);
                }
            }
            return fallback;
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
                    presented.add(new SummaryAction(action.activity(), constraint.isEmpty() ? common : constraint));
                }
                declaredActions.stream().filter(extra -> !used.contains(extra)).forEach(extra ->
                        presented.add(new SummaryAction(JsonUtil.text(extra, "label").trim(), constraintOf(extra))));
                orientations.add(new PlanOrientation(group, label, actions, presented));
            }
            for (JsonNode orientation : declared) {
                if (matched.contains(orientation)) {
                    continue;
                }
                String label = JsonUtil.text(orientation, "label").trim();
                List<SummaryAction> presented = declaredActions(orientation).stream()
                        .map(action -> new SummaryAction(JsonUtil.text(action, "label").trim(), constraintOf(action)))
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

        /** Risques presents, du plus critique au moins critique ; l'ordre des directions departage. */
        private List<Risk> risks() {
            List<Risk> risks = new ArrayList<>();
            for (WorkGroup group : groups) {
                for (JsonNode row : JsonUtil.arr(content(group, "S14"), "rows")) {
                    boolean present = !row.has("present") || row.get("present").asBoolean();
                    String details = JsonUtil.text(row, "riskDetails").trim();
                    if (!present || details.isEmpty()) {
                        continue;
                    }
                    risks.add(new Risk(group, JsonUtil.text(row, "category"), details, JsonUtil.text(row, "impactAreas"),
                            (int) JsonUtil.num(row, "levelN"), (int) JsonUtil.num(row, "quotationQ"),
                            JsonUtil.text(row, "criticalityLabel"), JsonUtil.text(row, "mitigationActions")));
                }
            }
            risks.sort(Comparator.comparingInt(Risk::criticality).reversed());
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
