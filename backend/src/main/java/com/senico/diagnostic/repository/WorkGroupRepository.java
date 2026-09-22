package com.senico.diagnostic.repository;

import com.senico.diagnostic.domain.WorkGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WorkGroupRepository extends JpaRepository<WorkGroup, Long> {

    /** Charge tous les groupes avec leur chef en une seule requete (evite le N+1 sur users). */
    @Query("select g from WorkGroup g left join fetch g.leader")
    List<WorkGroup> findAllWithLeader();

    /**
     * Directions en activite, dans l'ordre de leur creation. Les documents remis au Conseil d'Administration
     * ne reprennent qu'elles : une direction desactivee (groupe de test, entite dissoute) n'a plus a figurer
     * ni dans la legende des couleurs, ni dans le decompte des directions contributrices.
     */
    List<WorkGroup> findByEnabledTrueOrderByIdAsc();
}
