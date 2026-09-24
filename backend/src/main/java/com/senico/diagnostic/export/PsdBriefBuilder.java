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
import java.util.stream.Collectors;
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

    // Tableaux en annexe, numerotes dans l'ordre ou le corps de la note y renvoie (revue de l'auditeur du
    // 21/09/2026), qui est aussi celui du canevas : cadre logique (X.1), planification (X.2), budget (X.3),
    // cadre de mesure de rendement (XI.2). Les parties prenantes sont dans le corps (IV). Revue du 22/09/2026 :
    // la fiche des indicateurs, ancien tableau 6, ouvre le cadre de mesure de rendement devant les tableaux par
    // axe. Demande client du 23/09/2026 : la matrice d'analyse des risques, ancien tableau 1, quitte les annexes ;
    // les risques se lisent dans la cartographie (VI.6).
    static final String ANNEXE_CADRE_LOGIQUE = "Tableau 1 : Cadre logique";
    static final String ANNEXE_PLANIFICATION = "Tableau 2 : Planification";
    static final String ANNEXE_BUDGET = "Tableau 3 : Budget du plan";
    static final String ANNEXE_RENDEMENT = "Tableau 4 : Cadre de mesure de rendement";
    static final String FICHE_INDICATEURS = "Fiche des indicateurs quantitatifs et qualitatifs objectivement vérifiables";

    /** Intitules des parties, dans l'ordre ; chacune ouvre sa page. La note n'a plus de sommaire (revue du 22/09/2026). */
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
    /** Exercice en cours du bilan des performances (S01B) ; les cinq exercices ecoules le precedent. */
    private static final String REVIEW_YEAR = String.valueOf(PerformanceReviewTables.REVIEW_YEAR);
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

        // Revue du 22/09/2026 : les cinq exercices ecoules dans un seul tableau (un bandeau par exercice), puis
        // l'exercice en cours dans le sien, avec la tendance de chaque indicateur. Le bilan precede le diagnostic.
        part(b, BILAN);
        narrative(b, ctx, NarrativeBlockKey.BILAN_PSD_PRECEDENT);
        sub(b, "V.1 " + PerformanceReviewTables.PAST_TITLE);
        b.add(PerformanceReviewTables.pastYears(performanceLines(ctx), true));
        sub(b, "V.2 " + PerformanceReviewTables.CURRENT_TITLE);
        b.addAll(currentPerformances(ctx));

        part(b, DIAGNOSTIC);
        b.addAll(diagnostic(ctx));

        part(b, ENJEUX);
        // Demande client du 23/09/2026 : enjeux, puis defis, puis le tableau de synthese du canevas en fin de partie.
        sub(b, "VII.1 Enjeux");
        narrative(b, ctx, NarrativeBlockKey.ENJEUX);
        sub(b, "VII.2 Défis à relever");
        narrative(b, ctx, NarrativeBlockKey.DEFIS_A_RELEVER);
        sub(b, "VII.3 Synthèse des contraintes, enjeux, défis et priorités identifiés");
        b.addAll(constraintsSynthesis(ctx));

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
        return withoutText(b);
    }

    /**
     * Demande client du 23/09/2026 : la note ne garde que ses titres, sous-titres et tableaux ;
     * paragraphes, listes a puces et encadres sont retires.
     */
    private static List<ExportBlock> withoutText(List<ExportBlock> blocks) {
        return blocks.stream()
                .filter(block -> !(block instanceof ExportBlock.Paragraph
                        || block instanceof ExportBlock.BulletList
                        || block instanceof ExportBlock.Callout))
                .collect(Collectors.toCollection(ArrayList::new));
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
            case "S11" -> detailedBudgets(ctx, true);
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

        // Revue de l'auditeur (21-22/09/2026) : moins de texte. Le paragraphe qui resumait le plan repetait les
        // chiffres cles ci-dessous ; la vision n'est plus citee ici, elle ouvre le cadre strategique (IX.1).
        int axes = ctx.axisCount();
        int orientations = ctx.orientationCount();
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
        // Sans budget, pas de lecture de couverture : l'avertissement de perimetre vide, en tete, dit deja tout.
        if (f.budget() > 0) {
            b.add(analysis(coverageSentence(f)));
        }

        // Demande client du 23/09/2026 : plus de tableau « Les axes du plan » ici, il doublait le budget
        // par axe du X.3 (memes montants, meme part).
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
        b.add(new ExportBlock.ColorLegend("Code couleur des directions", ctx.groups.stream()
                .map(group -> new ExportBlock.Attribution(group.getName(), List.of(colorOf(group, ctx.groups))))
                .toList()));
        return b;
    }

    // ---- IV. Parties prenantes ----

    /**
     * Revue de l'auditeur (21/09/2026) : la matrice interet / pouvoir, absente du canevas, est retiree. La
     * partie presente directement le tableau des parties prenantes du canevas, jusque-la reporte en annexe.
     */
    private List<ExportBlock> stakeholders(Context ctx) {
        return stakeholdersTable(ctx);
    }

    /** Nom de la partie prenante ; a defaut, le debut de son role (« Direction Commerciale : exprime... »). */
    private static String actorOf(JsonNode row) {
        String actor = JsonUtil.text(row, "actor").trim();
        if (!actor.isEmpty()) {
            return actor;
        }
        String custom = customCategoryOf(row);
        if (!custom.isEmpty()) {
            return custom;
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
        if (!JsonUtil.text(row, "actor").isBlank() || !customCategoryOf(row).isEmpty()) {
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
        return actor.isEmpty() || actor.equals(category) ? category : category + " — " + actor;
    }

    /** Partie prenante saisie librement apres le choix « Autre » : la categorie porte alors son nom. */
    private static String customCategoryOf(JsonNode row) {
        String category = JsonUtil.text(row, "category").trim();
        return category.isEmpty() || SectionLabels.STAKEHOLDER_CATEGORY_LABELS.containsKey(category) ? "" : category;
    }

    // ---- V. Diagnostic strategique ----

    private List<ExportBlock> diagnostic(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();
        // Revue client du 15/09/2026 : les performances 2026 passent au bilan (partie V) et l'inventaire
        // du diagnostic est retire. Revue de l'auditeur (21/09/2026) : les tableaux se lisent sans introduction.
        sub(b, "VI.1 Matrice d'analyse de ressources et de compétences");
        b.addAll(resources(ctx));

        sub(b, "VI.2 Analyse PESTEL");
        b.addAll(pestel(ctx));

        sub(b, "VI.3 Analyse SWOT");
        b.add(mergedSwot(ctx));

        sub(b, "VI.4 Mise en relation du diagnostic stratégique");
        b.addAll(crossedStrategies(ctx));

        sub(b, "VI.5 Analyse causale");
        b.addAll(causalAnalysis(ctx));

        sub(b, "VI.6 Cartographie des risques");
        b.addAll(risks(ctx));
        return b;
    }

    /** Lignes du bilan des performances (S01B) de toutes les directions, l'indicateur a la couleur de la sienne. */
    private List<PerformanceReviewTables.Line> performanceLines(Context ctx) {
        List<PerformanceReviewTables.Line> lines = new ArrayList<>();
        for (WorkGroup group : ctx.groups) {
            for (JsonNode row : JsonUtil.arr(ctx.content(group, "S01B"), "rows")) {
                if (!PerformanceReviewTables.hasContent(row)) {
                    continue;
                }
                String indicator = JsonUtil.text(row, "indicator").trim();
                lines.add(new PerformanceReviewTables.Line(row,
                        indicator.isEmpty() ? new ExportBlock.Cell("—") : single(indicator, group, ctx)));
            }
        }
        return lines;
    }

    /**
     * L'exercice en cours et ses tendances. Une note datee avant la cloture de l'exercice ne peut pas
     * presenter ses chiffres comme realises : la lecture le rappelle, avec le compte des tendances.
     */
    private List<ExportBlock> currentPerformances(Context ctx) {
        List<PerformanceReviewTables.Line> lines = performanceLines(ctx);
        List<ExportBlock> b = new ArrayList<>();
        b.add(PerformanceReviewTables.currentYear(lines));
        ExportBlock.Callout trends = PerformanceReviewTables.trendAnalysis(lines, ctx.reviewYearClosed);
        if (trends != null) {
            b.add(trends);
        }
        return b;
    }

    /**
     * Matrice des ressources et des competences, sur le modele transmis par le client : une ligne
     * par ressource, les forces, faiblesses et defis de toutes les directions fondus dans chaque
     * case, chacun a la couleur de la direction qui l'a releve.
     */
    private List<ExportBlock> resources(Context ctx) {
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        // Les ressources saisies librement (« Autres », precise) suivent les lignes du canevas.
        Set<String> resourceKeys = new LinkedHashSet<>(List.of(DefaultSectionContentFactory.RESOURCE_KEYS));
        for (WorkGroup group : ctx.groups) {
            for (JsonNode row : JsonUtil.arr(ctx.content(group, "S02"), "rows")) {
                String key = JsonUtil.text(row, "resourceKey").trim();
                if (!key.isEmpty()) {
                    resourceKeys.add(key);
                }
            }
        }
        for (String resource : resourceKeys) {
            Contributions strengths = new Contributions(ctx.groups);
            Contributions weaknesses = new Contributions(ctx.groups);
            Contributions challenges = new Contributions(ctx.groups);
            Contributions recommendations = new Contributions(ctx.groups);
            for (WorkGroup group : ctx.groups) {
                for (JsonNode row : JsonUtil.arr(ctx.content(group, "S02"), "rows")) {
                    if (!resource.equals(JsonUtil.text(row, "resourceKey"))) {
                        continue;
                    }
                    strengths.addLines(group, JsonUtil.text(row, "strengths"));
                    weaknesses.addLines(group, JsonUtil.text(row, "weaknesses"));
                    challenges.addLines(group, JsonUtil.text(row, "challenges"));
                    recommendations.addLines(group, JsonUtil.text(row, "recommendations"));
                }
            }
            rows.add(new ExportBlock.TableRow(List.of(
                    new ExportBlock.Cell(SectionLabels.resource(resource), true, ExportBlock.Align.LEFT, ExportBlock.Background.GREY),
                    attributedCell(strengths), attributedCell(weaknesses), attributedCell(challenges),
                    attributedCell(recommendations))));
        }
        // Les lignes du canevas sont toutes la, alimentees ou non : vide, la matrice garde sa forme.
        return List.of(new ExportBlock.Table(List.of("Ressources", "Forces / Acquis", "Faiblesses", "Défis à relever",
                "Recommandations"), rows, List.of(20, 20, 20, 20, 20)));
    }

    private List<ExportBlock> pestel(Context ctx) {
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        for (String axis : DefaultSectionContentFactory.PESTEL_AXES) {
            Contributions analyses = new Contributions(ctx.groups);
            Contributions opportunities = new Contributions(ctx.groups);
            Contributions threats = new Contributions(ctx.groups);
            Contributions actions = new Contributions(ctx.groups);
            for (WorkGroup group : ctx.groups) {
                for (JsonNode row : JsonUtil.arr(ctx.content(group, "S03"), "rows")) {
                    if (!axis.equals(JsonUtil.text(row, "axis"))) {
                        continue;
                    }
                    analyses.addLines(group, JsonUtil.text(row, "analysis"));
                    opportunities.addLines(group, JsonUtil.text(row, "opportunities"));
                    threats.addLines(group, JsonUtil.text(row, "threats"));
                    actions.addLines(group, JsonUtil.text(row, "actions"));
                }
            }
            rows.add(new ExportBlock.TableRow(List.of(
                    new ExportBlock.Cell(SectionLabels.pestel(axis), true, ExportBlock.Align.LEFT, ExportBlock.Background.GREY),
                    attributedCell(analyses), attributedCell(threats), attributedCell(opportunities),
                    attributedCell(actions))));
        }
        return List.of(new ExportBlock.Table(List.of("Items", "Analyses", "Menaces", "Opportunités",
                "Actions pour atténuer les menaces ou saisir les opportunités"),
                rows, List.of(12, 22, 22, 22, 22)));
    }

    /**
     * SWOT de toutes les directions fondu en un seul tableau, sur la grille du canevas (chaque categorie en
     * INTERNE et EXTERNE) : un element cite plusieurs fois n'apparait qu'une fois, dans la couleur de ses auteurs.
     */
    private ExportBlock.Table mergedSwot(Context ctx) {
        List<ExportBlock.Cell> cells = new ArrayList<>();
        for (String field : SwotGridTable.FIELDS) {
            Contributions items = new Contributions(ctx.groups);
            for (WorkGroup group : ctx.groups) {
                for (String item : JsonUtil.strList(ctx.content(group, "S04"), field)) {
                    items.add(group, item);
                }
            }
            cells.add(attributedCell(items));
        }
        return SwotGridTable.build(cells);
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
        return List.of(TowsMatrixTable.build(field -> attributedCell(answers.get(field))));
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
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        bySource.forEach((source, items) -> rows.add(new ExportBlock.TableRow(List.of(
                rowLabel(SectionLabels.causal(source)), attributedCell(items)))));
        return List.of(new ExportBlock.Table(List.of("Sources", "Analyse"), rows, List.of(28, 72)));
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
        List<String> headers = List.of("Direction", "Synthèse de l'inventaire (SWOT, PESTEL, parties prenantes, analyse causale)");
        List<Integer> widths = List.of(22, 78);
        return List.of(rows.isEmpty() ? emptyTable(headers, widths) : new ExportBlock.Table(headers, rows, widths));
    }

    /**
     * Synthese des contraintes, enjeux et defis prioritaires (S06B), sur le modele du canevas : une ligne par
     * domaine d'activites, toutes directions confondues. Deux directions qui nomment le meme domaine partagent
     * sa ligne ; un domaine laisse vide (les domaines pre-remplis du modele) n'en occupe aucune.
     */
    private List<ExportBlock> constraintsSynthesis(Context ctx) {
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, Contributions> constraints = new LinkedHashMap<>();
        Map<String, Contributions> challenges = new LinkedHashMap<>();
        for (WorkGroup group : ctx.groups) {
            for (JsonNode row : JsonUtil.arr(ctx.content(group, "S06B"), "rows")) {
                List<String> rowConstraints = JsonUtil.strList(row, "constraints");
                List<String> rowChallenges = JsonUtil.strList(row, "challenges");
                if (rowConstraints.stream().allMatch(String::isBlank) && rowChallenges.stream().allMatch(String::isBlank)) {
                    continue;
                }
                String domain = JsonUtil.text(row, "domain").trim();
                String key = PsdCrossGroupMerge.normalize(domain);
                labels.putIfAbsent(key, domain.isEmpty() ? "Autres" : domain);
                Contributions c = constraints.computeIfAbsent(key, k -> new Contributions(ctx.groups));
                Contributions d = challenges.computeIfAbsent(key, k -> new Contributions(ctx.groups));
                rowConstraints.forEach(item -> c.add(group, item));
                rowChallenges.forEach(item -> d.add(group, item));
            }
        }
        List<String> headers = List.of("Domaines d'activités", "Contraintes", "Défis et enjeux prioritaires");
        List<Integer> widths = List.of(22, 39, 39);
        if (labels.isEmpty()) {
            return List.of(emptyTable(headers, widths));
        }
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        labels.forEach((key, label) -> rows.add(new ExportBlock.TableRow(List.of(
                rowLabel(label), attributedCell(constraints.get(key)), attributedCell(challenges.get(key))))));
        return List.of(new ExportBlock.Table(headers, rows, widths));
    }

    private static ExportBlock.Cell rowLabel(String text) {
        return new ExportBlock.Cell(text, true, ExportBlock.Align.LEFT, ExportBlock.Background.GREY);
    }

    /**
     * Ligne d'un tableau que rien n'alimente encore. Revue de l'auditeur (22/09/2026) : un tableau vide
     * garde sa structure dans la note — ses colonnes, et un tiret par case — plutot que de ceder la place
     * a une phrase « Aucun ... n'est encore approuvé ».
     */
    private static ExportBlock.TableRow emptyRow(int columns) {
        return new ExportBlock.TableRow(Collections.nCopies(columns, new ExportBlock.Cell("—")));
    }

    /** Un tableau du canevas sans aucune ligne : ses en-tetes et une ligne de tirets. */
    private static ExportBlock.Table emptyTable(List<String> headers, List<Integer> widths) {
        return new ExportBlock.Table(headers, List.of(emptyRow(headers.size())), widths);
    }

    private List<ExportBlock> risks(Context ctx) {
        List<Risk> risks = ctx.risks(false);
        List<Risk> high = risks.stream().filter(risk -> risk.criticality() >= 6).toList();
        List<String> headers = List.of("Catégorie", "Risque", "Impact sur les activités", "Fréquence (N)", "Gravité (Q)", "N × Q", "Criticité",
                "Actions d'atténuation");
        List<Integer> widths = List.of(12, 19, 14, 9, 9, 7, 9, 21);
        List<ExportBlock> b = new ArrayList<>();
        b.add(new ExportBlock.Heading("Risques de criticité élevée (N × Q de 6 à 9)", 3));
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        for (Risk risk : high) {
            rows.add(riskRow(risk, ctx));
        }
        b.add(rows.isEmpty() ? emptyTable(headers, widths) : new ExportBlock.Table(headers, rows, widths));
        if (!risks.isEmpty()) {
            b.add(analysis("les directions identifient " + risks.size() + " risques, dont " + high.size()
                    + " de criticité élevée."));
        }
        b.addAll(riskMethodology());
        return b;
    }

    /** Lecture des colonnes N, Q et N × Q de la cartographie, telle que la donne le canevas. */
    private static List<ExportBlock> riskMethodology() {
        return List.of(
                new ExportBlock.Heading("Méthodologie d'évaluation", 4),
                new ExportBlock.Table(List.of("Niveau de risque (Fréquence)", "Quotation (Gravité) / impact (Q)", "Criticité = N × Q"),
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

    private ExportBlock.TableRow riskRow(Risk risk, Context ctx) {
        ExportBlock.Cell criticality = new ExportBlock.Cell(SectionLabels.criticality(risk.label()), true,
                ExportBlock.Align.CENTER, SectionLabels.criticalityBackground(risk.label()));
        ExportBlock.Cell impact = new ExportBlock.Cell(JsonUtil.dash(risk.impact()));
        return new ExportBlock.TableRow(List.of(
                new ExportBlock.Cell(risk.category()),
                single(risk.details(), risk.group(), ctx),
                impact,
                center(String.valueOf(risk.level())),
                center(String.valueOf(risk.quotation())),
                center(String.valueOf(risk.criticality())),
                criticality,
                new ExportBlock.Cell(risk.mitigation())));
    }

    // ---- IX. Cadre strategique ----

    private List<ExportBlock> strategicFramework(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();

        // Demande client du 23/09/2026 : la note ne garde que titres, sous-titres et tableaux. Vision, mission et
        // valeurs sont des textes : seuls leurs titres figurent, que la Direction Generale les ait arretes ou non
        // (les propositions des directions n'y sont plus reprises). Le texte reste dans le Plan Strategique de SENICO.
        sub(b, "IX.1 Vision");
        sub(b, "IX.2 Mission");
        sub(b, "IX.3 Valeurs");

        sub(b, "IX.4 Synthèse des recommandations stratégiques");
        b.add(inventoryTable(ctx));

        sub(b, "IX.5 Orientations et axes stratégiques");
        b.addAll(axesTable(ctx));
        return b;
    }

    /**
     * Inventaire du diagnostic, sur le modele du canevas (« 7. INVENTAIRE ») : ce que les directions ont
     * retenu du SWOT, du PESTEL, des parties prenantes et de l'analyse causale, une colonne par analyse et
     * un constat par case, dans la couleur de ses auteurs. Demande client du 23/09/2026 : il precede les
     * orientations strategiques, qu'il fonde.
     */
    private ExportBlock.Table inventoryTable(Context ctx) {
        Contributions swot = new Contributions(ctx.groups);
        Contributions pestel = new Contributions(ctx.groups);
        Contributions stakeholders = new Contributions(ctx.groups);
        Contributions causal = new Contributions(ctx.groups);
        for (SwotGridTable.Category category : SwotGridTable.CATEGORIES) {
            for (WorkGroup group : ctx.groups) {
                for (String field : category.fields()) {
                    JsonUtil.strList(ctx.content(group, "S04"), field).stream().filter(item -> !item.isBlank())
                            .forEach(item -> swot.add(group, category.singular() + " : " + item.trim()));
                }
            }
        }
        for (WorkGroup group : ctx.groups) {
            for (JsonNode row : JsonUtil.arr(ctx.content(group, "S03"), "rows")) {
                String axis = SectionLabels.pestel(JsonUtil.text(row, "axis"));
                addPrefixedLines(pestel, group, axis + " — analyse : ", JsonUtil.text(row, "analysis"));
                addPrefixedLines(pestel, group, axis + " — menace : ", JsonUtil.text(row, "threats"));
                addPrefixedLines(pestel, group, axis + " — opportunité : ", JsonUtil.text(row, "opportunities"));
            }
            for (JsonNode row : JsonUtil.arr(ctx.content(group, "S01"), "rows")) {
                String label = stakeholderLabel(row).trim();
                if (label.isEmpty()) {
                    continue;
                }
                List<String> levels = new ArrayList<>();
                String importance = rating(JsonUtil.text(row, "importance"));
                String influence = rating(JsonUtil.text(row, "influence"));
                if (!importance.equals("—")) {
                    levels.add("importance " + importance.toLowerCase(java.util.Locale.ROOT));
                }
                if (!influence.equals("—")) {
                    levels.add("influence " + influence.toLowerCase(java.util.Locale.ROOT));
                }
                stakeholders.add(group, levels.isEmpty() ? label : label + " (" + String.join(", ", levels) + ")");
            }
            for (JsonNode row : JsonUtil.arr(ctx.content(group, "S06"), "rows")) {
                String source = SectionLabels.causal(JsonUtil.text(row, "source"));
                JsonUtil.strList(row, "items").stream().filter(item -> !item.isBlank())
                        .forEach(item -> causal.add(group, source + " : " + item.trim()));
            }
        }

        List<String> headers = List.of("SWOT", "PESTEL", "Analyse des parties prenantes", "Analyse causale et autres");
        List<Integer> widths = List.of(29, 28, 21, 22);
        List<List<ExportBlock.Attribution>> columns = Stream.of(swot, pestel, stakeholders, causal)
                .map(Contributions::toAttributions).toList();
        int rowCount = columns.stream().mapToInt(List::size).max().orElse(0);
        if (rowCount == 0) {
            return emptyTable(headers, widths);
        }
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            List<ExportBlock.Cell> cells = new ArrayList<>();
            for (List<ExportBlock.Attribution> column : columns) {
                cells.add(i < column.size() ? new ExportBlock.Cell(List.of(column.get(i))) : new ExportBlock.Cell(""));
            }
            rows.add(new ExportBlock.TableRow(cells));
        }
        return new ExportBlock.Table(headers, rows, widths);
    }

    /** Chaque ligne d'un champ libre, precedee de l'intitule qui la situe dans sa colonne. */
    private static void addPrefixedLines(Contributions items, WorkGroup group, String prefix, String raw) {
        if (raw == null) {
            return;
        }
        for (String line : raw.split("\\R")) {
            String text = line.replaceFirst("^\\s*•\\s*", "").trim();
            if (!text.isEmpty()) {
                items.add(group, prefix + text);
            }
        }
    }

    /**
     * Axes strategiques de la note, sur le modele du canevas (« Axes strategiques / orientations ») : un axe par
     * colonne, puis son objectif et ses objectifs specifiques. Revue de l'auditeur (21/09/2026) : ce tableau remplace
     * la liste des axes et leur presentation redigee, qui le repetaient. La rangee « Axes des directions regroupes »
     * est retiree a la demande du client (23/09/2026).
     * Au-dela de six axes, les colonnes deviendraient illisibles : la presentation detaillee reprend alors.
     */
    private List<ExportBlock> axesTable(Context ctx) {
        AxisPlan plan = ctx.plan;
        if (!plan.consolidated() || plan.axes().isEmpty() || plan.axes().size() > 6) {
            return axesBlocks(ctx);
        }
        List<String> headers = new ArrayList<>();
        List<ExportBlock.Cell> titles = new ArrayList<>();
        List<ExportBlock.Cell> goals = new ArrayList<>();
        List<ExportBlock.Cell> objectives = new ArrayList<>();
        boolean anyGoal = false;
        for (int i = 0; i < plan.axes().size(); i++) {
            PlanAxis axis = plan.axes().get(i);
            headers.add("");
            titles.add(new ExportBlock.Cell("Axe " + (i + 1) + " : " + axis.title()));
            goals.add(new ExportBlock.Cell(JsonUtil.dash(axis.objective())));
            anyGoal |= !axis.objective().isBlank();
            Contributions items = new Contributions(ctx.groups);
            for (DirectionAxis member : axis.members()) {
                member.objectives().forEach(objective -> items.add(member.group(), objective));
            }
            objectives.add(attributedCell(items));
        }
        // La rangee des axes est la premiere ligne du tableau, pas un en-tete : un en-tete se repete en haut de
        // la page suivante quand le tableau deborde, et la rangee « Axe 1 ... Axe 5 » apparaissait deux fois.
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        rows.add(new ExportBlock.TableRow(titles, true, ExportBlock.Background.PRIMARY_DARK));
        if (anyGoal) {
            rows.add(ExportBlock.TableRow.band("Objectif de l'axe (Orientation stratégique)", ExportBlock.Background.PRIMARY_LIGHT));
            rows.add(new ExportBlock.TableRow(goals));
        }
        rows.add(ExportBlock.TableRow.band("Objectifs spécifiques", ExportBlock.Background.PRIMARY_LIGHT));
        rows.add(new ExportBlock.TableRow(objectives));

        List<ExportBlock> b = new ArrayList<>();
        b.add(new ExportBlock.Table(headers, rows));
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

    private List<ExportBlock> axesBlocks(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();
        AxisPlan plan = ctx.plan;
        if (!plan.consolidated()) {
            b.add(new ExportBlock.Callout("Les axes stratégiques de l'entreprise ne sont pas encore arrêtés par la "
                    + "Direction Générale : figurent ci-dessous les axes proposés par chaque direction, sans "
                    + "regroupement. Le regroupement en axes communs se saisit sur " + EDIT_SCREEN + ".",
                    ExportBlock.Tone.WARNING));
            // Revue de l'auditeur (22/09/2026) : un tableau, meme vide, plutot que deux listes.
            List<ExportBlock.TableRow> rows = new ArrayList<>();
            for (PlanAxis axis : plan.axes()) {
                for (DirectionAxis member : axis.members()) {
                    Contributions objectives = new Contributions(ctx.groups);
                    member.objectives().forEach(objective -> objectives.add(member.group(), objective));
                    rows.add(new ExportBlock.TableRow(List.of(single(member.group().getName(), member.group(), ctx),
                            single(member.title(), member.group(), ctx), attributedCell(objectives))));
                }
            }
            List<String> headers = List.of("Direction", "Axe d'intervention proposé", "Objectifs spécifiques");
            List<Integer> widths = List.of(24, 32, 44);
            b.add(rows.isEmpty() ? emptyTable(headers, widths) : new ExportBlock.Table(headers, rows, widths));
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
        // Revue client du 15/09/2026 : les tableaux par axe (cadre logique, plan d'actions, budget detaille) sont
        // reportes en annexe ; le corps garde la synthese du cadre logique. Revue de l'auditeur (21/09/2026) : plus
        // de texte d'introduction, un simple renvoi ; le budget par axe et par exercice revient dans le corps.
        // Demande client du 23/09/2026 (titres et tableaux seulement) : la synthese du cadre logique (S09B), un
        // texte, n'y figure plus ; ses intitules par direction restaient seuls, sans rien dessous.
        sub(b, "X.1 Cadre logique");
        b.add(annexReference(ANNEXE_CADRE_LOGIQUE));
        sub(b, "X.2 Opérationnalisation : plan d'actions " + PERIOD);
        b.add(annexReference(ANNEXE_PLANIFICATION));
        sub(b, "X.3 Budget du plan");
        b.addAll(budgetBlocks(ctx));
        b.add(annexReference(ANNEXE_BUDGET));
        sub(b, "X.4 Plan de financement");
        b.addAll(financing(ctx));
        sub(b, "X.5 Plan d'évolution des effectifs (statut, hiérarchie, genre)");
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

    /** L'occurrence sans axe d'un tableau par axe : sa seule structure, sans intertitre. */
    private static final AxisView EMPTY_AXIS = new AxisView("", "", List.of());

    /**
     * Tableaux d'une rubrique, axe par axe. Sous un plan consolide, chaque axe de l'entreprise garde son
     * tableau, vide ou non (revue de l'auditeur du 22/09/2026). Sans consolidation, un axe de direction sans
     * contenu est passe — jusqu'a quatre par direction, leurs squelettes noieraient le document — mais la
     * rubrique garde au moins une occurrence vide de sa structure.
     */
    private List<ExportBlock> perAxis(Context ctx, java.util.function.Function<AxisView, List<ExportBlock>> render) {
        List<ExportBlock> blocks = new ArrayList<>();
        for (AxisView axis : axisViews(ctx)) {
            blocks.addAll(render.apply(axis));
        }
        if (blocks.isEmpty()) {
            blocks.addAll(render.apply(EMPTY_AXIS));
        }
        return blocks;
    }

    /** Vrai pour un axe de direction sans contenu sous un plan non consolide (cf. {@link #perAxis}). */
    private static boolean omitted(Context ctx, AxisView axis, boolean empty) {
        return empty && !ctx.plan.consolidated() && !axis.title().isBlank();
    }

    /** Intertitre d'un tableau par axe ; aucun pour l'occurrence vide sans axe. */
    private static void axisHeading(List<ExportBlock> blocks, AxisView axis, String prefix) {
        if (!axis.title().isBlank()) {
            blocks.add(new ExportBlock.Heading(prefix + axis.title(), 3));
        }
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
        // Aucune direction n'a formule l'objectif de son axe : une colonne de tirets n'apprend rien au
        // lecteur. La decision vaut pour tous les axes, pour que leurs tableaux gardent la meme forme.
        boolean withObjective = anyObjective;
        List<String> headers = withObjective
                ? List.of("Axe d'intervention de la direction", "Objectif de l'axe", "Objectifs spécifiques")
                : List.of("Axe d'intervention de la direction", "Objectifs spécifiques");
        List<Integer> widths = withObjective ? List.of(26, 34, 40) : List.of(35, 65);
        // Sans aucun axe d'intervention, la rubrique garde la structure du tableau (revue de l'auditeur du 22/09/2026).
        if (axes.isEmpty()) {
            return List.of(emptyTable(headers, widths));
        }

        List<ExportBlock> b = new ArrayList<>();
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
     */
    private List<ExportBlock> logicalFrameworks(Context ctx) {
        return perAxis(ctx, axis -> logicalFramework(ctx, axis));
    }

    private List<ExportBlock> logicalFramework(Context ctx, AxisView axis) {
        String[] fields = {"interventionLogic", "iov", "verificationMeans", "assumptions"};
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
        boolean empty = objectives.isEmpty()
                && cellsByLevel.values().stream().flatMap(List::stream).allMatch(Contributions::isEmpty);
        if (omitted(ctx, axis, empty)) {
            return List.of();
        }
        // Un axe sans cadre logique garde le sien, vide : les cinq niveaux du canevas, un tiret par case.
        List<ExportBlock> tables = new ArrayList<>();
        axisHeading(tables, axis, "");
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        if (!axis.objective().isBlank()) {
            // En bandeau du tableau, et non en paragraphe : la note de synthese retire ses paragraphes.
            rows.add(ExportBlock.TableRow.band("Objectif : " + axis.objective(), ExportBlock.Background.PRIMARY_LIGHT));
        } else if (!objectives.isEmpty()) {
            tables.add(new ExportBlock.AttributedList("Objectif", objectives.toAttributions()));
        }
        cellsByLevel.forEach((level, cells) -> rows.add(new ExportBlock.TableRow(List.of(
                rowLabel(SectionLabels.logframe(level)), attributedCell(cells.get(0)), attributedCell(cells.get(1)),
                attributedCell(cells.get(2)), attributedCell(cells.get(3))))));
        tables.add(new ExportBlock.Table(List.of("Logique d'intervention", "Énoncé",
                "Indicateurs objectivement vérifiables (IOV)", "Moyens et sources de vérification",
                "Conditions critiques / Hypothèses"), rows, List.of(17, 24, 21, 19, 19)));
        return tables;
    }

    /** Rubrique dont les tableaux sont reportes en annexe : le seul renvoi a l'annexe, qui existe toujours. */
    private static ExportBlock.Paragraph annexReference(String annex) {
        return new ExportBlock.Paragraph("Tableaux par axe en annexe (" + annex + ").", true, false);
    }

    /**
     * Plan d'actions de chaque axe, sur le modele du canevas : sous chaque effet (OS), les extrants,
     * les activites, les exercices ou elles sont programmees et les responsables. Les OS portent le
     * numero de la synthese du cadre strategique.
     */
    private List<ExportBlock> actionPlans(Context ctx) {
        return perAxis(ctx, axis -> actionPlan(ctx, axis));
    }

    private List<ExportBlock> actionPlan(Context ctx, AxisView axis) {
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
        if (omitted(ctx, axis, rows.isEmpty())) {
            return List.of();
        }
        List<String> headers = new ArrayList<>(List.of("Extrants", "Activités pour atteindre les résultats"));
        headers.addAll(List.of(YEARS));
        headers.add("Responsables");
        List<Integer> widths = new ArrayList<>(List.of(19, 29));
        widths.addAll(Collections.nCopies(YEARS.length, 7));
        widths.add(17);
        List<ExportBlock> tables = new ArrayList<>();
        axisHeading(tables, axis, "");
        tables.add(rows.isEmpty() ? emptyTable(headers, widths) : new ExportBlock.Table(headers, rows, widths));
        return tables;
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
        List<String> headers = new ArrayList<>(List.of("Extrants", "Activités pour atteindre les résultats"));
        headers.addAll(List.of(YEARS));
        headers.addAll(List.of("Totaux", "Responsable"));
        List<Integer> widths = new ArrayList<>(List.of(14, 20));
        widths.addAll(Collections.nCopies(YEARS.length, 8));
        widths.addAll(List.of(10, 16));
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
            if (omitted(ctx, axis, rows.isEmpty())) {
                continue;
            }
            axisHeading(tables, axis, "Budget détaillé — ");
            List<ExportBlock.Cell> recap = new ArrayList<>();
            recap.add(new ExportBlock.Cell(axis.title()));
            if (rows.isEmpty()) {
                // Un axe sans budget garde son tableau vide, et sa ligne a tirets dans le total general.
                tables.add(emptyTable(headers, widths));
                for (int y = 0; y < YEARS.length; y++) {
                    recap.add(right("—"));
                }
                recap.add(new ExportBlock.Cell("—", true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
                grandRows.add(new ExportBlock.TableRow(recap));
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
            tables.add(new ExportBlock.Table(headers, rows, widths));

            for (int y = 0; y < YEARS.length; y++) {
                grandYears[y] += axisYears[y];
                recap.add(right(JsonUtil.formatMillions(axisYears[y])));
            }
            recap.add(new ExportBlock.Cell(JsonUtil.formatMillions(axisTotal), true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
            grandRows.add(new ExportBlock.TableRow(recap));
            grandTotal += axisTotal;
        }
        if (tables.isEmpty()) {
            tables.add(emptyTable(headers, widths));
        }
        if (withGrandTotal && !grandRows.isEmpty()) {
            List<ExportBlock.Cell> grand = new ArrayList<>();
            grand.add(new ExportBlock.Cell("TOTAL GÉNÉRAL", true, ExportBlock.Align.LEFT, ExportBlock.Background.NONE));
            for (double yearTotal : grandYears) {
                grand.add(new ExportBlock.Cell(JsonUtil.formatMillions(yearTotal), true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
            }
            grand.add(new ExportBlock.Cell(JsonUtil.formatMillions(grandTotal), true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
            grandRows.add(new ExportBlock.TableRow(grand, true, ExportBlock.Background.PRIMARY_LIGHT));

            List<String> grandHeaders = new ArrayList<>(List.of("Axe stratégique"));
            grandHeaders.addAll(List.of(YEARS));
            grandHeaders.add("Totaux");
            List<Integer> grandWidths = new ArrayList<>(List.of(35));
            grandWidths.addAll(Collections.nCopies(YEARS.length, 11));
            grandWidths.add(10);
            tables.add(new ExportBlock.Heading("Total général du budget", 3));
            tables.add(new ExportBlock.Table(grandHeaders, grandRows, grandWidths));
        }
        return tables;
    }

    /**
     * Cadre de mesure de rendement de chaque axe, sur le modele du canevas : pour chaque niveau de
     * resultat, l'indicateur, sa reference, ses cibles annuelles et le responsable de son suivi.
     */
    private List<ExportBlock> performanceFrameworks(Context ctx) {
        return perAxis(ctx, axis -> performanceFramework(ctx, axis));
    }

    private List<ExportBlock> performanceFramework(Context ctx, AxisView axis) {
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        boolean empty = true;
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
            empty &= levelRows.isEmpty();
            // Un niveau sans ligne garde son bandeau et une ligne de tirets : le canevas reste lisible vide.
            rows.add(ExportBlock.TableRow.band(performanceBand(level), ExportBlock.Background.GREY));
            rows.addAll(levelRows.isEmpty() ? List.of(emptyRow(3 + YEARS.length + 1)) : levelRows);
        }
        if (omitted(ctx, axis, empty)) {
            return List.of();
        }
        List<String> headers = new ArrayList<>(List.of("Résultat / Extrant", "Indicateur (IOV)", "Réf. " + REVIEW_YEAR));
        headers.addAll(List.of(YEARS));
        headers.add("Responsables");
        List<Integer> widths = new ArrayList<>(List.of(21, 21, 10));
        widths.addAll(Collections.nCopies(YEARS.length, 7));
        widths.add(13);
        List<ExportBlock.HeaderBand> bands = List.of(new ExportBlock.HeaderBand("Indicateurs/Cibles", 3 + YEARS.length),
                new ExportBlock.HeaderBand("", 1));
        List<ExportBlock> tables = new ArrayList<>();
        axisHeading(tables, axis, "");
        tables.add(new ExportBlock.Table(headers, rows, widths, bands));
        return tables;
    }

    private static String performanceBand(String level) {
        return SectionLabels.performanceLevel(level);
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

    /** Budget par axe (ou par direction) et par exercice : le tableau, son graphique et sa lecture. */
    private List<ExportBlock> budgetBlocks(Context ctx) {
        List<ExportBlock> b = new ArrayList<>();
        List<BudgetLine> lines = budgetLines(ctx);
        double total = lines.stream().mapToDouble(line -> sum(line.years())).sum();
        // Sans montant, le tableau garde ses lignes (les axes de l'entreprise), un tiret par case.
        boolean empty = total <= 0;
        String dimension = ctx.plan.consolidated() ? "axe stratégique" : "direction";

        List<String> headers = new ArrayList<>();
        headers.add(ctx.plan.consolidated() ? "Axe stratégique" : "Direction");
        headers.addAll(List.of(YEARS));
        headers.add("Total (M FCFA)");
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
                cells.add(right(empty ? "—" : JsonUtil.formatMillions(line.years()[y])));
                yearTotals[y] += line.years()[y];
            }
            cells.add(new ExportBlock.Cell(empty ? "—" : JsonUtil.formatMillions(sum(line.years())), true,
                    ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
            cells.add(right(empty ? "—" : JsonUtil.formatPercent(sum(line.years()) / total * 100)));
            rows.add(new ExportBlock.TableRow(cells));
        }
        if (rows.isEmpty()) {
            b.add(emptyTable(headers, widths));
            return b;
        }
        List<ExportBlock.Cell> totalCells = new ArrayList<>();
        totalCells.add(new ExportBlock.Cell("TOTAL", true, ExportBlock.Align.LEFT, ExportBlock.Background.NONE));
        for (double yearTotal : yearTotals) {
            totalCells.add(new ExportBlock.Cell(empty ? "—" : JsonUtil.formatMillions(yearTotal), true,
                    ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
        }
        totalCells.add(new ExportBlock.Cell(empty ? "—" : JsonUtil.formatMillions(total), true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
        totalCells.add(new ExportBlock.Cell(empty ? "—" : "100 %", true, ExportBlock.Align.RIGHT, ExportBlock.Background.NONE));
        rows.add(new ExportBlock.TableRow(totalCells, true, ExportBlock.Background.PRIMARY_LIGHT));
        b.add(new ExportBlock.Table(headers, rows, widths));
        // Sans montant, ni graphique ni lecture.
        if (empty) {
            return b;
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
        // Sans montant, le plan de financement garde les cinq sources du canevas, un tiret par case.
        boolean empty = financingTotal <= 0;
        double budget = f.budget();
        List<ExportBlock> b = new ArrayList<>();
        // Colonnes du canevas (demande client du 23/09/2026) : la part du budget couverte se lit dans le graphique.
        List<String> headers = List.of("Sources de financement", "Montant (FCFA)", "Pourcentage (%)",
                "Modalités de mobilisation", "Période", "Responsables");
        List<Integer> widths = List.of(18, 17, 11, 24, 12, 18);

        List<ExportBlock.TableRow> rows = new ArrayList<>();
        Map.Entry<String, Double> top = null;
        for (Map.Entry<String, Double> entry : bySource.entrySet()) {
            // Chaque source du canevas garde sa ligne, chiffree ou non : une source sans montant peut deja
            // porter des modalites, une periode ou un responsable.
            boolean funded = entry.getValue() > 0;
            if (funded && (top == null || entry.getValue() > top.getValue())) {
                top = entry;
            }
            List<Contributions> details = detailsBySource.get(entry.getKey());
            rows.add(new ExportBlock.TableRow(List.of(
                    new ExportBlock.Cell(SectionLabels.financing(entry.getKey())),
                    right(funded ? JsonUtil.formatCurrency(entry.getValue()) : "—"),
                    right(funded ? JsonUtil.formatPercent(entry.getValue() / financingTotal * 100) : "—"),
                    attributedCell(details == null ? null : details.get(0)),
                    attributedCell(details == null ? null : details.get(1)),
                    attributedCell(details == null ? null : details.get(2)))));
        }
        if (empty) {
            b.add(new ExportBlock.Table(headers, rows, widths));
            return b;
        }
        rows.add(totalRow("TOTAL", JsonUtil.formatCurrency(financingTotal), "100 %", "", "", ""));
        b.add(new ExportBlock.Table(headers, rows, widths));

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
        // Les lignes du modele client d'abord, dans son ordre : une direction qui n'en a renseigne qu'une
        // partie ne fait pas disparaitre les autres, et un tableau vide garde toute sa structure.
        for (String[] row : DefaultSectionContentFactory.STAFF_ROWS) {
            String key = row[0] + "|" + row[1];
            categoryOf.put(key, row[0]);
            labelOf.put(key, SectionLabels.staffRow(row[1], ""));
            valuesOf.put(key, new int[YEARS.length][2]);
        }
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
        // Vide, le plan garde les lignes du modele, un tiret par case (cf. staffLine).
        boolean any = false;
        for (int[] year : totals) {
            any |= year[0] + year[1] > 0;
        }

        List<String> headers = new ArrayList<>(List.of("Années"));
        List<ExportBlock.HeaderBand> bands = new ArrayList<>(List.of(new ExportBlock.HeaderBand("", 1)));
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
                    rows.add(staffLine(labelOf.get(entry.getKey()), valuesOf.get(entry.getKey()), false, !any));
                }
            }
        }
        rows.add(staffLine("TOTAUX", totals, true, !any));
        if (!any) {
            return List.of(new ExportBlock.Paragraph("M : hommes, F : femmes."),
                    new ExportBlock.Table(headers, rows, widths, bands));
        }

        int last = YEARS.length - 1;
        int firstTotal = totals[0][0] + totals[0][1];
        int lastTotal = totals[last][0] + totals[last][1];
        double firstShare = firstTotal > 0 ? totals[0][1] * 100d / firstTotal : 0;
        double lastShare = lastTotal > 0 ? totals[last][1] * 100d / lastTotal : 0;
        String change = firstTotal > 0
                ? " (" + (lastTotal >= firstTotal ? "+" : "") + JsonUtil.formatPercent((lastTotal - firstTotal) * 100d / firstTotal) + ")"
                : "";
        return List.of(
                new ExportBlock.Paragraph("M : hommes, F : femmes."),
                new ExportBlock.Table(headers, rows, widths, bands),
                analysis("les effectifs prévus passeront de " + JsonUtil.formatNumber(firstTotal) + " agents en " + FIRST_YEAR
                        + " à " + JsonUtil.formatNumber(lastTotal) + " en " + LAST_YEAR + change
                        + " ; la part des femmes, de " + JsonUtil.formatDecimal(firstShare) + " % à "
                        + JsonUtil.formatDecimal(lastShare) + " %."));
    }

    /** @param blank tableau vide : un tiret par case plutot que des zeros qui se liraient comme des effectifs nuls */
    private ExportBlock.TableRow staffLine(String label, int[][] values, boolean total, boolean blank) {
        List<ExportBlock.Cell> cells = new ArrayList<>();
        cells.add(new ExportBlock.Cell(label, total, ExportBlock.Align.LEFT, ExportBlock.Background.NONE));
        for (int[] year : values) {
            cells.add(right(blank ? "—" : JsonUtil.formatNumber(year[0])));
            cells.add(right(blank ? "—" : JsonUtil.formatNumber(year[1])));
            cells.add(new ExportBlock.Cell(blank ? "—" : JsonUtil.formatNumber(year[0] + year[1]), true, ExportBlock.Align.RIGHT,
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
        // Revue du 22/09/2026 : la fiche des indicateurs ouvre les tableaux du cadre de mesure de rendement, en
        // annexe ; les tableaux de repartition des indicateurs (ancien XI.3), qui la repetaient, sont retires.
        // Demande client du 23/09/2026 : le renvoi en annexe, un paragraphe, n'est plus repris ; le titre seul reste.
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
        // Sans aucun axe, le tableau garde ses intitules de colonnes et une ligne de tirets.
        if (rows.isEmpty()) {
            rows.add(summaryHeaderRow());
            rows.add(emptyRow(5));
        }
        String vision = ctx.narrative(NarrativeBlockKey.VISION);
        if (!vision.isBlank()) {
            rows.add(0, ExportBlock.TableRow.band("Vision : « " + unquote(vision) + " »", ExportBlock.Background.PRIMARY_LIGHT));
        }
        return List.of(
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
        // Un axe de l'entreprise sans OS garde son bandeau, ses intitules et une ligne de tirets, comme les
        // tableaux par axe des annexes ; seuls les axes non rattaches ne valent rien quand ils sont vides.
        if (axisRows.isEmpty()) {
            if (UNLINKED_AXES.equals(title)) {
                return;
            }
            axisRows.add(emptyRow(5));
        }
        rows.add(ExportBlock.TableRow.band(title, ExportBlock.Background.PRIMARY_DARK));
        rows.add(summaryHeaderRow());
        rows.addAll(axisRows);
    }

    /** Les intitules de colonnes de la synthese, repetes sous le bandeau de chaque axe comme dans le modele client. */
    private static ExportBlock.TableRow summaryHeaderRow() {
        return new ExportBlock.TableRow(List.of(new ExportBlock.Cell("Orientation stratégique (OS)"),
                new ExportBlock.Cell("Actions"), new ExportBlock.Cell("Budget (M FCFA)"), new ExportBlock.Cell("Objectif"),
                new ExportBlock.Cell("Contraintes à lever ou opportunités à saisir")),
                true, ExportBlock.Background.PRIMARY_LIGHT);
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
        List<ExportBlock> b = new ArrayList<>();
        narrative(b, ctx, NarrativeBlockKey.CONCLUSION);
        // Ni chiffres cles ni couverture du financement ici : « L'essentiel du plan » et le plan de
        // financement (X.4) les donnent deja.
        b.add(new ExportBlock.Paragraph("Le détail propre à chaque direction figure dans son plan stratégique sectoriel.",
                true, false));
        return b;
    }

    // ---- Annexes ----

    private List<ExportBlock> annexes(Context ctx) {
        // Dans l'ordre ou le corps y renvoie (cf. ANNEXE_*). Le budget par axe et par exercice est dans le corps (X.3) :
        // l'annexe du budget ne porte que le budget detaille.
        // Demande client du 23/09/2026 : la fiche des indicateurs ouvre les annexes, devant le tableau 1.
        List<ExportBlock> b = new ArrayList<>();
        sub(b, FICHE_INDICATEURS);
        b.addAll(indicatorTable(ctx));
        sub(b, ANNEXE_CADRE_LOGIQUE);
        b.addAll(logicalFrameworks(ctx));
        sub(b, ANNEXE_PLANIFICATION);
        b.addAll(actionPlans(ctx));
        sub(b, ANNEXE_BUDGET);
        b.addAll(detailedBudgets(ctx, false));
        sub(b, ANNEXE_RENDEMENT);
        b.addAll(performanceFrameworks(ctx));
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
                        center(JsonUtil.dash(SectionLabels.stakeholderScope(JsonUtil.text(row, "scope")))),
                        new ExportBlock.Cell(JsonUtil.dash(rolesOf(row))),
                        new ExportBlock.Cell(JsonUtil.dash(JsonUtil.text(row, "expectations"))),
                        new ExportBlock.Cell(JsonUtil.dash(JsonUtil.text(row, "adaptationStrategy"))),
                        center(rating(JsonUtil.text(row, "importance"))),
                        center(rating(JsonUtil.text(row, "influence"))),
                        new ExportBlock.Cell(JsonUtil.dash(JsonUtil.text(row, "actions"))))));
            }
        }
        List<String> headers = List.of("Acteur (PP)", "Portée", "Rôles / Responsabilités", "Attentes / Intérêt / Priorités",
                "Stratégie d'adaptation", "Niveau importance", "Niveau influence", "Actions");
        List<Integer> widths = List.of(13, 8, 15, 15, 15, 10, 10, 14);
        return List.of(rows.isEmpty() ? emptyTable(headers, widths) : new ExportBlock.Table(headers, rows, widths));
    }

    /**
     * Fiche des indicateurs, rangee comme celle du modele client : un bandeau « AXE n : ... » par axe de
     * l'entreprise, sous lequel viennent les indicateurs que les directions ont rattaches a leurs axes.
     */
    private List<ExportBlock> indicatorTable(Context ctx) {
        Map<String, List<ExportBlock.TableRow>> byAxis = new LinkedHashMap<>();
        List<PlanAxis> planAxes = ctx.plan.axes();
        for (int i = 0; i < planAxes.size(); i++) {
            byAxis.put("AXE " + (i + 1) + " : " + planAxes.get(i).title(), new ArrayList<>());
        }
        String unlinked = "Indicateurs sans axe rattaché";
        for (WorkGroup group : ctx.groups) {
            for (JsonNode row : JsonUtil.arr(ctx.content(group, "S13"), "rows")) {
                String title = JsonUtil.text(row, "indicatorTitle").trim();
                if (title.isEmpty()) {
                    continue;
                }
                String band = unlinked;
                String axisCode = JsonUtil.text(row, "axisCode").trim();
                for (int i = 0; i < planAxes.size() && !axisCode.isEmpty(); i++) {
                    if (planAxes.get(i).keys().contains(key(group, axisCode))) {
                        band = "AXE " + (i + 1) + " : " + planAxes.get(i).title();
                        break;
                    }
                }
                byAxis.computeIfAbsent(band, k -> new ArrayList<>()).add(new ExportBlock.TableRow(List.of(
                        single(title, group, ctx),
                        new ExportBlock.Cell(JsonUtil.text(row, "calculationMethod")),
                        new ExportBlock.Cell(JsonUtil.text(row, "periodicity")),
                        new ExportBlock.Cell(JsonUtil.text(row, "collectionSource")),
                        new ExportBlock.Cell(JsonUtil.text(row, "verificationSource")),
                        new ExportBlock.Cell(JsonUtil.text(row, "responsibleStructure")))));
            }
        }
        List<ExportBlock.TableRow> rows = new ArrayList<>();
        byAxis.forEach((band, axisRows) -> {
            if (!axisRows.isEmpty()) {
                rows.add(ExportBlock.TableRow.band(band, ExportBlock.Background.PRIMARY_LIGHT));
                rows.addAll(axisRows);
            }
        });
        List<String> headers = List.of("Intitulés indicateurs", "Modes de calcul", "Périodicités",
                "Sources et moyens de collecte", "Sources de vérification", "Structures responsables");
        List<Integer> widths = List.of(18, 20, 14, 17, 15, 16);
        return List.of(rows.isEmpty() ? emptyTable(headers, widths) : new ExportBlock.Table(headers, rows, widths));
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

    /**
     * Ecart entre le budget et le financement identifie : c'est la question que pose un comite de
     * pilotage devant deux totaux differents, autant y repondre des les chiffres cles.
     */
    private String coverageSentence(PsdKeyFigures f) {
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
