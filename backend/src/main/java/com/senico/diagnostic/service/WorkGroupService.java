package com.senico.diagnostic.service;

import com.senico.diagnostic.domain.*;
import com.senico.diagnostic.dto.group.CreateWorkGroupRequest;
import com.senico.diagnostic.dto.group.ResetPasswordResponse;
import com.senico.diagnostic.dto.group.UpdateWorkGroupRequest;
import com.senico.diagnostic.dto.group.WorkGroupDto;
import com.senico.diagnostic.exception.ResourceNotFoundException;
import com.senico.diagnostic.repository.GroupSectionStatusRepository;
import com.senico.diagnostic.repository.SectionDefRepository;
import com.senico.diagnostic.repository.UserRepository;
import com.senico.diagnostic.repository.WorkGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkGroupService {

    private final WorkGroupRepository workGroupRepository;
    private final UserRepository userRepository;
    private final SectionDefRepository sectionDefRepository;
    private final GroupSectionStatusRepository groupSectionStatusRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGeneratorService passwordGeneratorService;
    private final ProgressService progressService;

    @Transactional(readOnly = true)
    public List<WorkGroupDto> listAll() {
        return workGroupRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public WorkGroupDto getById(Long id) {
        return toDto(findGroupOrThrow(id));
    }

    @Transactional
    public WorkGroupDto create(CreateWorkGroupRequest request) {
        if (userRepository.existsByUsername(request.leaderUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet identifiant de chef de groupe existe deja");
        }

        WorkGroup group = WorkGroup.builder()
                .name(request.name())
                .description(request.description())
                .enabled(true)
                .build();
        group = workGroupRepository.save(group);

        String rawPassword = (request.leaderPassword() != null && !request.leaderPassword().isBlank())
                ? request.leaderPassword()
                : passwordGeneratorService.generate();

        User leader = User.builder()
                .username(request.leaderUsername())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName(request.leaderFullName())
                .role(Role.GROUP_LEADER)
                .group(group)
                .enabled(true)
                .build();
        leader = userRepository.save(leader);

        group.setLeader(leader);
        group = workGroupRepository.save(group);

        initSectionStatuses(group);

        return toDto(group);
    }

    @Transactional
    public WorkGroupDto update(Long id, UpdateWorkGroupRequest request) {
        WorkGroup group = findGroupOrThrow(id);
        group.setName(request.name());
        group.setDescription(request.description());
        if (request.enabled() != null) {
            group.setEnabled(request.enabled());
            if (group.getLeader() != null) {
                group.getLeader().setEnabled(request.enabled());
                userRepository.save(group.getLeader());
            }
        }
        if (request.leaderFullName() != null && !request.leaderFullName().isBlank() && group.getLeader() != null) {
            group.getLeader().setFullName(request.leaderFullName());
            userRepository.save(group.getLeader());
        }
        return toDto(workGroupRepository.save(group));
    }

    @Transactional
    public void setEnabled(Long id, boolean enabled) {
        WorkGroup group = findGroupOrThrow(id);
        group.setEnabled(enabled);
        workGroupRepository.save(group);
        if (group.getLeader() != null) {
            group.getLeader().setEnabled(enabled);
            userRepository.save(group.getLeader());
        }
    }

    @Transactional
    public ResetPasswordResponse resetLeaderPassword(Long id) {
        WorkGroup group = findGroupOrThrow(id);
        if (group.getLeader() == null) {
            throw new ResourceNotFoundException("Ce groupe n'a pas de chef de groupe associe");
        }
        String rawPassword = passwordGeneratorService.generate();
        group.getLeader().setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(group.getLeader());
        return new ResetPasswordResponse(group.getLeader().getUsername(), rawPassword);
    }

    private void initSectionStatuses(WorkGroup group) {
        List<SectionDef> sections = sectionDefRepository.findAll();
        List<GroupSectionStatus> statuses = sections.stream()
                .map(section -> GroupSectionStatus.builder()
                        .group(group)
                        .section(section)
                        .status(SectionStatus.NOT_STARTED)
                        .build())
                .toList();
        groupSectionStatusRepository.saveAll(statuses);
    }

    WorkGroup findGroupOrThrow(Long id) {
        return workGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Groupe introuvable : " + id));
    }

    private WorkGroupDto toDto(WorkGroup group) {
        return WorkGroupDto.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .enabled(group.isEnabled())
                .leaderUserId(group.getLeader() != null ? group.getLeader().getId() : null)
                .leaderUsername(group.getLeader() != null ? group.getLeader().getUsername() : null)
                .leaderFullName(group.getLeader() != null ? group.getLeader().getFullName() : null)
                .createdAt(group.getCreatedAt())
                .completionPercent(progressService.completionPercent(group.getId()))
                .sectionsSubmitted((int) progressService.countByStatus(group.getId(), SectionStatus.SUBMITTED))
                .sectionsValidated((int) progressService.countByStatus(group.getId(), SectionStatus.VALIDATED))
                .lastActivityAt(progressService.lastActivity(group.getId()).orElse(null))
                .build();
    }
}
