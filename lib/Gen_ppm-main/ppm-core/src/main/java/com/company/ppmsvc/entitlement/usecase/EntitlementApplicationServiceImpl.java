package com.company.ppmsvc.entitlement.usecase;

import com.company.ppmsvc.entitlement.model.Entitlement;
import com.company.ppmsvc.entitlement.model.EntitlementType;
import com.company.ppmsvc.entitlement.port.EntitlementRepositoryPort;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
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
public class EntitlementApplicationServiceImpl implements EntitlementApplicationService {

    private final EntitlementRepositoryPort entitlementRepository;

    // ── createEntitlement ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public Entitlement createEntitlement(UUID actorId, String code, String name, String description,
                                          EntitlementType type, Boolean active) {
        String normalizedCode = code.strip();  // BR-E3

        // BR-E1: code must be unique across all active (non-deleted) entitlements
        if (entitlementRepository.existsByCode(normalizedCode)) {
            log.warn("entitlement.code_conflict code={}", normalizedCode);
            throw new BusinessException(ErrorCode.ENTITLEMENT_CODE_ALREADY_EXISTS,
                "An entitlement with code '" + normalizedCode + "' already exists.");
        }

        Instant now = Instant.now();
        boolean activeValue = active != null ? active : true;  // BR-E5

        Entitlement entitlement = Entitlement.builder()
            .id(UUID.randomUUID())
            .code(normalizedCode)
            .name(name.strip())  // BR-E4
            .description(description != null ? description.strip() : null)
            .type(type)
            .active(activeValue)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(actorId)   // BR-E7
            .updatedBy(actorId)
            .build();

        Entitlement saved = entitlementRepository.save(entitlement);
        log.info("Entitlement created id={} code={}", saved.getId(), saved.getCode());
        return saved;
    }

    // ── updateEntitlement ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public Entitlement updateEntitlement(UUID actorId, UUID entitlementId, String name, String description,
                                          Boolean active) {
        // BR-E6: soft-deleted entitlements are invisible
        Entitlement existing = entitlementRepository.findById(entitlementId)
            .orElseThrow(() -> {
                log.warn("entitlement.not_found id={}", entitlementId);
                return new ResourceNotFoundException(
                    ErrorCode.ENTITLEMENT_NOT_FOUND, "Entitlement not found: " + entitlementId);
            });

        // BR-E4: if name is supplied it must not be blank
        if (name != null && name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Entitlement name must not be blank when provided.");
        }

        Entitlement updated = Entitlement.builder()
            .id(existing.getId())
            .version(existing.getVersion())
            .code(existing.getCode())   // BR-E2: code is immutable
            .name(name != null ? name.strip() : existing.getName())
            .description(description != null ? description.strip() : existing.getDescription())
            .type(existing.getType())
            .active(active != null ? active : existing.isActive())
            .createdAt(existing.getCreatedAt())
            .createdBy(existing.getCreatedBy())  // BR-E7: createdBy preserved
            .updatedAt(Instant.now())
            .updatedBy(actorId)                  // BR-E7: updatedBy refreshed
            .build();

        Entitlement saved = entitlementRepository.save(updated);
        log.info("Entitlement updated id={}", saved.getId());
        return saved;
    }

    // ── getEntitlement ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Entitlement getEntitlement(UUID entitlementId) {
        log.debug("entitlement.get id={}", entitlementId);
        return entitlementRepository.findById(entitlementId)
            .orElseThrow(() -> {
                log.warn("entitlement.not_found id={}", entitlementId);
                return new ResourceNotFoundException(
                    ErrorCode.ENTITLEMENT_NOT_FOUND, "Entitlement not found: " + entitlementId);
            });
    }

    // ── getEntitlementByCode ──────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Entitlement getEntitlementByCode(String code) {
        log.debug("entitlement.get code={}", code);
        return entitlementRepository.findByCode(code)
            .orElseThrow(() -> {
                log.warn("entitlement.not_found code={}", code);
                return new ResourceNotFoundException(
                    ErrorCode.ENTITLEMENT_NOT_FOUND, "No entitlement with code: " + code);
            });
    }

    // ── listEntitlements ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<Entitlement> listEntitlements(Boolean active, EntitlementType type) {
        log.debug("entitlement.list active={} type={}", active, type);
        return entitlementRepository.findAll().stream()
            .filter(e -> active == null || e.isActive() == active)
            .filter(e -> type   == null || e.getType()   == type)
            .toList();
    }

    // ── deleteEntitlement ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteEntitlement(UUID actorId, UUID entitlementId) {
        entitlementRepository.softDelete(entitlementId, actorId);
        log.info("Entitlement soft-deleted id={}", entitlementId);
    }
}
