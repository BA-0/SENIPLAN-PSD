package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.NarrativeBlockKey;
import com.senico.diagnostic.domain.SectionDef;
import com.senico.diagnostic.domain.SectionResponse;
import com.senico.diagnostic.domain.SectionStatus;
import com.senico.diagnostic.domain.SectionType;
import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.repository.SectionResponseRepository;
import com.senico.diagnostic.service.DerivedFieldsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Recette du contenu de la note de synthese, sur des saisies fabriquees ici : contrairement a
 * {@link SynthesisNoteIT}, qui lit la base de developpement et ne verifie donc que ce qu'elle
 * contient a l'instant t, ce test garantit que chaque partie sait rendre ses donnees — y
 * compris quand la base de developpement n'a encore rien d'approuve par le DG.
 *
 * <p>Verifie aussi la regle qui fait foi : une section validee mais pas approuvee par le DG
 * n'entre pas dans la note ; et celle du cadre strategique : ce que la Direction Generale a
 * arrete remplace les propositions des directions.</p>
 */
class PsdBriefBuilderTest {

    private static final Map<String, SectionType> TYPES = Map.ofEntries(
            Map.entry("S01", SectionType.STAKEHOLDERS),
            Map.entry("S01B", SectionType.PERFORMANCE_REVIEW_2026),
            Map.entry("S02", SectionType.RESOURCES_MATRIX),
            Map.entry("S04", SectionType.SWOT),
            Map.entry("S05", SectionType.TOWS_MATRIX),
            Map.entry("S06", SectionType.CAUSAL_ANALYSIS),
            Map.entry("S07", SectionType.INVENTORY),
            Map.entry("S09", SectionType.LOGICAL_FRAMEWORK),
            Map.entry("S03B", SectionType.RESOURCES_SYNTHESIS),
            Map.entry("S09B", SectionType.LOGFRAME_SYNTHESIS),
            Map.entry("S10", SectionType.ACTION_PLAN),
            Map.entry("S06B", SectionType.CONSTRAINTS_SYNTHESIS),
            Map.entry("S07B", SectionType.STRATEGIC_FRAMEWORK),
            Map.entry("S08", SectionType.STRATEGIC_AXES),
            Map.entry("S11", SectionType.BUDGET),
            Map.entry("S12", SectionType.PERFORMANCE_FRAMEWORK),
            Map.entry("S13", SectionType.INDICATOR_SHEET),
            Map.entry("S14", SectionType.RISK_MATRIX),
            Map.entry("S14B", SectionType.STAFF_EVOLUTION),
            Map.entry("S15", SectionType.FINANCING_PLAN),
            Map.entry("S17", SectionType.STRATEGIC_SUMMARY));

    private final Map<String, SectionDef> sectionsByCode = new LinkedHashMap<>();
    private final Map<String, SectionResponse> responsesByKey = new LinkedHashMap<>();
    private final Map<String, GroupSectionStatus> statusesByKey = new LinkedHashMap<>();
    private final Map<NarrativeBlockKey, String> narratives = new EnumMap<>(NarrativeBlockKey.class);

    private final PsdBriefBuilder builder = builder();

    private static PsdBriefBuilder builder() {
        SectionResponseRepository repository = mock(SectionResponseRepository.class);
        when(repository.findByGroupIdAndSectionId(anyLong(), anyInt())).thenReturn(Optional.empty());
        ObjectMapper mapper = new ObjectMapper();
        return new PsdBriefBuilder(new ExportContentReader(mapper, new DerivedFieldsService(repository, mapper)));
    }

    private WorkGroup group(long id, String name) {
        return group(id, name, null);
    }

    private WorkGroup group(long id, String name, String color) {
        WorkGroup group = new WorkGroup();
        group.setId(id);
        group.setName(name);
        group.setColor(color);
        return group;
    }

    private List<ExportBlock> build(WorkGroup... groups) {
        return builder.build(List.of(groups), sectionsByCode, responsesByKey, statusesByKey, narratives);
    }

    private List<ExportBlock> build(LocalDate today, WorkGroup... groups) {
        return builder.build(List.of(groups), sectionsByCode, responsesByKey, statusesByKey, narratives, today);
    }

    /** Le tableau de la note coiffe du titre donne. */
    private ExportBlock.Table tableau(List<ExportBlock> blocs, String titre) {
        return blocs.stream()
                .filter(ExportBlock.Table.class::isInstance).map(ExportBlock.Table.class::cast)
                .filter(table -> table.bands().stream().anyMatch(band -> band.label().equals(titre)))
                .findFirst().orElseThrow();
    }

    /** Vrai si une cellule de la ligne porte ce texte, en propre ou dans ses attributions. */
    private static boolean contientTexte(ExportBlock.TableRow row, String texte) {
        return row.cells().stream().anyMatch(cell -> texte.equals(cell.text())
                || (cell.attributions() != null && cell.attributions().stream().anyMatch(a -> texte.equals(a.text()))));
    }

    /** Toutes les attributions de la note, cadrans et listes confondus. */
    private List<ExportBlock.Attribution> attributions(List<ExportBlock> blocs) {
        List<ExportBlock.Attribution> items = new ArrayList<>();
        for (ExportBlock bloc : blocs) {
            if (bloc instanceof ExportBlock.AttributedList list) {
                items.addAll(list.items());
            } else if (bloc instanceof ExportBlock.AttributedQuadrant quadrant) {
                quadrant.cells().forEach(cell -> items.addAll(cell.items()));
            }
        }
        return items;
    }

    /** Saisie d'une direction sur une section, approuvee par le DG (donc reprise dans la note). */
    private void saisie(WorkGroup group, String code, String json) {
        saisie(group, code, json, true);
    }

    private void saisie(WorkGroup group, String code, String json, boolean dgApproved) {
        SectionDef section = sectionsByCode.computeIfAbsent(code, c -> SectionDef.builder()
                .id(sectionsByCode.size() + 1)
                .code(c)
                .title(c)
                .order(sectionsByCode.size() + 1)
                .type(TYPES.get(c))
                .build());
        String key = group.getId() + ":" + section.getId();

        SectionResponse response = new SectionResponse();
        response.setContentJson(json);
        responsesByKey.put(key, response);

        statusesByKey.put(key, GroupSectionStatus.builder()
                .group(group)
                .section(section)
                .status(SectionStatus.VALIDATED)
                .dgApprovedAt(dgApproved ? LocalDateTime.now() : null)
                .build());
    }

    private String texte(List<ExportBlock> blocks) {
        List<String> parts = new ArrayList<>();
        for (ExportBlock block : blocks) {
            switch (block) {
                case ExportBlock.Heading h -> parts.add(h.text());
                case ExportBlock.Paragraph p -> parts.add(p.text());
                case ExportBlock.BulletList b -> {
                    parts.add(String.valueOf(b.title()));
                    parts.addAll(b.items());
                }
                case ExportBlock.KeyValueList kv -> kv.pairs()
                        .forEach(pair -> parts.add(pair.label() + " : " + pair.value()));
                case ExportBlock.Quadrant q -> q.cells().forEach(cell -> {
                    parts.add(cell.title());
                    parts.addAll(cell.items());
                });
                case ExportBlock.Table t -> {
                    parts.addAll(t.columnHeaders());
                    t.rows().forEach(row -> row.cells().forEach(cell -> {
                        parts.add(cell.text());
                        cell.attributions().forEach(item -> parts.add(item.text()));
                    }));
                }
                case ExportBlock.Callout c -> parts.add(c.text());
                case ExportBlock.AttributedList a -> {
                    parts.add(String.valueOf(a.title()));
                    a.items().forEach(item -> parts.add(item.text()));
                }
                case ExportBlock.AttributedQuadrant q -> q.cells().forEach(cell -> {
                    parts.add(cell.title());
                    parts.add(String.valueOf(cell.caption()));
                    cell.items().forEach(item -> parts.add(item.text()));
                });
                case ExportBlock.ColorLegend l -> l.entries().forEach(entry -> parts.add(entry.text()));
                case ExportBlock.MetricGrid m -> m.metrics()
                        .forEach(metric -> parts.add(metric.label() + " : " + metric.value()));
                case ExportBlock.Chart c -> {
                    parts.add(c.title());
                    c.series().forEach(series -> parts.add(series.name()));
                }
            }
        }
        return String.join("\n", parts);
    }

