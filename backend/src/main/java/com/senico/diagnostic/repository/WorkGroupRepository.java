package com.senico.diagnostic.repository;

import com.senico.diagnostic.domain.WorkGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WorkGroupRepository extends JpaRepository<WorkGroup, Long> {

    /** Charge tous les groupes avec leur chef en une seule requete (evite le N+1 sur users). */
    @Query("select g from WorkGroup g left join fetch g.leader")
    List<WorkGroup> findAllWithLeader();
}
