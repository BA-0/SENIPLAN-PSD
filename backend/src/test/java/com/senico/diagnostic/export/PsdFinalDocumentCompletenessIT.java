package com.senico.diagnostic.export;

import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.repository.WorkGroupRepository;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Recette de completude du "Plan Strategique de SENICO" : le document consolide ne doit
 * plus comporter la moindre rubrique vide une fois les cinq directions saisies et validees.
 *
 * <p>Ce test lit la base de developpement reelle (profil dev, MySQL local) : la completude
 * d'une saisie est une propriete des donnees, pas du code, et ne peut donc pas se verifier
 * sur un jeu de test en memoire. Son suffixe {@code IT} le tient hors du {@code mvn test}
 * ordinaire (surefire ne collecte que les {@code *Test}) ; il se lance a la demande, base
 * locale demarree :</p>
 *
 * <pre>mvn test -Dtest=PsdFinalDocumentCompletenessIT</pre>
 */
@SpringBootTest
class PsdFinalDocumentCompletenessIT {

    /** Marqueurs emis par SectionExportRenderer / PdfExportService quand une rubrique est vide. */
    private static final List<String> MARQUEURS_DE_VIDE = List.of(
            "(contenu à renseigner)",
            "Aucune donnée saisie",
            "Section non validée");

    @Autowired
    private WordExportService wordExportService;

    @Autowired
    private PdfExportService pdfExportService;

    @Autowired
    private WorkGroupRepository workGroupRepository;

    private static String texte;
    private static List<String> directions;

    @BeforeAll
    static void reinitialiser() {
        texte = null;
        directions = null;
    }

    private String document() throws Exception {
        if (texte == null) {
            directions = workGroupRepository.findAll().stream().map(WorkGroup::getName).toList();
            assumeTrue(!directions.isEmpty(), "Aucune direction en base : recette ignoree");
            byte[] docx = wordExportService.exportPsdFinalDocument();
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx));
                 XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                texte = extractor.getText();
            }
        }
        return texte;
    }

    @Test
    @DisplayName("Aucune rubrique du document consolide n'est vide")
    void aucuneRubriqueVide() throws Exception {
        String contenu = document();
        for (String marqueur : MARQUEURS_DE_VIDE) {
            assertThat(contenu)
                    .as("le document consolide contient encore le marqueur « %s »", marqueur)
                    .doesNotContain(marqueur);
        }
    }

    @Test
    @DisplayName("Les blocs narratifs rediges par l'admin sont tous presents")
    void blocsNarratifsRediges() throws Exception {
        String contenu = document();
        assertThat(contenu).contains("Mot du DG", "Préambule", "Introduction",
                "Rappel des missions", "Organisation", "Ressources", "Défis à relever", "Enjeux");
    }

    @Test
    @DisplayName("Les cinq sections ajoutees en revue client sont reprises dans le document")
    void sectionsDeRevueClientReprises() throws Exception {
        String contenu = document();
        assertThat(contenu).contains(
                "Analyse des performances de l'année 2026",
                "Synthèse de l'analyse des ressources",
                "Synthèse des enjeux et des contraintes",
                "Synthèse du cadre logique",
                "Plan d'évolution des effectifs");
    }

    @Test
    @DisplayName("Chaque direction est representee dans le document consolide")
    void toutesLesDirectionsRepresentees() throws Exception {
        String contenu = document();
        assertThat(directions).isNotEmpty();
        assertThat(contenu).contains(directions.toArray(String[]::new));
    }

    /**
     * Le sommaire du PDF castait ses entrees en SectionEntry : depuis l'ajout de
     * « Synthese du PSD » (SynthesisEntry), l'export levait une ClassCastException
     * alors que la version Word passait. Seule une generation reelle le detecte.
     */
    @Test
    @DisplayName("Le meme document s'exporte aussi en PDF, sommaire compris")
    void exportPdfDuMemeDocument() {
        assumeTrue(!workGroupRepository.findAll().isEmpty(), "Aucune direction en base : recette ignoree");
        assertThat(pdfExportService.exportPsdFinalDocument()).isNotEmpty();
    }
}
