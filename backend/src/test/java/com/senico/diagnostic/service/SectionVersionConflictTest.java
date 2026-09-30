package com.senico.diagnostic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.SectionDef;
import com.senico.diagnostic.domain.SectionResponse;
import com.senico.diagnostic.domain.SectionStatus;
import com.senico.diagnostic.domain.SectionType;
import com.senico.diagnostic.domain.User;
import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.exception.SectionVersionConflictException;
import com.senico.diagnostic.repository.GroupSectionStatusRepository;
import com.senico.diagnostic.repository.SectionDefRepository;
import com.senico.diagnostic.repository.SectionResponseRepository;
import com.senico.diagnostic.repository.WorkGroupRepository;
import com.senico.diagnostic.validation.SectionContentValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Une page restee ouverte ne doit pas ecraser, a son enregistrement automatique, ce qui a ete
 * enregistre depuis son ouverture (autre onglet, autre membre de la direction, correction admin).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SectionVersionConflictTest {

    @Mock WorkGroupRepository workGroupRepository;
    @Mock SectionDefRepository sectionDefRepository;
    @Mock GroupSectionStatusRepository groupSectionStatusRepository;
    @Mock SectionResponseRepository sectionResponseRepository;
    @Mock SectionContentValidator contentValidator;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks SectionEngineService service;

    private final WorkGroup group = WorkGroup.builder().id(4L).name("DSI").build();
    private final SectionDef section = SectionDef.builder().id(12).code("S12").type(SectionType.PERFORMANCE_FRAMEWORK).build();

    @BeforeEach
    void setUp() {
        when(workGroupRepository.findById(4L)).thenReturn(Optional.of(group));
        when(sectionDefRepository.findByCode("S12")).thenReturn(Optional.of(section));
        when(groupSectionStatusRepository.findByGroupIdAndSectionId(4L, 12)).thenReturn(Optional.of(
                GroupSectionStatus.builder().group(group).section(section).status(SectionStatus.IN_PROGRESS).build()));
        when(sectionResponseRepository.findByGroupIdAndSectionId(4L, 12)).thenReturn(Optional.of(
                SectionResponse.builder().group(group).section(section).contentJson("{\"axes\":[]}").version(8).build()));
    }

    @Test
    @DisplayName("Une page ouverte sur la version 7 ne peut pas écraser la version 8 enregistrée depuis")
    void refuseUneVersionDepassee() {
        assertThatThrownBy(() -> service.saveDraft(4L, "S12", objectMapper.readTree("{\"axes\":[]}"), 7, new User()))
                .isInstanceOf(SectionVersionConflictException.class);
        verify(sectionResponseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Une page ouverte avant ce contrôle (sans version) doit être rechargée")
    void refuseUnePageSansVersion() {
        assertThatThrownBy(() -> service.saveDraft(4L, "S12", objectMapper.readTree("{\"axes\":[]}"), null, new User()))
                .isInstanceOf(SectionVersionConflictException.class);
        verify(sectionResponseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Correction admin partie d'une version dépassée : refusée aussi")
    void refuseUneCorrectionAdminDepassee() {
        assertThatThrownBy(() -> service.adminUpdateContent(4L, "S12", objectMapper.readTree("{\"axes\":[]}"), 5, new User()))
                .isInstanceOf(SectionVersionConflictException.class);
        verify(sectionResponseRepository, never()).save(any());
    }
}
