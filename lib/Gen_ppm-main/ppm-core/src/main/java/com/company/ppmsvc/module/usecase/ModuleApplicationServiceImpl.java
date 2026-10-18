package com.company.ppmsvc.module.usecase;

import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.module.model.ModuleCode;
import com.company.ppmsvc.module.port.ModuleRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleApplicationServiceImpl implements ModuleApplicationService {

    private final ModuleRepositoryPort moduleRepository;

    // ── createModule ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Module createModule(UUID actorId, ModuleCode code, String name, String description, Boolean active) {
        // BR-1: code must be unique across all active (non-deleted) modules
        if (moduleRepository.existsByCode(code)) {
            log.warn("module.code_conflict code={}", code.getValue());
            throw new BusinessException(ErrorCode.MODULE_CODE_ALREADY_EXISTS,
                "A module with code '" + code.getValue() + "' already exists.");
        }

        Instant now = Instant.now();
        // BR-2: active defaults to true when not supplied
        boolean activeValue = active != null ? active : true;

        Module module = Module.builder()
            .id(UUID.randomUUID())
            .code(code)
            .name(name.strip())
            .description(description != null ? description.strip() : null)
            .active(activeValue)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(actorId)  // BR-3
            .updatedBy(actorId)
            .build();

        Module saved = moduleRepository.save(module);
        log.info("Module created id={} code={}", saved.getId(), saved.getCode().getValue());
        return saved;
    }

    // ── updateModule ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Module updateModule(UUID actorId, UUID moduleId, String name, String description, Boolean active) {
        // BR-1: module must exist and must not be soft-deleted (@SQLRestriction handles the latter)
        Module existing = moduleRepository.findById(moduleId)
            .orElseThrow(() -> {
                log.warn("module.not_found id={}", moduleId);
                return new ResourceNotFoundException(
                    ErrorCode.MODULE_NOT_FOUND, "Module not found: " + moduleId);
            });

        // BR-3: if name is supplied it must not be blank
        if (name != null && name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Module name must not be blank when provided.");
        }

        Module updated = Module.builder()
            .id(existing.getId())
            .version(existing.getVersion())
            .code(existing.getCode())                                                     // BR-4: code is immutable
            .name(name != null ? name.strip() : existing.getName())                       // BR-2: null = keep
            .description(description != null ? description.strip() : existing.getDescription())
            .active(active != null ? active : existing.isActive())
            .createdAt(existing.getCreatedAt())
            .createdBy(existing.getCreatedBy())
            .updatedAt(Instant.now())   // BR-5
            .updatedBy(actorId)         // BR-5
            .build();

        Module saved = moduleRepository.save(updated);
        log.info("Module updated id={}", saved.getId());
        return saved;
    }

    // ── getModule ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Module getModule(UUID moduleId) {
        log.debug("module.get id={}", moduleId);
        return moduleRepository.findById(moduleId)
            .orElseThrow(() -> {
                log.warn("module.not_found id={}", moduleId);
                return new ResourceNotFoundException(
                    ErrorCode.MODULE_NOT_FOUND, "Module not found: " + moduleId);
            });
    }

    // ── getModuleByCode ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Module getModuleByCode(ModuleCode code) {
        log.debug("module.get code={}", code.getValue());
        return moduleRepository.findByCode(code)
            .orElseThrow(() -> {
                log.warn("module.not_found code={}", code.getValue());
                return new ResourceNotFoundException(
                    ErrorCode.MODULE_NOT_FOUND, "No module with code: " + code.getValue());
            });
    }

    // ── deleteModule ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteModule(UUID actorId, UUID moduleId) {
        moduleRepository.softDelete(moduleId, actorId);
        log.info("Module soft-deleted id={}", moduleId);
    }

    // ── listModules ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<Module> listModules(Boolean active) {
        log.debug("module.list active={}", active);
        List<Module> modules = moduleRepository.findAll();

        if (active != null) {
            modules = modules.stream()
                .filter(m -> m.isActive() == active)
                .toList();
        }

        return modules;
    }
}
