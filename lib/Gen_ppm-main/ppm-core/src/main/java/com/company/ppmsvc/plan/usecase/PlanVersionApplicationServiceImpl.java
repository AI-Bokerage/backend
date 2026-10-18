package com.company.ppmsvc.plan.usecase;

import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.model.PlanVersion;
import com.company.ppmsvc.plan.model.PlanVersionLimitsResponse;
import com.company.ppmsvc.plan.model.PlanVersionMetaResponse;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.plan.port.PlanVersionRepositoryPort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanVersionApplicationServiceImpl implements PlanVersionApplicationService {

    private final PlanVersionRepositoryPort planVersionRepository;
    private final PlanRepositoryPort        planRepository;

    // ── createVersion ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PlanVersion createVersion(UUID actorId, UUID planId, Integer versionNo, LocalDate effectiveFrom,
            Boolean active, Integer maxInternalUsers, Integer maxClientUsers, Integer maxActiveProjects,
            Long storageQuotaBytes, Boolean customDomainEnabled, Boolean ssoEnabled, Boolean prioritySupport) {

        // BR-1: referenced plan must exist
        planRepository.findById(planId)
            .orElseThrow(() -> {
                log.warn("plan.not_found id={}", planId);
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + planId);
            });

        // BR-2: version number must be unique per plan among active rows
        if (planVersionRepository.existsByPlanIdAndVersionNo(planId, versionNo)) {
            log.warn("plan_version.no_conflict planId={} versionNo={}", planId, versionNo);
            throw new BusinessException(ErrorCode.PLAN_VERSION_ALREADY_EXISTS,
                "Version " + versionNo + " already exists for plan " + planId);
        }

        // Load existing active versions once — used for BR-3, BR-6, BR-7
        List<PlanVersion> existingVersions = planVersionRepository.findByPlanId(planId);

        // BR-3: effectiveFrom must be unique per plan among active rows
        boolean effectiveFromTaken = existingVersions.stream()
            .anyMatch(v -> v.getEffectiveFrom().equals(effectiveFrom));
        if (effectiveFromTaken) {
            log.warn("plan_version.date_conflict planId={} effectiveFrom={}", planId, effectiveFrom);
            throw new BusinessException(ErrorCode.PLAN_VERSION_ALREADY_EXISTS,
                "A version with effectiveFrom=" + effectiveFrom
                    + " already exists for plan " + planId);
        }

        // BR-7: new effectiveFrom must not fall inside any existing closed version's range
        boolean dateConflict = existingVersions.stream()
            .filter(v -> v.getEffectiveTo() != null)
            .anyMatch(v -> !effectiveFrom.isBefore(v.getEffectiveFrom())
                        && !effectiveFrom.isAfter(v.getEffectiveTo()));
        if (dateConflict) {
            log.warn("plan_version.date_range_conflict planId={} effectiveFrom={}", planId, effectiveFrom);
            throw new BusinessException(ErrorCode.PLAN_VERSION_DATE_CONFLICT,
                "effectiveFrom=" + effectiveFrom
                    + " falls inside an existing version's active date range for plan " + planId);
        }

        // BR-6: auto-close the previous open-ended version (effectiveTo = null AND effectiveFrom < new)
        existingVersions.stream()
            .filter(v -> v.getEffectiveTo() == null
                      && v.getEffectiveFrom().isBefore(effectiveFrom))
            .findFirst()
            .ifPresent(prevOpen -> {
                Instant now = Instant.now();
                PlanVersion closed = PlanVersion.builder()
                    .id(prevOpen.getId())
                    .version(prevOpen.getVersion())
                    .planId(prevOpen.getPlanId())
                    .versionNo(prevOpen.getVersionNo())
                    .effectiveFrom(prevOpen.getEffectiveFrom())
                    .effectiveTo(effectiveFrom.minusDays(1))
                    .active(prevOpen.isActive())
                    .createdAt(prevOpen.getCreatedAt())
                    .createdBy(prevOpen.getCreatedBy())
                    .updatedAt(now)
                    .updatedBy(actorId)
                    .build();
                planVersionRepository.save(closed);
                log.info("Plan version auto-closed id={} effectiveTo={}", prevOpen.getId(),
                    effectiveFrom.minusDays(1));
            });

        // BR-8: active defaults to true
        boolean resolvedActive = active != null ? active : true;

        Instant now = Instant.now();
        PlanVersion newVersion = PlanVersion.builder()
            .id(UUID.randomUUID())
            // version null → new entity → Spring Data calls persist() not merge()
            .planId(planId)
            .versionNo(versionNo)
            .effectiveFrom(effectiveFrom)
            .effectiveTo(null)
            .active(resolvedActive)
            .maxInternalUsers(maxInternalUsers)
            .maxClientUsers(maxClientUsers)
            .maxActiveProjects(maxActiveProjects)
            .storageQuotaBytes(storageQuotaBytes)
            .customDomainEnabled(customDomainEnabled)
            .ssoEnabled(ssoEnabled)
            .prioritySupport(prioritySupport)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(actorId)
            .updatedBy(actorId)
            .build();

        PlanVersion saved = planVersionRepository.save(newVersion);
        log.info("Plan version created id={} planId={} versionNo={} effectiveFrom={}",
            saved.getId(), saved.getPlanId(), saved.getVersionNo(), saved.getEffectiveFrom());
        return saved;
    }

    // ── updateVersion ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PlanVersion updateVersion(UUID actorId, UUID versionId, LocalDate effectiveTo, Boolean active,
            Integer maxInternalUsers, Integer maxClientUsers, Integer maxActiveProjects,
            Long storageQuotaBytes, Boolean customDomainEnabled, Boolean ssoEnabled, Boolean prioritySupport) {

        PlanVersion existing = planVersionRepository.findById(versionId)
            .orElseThrow(() -> {
                log.warn("plan_version.not_found id={}", versionId);
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_VERSION_NOT_FOUND, "Plan version not found: " + versionId);
            });

        // Validate effectiveTo is not before effectiveFrom if supplied
        if (effectiveTo != null && effectiveTo.isBefore(existing.getEffectiveFrom())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "effectiveTo must not be before effectiveFrom (" + existing.getEffectiveFrom() + ")");
        }

        PlanVersion updated = PlanVersion.builder()
            .id(existing.getId())
            .version(existing.getVersion())
            // BR-4: immutable identity fields always carried forward unchanged
            .planId(existing.getPlanId())
            .versionNo(existing.getVersionNo())
            .effectiveFrom(existing.getEffectiveFrom())
            // Mutable fields
            .effectiveTo(effectiveTo != null ? effectiveTo : existing.getEffectiveTo())
            .active(active != null ? active : existing.isActive())
            // Limit fields — null in request means "leave existing value unchanged"
            .maxInternalUsers(maxInternalUsers != null ? maxInternalUsers : existing.getMaxInternalUsers())
            .maxClientUsers(maxClientUsers != null ? maxClientUsers : existing.getMaxClientUsers())
            .maxActiveProjects(maxActiveProjects != null ? maxActiveProjects : existing.getMaxActiveProjects())
            .storageQuotaBytes(storageQuotaBytes != null ? storageQuotaBytes : existing.getStorageQuotaBytes())
            .customDomainEnabled(customDomainEnabled != null ? customDomainEnabled : existing.getCustomDomainEnabled())
            .ssoEnabled(ssoEnabled != null ? ssoEnabled : existing.getSsoEnabled())
            .prioritySupport(prioritySupport != null ? prioritySupport : existing.getPrioritySupport())
            // Audit — createdAt/createdBy preserved; updatedAt/updatedBy refreshed
            .createdAt(existing.getCreatedAt())
            .createdBy(existing.getCreatedBy())
            .updatedAt(Instant.now())
            .updatedBy(actorId)
            .build();

        PlanVersion saved = planVersionRepository.save(updated);
        log.info("Plan version updated id={}", saved.getId());
        return saved;
    }

    // ── getVersion ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PlanVersion getVersion(UUID versionId) {
        log.debug("plan_version.get id={}", versionId);
        return planVersionRepository.findById(versionId)
            .orElseThrow(() -> {
                log.warn("plan_version.not_found id={}", versionId);
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_VERSION_NOT_FOUND, "Plan version not found: " + versionId);
            });
    }

    // ── getLatestVersion ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PlanVersion getLatestVersion(UUID planId) {
        log.debug("plan_version.get_latest planId={}", planId);
        // BR-1: plan must exist so we return PLAN_NOT_FOUND vs PLAN_VERSION_NOT_FOUND correctly
        planRepository.findById(planId)
            .orElseThrow(() -> {
                log.warn("plan.not_found id={}", planId);
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + planId);
            });

        return planVersionRepository.findByPlanIdOrderByVersionNoDesc(planId)
            .stream()
            .findFirst()
            .orElseThrow(() -> {
                log.warn("plan_version.not_found planId={}", planId);
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_VERSION_NOT_FOUND,
                    "No active version found for plan: " + planId);
            });
    }

    // ── listVersions ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<PlanVersion> listVersions(UUID planId, Boolean active) {
        log.debug("plan_version.list planId={}", planId);
        List<PlanVersion> all = planId != null
            ? planVersionRepository.findByPlanIdOrderByVersionNoDesc(planId)
            : planVersionRepository.findAll();

        return all.stream()
            .filter(v -> active == null || v.isActive() == active)
            .toList();
    }

    // ── deleteVersion ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteVersion(UUID actorId, UUID versionId) {
        planVersionRepository.softDelete(versionId, actorId);
        log.info("Plan version soft-deleted id={}", versionId);
    }

    // ── getPlanVersionMeta ────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PlanVersionMetaResponse getPlanVersionMeta(UUID versionId) {
        log.debug("plan_version.get_meta id={}", versionId);
        PlanVersion version = planVersionRepository.findById(versionId)
            .orElseThrow(() -> {
                log.warn("plan_version.not_found id={}", versionId);
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_VERSION_NOT_FOUND, "Plan version not found: " + versionId);
            });
        Plan plan = planRepository.findById(version.getPlanId())
            .orElseThrow(() -> {
                log.warn("plan.not_found id={}", version.getPlanId());
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + version.getPlanId());
            });
        return new PlanVersionMetaResponse(
            version.getId(),
            plan.getId(),
            plan.getCode(),
            version.getVersionNo(),
            version.isActive(),
            plan.isActive(),
            version.getEffectiveFrom(),
            version.getEffectiveTo(),
            plan.getTier()
        );
    }

    // ── getPlanVersionLimits ──────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PlanVersionLimitsResponse getPlanVersionLimits(UUID versionId) {
        log.debug("plan_version.get_limits id={}", versionId);
        PlanVersion version = planVersionRepository.findById(versionId)
            .orElseThrow(() -> {
                log.warn("plan_version.not_found id={}", versionId);
                return new ResourceNotFoundException(
                    ErrorCode.PLAN_VERSION_NOT_FOUND, "Plan version not found: " + versionId);
            });
        return new PlanVersionLimitsResponse(
            version.getId(),
            version.getMaxInternalUsers(),
            version.getMaxClientUsers(),
            version.getMaxActiveProjects(),
            version.getStorageQuotaBytes(),
            version.getCustomDomainEnabled(),
            version.getSsoEnabled(),
            version.getPrioritySupport()
        );
    }
}
