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
            Map.entry("S04", SectionType.SWOT),
            Map.entry("S06B", SectionType.CONSTRAINTS_SYNTHESIS),
            Map.entry("S07B", SectionType.STRATEGIC_FRAMEWORK),
            Map.entry("S08", SectionType.STRATEGIC_AXES),
            Map.entry("S11", SectionType.BUDGET),
            Map.entry("S13", SectionType.INDICATOR_SHEET),
            Map.entry("S14", SectionType.RISK_MATRIX),
            Map.entry("S14B", SectionType.STAFF_EVOLUTION),
            Map.entry("S15", SectionType.FINANCING_PLAN));

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
                "Courrier", "Compenser par le colis",       // annexe, tableau 1
                "Devenir l'opérateur logistique de référence", "Fiabilité",  // IX. propositions des directions
                "Croissance commerciale",                   // IX. et X.
                "Lancer l'offre grands comptes",            // XII. synthese du cadre strategique
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
