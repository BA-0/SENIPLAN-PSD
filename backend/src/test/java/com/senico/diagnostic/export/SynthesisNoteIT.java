package com.senico.diagnostic.export;

import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.repository.GroupSectionStatusRepository;
import com.senico.diagnostic.repository.WorkGroupRepository;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Recette de la "Note de synthese" : le resume de toutes les directions doit tenir debout
 * seul — chiffres agreges renseignes, les parties du plan d'un PSD publie presentes (sans page de
 * sommaire depuis la revue du 22/09/2026), et surtout aucune reprise direction par direction, qui est
 * precisement ce que la note doit eviter.
 *
 * <p>Comme {@link PsdFinalDocumentCompletenessIT}, lit la base de developpement reelle : ce
 * qu'on verifie ici tient autant aux donnees qu'au code. Suffixe {@code IT}, donc hors du
 * {@code mvn test} ordinaire ; se lance a la demande, base locale demarree :</p>
 *
 * <pre>mvn test -Dtest=SynthesisNoteIT</pre>
 */
@SpringBootTest
class SynthesisNoteIT {

    @Autowired
    private WordExportService wordExportService;

    @Autowired
    private PdfExportService pdfExportService;

    @Autowired
    private WorkGroupRepository workGroupRepository;

    @Autowired
    private GroupSectionStatusRepository groupSectionStatusRepository;

