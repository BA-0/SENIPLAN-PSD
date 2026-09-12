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
                "Courrier", "Compenser par le colis",       // VII.3 synthese des contraintes
                "Devenir l'opérateur logistique de référence", "Fiabilité",  // IX. propositions des directions
                "Croissance commerciale",                   // IX. et X.
                "Lancer l'offre grands comptes",            // XII.2 recapitulatif du plan
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
    @DisplayName("Les tableaux du modèle client : ressources, synthèse du cadre stratégique, récapitulatif, impact, collecte")
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
        assertThat(note).as("récapitulatif : indicateur et cible 2031 repris du cadre de mesure de rendement")
                .contains("Chiffre d'affaires annuel", "25 Mds FCFA", "Cible 2031");
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
        assertThat(premiere.cells().get(2).attributions().get(0).text()).isEqualTo("Concurrence des opérateurs privés");
        assertThat(premiere.cells().get(2).rowSpan())
                .as("la contrainte commune aux trois actions n'est écrite qu'une fois").isEqualTo(3);
        assertThat(lignes.get(5).cells().get(0).attributions().get(0).text()).isEqualTo("OS2 : Fidéliser la clientèle");
        assertThat(lignes.get(5).cells().get(2).isCovered()).isTrue();

        String note = texte(blocs);
        assertThat(note).contains("2 orientations stratégiques (OS)");
        assertThat(note).as("sans indicateur propre, l'OS est suivie par l'indicateur d'extrants de son axe")
                .contains("Taux de réalisation des activités programmées")
                .doesNotContain("n'y est encore rattaché");
    }

    @Test
    @DisplayName("Tant que 2026 n'est pas clos, ses réalisations sont présentées comme des estimations")
    void presenteLesRealisations2026CommeDesEstimationsAvantLaCloture() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        saisie(commerciale, "S01B", """
                {"rows":[{"domain":"Ventes","indicator":"Chiffre d'affaires","target2026":4200,"achieved2026":3980}]}""");

        assertThat(texte(build(LocalDate.of(2026, 9, 12), commerciale)))
                .contains("Estimation 2026", "n'étant pas clos")
                .doesNotContain("Réalisé 2026");
        assertThat(texte(build(LocalDate.of(2027, 3, 1), commerciale)))
                .contains("Réalisé 2026")
                .doesNotContain("Estimation 2026");
    }

    @Test
    @DisplayName("Effectifs : par hiérarchie et par statut, sans compter deux fois les mêmes agents")
    void rendLesEffectifsParHierarchieEtStatut() {
        WorkGroup commerciale = group(1, "Direction commerciale");
        saisie(commerciale, "S14B", """
                {"rows":[{"category":"HIERARCHIE","staffKey":"CADRE","years":{"2027":{"male":8,"female":4}}},
                         {"category":"STATUT","staffKey":"FONCTIONNAIRE","years":{"2027":{"male":8,"female":4}}}]}""");

        List<ExportBlock> blocs = build(commerciale);
        assertThat(texte(blocs)).contains("Hiérarchie", "Cadre", "Statut", "Fonctionnaire", "TOTAUX");

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
        assertThat(note).contains("VI. BILAN DES PERFORMANCES DES ANNÉES PRÉCÉDENTES", "VII. PRINCIPAUX ENJEUX ET DÉFIS");
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
    @DisplayName("Sans aucune saisie, la note tient debout et le dit")
    void supporteUneBaseVide() {
        String note = texte(build(group(1, "Direction commerciale")));

        assertThat(note).contains(PsdBriefBuilder.partTitles());
        assertThat(note).contains("Le budget consolidé n'est pas encore renseigné");
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
