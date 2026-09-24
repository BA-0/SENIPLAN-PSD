package com.senico.diagnostic.service;

import com.senico.diagnostic.domain.User;
import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.dto.group.CreateWorkGroupRequest;
import com.senico.diagnostic.dto.group.WorkGroupDto;
import com.senico.diagnostic.repository.GroupSectionStatusRepository;
import com.senico.diagnostic.repository.UserRepository;
import com.senico.diagnostic.repository.WorkGroupRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Supprimer une direction l'emporte avec ses statuts de section, son journal et son compte chef de
 * groupe. Tout se passe dans la transaction du test, que Spring annule a la sortie.
 *
 * <pre>mvn test -Dtest=SuppressionDirectionIT</pre>
 */
@SpringBootTest
@Transactional
class SuppressionDirectionIT {

    private static final String IDENTIFIANT = "recette.direction.supprimee";

    @Autowired
    private WorkGroupService workGroupService;

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private WorkGroupRepository workGroupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupSectionStatusRepository groupSectionStatusRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Supprimer une direction retire la direction, ses sections et son chef de groupe")
    void laDirectionDisparaitAvecSonChefDeGroupe() {
        WorkGroupDto direction = workGroupService.create(new CreateWorkGroupRequest(
                "Direction a supprimer", "", null, IDENTIFIANT, "Chef de groupe de recette", "RemisParLadmin1!"));
        WorkGroup groupe = workGroupRepository.findById(direction.id()).orElseThrow();
        User chef = userRepository.findByUsername(IDENTIFIANT).orElseThrow();
        activityLogService.log(groupe, chef, ActivityLogService.ACTION_LOGIN, null);
        entityManager.flush();
        assertThat(groupSectionStatusRepository.findByGroupIdWithSection(direction.id())).isNotEmpty();

        workGroupService.delete(direction.id());
        entityManager.flush();
        entityManager.clear();

        assertThat(workGroupRepository.findById(direction.id())).isEmpty();
        assertThat(userRepository.findByUsername(IDENTIFIANT)).as("le compte chef de groupe part avec la direction").isEmpty();
        assertThat(groupSectionStatusRepository.findByGroupIdWithSection(direction.id())).isEmpty();
    }
}
