package com.senico.diagnostic.repository;

import com.senico.diagnostic.domain.SectionDef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SectionDefRepository extends JpaRepository<SectionDef, Integer> {
    List<SectionDef> findAllByOrderByOrderAsc();
    Optional<SectionDef> findByCode(String code);
}
