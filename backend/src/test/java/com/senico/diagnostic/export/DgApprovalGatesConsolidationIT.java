package com.senico.diagnostic.export;

import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.SectionStatus;
import com.senico.diagnostic.domain.User;
import com.senico.diagnostic.dto.section.DgApprovalRequest;
import com.senico.diagnostic.dto.section.DgDecision;
import com.senico.diagnostic.repository.GroupSectionStatusRepository;
import com.senico.diagnostic.repository.UserRepository;
import com.senico.diagnostic.service.SectionEngineService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regle centrale de la validation a deux niveaux : tant que le DG n'a pas approuve une section,
 * son contenu n'entre pas dans les documents consolides. On le verifie sur le classeur du
 * Document de consolidation, ou chaque direction occupe une ligne lisible telle quelle.
 *
 * <p>Le test approuve reellement une section puis annule tout : {@code @Transactional} tient
 * l'ecriture dans la transaction du test, que Spring annule a la sortie. La base de dev ressort
 * donc inchangee, approbation comprise.</p>
 *
 * <pre>mvn test -Dtest=DgApprovalGatesConsolidationIT</pre>
 */
@SpringBootTest
class DgApprovalGatesConsolidationIT {

    private static final String MESSAGE_RETENU = "Section non approuvée par la Direction Générale";

    @Autowired
    private ExcelExportService excelExportService;

    @Autowired
    private SectionEngineService sectionEngineService;

    @Autowired
    private GroupSectionStatusRepository groupSectionStatusRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Transactional
    @DisplayName("Le contenu d'une section validee n'entre dans le document consolide qu'apres approbation du DG")
    void leContenuNentreQuApresApprobation() throws Exception {
        User dg = userRepository.findByUsername("m.dia").orElse(null);
        assumeTrue(dg != null, "Compte m.dia absent : migration V15 non appliquee");

        GroupSectionStatus cible = groupSectionStatusRepository.findAll().stream()
                .filter(s -> s.getStatus() == SectionStatus.VALIDATED && !s.isDgApproved())
                .findFirst()
                .orElse(null);
        assumeTrue(cible != null, "Aucune section validee en attente d'approbation : recette ignoree");

        String direction = cible.getGroup().getName();
        String codeSection = cible.getSection().getCode();

        assertThat(contenuConsolide(codeSection, direction))
                .as("une section validee mais pas encore approuvee doit rester hors du document")
                .contains(MESSAGE_RETENU);

        sectionEngineService.dgReview(cible.getGroup().getId(), codeSection,
                new DgApprovalRequest(DgDecision.APPROVE, null), dg);

        assertThat(contenuConsolide(codeSection, direction))
                .as("une fois le DG passe, la contribution doit figurer dans le document")
                .doesNotContain(MESSAGE_RETENU);
    }

    /** Cellule "Contenu" de la direction dans la feuille de la section, telle que la lit le client. */
    private String contenuConsolide(String codeSection, String direction) throws Exception {
        byte[] xlsx = excelExportService.exportConsolidatedFull();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            for (Sheet sheet : workbook) {
                if (!sheet.getSheetName().startsWith(codeSection)) {
                    continue;
                }
                for (Row row : sheet) {
                    if (row.getCell(0) != null && direction.equals(row.getCell(0).getStringCellValue())) {
                        return row.getCell(2) != null ? row.getCell(2).getStringCellValue() : "";
                    }
                }
            }
        }
        throw new AssertionError("Ligne introuvable pour " + direction + " dans la feuille " + codeSection);
    }
}
