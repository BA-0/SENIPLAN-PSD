package com.senico.diagnostic.repository;

import com.senico.diagnostic.domain.SynthesisNoteVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

public interface SynthesisNoteVersionRepository extends JpaRepository<SynthesisNoteVersion, String> {

    @Modifying
    @Query("delete from SynthesisNoteVersion v where v.createdAt < :before")
    int deleteOlderThan(LocalDateTime before);
}
