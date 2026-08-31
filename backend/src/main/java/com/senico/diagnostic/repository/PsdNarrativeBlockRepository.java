package com.senico.diagnostic.repository;

import com.senico.diagnostic.domain.NarrativeBlockKey;
import com.senico.diagnostic.domain.PsdNarrativeBlock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PsdNarrativeBlockRepository extends JpaRepository<PsdNarrativeBlock, NarrativeBlockKey> {
}
