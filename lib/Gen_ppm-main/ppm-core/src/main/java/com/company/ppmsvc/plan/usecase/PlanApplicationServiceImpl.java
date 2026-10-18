package com.company.ppmsvc.plan.usecase;

import com.company.ppmsvc.plan.usecase.PlanApplicationService;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.model.PlanVersion;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.plan.port.PlanVersionRepositoryPort;
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
public class PlanApplicationServiceImpl implements PlanApplicationService {

    private final PlanRepositoryPort        planRepository;
    private final PlanVersionRepositoryPort planVersionRepository;

    // ── createPlan ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Plan createPlan(UUID actorId, String code, String name, String tagline, String description,
                            PlanVisibility visibility, Integer trialDays, Boolean active, String tier) {

        // BR-1: code must be unique across all active (non-deleted) plans
        if (planRepository.existsByCode(code)) {
            log.warn("plan.code_conflict code={}", code);
            throw new BusinessException(ErrorCode.PLAN_CODE_ALREADY_EXISTS,
                "A plan with code '" + code + "' already exists.");
        }

        // Generate slug from DB sequence — concurrency safe, no in-memory counters
        long seq = planRepository.nextSlugSequenceValue();
        String slug = "PLN-%04d".formatted(seq);

        Instant now          = Instant.now();
        int trialDaysValue   = trialDays != null ? trialDays : 0;   // BR-3
        boolean activeValue  = active    != null ? active    : true; // BR-4

        Plan plan = Plan.builder()
            .id(UUID.randomUUID())
            .code(code)
            .slug(slug)
            .name(name.strip())
            .tagline(tagline != null ? tagline.strip() : null)
            .description(description != null ? description.strip() : null)
            .visibility(visibility)
            .trialDays(trialDaysValue)
            .active(activeValue)
            .tier(tier)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(actorId)   // BR-5
            .updatedBy(actorId)
            .build();

        Plan saved = planRepository.save(plan);
        log.info("Plan created id={} code={} slug={}", saved.getId(), saved.getCode(), saved.getSlug());
        return saved;
    }

    // ── updatePlan ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Plan updatePlan(UUID actorId, UUID planId, String name, String tagline, String description,
                            PlanVisibility visibility, Integer trialDays, Boolean active, String tier) {

        // BR-1: plan must exist and must not be soft-deleted
        Plan existing = planRepository.findById(planId)
            .orElseThrow(() -> {
                log.warn("plan.not_found id={}", planId);
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + planId);
            });

        // BR-3: if name is supplied it must not be blank
        if (name != null && name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Plan name must not be blank when provided.");
        }

        Plan updated = Plan.builder()
            .id(existing.getId())
            .version(existing.getVersion())
            .code(existing.getCode())                                             // BR-4: code immutable
            .slug(existing.getSlug())                                             // BR-5: slug immutable
            .name(name != null ? name.strip() : existing.getName())
            .tagline(tagline != null ? tagline.strip() : existing.getTagline())
            .description(description != null ? description.strip() : existing.getDescription())
            .visibility(visibility != null ? visibility : existing.getVisibility())
            .trialDays(trialDays   != null ? trialDays   : existing.getTrialDays())
            .active(active         != null ? active      : existing.isActive())
            .tier(tier             != null ? tier         : existing.getTier())
            .createdAt(existing.getCreatedAt())
            .createdBy(existing.getCreatedBy())
            .updatedAt(Instant.now())   // BR-6
            .updatedBy(actorId)         // BR-6
            .build();

        Plan saved = planRepository.save(updated);
        log.info("Plan updated id={}", saved.getId());
        return saved;
    }

    // ── getPlan ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Plan getPlan(UUID planId) {
        log.debug("plan.get id={}", planId);
        return planRepository.findById(planId)
            .orElseThrow(() -> {
                log.warn("plan.not_found id={}", planId);
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + planId);
            });
    }

    // ── getPlanBySlug ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Plan getPlanBySlug(String slug) {
        log.debug("plan.get slug={}", slug);
        return planRepository.findBySlug(slug)
            .orElseThrow(() -> {
                log.warn("plan.not_found slug={}", slug);
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "No plan with slug: " + slug);
            });
    }

    // ── listPlans ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<Plan> listPlans(Boolean active, PlanVisibility visibility) {
        log.debug("plan.list active={} visibility={}", active, visibility);
        List<Plan> plans = planRepository.findAll();

        if (active != null) {
            plans = plans.stream()
                .filter(p -> p.isActive() == active)
                .toList();
        }

        if (visibility != null) {
            plans = plans.stream()
                .filter(p -> p.getVisibility() == visibility)
                .toList();
        }

        return plans;
    }

    // ── deletePlan ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deletePlan(UUID actorId, UUID planId) {
        planRepository.softDelete(planId, actorId);
        log.info("Plan soft-deleted id={}", planId);
    }

    // ── getPlanByCode ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PlanWithActiveVersion getPlanByCode(String code) {
        log.debug("plan.get.by_code code={}", code);
        Plan plan = planRepository.findByCode(code)
            .orElseThrow(() -> {
                log.warn("plan.not_found code={}", code);
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "No plan with code: " + code);
            });

        PlanVersion activeVersion = planVersionRepository
            .findByPlanIdOrderByVersionNoDesc(plan.getId())
            .stream()
            .filter(PlanVersion::isActive)
            .findFirst()
            .orElseThrow(() -> {
                log.warn("plan.no_active_version code={} planId={}", code, plan.getId());
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_VERSION_NOT_FOUND,
                    "Plan has no active version: " + code);
            });

        return new PlanWithActiveVersion(plan, activeVersion);
    }

    // ── getDefaultTrialPlan ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PlanWithActiveVersion getDefaultTrialPlan() {
        Plan trialPlan = planRepository.findAll().stream()
            .filter(p -> p.isActive() && "trial".equalsIgnoreCase(p.getTier()))
            .findFirst()
            .orElseThrow(() -> {
                log.warn("default-trial: no active plan with tier=trial found in catalog");
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "No active trial plan found in catalog");
            });

        PlanVersion activeVersion = planVersionRepository
            .findByPlanIdOrderByVersionNoDesc(trialPlan.getId())
            .stream()
            .filter(PlanVersion::isActive)
            .findFirst()
            .orElseThrow(() -> {
                log.warn("default-trial: trial plan {} has no active version", trialPlan.getId());
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_VERSION_NOT_FOUND,
                    "Trial plan has no active version: " + trialPlan.getId());
            });

        log.debug("default-trial: planId={} planCode={} versionId={}",
            trialPlan.getId(), trialPlan.getCode(), activeVersion.getId());
        return new PlanWithActiveVersion(trialPlan, activeVersion);
    }
}