    private long parties(List<ExportBlock> blocks) {
        return blocks.stream().filter(b -> b instanceof ExportBlock.Heading h && h.level() == 1).count();
    }

    @Test
    @DisplayName("Chaque partie du plan rend les saisies des directions")
    void rendChaquePartie() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        WorkGroup technique = group(2, "Direction technique");

        saisie(commerciale, "S04", """
                {"strengths":["Réseau national"],"weaknesses":["SI vieillissant"],
                 "opportunities":["Croissance du colis"],"threats":["Concurrence privée"]}""");
        saisie(commerciale, "S06B", """
                {"rows":[{"domain":"Courrier","constraints":["Recul des volumes"],
                          "challenges":["Compenser par le colis"]}]}""");
        saisie(commerciale, "S07B", """
                {"vision":"Devenir l'opérateur logistique de référence",
                 "mission":["Acheminer le courrier et les colis"],"values":["Fiabilité"]}""");
        saisie(commerciale, "S08", """
                {"axes":[{"axisCode":"AXE1","title":"Croissance commerciale",
                          "specificObjectives":["Développer le chiffre d'affaires"]}]}""");
        saisie(commerciale, "S11", """
                {"axes":[{"axisCode":"AXE1","axisTitle":"Croissance commerciale",
                          "effects":[{"effectLabel":"Gagner des parts de marché",
                                      "rows":[{"activities":"Lancer l'offre grands comptes","responsible":"DC",
                                               "years":{"2027":600,"2028":400}}]}]}]}""");
        saisie(commerciale, "S13", """
                {"rows":[{"indicatorTitle":"Chiffre d'affaires","periodicity":"Trimestrielle",
                          "responsibleStructure":"Direction commerciale"}]}""");
        saisie(commerciale, "S14", """
                {"rows":[{"category":"Commercial","riskDetails":"Perte de parts de marché","levelN":3,
                          "quotationQ":3,"present":true,"mitigationActions":"Différenciation"}]}""");
        saisie(commerciale, "S14B", """
                {"rows":[{"years":{"2027":{"male":8,"female":4},"2031":{"male":10,"female":6}}}]}""");
        saisie(commerciale, "S15", """
                {"rows":[{"source":"RESSOURCES_PROPRES","amount":400}]}""");

        // La direction technique cite la meme force : elle ne doit apparaitre qu'une fois.
        saisie(technique, "S04", """
                {"strengths":["Réseau national"],"weaknesses":["Parc technique vieillissant"],
                 "opportunities":[],"threats":[]}""");

        List<ExportBlock> blocs = build(commerciale, technique);
        String note = texte(blocs);

        assertThat(parties(blocs)).as("chaque partie du plan ouvre sa page").isEqualTo(PsdBriefBuilder.partTitles().size());
        assertThat(note).as("les parties du plan").contains(PsdBriefBuilder.partTitles());
        assertThat(note).contains(
                "Réseau national",                          // V. diagnostic
                "Parc technique vieillissant",
                "Devenir l'opérateur logistique de référence", "Fiabilité",  // IX. propositions des directions
                "Croissance commerciale",                   // IX. et X.
                "Lancer l'offre grands comptes",            // XII.1 synthese du cadre strategique
                "Perte de parts de marché",                 // V.5 risques de criticite elevee
                "1 000 FCFA",                               // budget global (600 + 400)
                "Trimestrielle",                            // XI. pilotage
                "Analyse :");                               // lecture sous les tableaux

        assertThat(note.split("Réseau national", -1).length - 1)
                .as("une force citée par deux directions ne doit apparaître qu'une fois")
                .isEqualTo(1);

        assertThat(note)
                .as("l'écart entre budget et financement identifié est la question du comité")
                .contains("600 FCFA reste à mobiliser");

