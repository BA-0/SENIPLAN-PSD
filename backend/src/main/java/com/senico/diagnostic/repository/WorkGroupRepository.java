package com.senico.diagnostic.repository;

import com.senico.diagnostic.domain.WorkGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkGroupRepository extends JpaRepository<WorkGroup, Long> {
}