    /**
     * La recette porte sur les donnees autant que sur le code : tant qu'aucune section n'est approuvee
     * par la Direction Generale (seul perimetre repris dans la note : une saisie en cours, soumise ou
     * validee par le comite de pilotage n'y entre pas), la note se genere mais n'a rien a montrer. On
     * l'ignore alors, plutot que de la laisser echouer sur des tableaux absents.
     */
    private String note() throws Exception {
        assumeTrue(!workGroupRepository.findAll().isEmpty(), "Aucune direction en base : recette ignoree");
        assumeTrue(groupSectionStatusRepository.findAllWithGroupAndSection().stream().anyMatch(GroupSectionStatus::isDgApproved),
                "Aucune section approuvee par la Direction Generale : recette ignoree");
        byte[] docx = wordExportService.exportSynthesisNote();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    @Test
    @DisplayName("Toutes les parties de la note sont presentes, sans page de sommaire")
    void toutesLesPartiesSontPresentes() throws Exception {
        String contenu = note();
        // Revue de l'auditeur (22/09/2026) : la note n'a plus de sommaire.
        assertThat(contenu).doesNotContain("SOMMAIRE");
        for (String partie : PsdBriefBuilder.partTitles()) {
            assertThat(contenu.split(java.util.regex.Pattern.quote(partie), -1).length - 1)
                    .as("« %s » : attendu une fois, en tete de partie", partie)
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("La page de garde Word porte le logo SENICO, comme le PDF")
    void lapageDeGardePorteLeLogo() throws Exception {
        assumeTrue(!workGroupRepository.findAll().isEmpty(), "Aucune direction en base : recette ignoree");
        byte[] docx = wordExportService.exportSynthesisNote();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertThat(document.getAllPictures())
                    .as("note remise sans identite visuelle, la ou son pendant PDF est en-tete")
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("Les tableaux chiffres sont commentes, comme dans un PSD publie")
    void lesTableauxSontCommentes() throws Exception {
        assertThat(note())
                .as("les lectures « Analyse : ... » sous les tableaux ont disparu")
                .contains("Analyse :");
    }

    @Test
    @DisplayName("Les chiffres cles sont renseignes, pas laisses a zero")
    void lesChiffresClesSontRenseignes() throws Exception {
        String contenu = note();
        assertThat(contenu).contains("Directions contributrices", "Budget global", "Effectifs ");
        // Un budget a zero signalerait que les champs calcules ne sont pas appliques a
        // l'export, le defaut corrige par ExportContentReader.
        assertThat(contenu)
                .as("le budget global ne doit pas ressortir a zero")
                .doesNotContain("Budget global\t0 FCFA");
    }

    @Test
    @DisplayName("La note ne deroule pas les directions une par une")
    void neRependPasLesDirectionsUneParUne() throws Exception {
        List<String> parties = partiesDeLaNote(note());
        List<String> directions = workGroupRepository.findAll().stream().map(WorkGroup::getName).toList();

        // Ce qu'on compte, c'est le nombre de PARTIES qui nomment une direction, pas le nombre
        // d'occurrences. Une direction peut legitimement se nommer elle-meme — dans sa propre
        // vision au cadre strategique, ou parmi les structures responsables du suivi — sans que
        // la note deroule quoi que ce soit. Une reprise direction par direction, elle, la
        // nommerait dans toutes les parties. Compter les occurrences confondait les deux, et ne
        // passait que parce que les listes etaient alors tronquees a huit elements.
        // Depuis la troisieme revue client, la note reprend aussi les syntheses propres a chaque
        // direction (ressources, inventaire, cadre logique), chacune dans un tableau a colonne
        // « Direction » : une direction s'y nomme donc legitimement dans quelques parties de plus.
        // Une reprise direction par direction, elle, la nommerait toujours dans presque toutes.
        int seuil = parties.size() * 2 / 3;
        for (String direction : directions) {
            long partiesCitantes = parties.stream().filter(partie -> partie.contains(direction)).count();
            assertThat(partiesCitantes)
                    .as("« %s » est citee dans %d des %d parties : la note redeviendrait un "
                            + "document par direction", direction, partiesCitantes, parties.size())
                    .isLessThan(seuil);
        }
    }

    /** Decoupe le corps de la note sur ses intertitres de partie. */
    private List<String> partiesDeLaNote(String contenu) {
        List<String> titres = PsdBriefBuilder.partTitles();
        // Sans sommaire (revue du 22/09/2026), chaque titre de partie n'apparait qu'une fois : le corps
        // commence a la premiere occurrence du premier.
        int debutDuCorps = contenu.indexOf(titres.get(0));
        String corps = debutDuCorps < 0 ? contenu : contenu.substring(debutDuCorps);

        List<String> parties = new ArrayList<>();
        for (int i = 0; i < titres.size(); i++) {
            int debut = corps.indexOf(titres.get(i));
            if (debut < 0) {
                continue;
            }
            int fin = i + 1 < titres.size() ? corps.indexOf(titres.get(i + 1), debut) : -1;
            parties.add(corps.substring(debut, fin < 0 ? corps.length() : fin));
        }
        return parties;
    }

    /**
     * Troisieme revue client : « les parties les plus importantes du PSD ne sont pas dedans ». La note
     * n'a plus a etre courte — elle doit reprendre, axe par axe, le cadre logique, la planification,
     * le budget et le cadre de mesure de rendement. Le Plan Strategique complet presentant desormais
     * les memes tableaux, comparer la longueur des deux documents n'avait plus de sens.
     */
    @Test
    @DisplayName("La note reprend les parties les plus importantes : cadre logique, planification, budget, rendement")
    void reprendLesPartiesLesPlusImportantes() throws Exception {
        assertThat(note()).contains(
                "X.1 Cadre logique", "Indicateurs objectivement vérifiables (IOV)",
                "X.2 Opérationnalisation : plan d'actions", "Activités pour atteindre les résultats",
                "X.3 Budget du plan", "Budget détaillé — ",
                "XI.2 Cadre de mesure de rendement", "Résultat / extrant",
                PsdBriefBuilder.ANNEXE_CADRE_LOGIQUE, PsdBriefBuilder.ANNEXE_PLANIFICATION, PsdBriefBuilder.ANNEXE_RENDEMENT);
    }

    @Test
    @DisplayName("La version PDF se genere aussi")
    void lePdfSeGenere() {
        assumeTrue(!workGroupRepository.findAll().isEmpty(), "Aucune direction en base : recette ignoree");
        assertThat(pdfExportService.exportSynthesisNote())
                .as("PDF vide ou tronque")
                .hasSizeGreaterThan(5_000);
    }
}