        assertThat(blocs)
                .as("le budget s'accompagne de son graphique")
                .anyMatch(bloc -> bloc instanceof ExportBlock.Chart);
    }

    @Test
    @DisplayName("Les tableaux du modèle client : ressources, synthèse du cadre stratégique avec budget et objectif, impact, collecte")
    void rendLesTableauxDuModeleClient() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        saisie(commerciale, "S02", """
                {"rows":[{"resourceKey":"RECHERCHE_DEVELOPPEMENT","strengths":"Cellule innovation",
                          "weaknesses":"","challenges":"Financer la R&D"}]}""");
        saisie(commerciale, "S08", """
                {"axes":[{"axisCode":"AXE1","title":"Croissance commerciale",
                          "specificObjectives":["Développer le chiffre d'affaires"]}]}""");
        saisie(commerciale, "S11", """
                {"axes":[{"axisCode":"AXE1","effects":[{"effectLabel":"Développer le chiffre d'affaires",
                          "rows":[{"extrant":"Offre grands comptes","activities":"Lancer l'offre grands comptes",
                                   "years":{"2027":600000000}}]}]}]}""");
        saisie(commerciale, "S12", """
                {"axes":[{"axisCode":"AXE1","groups":[{"level":"EFFET","rows":[
                          {"resultOrExtrant":"Développer le chiffre d'affaires","indicator":"Chiffre d'affaires annuel",
                           "years":{"2031":"25 Mds FCFA"}}]}]}]}""");
        saisie(commerciale, "S17", """
                {"axes":[{"axisCode":"AXE1","orientations":[{"label":"Conquérir les grands comptes",
                          "actions":[{"label":"Créer une cellule grands comptes",
                                      "constraintsOrOpportunities":"Concurrence des majors"}]}]}]}""");
        saisie(commerciale, "S13", """
                {"rows":[{"indicatorTitle":"Chiffre d'affaires","collectionSource":"Extraction du logiciel de facturation"}]}""");
        saisie(commerciale, "S14", """
                {"rows":[{"category":"Commercial","riskDetails":"Perte de parts de marché","impactAreas":"Transport de fret",
                          "levelN":3,"quotationQ":3,"mitigationActions":"Différenciation"}]}""");

        String note = texte(build(commerciale));

        assertThat(note).as("matrice des ressources, lignes ajoutées par le client comprises")
                .contains("Recherche et développement", "Cellule innovation", "Financer la R&D");
        assertThat(note).as("synthèse du cadre stratégique : les OS du budget, puis celles du seul tableau de synthèse")
                .contains("OS1 : Développer le chiffre d'affaires", "Action 1.1 : Lancer l'offre grands comptes",
                        "OS2 : Conquérir les grands comptes", "Action 2.1 : Créer une cellule grands comptes",
                        "Concurrence des majors");
        assertThat(note).as("synthèse du cadre stratégique : budget de chaque action et objectif de l'axe, sans récapitulatif")
                .contains("Budget (M FCFA)", "Objectif", "Développer le chiffre d'affaires")
                .doesNotContain("Récapitulatif des axes", "Cible 2031");
        assertThat(note).as("fiche des indicateurs : moyens de collecte")
                .contains("Sources et moyens de collecte", "Extraction du logiciel de facturation");
        assertThat(note).as("cartographie des risques : impact")
                .contains("Impact sur les activités", "Transport de fret");
    }

    @Test
    @DisplayName("Synthèse du cadre stratégique au modèle client : OS fusionnée sur ses actions, contrainte commune écrite une fois")
    void rendLaSyntheseDuCadreStrategiqueAuModeleClient() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        saisie(commerciale, "S08", """
                {"axes":[{"axisCode":"AXE1","title":"Croissance commerciale"}]}""");
        saisie(commerciale, "S11", """
                {"axes":[{"axisCode":"AXE1","effects":[
                   {"effectLabel":"Gagner des parts de marché","rows":[
                      {"extrant":"Offre grands comptes","activities":"Lancer l'offre grands comptes","years":{"2027":100}},
                      {"extrant":"Agences ouvertes","activities":"Ouvrir deux agences","years":{"2027":50}}]},
                   {"effectLabel":"Fidéliser la clientèle","rows":[
                      {"extrant":"Programme de fidélité","activities":"Lancer un programme de fidélité","years":{"2027":30}}]}]}]}""");
        saisie(commerciale, "S12", """
                {"axes":[{"axisCode":"AXE1","groups":[{"level":"EXTRANTS","rows":[
                   {"resultOrExtrant":"Réalisations de l'axe","indicator":"Taux de réalisation des activités programmées",
                    "years":{"2031":"100%"}}]}]}]}""");
        saisie(commerciale, "S17", """
                {"axes":[{"axisCode":"AXE1","orientations":[
                   {"label":"Gagner des parts de marché","actions":[{"label":"Lancer l'offre grands comptes",
                     "constraintsOrOpportunities":"Concurrence des opérateurs privés"}]},
                   {"label":"Fidéliser la clientèle","actions":[{"label":"Lancer un programme de fidélité",
                     "constraintsOrOpportunities":"Concurrence des opérateurs privés"}]}]}]}""");
        narratives.put(NarrativeBlockKey.VISION, "Être l'opérateur de référence");

        List<ExportBlock> blocs = build(commerciale);
        ExportBlock.Table synthese = tableau(blocs, "SYNTHÈSE DU CADRE STRATÉGIQUE");
        List<ExportBlock.TableRow> lignes = synthese.rows();

        assertThat(synthese.showsHeaders()).as("les intitulés se répètent sous chaque axe, pas en tête du tableau").isFalse();
        assertThat(lignes.get(0).cells().get(0).text()).as("la vision ouvre le tableau").contains("Vision", "Être l'opérateur de référence");
        assertThat(lignes.get(1).band()).as("bandeau de l'axe").isTrue();
        assertThat(lignes.get(2).cells().get(0).text()).isEqualTo("Orientation stratégique (OS)");

        ExportBlock.TableRow premiere = lignes.get(3);
        assertThat(premiere.cells().get(0).attributions().get(0).text()).isEqualTo("OS1 : Gagner des parts de marché");
        assertThat(premiere.cells().get(0).rowSpan()).as("l'OS coiffe ses deux actions").isEqualTo(2);
        assertThat(lignes.get(4).cells().get(0).isCovered()).isTrue();
        assertThat(lignes.get(4).cells().get(1).attributions().get(0).text()).isEqualTo("Action 1.2 : Ouvrir deux agences");
        assertThat(premiere.cells().get(2).align()).as("le budget de l'action suit son intitulé").isEqualTo(ExportBlock.Align.RIGHT);
        assertThat(premiere.cells().get(2).text()).isNotEqualTo("—");
        assertThat(premiere.cells().get(3).rowSpan()).as("l'objectif de l'axe coiffe toutes ses OS").isEqualTo(3);
        assertThat(premiere.cells().get(4).attributions().get(0).text()).isEqualTo("Concurrence des opérateurs privés");
        assertThat(premiere.cells().get(4).rowSpan())
                .as("la contrainte commune aux trois actions n'est écrite qu'une fois").isEqualTo(3);
        assertThat(lignes.get(5).cells().get(0).attributions().get(0).text()).isEqualTo("OS2 : Fidéliser la clientèle");
        assertThat(lignes.get(5).cells().get(3).isCovered()).isTrue();
        assertThat(lignes.get(5).cells().get(4).isCovered()).isTrue();

        String note = texte(blocs);
        assertThat(note).contains("Orientations stratégiques (OS) : 2", "Budget (M FCFA)", "Objectif")
                .doesNotContain("Récapitulatif des axes");
    }

    @Test
    @DisplayName("Les tableaux du canevas par axe : cadre logique, plan d'actions, budget détaillé, cadre de mesure de rendement")
    void rendLesTableauxDuCanevasParAxe() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        saisie(commerciale, "S08", """
                {"axes":[{"axisCode":"AXE1","title":"Croissance commerciale"}]}""");
        saisie(commerciale, "S09", """
                {"axes":[{"axisCode":"AXE1","objective":"Accroître le chiffre d'affaires","rows":[
                   {"level":"IMPACT","interventionLogic":"Position commerciale consolidée","iov":"Part de marché",
                    "verificationMeans":"Rapport annuel","assumptions":"Contexte économique stable"}]}]}""");
        saisie(commerciale, "S10", """
                {"axes":[{"axisCode":"AXE1","effects":[{"effectLabel":"Gagner des parts de marché","rows":[
                   {"extrant":"Offre grands comptes","activities":"Lancer l'offre grands comptes",
                    "responsible":"DC / Grands comptes","years":{"2027":true,"2028":false}}]}]}]}""");
        saisie(commerciale, "S11", """
                {"axes":[{"axisCode":"AXE1","effects":[{"effectLabel":"Gagner des parts de marché","rows":[
                   {"extrant":"Offre grands comptes","activities":"Lancer l'offre grands comptes",
                    "responsible":"DC / Grands comptes","years":{"2027":15000000,"2028":25000000}}]}]}]}""");
        saisie(commerciale, "S12", """
                {"axes":[{"axisCode":"AXE1","groups":[{"level":"IMPACT","rows":[
                   {"resultOrExtrant":"Position commerciale consolidée","indicator":"Part de marché colis",
                    "ref2026":"18 %","years":{"2031":"25 %"},"responsible":"DC"},
                   {"resultOrExtrant":"Position commerciale consolidée","indicator":"Part de marché colis",
                    "ref2026":"18 %","years":{"2031":"25 %"},"responsible":"DC"},
                   {"resultOrExtrant":"Clientèle fidélisée","indicator":"Taux de fidélisation",
                    "ref2026":"18 %","years":{"2031":"25 %"},"responsible":"DC"}]}]}]}""");
        saisie(commerciale, "S05", """
                {"strengthsForOpportunities":"Mobiliser le réseau pour capter le e-commerce"}""");
        saisie(commerciale, "S06", """
                {"rows":[{"source":"CAUSES_PROFONDES","items":["Retard de digitalisation"]}]}""");
        saisie(commerciale, "S03B", """
                {"majorStrengths":["Marque connue de tous"],"synthesisNote":"Une direction solide mais peu outillée"}""");
        saisie(commerciale, "S09B", """
                {"synthesisNote":"Un impact unique : une position commerciale consolidée"}""");
        saisie(commerciale, "S14", """
                {"rows":[{"category":"Commercial","riskDetails":"Perte de parts de marché","levelN":3,"quotationQ":3,"present":true},
                         {"category":"Juridique","riskDetails":"Contentieux fournisseur","levelN":1,"quotationQ":1,"present":false}]}""");

        List<ExportBlock> blocs = build(commerciale);
        String note = texte(blocs);

        assertThat(note).as("synthèses des ressources et du cadre logique")
                .contains("Marque connue de tous", "Un impact unique : une position commerciale consolidée")
                .as("la note rédigée de chaque direction sur ses ressources reste dans son plan sectoriel")
                .doesNotContain("Une direction solide mais peu outillée");
        assertThat(note).as("la matrice complète garde le risque jugé absent, et la méthodologie du canevas")
                .contains("Présence (Oui/Non)", "Contentieux fournisseur", "Méthodologie d'évaluation", "6 à 9 : criticité élevée");

        assertThat(note).as("cadre logique par axe")
                .contains("X.1 Cadre logique", "Accroître le chiffre d'affaires", "Impact (Finalité)",
                        "Position commerciale consolidée", "Part de marché", "Rapport annuel", "Contexte économique stable");
        assertThat(note).as("cadre de mesure de rendement par axe")
                .contains("XI.2 Cadre de mesure de rendement", "IMPACT (Finalité) — horizon 2031", "Part de marché colis",
                        "18 %", "25 %");
        assertThat(note).as("mise en relation du diagnostic et analyse causale")
                .contains("Mobiliser le réseau pour capter le e-commerce", "Causes profondes", "Retard de digitalisation");

        ExportBlock.Table rendement = blocs.stream()
                .filter(ExportBlock.Table.class::isInstance).map(ExportBlock.Table.class::cast)
                .filter(table -> table.columnHeaders().contains("Réf. 2026"))
                .findFirst().orElseThrow();
        assertThat(rendement.rows().stream().filter(row -> contientTexte(row, "Part de marché colis")))
                .as("une ligne saisie deux fois a l'identique n'est ecrite qu'une fois")
                .hasSize(1);
        assertThat(rendement.rows().stream().filter(row -> contientTexte(row, "Taux de fidélisation")))
                .as("une ligne distincte aux memes cibles et au meme responsable reste ecrite")
                .hasSize(1);

        ExportBlock.Table plan = blocs.stream()
                .filter(ExportBlock.Table.class::isInstance).map(ExportBlock.Table.class::cast)
                .filter(table -> table.columnHeaders().contains("Responsables") && table.columnHeaders().get(0).equals("Extrants"))
                .findFirst().orElseThrow();
        assertThat(plan.rows().get(0).cells().get(0).text()).isEqualTo("EFFET 1 — OS1 : Gagner des parts de marché");
        List<ExportBlock.Cell> action = plan.rows().get(1).cells();
        assertThat(action.get(2).text()).as("programmée en 2027").isEqualTo("✓");
        assertThat(action.get(3).text()).as("pas en 2028, malgré le budget : le plan d'actions fait foi").isEmpty();
        assertThat(action.get(action.size() - 1).text()).isEqualTo("DC / Grands comptes");

        ExportBlock.Table budget = blocs.stream()
                .filter(ExportBlock.Table.class::isInstance).map(ExportBlock.Table.class::cast)
                .filter(table -> table.columnHeaders().contains("Totaux"))
                .findFirst().orElseThrow();
        List<ExportBlock.Cell> costs = budget.rows().get(1).cells();
        assertThat(costs.get(2).text()).isEqualTo("15");
        assertThat(costs.get(3).text()).isEqualTo("25");
        assertThat(costs.get(7).text()).as("total de l'activité, en millions").isEqualTo("40");
        List<ExportBlock.Cell> axisTotal = budget.rows().get(budget.rows().size() - 1).cells();
        assertThat(axisTotal.get(0).text()).isEqualTo("Total de l'axe");
        assertThat(axisTotal.get(7).text()).isEqualTo("40");
    }

    @Test
    @DisplayName("Le Plan Stratégique complet reprend les tableaux par axe de la note")
    void lePlanCompletReprendLesTableauxParAxe() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        saisie(commerciale, "S08", """
                {"axes":[{"axisCode":"AXE1","title":"Croissance commerciale","objective":"Accroître le chiffre d'affaires",
                  "specificObjectives":["Ouvrir 3 nouveaux points de vente"]}]}""");
        saisie(commerciale, "S09", """
                {"axes":[{"axisCode":"AXE1","rows":[{"level":"IMPACT","interventionLogic":"Position commerciale consolidée"}]}]}""");
        saisie(commerciale, "S09B", """
                {"synthesisNote":"Un impact unique : une position commerciale consolidée"}""");
        saisie(commerciale, "S11", """
                {"axes":[{"axisCode":"AXE1","effects":[{"effectLabel":"Gagner des parts de marché","rows":[
                   {"extrant":"Offre grands comptes","activities":"Lancer l'offre grands comptes",
                    "responsible":"DC / Grands comptes","years":{"2027":15000000}}]}]}]}""");
        saisie(commerciale, "S06", """
                {"rows":[{"source":"CAUSES_PROFONDES","items":["Retard de digitalisation"]}]}""");
        saisie(commerciale, "S07", """
                {"synthesisNote":"Une position forte, freinée par l'outillage"}""");
        List<WorkGroup> groups = List.of(commerciale);

        assertThat(texte(builder.planSection("S06", groups, sectionsByCode, responsesByKey, statusesByKey, narratives).orElseThrow()))
                .as("l'analyse causale du canevas, toutes directions confondues")
                .contains("Causes profondes", "Retard de digitalisation");
        assertThat(texte(builder.planSection("S07", groups, sectionsByCode, responsesByKey, statusesByKey, narratives).orElseThrow()))
                .as("l'inventaire du diagnostic, dans la couleur de chaque direction")
                .contains("Direction commerciale", "Une position forte, freinée par l'outillage");

        assertThat(builder.planSection("S01", groups, sectionsByCode, responsesByKey, statusesByKey, narratives))
                .as("les rubriques du diagnostic restent rendues direction par direction")
                .isEmpty();

        assertThat(texte(builder.planSection("S08", groups, sectionsByCode, responsesByKey, statusesByKey, narratives).orElseThrow()))
                .contains("Croissance commerciale", "Accroître le chiffre d'affaires", "Ouvrir 3 nouveaux points de vente");

        assertThat(texte(builder.planSection("S09", groups, sectionsByCode, responsesByKey, statusesByKey, narratives).orElseThrow()))
                .contains("Impact (Finalité)", "Position commerciale consolidée")
                .as("la synthèse du cadre logique a sa propre rubrique dans le Plan")
                .doesNotContain("Un impact unique : une position commerciale consolidée");

        assertThat(builder.planSection("S11", groups, sectionsByCode, responsesByKey, statusesByKey, narratives).orElseThrow())
                .anySatisfy(bloc -> assertThat(bloc).isInstanceOfSatisfying(ExportBlock.Heading.class,
                        heading -> assertThat(heading.text()).isEqualTo("Budget détaillé — Croissance commerciale")));
        ExportBlock.Table grandTotal = (ExportBlock.Table) builder.planSection("S11", groups, sectionsByCode,
                responsesByKey, statusesByKey, narratives).orElseThrow().getLast();
        List<ExportBlock.Cell> totalGeneral = grandTotal.rows().getLast().cells();
        assertThat(totalGeneral.get(0).text()).as("le budget détaillé se clôt sur le total général du canevas")
                .isEqualTo("TOTAL GÉNÉRAL");
        assertThat(totalGeneral.getLast().text()).isEqualTo("15");

        assertThat(texte(builder.planSection("S17", groups, sectionsByCode, responsesByKey, statusesByKey, narratives).orElseThrow()))
                .contains("Tableau de synthèse du cadre stratégique", "OS1 : Gagner des parts de marché")
                .as("le récapitulatif des axes a été retiré à la revue client")
                .doesNotContain("Récapitulatif des axes");
    }

    @Test
    @DisplayName("Revue client du 15/09/2026 : bilan avant diagnostic, tableaux par axe en annexe, parties retirées")
    void suitLeSommaireDeLaRevueClient() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        saisie(commerciale, "S01B", """
                {"rows":[{"domain":"Ventes","indicator":"Chiffre d'affaires","target2026":4200,"achieved2026":3980}]}""");
        saisie(commerciale, "S08", """
                {"axes":[{"axisCode":"AXE1","title":"Croissance commerciale"}]}""");
        saisie(commerciale, "S09", """
                {"axes":[{"axisCode":"AXE1","rows":[{"level":"IMPACT","interventionLogic":"Position commerciale consolidée"}]}]}""");
        saisie(commerciale, "S09B", """
                {"synthesisNote":"Un impact unique : une position commerciale consolidée"}""");
        saisie(commerciale, "S11", """
                {"axes":[{"axisCode":"AXE1","effects":[{"effectLabel":"Gagner des parts de marché","rows":[
                   {"extrant":"Offre grands comptes","activities":"Lancer l'offre grands comptes","years":{"2027":15000000}}]}]}]}""");

        List<ExportBlock> blocs = build(commerciale);
        List<String> titres = blocs.stream().filter(ExportBlock.Heading.class::isInstance).map(ExportBlock.Heading.class::cast)
                .filter(heading -> heading.level() <= 2).map(ExportBlock.Heading::text).toList();

        assertThat(titres).containsSubsequence("III.1 Historique", "III.2 Missions", "III.3 Gouvernance", "III.4 Organisation",
                PsdBriefBuilder.BILAN, "V.1 Performances des années passées", "V.2 Performances de l'année 2026 et tendances",
                PsdBriefBuilder.DIAGNOSTIC, "X.3 Budget du plan", "XI.2 Cadre de mesure de rendement",
                "XII.1 Tableau de synthèse du cadre stratégique", PsdBriefBuilder.ANNEXES,
                "Tableau 1 : Matrice d'analyse des risques", "Tableau 2 : Cadre logique", "Tableau 3 : Planification",
                "Tableau 4 : Budget du plan", "Tableau 5 : Cadre de mesure de rendement");
        assertThat(titres).as("revue du 22/09/2026 : plus de XI.3 ni de tableau 6, la fiche des indicateurs ouvre le tableau 5")
                .noneMatch(titre -> titre.startsWith("XI.3") || titre.startsWith("Tableau 6"));
        assertThat(titres).as("inventaire du diagnostic et récapitulatif des axes retirés")
                .noneMatch(titre -> titre.contains("Inventaire") || titre.contains("Récapitulatif"));
        assertThat(titres).as("revue de l'auditeur : la synthèse des contraintes du canevas ouvre la partie VII")
                .containsSubsequence(PsdBriefBuilder.ENJEUX, "VII.1 Synthèse des contraintes, enjeux et défis prioritaires",
                        "VII.2 Enjeux", "VII.3 Défis à relever");

        int annexes = titres.isEmpty() ? -1 : blocs.indexOf(blocs.stream()
                .filter(bloc -> bloc instanceof ExportBlock.Heading heading && heading.text().equals(PsdBriefBuilder.ANNEXES))
                .findFirst().orElseThrow());
        List<ExportBlock> corps = blocs.subList(0, annexes);
        assertThat(corps).as("les tableaux par axe ne sont plus dans le corps du document")
                .noneMatch(bloc -> bloc instanceof ExportBlock.Table table
                        && (table.columnHeaders().contains("Logique d'intervention")
                        || table.columnHeaders().contains("Activités pour atteindre les résultats")));
        assertThat(texte(corps)).as("le texte de la synthèse du cadre logique reste dans le corps, avec le renvoi à l'annexe")
                .contains("Un impact unique : une position commerciale consolidée", "(Tableau 2 : Cadre logique)");
        assertThat(blocs.subList(annexes, blocs.size())).as("le cadre logique par axe est en annexe")
                .anyMatch(bloc -> bloc instanceof ExportBlock.Table table && table.columnHeaders().contains("Logique d'intervention"));

        List<ExportBlock> annexe = blocs.subList(annexes, blocs.size());
        int tableau5 = annexe.indexOf(annexe.stream().filter(bloc -> bloc instanceof ExportBlock.Heading heading
                && heading.text().equals(PsdBriefBuilder.ANNEXE_RENDEMENT)).findFirst().orElseThrow());
        int fiche = annexe.indexOf(annexe.stream().filter(bloc -> bloc instanceof ExportBlock.Heading heading
                && heading.text().equals(PsdBriefBuilder.FICHE_INDICATEURS)).findFirst().orElseThrow());
        int ficheTable = annexe.indexOf(annexe.stream().filter(bloc -> bloc instanceof ExportBlock.Table table
                && table.columnHeaders().contains("Sources et moyens de collecte")).findFirst().orElseThrow());
        int rendement = annexe.indexOf(annexe.stream().filter(bloc -> bloc instanceof ExportBlock.Table table
                && table.columnHeaders().contains("Réf. 2026")).findFirst().orElseThrow());
        assertThat(tableau5).as("la fiche des indicateurs ouvre le tableau 5, devant les tableaux par axe du rendement")
                .isLessThan(fiche);
        assertThat(fiche).isLessThan(ficheTable);
        assertThat(ficheTable).isLessThan(rendement);
    }

    @Test
    @DisplayName("Synthèse des contraintes : une ligne par domaine, partagée entre directions, sans domaine vide")
    void fusionneLaSyntheseDesContraintesParDomaine() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        WorkGroup technique = group(2, "Direction technique");
        saisie(commerciale, "S06B", """
                {"rows":[{"domain":"Distribution","constraints":["Délais de livraison"],"challenges":["Fiabiliser les livraisons"]},
                         {"domain":"Agence maritime","constraints":[],"challenges":[]}]}""");
        saisie(technique, "S06B", """
                {"rows":[{"domain":"distribution ","constraints":["Parc vieillissant"],"challenges":[]}]}""");

        ExportBlock.Table synthese = build(commerciale, technique).stream()
                .filter(ExportBlock.Table.class::isInstance).map(ExportBlock.Table.class::cast)
                .filter(table -> table.columnHeaders().contains("Contraintes prioritaires"))
                .findFirst().orElseThrow();

        assertThat(synthese.rows()).as("« Distribution » des deux directions sur une ligne ; le domaine vide écarté").hasSize(1);
        assertThat(contientTexte(synthese.rows().get(0), "Délais de livraison")).isTrue();
        assertThat(contientTexte(synthese.rows().get(0), "Parc vieillissant")).isTrue();
        assertThat(contientTexte(synthese.rows().get(0), "Fiabiliser les livraisons")).isTrue();
    }

    @Test
    @DisplayName("Revue de l'auditeur : parties prenantes en tableau dans le corps, sans matrice intérêt / pouvoir")
    void presenteLesPartiesPrenantesEnTableauSansMatrice() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        saisie(commerciale, "S01", """
                {"rows":[{"actor":"Ministère de tutelle","roles":"Tutelle","importance":"FORT","influence":"FORT"}]}""");

        List<ExportBlock> blocs = build(commerciale);
        int annexes = blocs.indexOf(blocs.stream()
                .filter(bloc -> bloc instanceof ExportBlock.Heading heading && heading.text().equals(PsdBriefBuilder.ANNEXES))
                .findFirst().orElseThrow());

        assertThat(blocs).as("la matrice intérêt / pouvoir, absente du canevas, est retirée")
                .noneMatch(bloc -> bloc instanceof ExportBlock.AttributedQuadrant quadrant
                        && quadrant.cells().stream().anyMatch(cell -> cell.title().startsWith("INTÉRÊT")));
        assertThat(blocs.subList(0, annexes)).as("le tableau du canevas est dans la partie IV")
                .anyMatch(bloc -> bloc instanceof ExportBlock.Table table && table.columnHeaders().contains("Acteur (PP)"));
        assertThat(blocs.subList(annexes, blocs.size())).as("et n'est plus répété en annexe")
                .noneMatch(bloc -> bloc instanceof ExportBlock.Table table && table.columnHeaders().contains("Acteur (PP)"));
    }

    @Test
    @DisplayName("Sans objectif d'axe saisi, le tableau des axes d'intervention n'aligne pas une colonne de tirets")
    void masqueLaColonneObjectifDeLAxeQuandPersonneNeLaRenseigne() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        saisie(commerciale, "S08", """
                {"axes":[{"axisCode":"AXE1","title":"Croissance commerciale","objective":"",
                  "specificObjectives":["Ouvrir 3 nouveaux points de vente"]}]}""");

        ExportBlock.Table axes = builder.planSection("S08", List.of(commerciale), sectionsByCode, responsesByKey,
                        statusesByKey, narratives).orElseThrow().stream()
                .filter(ExportBlock.Table.class::isInstance).map(ExportBlock.Table.class::cast)
                .findFirst().orElseThrow();
        assertThat(axes.columnHeaders()).containsExactly("Axe d'intervention de la direction", "Objectifs spécifiques");
        assertThat(axes.rows().get(0).cells()).hasSize(2);
    }

    @Test
    @DisplayName("Tant que 2026 n'est pas clos, ses résultats de décembre sont présentés comme des projections")
    void presenteLesResultats2026CommeDesProjectionsAvantLaCloture() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        saisie(commerciale, "S01B", """
                {"rows":[{"year":2026,"objective":"Ventes","indicator":"Chiffre d'affaires","expectedResult":"4 200",
                          "gap":"-220","trend":"DEFAVORABLE"}]}""");

        assertThat(texte(build(LocalDate.of(2026, 9, 22), commerciale)))
                .contains("sont des projections à date")
                .doesNotContain("se lisent comme des réalisations");
        assertThat(texte(build(LocalDate.of(2027, 3, 1), commerciale)))
                .contains("se lisent comme des réalisations")
                .doesNotContain("sont des projections à date");
    }

    @Test
    @DisplayName("Le bilan : les cinq exercices écoulés dans un seul tableau, 2026 dans le sien avec ses tendances")
    void regroupeLesExercicesEcoulesEtIsoleLExerciceEnCours() {
        WorkGroup technique = group(2, "Direction Technique");
        saisie(technique, "S01B", """
                {"rows":[{"year":2024,"objective":"Fiabiliser l'acheminement","indicator":"Délai moyen d'acheminement (jours)",
                          "expectedResult":"3","gap":"+0,5","cause":"Pannes","rootCause":"Parc vieillissant","action":"Renouvellement"},
                         {"domain":"Acheminement","indicator":"Délai moyen d'acheminement (jours)",
                          "target2026":3,"achieved2026":3.8,"comment":"Pannes"}]}""");

        List<ExportBlock> blocs = build(technique);
        ExportBlock.Table passees = tableauApres(blocs, "V.1 " + PerformanceReviewTables.PAST_TITLE);
        ExportBlock.Table courante = tableauApres(blocs, "V.2 " + PerformanceReviewTables.CURRENT_TITLE);

        assertThat(passees.columnHeaders()).as("exercices écoulés : le résultat est celui obtenu")
                .containsExactly("Objectif", "Indicateur", "Résultat obtenu", "Écart", "Cause sous-jacente", "Cause profonde", "Action entreprise");
        assertThat(courante.columnHeaders()).containsExactly("Objectif", "Indicateur", "Résultat attendu en décembre", "Écart",
                "Cause sous-jacente", "Cause profonde", "Action à entreprendre");
        assertThat(passees.rows().stream().filter(ExportBlock.TableRow::band).map(row -> row.cells().get(0).text()))
                .as("un bandeau par exercice écoulé, du plus ancien au plus récent")
                .containsExactly("Exercice 2021", "Exercice 2022", "Exercice 2023", "Exercice 2024", "Exercice 2025");
        assertThat(passees.rows()).anyMatch(row -> contientTexte(row, "Fiabiliser l'acheminement"))
                .noneMatch(row -> contientTexte(row, "Acheminement"));

        // La ligne saisie avec l'ancien tableau 2026 est convertie : cible -> résultat attendu, écart signé, tendance
        // déduite du sens de l'indicateur (un délai au-dessus de sa cible est défavorable).
        assertThat(courante.rows()).hasSize(1);
        List<ExportBlock.Cell> cellules = courante.rows().get(0).cells();
        assertThat(cellules).hasSize(7);
        assertThat(cellules.get(0).text()).isEqualTo("Acheminement");
        assertThat(cellules.get(2).text()).isEqualTo("3");
        assertThat(cellules.get(3).text()).isEqualTo("+0,8 (tendance défavorable)");
        assertThat(cellules.get(3).background()).isEqualTo(ExportBlock.Background.ORANGE);
        assertThat(cellules.get(4).text()).isEqualTo("Pannes");
        assertThat(texte(blocs)).contains("tendances 2026", "1 à tendance défavorable");
    }

    /** Le premier tableau qui suit l'intertitre donne. */
    private static ExportBlock.Table tableauApres(List<ExportBlock> blocs, String titre) {
        int debut = blocs.indexOf(blocs.stream().filter(bloc -> bloc instanceof ExportBlock.Heading heading
                && heading.text().equals(titre)).findFirst().orElseThrow());
        return blocs.subList(debut, blocs.size()).stream()
                .filter(ExportBlock.Table.class::isInstance).map(ExportBlock.Table.class::cast)
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Une partie prenante nommée dans la case des rôles n'est pas écrite deux fois en annexe")
    void neRepetePasLeNomDeLaPartiePrenanteDansSesRoles() {
        WorkGroup logistique = group(5, "Direction Logistique");
        saisie(logistique, "S01", """
                {"rows":[{"category":"AUTRE","roles":"Direction Commerciale : exprime les besoins de livraison",
                          "importance":"FORT","influence":"FORT"},
                         {"category":"FOURNISSEUR","roles":"Fournisseurs de véhicules",
                          "importance":"FORT","influence":"MOYEN"}]}""");

        ExportBlock.Table annexe = build(logistique).stream()
                .filter(ExportBlock.Table.class::isInstance).map(ExportBlock.Table.class::cast)
                .filter(table -> table.columnHeaders().contains("Acteur (PP)"))
                .findFirst().orElseThrow();

        assertThat(contientTexte(annexe.rows().get(0), "Direction Commerciale")).isTrue();
        assertThat(annexe.rows().get(0).cells().get(1).text()).isEqualTo("Exprime les besoins de livraison");
        assertThat(contientTexte(annexe.rows().get(1), "Fournisseurs de véhicules")).isTrue();
        assertThat(annexe.rows().get(1).cells().get(1).text()).as("le seul nom, sans rôle décrit").isEqualTo("—");
    }

    @Test
    @DisplayName("« Autre » précisé : la partie prenante, la ressource et la source saisies librement sont publiées")
    void publieLesValeursSaisiesApresAutre() {
        WorkGroup logistique = group(5, "Direction Logistique");
        saisie(logistique, "S01", """
                {"rows":[{"category":"ONG locale","scope":"EXTERNE","roles":"Appui aux communautés",
                          "importance":"MOYEN","influence":"FAIBLE"}]}""");
        saisie(logistique, "S02", """
                {"rows":[{"resourceKey":"Parc informatique","strengths":"Postes récents","weaknesses":"","challenges":""}]}""");
        saisie(logistique, "S15", """
                {"rows":[{"source":"Mécénat","amount":5000000,"modalities":"Convention","period":"2027","responsible":"DG"}],
                 "total":5000000}""");

        List<ExportBlock> blocs = build(logistique);
        ExportBlock.Table annexe = blocs.stream()
                .filter(ExportBlock.Table.class::isInstance).map(ExportBlock.Table.class::cast)
                .filter(table -> table.columnHeaders().contains("Acteur (PP)"))
                .findFirst().orElseThrow();
        assertThat(contientTexte(annexe.rows().get(0), "ONG locale")).isTrue();
        assertThat(annexe.rows().get(0).cells().get(1).text()).isEqualTo("Appui aux communautés");
        assertThat(texte(blocs)).contains("Parc informatique", "Postes récents", "Mécénat");
    }

    @Test
    @DisplayName("Effectifs : par hiérarchie et par statut, sans compter deux fois les mêmes agents")
    void rendLesEffectifsParHierarchieEtStatut() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        saisie(commerciale, "S14B", """
                {"rows":[{"category":"HIERARCHIE","staffKey":"CADRE","years":{"2027":{"male":8,"female":4}}},
                         {"category":"STATUT","staffKey":"CDI","years":{"2027":{"male":8,"female":4}}}]}""");

        List<ExportBlock> blocs = build(commerciale);
        assertThat(texte(blocs)).contains("Hiérarchie", "Cadre", "Statut", "CDI", "TOTAUX")
                .as("la ligne « Fonctionnaire » a été retirée du modèle").doesNotContain("Fonctionnaire");

        ExportBlock.Table effectifs = blocs.stream()
                .filter(ExportBlock.Table.class::isInstance).map(ExportBlock.Table.class::cast)
                .filter(table -> table.columnHeaders().get(0).equals("Effectifs"))
                .findFirst().orElseThrow();
        assertThat(effectifs.bands()).as("les années coiffent les colonnes M, F, Total").isNotEmpty();
        List<ExportBlock.Cell> totaux = effectifs.rows().get(effectifs.rows().size() - 1).cells();
        assertThat(totaux.get(3).text())
                .as("hiérarchie et statut ventilent les mêmes 12 agents : le total ne les additionne pas")
                .isEqualTo("12");
    }

    @Test
    @DisplayName("La note ne présente pas SENICO comme dotée d'un plan stratégique de développement")
    void neParlePasDePlanStrategiqueDeDeveloppement() {
        String note = texte(build(group(1, "Direction commerciale")));

        assertThat(note).doesNotContainIgnoringCase("stratégique de développement").doesNotContain("PSD");
        assertThat(note).contains("V. BILAN DES PERFORMANCES DES ANNÉES PRÉCÉDENTES", "VII. PRINCIPAUX ENJEUX ET DÉFIS");
    }

    @Test
    @DisplayName("Le cadre stratégique arrêté par la DG remplace les propositions des directions")
    void publieLeCadreArreteParLaDirectionGenerale() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        WorkGroup technique = group(2, "Direction technique");
        saisie(commerciale, "S07B", "{\"vision\":\"Vision de la direction commerciale\"}");
        saisie(commerciale, "S08", """
                {"axes":[{"axisCode":"AXE1","title":"Croissance commerciale","specificObjectives":["Vendre plus"]}]}""");
        saisie(technique, "S08", """
                {"axes":[{"axisCode":"AXE1","title":"Fiabilité du réseau","specificObjectives":["Réparer vite"]},
                         {"axisCode":"AXE2","title":"Axe oublié","specificObjectives":[]}]}""");
        saisie(commerciale, "S11", """
                {"axes":[{"axisCode":"AXE1","effects":[{"rows":[{"activities":"Action commerciale","years":{"2027":300}}]}]}]}""");
        saisie(technique, "S11", """
                {"axes":[{"axisCode":"AXE1","effects":[{"rows":[{"activities":"Action technique","years":{"2027":200}}]}]},
                         {"axisCode":"AXE2","effects":[{"rows":[{"activities":"Action orpheline","years":{"2027":100}}]}]}]}""");

        narratives.put(NarrativeBlockKey.VISION, "Être l'opérateur de référence du pays");
        narratives.put(NarrativeBlockKey.AXES_CONSOLIDES, """
                {"axes":[{"title":"Axe commun","objective":"Servir mieux",
                          "links":[{"groupId":1,"axisCode":"AXE1"},{"groupId":2,"axisCode":"AXE1"}]}]}""");

        List<ExportBlock> blocs = build(commerciale, technique);
        String note = texte(blocs);

        assertThat(note).contains("Être l'opérateur de référence du pays", "Axe 1 : Axe commun", "Servir mieux",
                "Vendre plus", "Réparer vite");
        assertThat(note)
                .as("une vision arrêtée par la DG ne laisse plus place à celles des directions")
                .doesNotContain("Vision de la direction commerciale");
        assertThat(note)
                .as("un axe peut ne regrouper les axes que d'une seule direction")
                .doesNotContain("aucun ne relève d'une seule direction");
        assertThat(note)
                .as("un axe de direction non rattaché doit être signalé, pas perdu")
                .contains("Axe oublié", "Axes non rattachés", "Action orpheline");

        ExportBlock.Chart chart = blocs.stream()
                .filter(ExportBlock.Chart.class::isInstance).map(ExportBlock.Chart.class::cast)
                .filter(c -> c.kind() == ExportBlock.ChartKind.STACKED_COLUMNS)
                .findFirst().orElseThrow();
        assertThat(chart.series())
                .as("le budget des deux axes rattachés s'additionne sous l'axe commun")
                .anySatisfy(series -> {
                    assertThat(series.name()).isEqualTo("Axe 1 : Axe commun");
                    assertThat(series.values().get(0)).isEqualTo(500d);
                });
    }

    @Test
    @DisplayName("Un texte de la Direction Générale non rédigé est signalé à sa place")
    void signaleUnTexteNonRedige() {
        String note = texte(build(group(1, "Direction commerciale")));
        assertThat(note).contains("« Mot du DG » n'est pas encore rédigé");
    }

    @Test
    @DisplayName("Une section validée mais pas approuvée par le DG n'entre pas dans la note")
    void ecarteCeQueLeDgNaPasApprouve() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        saisie(commerciale, "S07B", "{\"vision\":\"Vision non approuvée\"}", false);

        assertThat(texte(build(commerciale))).doesNotContain("Vision non approuvée");
    }

    @Test
    @DisplayName("Sans aucune saisie, la note tient debout : chaque tableau du canevas garde sa structure, vide")
    void supporteUneBaseVide() {
        // Date fixee : apres la cloture de 2026, l'en-tete devient « Réalisé 2026 ».
        List<ExportBlock> blocs = build(LocalDate.of(2026, 9, 22), group(1, "Direction commerciale"));
        String note = texte(blocs);

        assertThat(note).contains(PsdBriefBuilder.partTitles());
        assertThat(note).as("revue de l'auditeur : plus de phrase « Aucun ... n'est encore approuvé » à la place d'un tableau")
                .doesNotContain("n'est encore approuvé", "n'est pas encore renseigné");

        List<List<String>> entetes = blocs.stream()
                .filter(ExportBlock.Table.class::isInstance).map(ExportBlock.Table.class::cast)
                .map(ExportBlock.Table::columnHeaders).toList();
        assertThat(entetes).as("les tableaux du canevas, vides, sont tous là")
                .anySatisfy(h -> assertThat(h).contains("Acteur (PP)"))                              // parties prenantes
                .anySatisfy(h -> assertThat(h).contains("Résultat attendu en décembre"))             // bilan des performances
                .anySatisfy(h -> assertThat(h).contains("Défis à relever"))                          // ressources et compétences
                .anySatisfy(h -> assertThat(h).contains("Forces majeures"))                          // synthèse des ressources
                .anySatisfy(h -> assertThat(h).contains("Menaces", "Opportunités"))                  // PESTEL
                .anySatisfy(h -> assertThat(h).contains("Liste des forces", "Liste des faiblesses"))  // mise en relation
                .anySatisfy(h -> assertThat(h).contains("Sources", "Analyse"))                       // analyse causale
                .anySatisfy(h -> assertThat(h).contains("Actions d'atténuation"))                    // risques élevés
                .anySatisfy(h -> assertThat(h).contains("Contraintes prioritaires"))                 // contraintes / défis
                .anySatisfy(h -> assertThat(h).contains("Total (M FCFA)"))                           // budget par axe
                .anySatisfy(h -> assertThat(h).contains("Sources de financement"))                   // plan de financement
                .anySatisfy(h -> assertThat(h).contains("Effectifs"))                                // effectifs
                .anySatisfy(h -> assertThat(h).contains("Présence (Oui/Non)"))                       // annexe 1 : matrice des risques
                .anySatisfy(h -> assertThat(h).contains("Logique d'intervention"))                   // annexe 2 : cadre logique
                .anySatisfy(h -> {                                                                   // annexe 3 : planification
                    assertThat(h.get(0)).isEqualTo("Extrants");
                    assertThat(h).contains("Responsables");
                })
                .anySatisfy(h -> assertThat(h).contains("Totaux", "Responsable"))                    // annexe 4 : budget détaillé
                .anySatisfy(h -> assertThat(h).contains("Sources et moyens de collecte"))            // annexe 5 : fiche des indicateurs
                .anySatisfy(h -> assertThat(h).contains("Réf. 2026"));                               // annexe 5 : cadre de rendement par axe
        assertThat(tableau(blocs, "SYNTHÈSE DU CADRE STRATÉGIQUE").rows())
                .as("la synthèse du cadre stratégique, vide, garde ses intitulés de colonnes")
                .anyMatch(row -> row.cells().get(0).text().equals("Orientation stratégique (OS)"));
        assertThat(note).as("le plan des effectifs vide garde les lignes du modèle client, sans zéro trompeur")
                .contains("Agents de maîtrise", "Employé", "Journalier", "CDI", "Expatrié", "Stagiaire", "TOTAUX");
        assertThat(note).as("IX.4 : sans axe consolidé, un tableau vide plutôt que des listes vides")
                .contains("Axe d'intervention proposé")
                .doesNotContain("Axes proposés par les directions");
    }

    @Test
    @DisplayName("Une seule direction a saisi : les tableaux gardent la structure entière du plan et du modèle")
    void gardeLaStructureQuandUneSeuleDirectionASaisi() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        WorkGroup technique = group(2, "Direction technique");
        saisie(commerciale, "S08", """
                {"axes":[{"axisCode":"AXE1","title":"Croissance commerciale"}]}""");
        saisie(technique, "S08", """
                {"axes":[{"axisCode":"AXE1","title":"Fiabilité du réseau"}]}""");
        saisie(commerciale, "S11", """
                {"axes":[{"axisCode":"AXE1","effects":[{"effectLabel":"Gagner des parts de marché","rows":[
                   {"extrant":"Offre grands comptes","activities":"Lancer l'offre grands comptes","years":{"2027":100}}]}]}]}""");
        saisie(commerciale, "S15", """
                {"rows":[{"source":"RESSOURCES_PROPRES","amount":100},
                         {"source":"EMPRUNTS","amount":0,"modalities":"Négociation d'une ligne BOAD"}]}""");
        saisie(commerciale, "S14B", """
                {"rows":[{"category":"HIERARCHIE","staffKey":"CADRE","years":{"2027":{"male":8,"female":4}}}],
                 "totals":{"2027":{"male":8,"female":4}}}""");
        narratives.put(NarrativeBlockKey.AXES_CONSOLIDES, """
                {"axes":[{"title":"Axe commercial","links":[{"groupId":1,"axisCode":"AXE1"}]},
                         {"title":"Axe technique","links":[{"groupId":2,"axisCode":"AXE1"}]}]}""");

        List<ExportBlock> blocs = build(commerciale, technique);
        ExportBlock.Table synthese = tableau(blocs, "SYNTHÈSE DU CADRE STRATÉGIQUE");
        assertThat(synthese.rows().stream().filter(ExportBlock.TableRow::band).map(row -> row.cells().get(0).text()))
                .as("l'axe sans OS garde son bandeau dans la synthèse du cadre stratégique")
                .containsExactly("AXE 1 : Axe commercial", "AXE 2 : Axe technique");

        String note = texte(blocs);
        assertThat(note).as("une source de financement sans montant garde sa ligne et ses modalités")
                .contains("Emprunts", "Négociation d'une ligne BOAD");
        assertThat(note).as("les lignes du modèle des effectifs restent toutes, même non renseignées")
                .contains("Stagiaire", "Expatrié");
        assertThat(blocs.stream().filter(ExportBlock.Heading.class::isInstance).map(ExportBlock.Heading.class::cast)
                .map(ExportBlock.Heading::text))
                .as("chaque axe de l'entreprise garde son tableau dans les annexes par axe")
                .contains("Budget détaillé — Axe 1 : Axe commercial", "Budget détaillé — Axe 2 : Axe technique");
    }

    @Test
    @DisplayName("Un constat porte la couleur de la direction qui l'a ecrit")
    void attribueChaqueConstatASaDirection() {
        WorkGroup commerciale = group(1, "Direction commerciale", "#2563EB");
        WorkGroup technique = group(2, "Direction technique", "#16A34A");
        saisie(commerciale, "S04", "{\"strengths\":[\"Reseau national\"]}");
        saisie(technique, "S04", "{\"strengths\":[\"Parc vieillissant\"]}");

        List<ExportBlock.Attribution> items = attributions(build(commerciale, technique));

        assertThat(items)
                .filteredOn(item -> item.text().equals("Reseau national"))
                .singleElement()
                .satisfies(item -> assertThat(item.colorHexes()).containsExactly("#2563EB"));
        assertThat(items)
                .filteredOn(item -> item.text().equals("Parc vieillissant"))
                .singleElement()
                .satisfies(item -> assertThat(item.colorHexes()).containsExactly("#16A34A"));
    }

    @Test
    @DisplayName("Un constat partage porte les couleurs de toutes les directions qui le citent")
    void attribueUnConstatPartageATousSesAuteurs() {
        WorkGroup commerciale = group(1, "Direction commerciale", "#2563EB");
        WorkGroup technique = group(2, "Direction technique", "#16A34A");
        saisie(commerciale, "S04", "{\"weaknesses\":[\"SI vieillissant\"]}");
        saisie(technique, "S04", "{\"weaknesses\":[\"SI vieillissant\"]}");

        List<ExportBlock.Attribution> items = attributions(build(commerciale, technique));

        assertThat(items)
                .as("le constat fusionne doit garder ses deux auteurs, sinon la couleur ment")
                .filteredOn(item -> item.text().equals("SI vieillissant"))
                .singleElement()
                .satisfies(item -> assertThat(item.colorHexes())
                        .containsExactlyInAnyOrder("#2563EB", "#16A34A"));
    }

    @Test
    @DisplayName("Deux formulations du meme constat n'occupent qu'une ligne")
    void fusionneDeuxFormulationsDuMemeConstat() {
        WorkGroup technique = group(2, "Direction technique", "#16A34A");
        WorkGroup logistique = group(5, "Direction logistique", "#DC2626");
        saisie(technique, "S04", "{\"weaknesses\":[\"Délais d'acheminement parfois supérieurs à la concurrence\"]}");
        saisie(logistique, "S04", "{\"weaknesses\":[\"Délais d'acheminement supérieurs à ceux de la concurrence\"]}");

        assertThat(attributions(build(technique, logistique)))
                .filteredOn(item -> item.text().startsWith("Délais d'acheminement"))
                .singleElement()
                .satisfies(item -> assertThat(item.colorHexes()).containsExactlyInAnyOrder("#16A34A", "#DC2626"));
    }

    @Test
    @DisplayName("Une direction sans couleur choisie en recoit une, pas du gris")
    void donneUneCouleurAToutesLesDirections() {
        WorkGroup sansCouleur = group(4, "Direction des systemes d'information");
        saisie(sansCouleur, "S04", "{\"threats\":[\"Cybersecurite\"]}");

        List<ExportBlock.Attribution> items = attributions(build(sansCouleur));

        assertThat(items)
                .filteredOn(item -> item.text().equals("Cybersecurite"))
                .singleElement()
                .satisfies(item -> assertThat(item.colorHexes())
                        .singleElement().asString().matches("#[0-9A-F]{6}"));
    }

    @Test
    @DisplayName("Rien d'approuvé : la note l'annonce en tete plutot que d'aligner des zeros muets")
    void annonceUnPerimetreVide() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        saisie(commerciale, "S11", "{\"grandTotal\":900}", false);
        saisie(commerciale, "S08", "{\"axes\":[{\"title\":\"Axe non approuvé\"}]}", false);

        assertThat(build(commerciale))
                .as("une note vide doit expliquer pourquoi elle l'est")
                .anySatisfy(bloc -> assertThat(bloc)
                        .isInstanceOfSatisfying(ExportBlock.Callout.class, callout -> {
                            assertThat(callout.tone()).isEqualTo(ExportBlock.Tone.WARNING);
                            assertThat(callout.text())
                                    .contains("Aucune section n'a encore été approuvée")
                                    .contains("2 section(s)");
                        }));
    }

    @Test
    @DisplayName("Des sections approuvees : pas d'avertissement de perimetre vide")
    void tairLAvertissementQuandLeDgAApprouve() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        saisie(commerciale, "S11", "{\"grandTotal\":900}");

        assertThat(build(commerciale))
                .as("l'avertissement ne doit pas survivre a l'approbation du DG")
                .noneMatch(bloc -> bloc instanceof ExportBlock.Callout callout
                        && callout.text().contains("Aucune section"));
    }
}
