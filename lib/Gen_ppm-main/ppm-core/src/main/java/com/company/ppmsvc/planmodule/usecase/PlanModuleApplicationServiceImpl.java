package com.company.ppmsvc.planmodule.usecase;

import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.module.port.ModuleRepositoryPort;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.planmodule.model.PlanModule;
import com.company.ppmsvc.planmodule.port.PlanModuleRepositoryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanModuleApplicationServiceImpl implements PlanModuleApplicationService {

    private final PlanRepositoryPort       planRepository;
    private final ModuleRepositoryPort     moduleRepository;
    private final PlanModuleRepositoryPort planModuleRepository;

    // ── assignModules ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public List<Module> assignModules(UUID actorId, UUID planId, Set<UUID> moduleIds) {
        // BR-1: plan must exist
        verifyPlanExists(planId);

        Instant now     = Instant.now();
        List<PlanModule> toSave  = new ArrayList<>();
        List<Module>     modules = new ArrayList<>();

        for (UUID moduleId : moduleIds) {
            // BR-2: module must exist
            Module module = findModuleOrThrow(moduleId);
            // BR-3: duplicate assignment rejected
            if (planModuleRepository.exists(planId, moduleId)) {
                log.warn("plan_module.duplicate planId={} moduleId={}", planId, moduleId);
                throw new BusinessException(ErrorCode.MODULE_ALREADY_ASSIGNED_TO_PLAN,
                    "Module " + module.getCode().getValue() + " is already assigned to plan " + planId);
            }
            toSave.add(PlanModule.builder()
                .id(UUID.randomUUID())
                .planId(planId)
                .moduleId(moduleId)
                .createdAt(now)
                .createdBy(actorId)
                .build());
            modules.add(module);
        }

        planModuleRepository.saveAll(toSave);
        log.info("Assigned {} module(s) to plan {}", toSave.size(), planId);

        return modules;
    }

    // ── getModules ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<Module> getModules(UUID planId) {
        log.debug("plan_module.get planId={}", planId);
        // BR-6: return only currently assigned modules; also validates plan existence
        verifyPlanExists(planId);

        return planModuleRepository.findByPlanId(planId).stream()
            .map(mapping -> findModuleOrThrow(mapping.getModuleId()))
            .toList();
    }

    // ── replaceModules ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public List<Module> replaceModules(UUID actorId, UUID planId, Set<UUID> moduleIds) {
        // BR-1: plan must exist
        verifyPlanExists(planId);

        // BR-2: validate all modules before touching the DB
        List<Module> modules = new ArrayList<>();
        for (UUID moduleId : moduleIds) {
            modules.add(findModuleOrThrow(moduleId));
        }

        // BR-5: atomic replace — delete then insert in the same transaction
        planModuleRepository.deleteAllByPlanId(planId);

        Instant now     = Instant.now();
        List<PlanModule> newMappings = new ArrayList<>();
        for (Module module : modules) {
            newMappings.add(PlanModule.builder()
                .id(UUID.randomUUID())
                .planId(planId)
                .moduleId(module.getId())
                .createdAt(now)
                .createdBy(actorId)
                .build());
        }
        planModuleRepository.saveAll(newMappings);
        log.info("Replaced module assignments for plan {} with {} module(s)", planId, newMappings.size());

        return modules;
    }

    // ── removeModule ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void removeModule(UUID planId, UUID moduleId) {
        // BR-1: plan must exist
        verifyPlanExists(planId);

        // Verify the mapping exists before deleting (BR-7 guard)
        if (!planModuleRepository.exists(planId, moduleId)) {
            log.warn("plan_module.mapping_not_found planId={} moduleId={}", planId, moduleId);
            throw new ResourceNotFoundException(ErrorCode.PLAN_MODULE_MAPPING_NOT_FOUND,
                "No mapping found for plan " + planId + " and module " + moduleId);
        }

        // BR-7: only the mapping row is deleted — module itself is untouched
        planModuleRepository.delete(planId, moduleId);
        log.info("Removed module {} from plan {}", moduleId, planId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void verifyPlanExists(UUID planId) {
        planRepository.findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + planId));
    }

    private Module findModuleOrThrow(UUID moduleId) {
        return moduleRepository.findById(moduleId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.MODULE_NOT_FOUND, "Module not found: " + moduleId));
    }
}
