package com.senico.diagnostic.service;

import com.senico.diagnostic.domain.NarrativeBlockKey;
import com.senico.diagnostic.domain.PsdNarrativeBlock;
import com.senico.diagnostic.dto.psd.NarrativeBlockDto;
import com.senico.diagnostic.dto.psd.UpdateNarrativeBlockRequest;
import com.senico.diagnostic.export.PsdConsolidatedAxes;
import com.senico.diagnostic.repository.PsdNarrativeBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PsdNarrativeService {

    private final PsdNarrativeBlockRepository repository;

    @Transactional(readOnly = true)
    public List<NarrativeBlockDto> listAll() {
        Map<NarrativeBlockKey, PsdNarrativeBlock> byKey = repository.findAll().stream()
                .collect(Collectors.toMap(PsdNarrativeBlock::getKey, b -> b));

        return java.util.Arrays.stream(NarrativeBlockKey.values())
                .map(key -> toDto(key, byKey.get(key)))
                .toList();
    }

    @Transactional
    public NarrativeBlockDto update(String key, UpdateNarrativeBlockRequest request, String updatedBy) {
        NarrativeBlockKey blockKey = parseKey(key);
        // Les axes de l'entreprise sont un contenu structure : un JSON illisible ferait disparaitre
        // le cadre strategique des documents sans que personne ne s'en apercoive avant l'export.
        if (blockKey == NarrativeBlockKey.AXES_CONSOLIDES) {
            String error = PsdConsolidatedAxes.validationError(request.content());
            if (error != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
            }
        }
        PsdNarrativeBlock block = repository.findById(blockKey)
                .orElse(PsdNarrativeBlock.builder().key(blockKey).build());
        block.setContent(request.content());
        block.setUpdatedAt(LocalDateTime.now());
        block.setUpdatedBy(updatedBy);
        repository.save(block);
        return toDto(blockKey, block);
    }

    private NarrativeBlockKey parseKey(String key) {
        try {
            return NarrativeBlockKey.valueOf(key);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bloc narratif inconnu : " + key);
        }
    }

    private NarrativeBlockDto toDto(NarrativeBlockKey key, PsdNarrativeBlock block) {
        return NarrativeBlockDto.builder()
                .key(key.name())
                .label(key.getLabel())
                .content(block != null ? block.getContent() : "")
                .updatedAt(block != null ? block.getUpdatedAt() : null)
                .updatedBy(block != null ? block.getUpdatedBy() : null)
                .build();
    }
}
