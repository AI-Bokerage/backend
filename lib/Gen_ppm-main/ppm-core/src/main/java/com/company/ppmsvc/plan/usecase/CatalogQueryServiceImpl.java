package com.company.ppmsvc.plan.usecase;

import com.company.ppmsvc.plan.model.CatalogEntitlementResponse;
import com.company.ppmsvc.plan.model.CatalogModuleResponse;
import com.company.ppmsvc.plan.model.CatalogPlanDetailResponse;
import com.company.ppmsvc.plan.model.CatalogPlanSummaryResponse;
import com.company.ppmsvc.plan.model.CatalogVersionResponse;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.entitlement.model.Entitlement;
import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.planentitlement.model.PlanEntitlement;
import com.company.ppmsvc.planmodule.model.PlanModule;
import com.company.ppmsvc.plan.model.PlanVersion;
import com.company.ppmsvc.entitlement.port.EntitlementRepositoryPort;
import com.company.ppmsvc.module.port.ModuleRepositoryPort;
import com.company.ppmsvc.planentitlement.port.PlanEntitlementRepositoryPort;
import com.company.ppmsvc.planmodule.port.PlanModuleRepositoryPort;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.plan.port.PlanVersionRepositoryPort;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pure read implementation of the Catalog Query Engine (PPM-10).
 *
 * <p>No write operations. No mutations. All methods are read-only transactions.
 *
 * <p>{@link #listPublicPlans()} resolves plans and all version rows in exactly
 * two database queries — the version list is grouped in memory to avoid N+1.
 *
 * <p>{@link #getPlanDetail(String)} issues one query per aggregate type:
 * plan, version list, plan-module join rows, module catalog (full findAll filtered in memory),
 * plan-entitlement join rows, and a batch entitlement load via {@code findAllById}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogQueryServiceImpl implements CatalogQueryService {

    private final PlanRepositoryPort           planRepository;
    private final PlanVersionRepositoryPort    planVersionRepository;
    private final PlanModuleRepositoryPort     planModuleRepository;
    private final ModuleRepositoryPort         moduleRepository;
    private final PlanEntitlementRepositoryPort planEntitlementRepository;
    private final EntitlementRepositoryPort    entitlementRepository;

    // ── listPublicPlans ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<CatalogPlanSummaryResponse> listPublicPlans() {
        List<Plan> publicPlans = planRepository.findAll().stream()
                .filter(Plan::isActive)
                .filter(p -> p.getVisibility() == PlanVisibility.PUBLIC)
                .toList();

        if (publicPlans.isEmpty()) {
            return List.of();
        }

        // findAll() returns rows ordered by planId asc, versionNo desc — one query, no N+1
        Map<UUID, List<PlanVersion>> versionsByPlanId = planVersionRepository.findAll().stream()
                .collect(Collectors.groupingBy(PlanVersion::getPlanId));

        return publicPlans.stream()
                .sorted(Comparator.comparing(Plan::getName))
                .map(plan -> {
                    CatalogVersionResponse latestVersion = latestActiveVersion(
                            versionsByPlanId.getOrDefault(plan.getId(), List.of()));
                    return toSummaryResponse(plan, latestVersion);
                })
                .toList();
    }

    // ── getPlanDetail ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public CatalogPlanDetailResponse getPlanDetail(String slug) {
        Plan plan = planRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PLAN_NOT_FOUND, "Plan not found for slug: " + slug));

        UUID planId = plan.getId();

        CatalogVersionResponse latestVersion = planVersionRepository
                .findByPlanIdOrderByVersionNoDesc(planId).stream()
                .filter(PlanVersion::isActive)
                .findFirst()
                .map(this::toVersionResponse)
                .orElse(null);

        List<CatalogModuleResponse>      modules      = resolveModules(planId);
        List<CatalogEntitlementResponse> entitlements = resolveEntitlements(planId);

        return toDetailResponse(plan, latestVersion, modules, entitlements);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Picks the latest active version from a pre-grouped, versionNo-desc list.
     * Returns {@code null} when the plan has no active version rows.
     */
    private CatalogVersionResponse latestActiveVersion(List<PlanVersion> versions) {
        return versions.stream()
                .filter(PlanVersion::isActive)
                .findFirst()
                .map(this::toVersionResponse)
                .orElse(null);
    }

    private List<CatalogModuleResponse> resolveModules(UUID planId) {
        List<PlanModule> planModules = planModuleRepository.findByPlanId(planId);
        if (planModules.isEmpty()) {
            return List.of();
        }
        Set<UUID> moduleIds = planModules.stream()
                .map(PlanModule::getModuleId)
                .collect(Collectors.toSet());
        // findAll() returns the small module catalog; filter in memory (no N+1)
        Map<UUID, Module> moduleMap = moduleRepository.findAll().stream()
                .filter(m -> moduleIds.contains(m.getId()))
                .collect(Collectors.toMap(Module::getId, m -> m));
        return planModules.stream()
                .map(pm -> moduleMap.get(pm.getModuleId()))
                .filter(Objects::nonNull)
                .map(this::toModuleResponse)
                .toList();
    }

    private List<CatalogEntitlementResponse> resolveEntitlements(UUID planId) {
        List<PlanEntitlement> planEntitlements = planEntitlementRepository.findByPlanId(planId);
        if (planEntitlements.isEmpty()) {
            return List.of();
        }
        Set<UUID> entitlementIds = planEntitlements.stream()
                .map(PlanEntitlement::getEntitlementId)
                .collect(Collectors.toSet());
        Map<UUID, Entitlement> entitlementMap = entitlementRepository.findAllById(entitlementIds).stream()
                .collect(Collectors.toMap(Entitlement::getId, e -> e));
        return planEntitlements.stream()
                .map(pe -> {
                    Entitlement e = entitlementMap.get(pe.getEntitlementId());
                    return e != null ? toEntitlementResponse(e, pe.getValue()) : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // ── mapping ───────────────────────────────────────────────────────────────

    private CatalogVersionResponse toVersionResponse(PlanVersion v) {
        return new CatalogVersionResponse(
                v.getId(), v.getVersionNo(), v.getEffectiveFrom(), v.getEffectiveTo(), v.isActive());
    }

    private CatalogModuleResponse toModuleResponse(Module m) {
        return new CatalogModuleResponse(m.getId(), m.getCode(), m.getName(), m.getDescription());
    }

    private CatalogEntitlementResponse toEntitlementResponse(Entitlement e, String value) {
        return new CatalogEntitlementResponse(
                e.getId(), e.getCode(), e.getName(), e.getDescription(), e.getType(), value);
    }

    private CatalogPlanSummaryResponse toSummaryResponse(Plan plan, CatalogVersionResponse latestVersion) {
        return new CatalogPlanSummaryResponse(
                plan.getId(), plan.getCode(), plan.getSlug(), plan.getName(), plan.getTagline(),
                plan.getVisibility(), plan.getTrialDays(), plan.isActive(), latestVersion);
    }

    private CatalogPlanDetailResponse toDetailResponse(Plan plan,
                                                        CatalogVersionResponse latestVersion,
                                                        List<CatalogModuleResponse> modules,
                                                        List<CatalogEntitlementResponse> entitlements) {
        return new CatalogPlanDetailResponse(
                plan.getId(), plan.getCode(), plan.getSlug(), plan.getName(), plan.getTagline(),
                plan.getDescription(), plan.getVisibility(), plan.getTrialDays(), plan.isActive(),
                latestVersion, modules, entitlements);
    }
}
