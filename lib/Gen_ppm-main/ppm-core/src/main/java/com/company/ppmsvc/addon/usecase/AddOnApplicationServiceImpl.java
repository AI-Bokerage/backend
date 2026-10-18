package com.company.ppmsvc.addon.usecase;

import com.company.ppmsvc.addon.model.AddOn;
import com.company.ppmsvc.addon.model.AddOnType;
import com.company.ppmsvc.addon.port.AddOnRepositoryPort;
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
public class AddOnApplicationServiceImpl implements AddOnApplicationService {

    private final AddOnRepositoryPort addOnRepository;

    // ── createAddOn ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AddOn createAddOn(UUID actorId, String code, String name, String description,
                              AddOnType type, Boolean active) {
        // BR-A2: normalise code — strip whitespace and uppercase
        String normalizedCode = code.strip().toUpperCase();

        // BR-A1: code must be unique across all active add-ons
        if (addOnRepository.existsByCode(normalizedCode)) {
            log.warn("addon.code_conflict code={}", normalizedCode);
            throw new BusinessException(ErrorCode.ADD_ON_CODE_ALREADY_EXISTS,
                "An add-on with code '" + normalizedCode + "' already exists.");
        }

        // BR-A4: active defaults to true when not supplied
        boolean activeValue = active != null ? active : true;

        Instant now = Instant.now();
        AddOn addOn = AddOn.builder()
            .id(UUID.randomUUID())
            .code(normalizedCode)
            .name(name.strip())                                                  // BR-A3: strip name
            .description(description != null ? description.strip() : null)
            .type(type)
            .active(activeValue)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(actorId)
            .updatedBy(actorId)
            .build();

        AddOn saved = addOnRepository.save(addOn);
        log.info("AddOn created id={} code={}", saved.getId(), saved.getCode());
        return saved;
    }

    // ── updateAddOn ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AddOn updateAddOn(UUID actorId, UUID id, String name, String description, Boolean active) {
        AddOn existing = findAddOnOrThrow(id);

        AddOn updated = AddOn.builder()
            .id(existing.getId())
            .version(existing.getVersion())
            .code(existing.getCode())            // BR-A5: code is immutable after creation
            .type(existing.getType())            // BR-A6: type is immutable after creation
            .name(name != null ? name.strip() : existing.getName())
            .description(description != null ? description.strip() : existing.getDescription())
            .active(active != null ? active : existing.isActive())
            .createdAt(existing.getCreatedAt())
            .createdBy(existing.getCreatedBy())
            .updatedAt(Instant.now())            // BR-A7: refresh audit timestamps
            .updatedBy(actorId)
            .build();

        AddOn saved = addOnRepository.save(updated);
        log.info("AddOn updated id={}", saved.getId());
        return saved;
    }

    // ── getAddOn ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AddOn getAddOn(UUID id) {
        log.debug("addon.get id={}", id);
        return findAddOnOrThrow(id);
    }

    // ── getAddOnByCode ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AddOn getAddOnByCode(String code) {
        // Normalize to match stored form (strip + uppercase, same as createAddOn)
        String normalized = code.strip().toUpperCase();
        log.debug("addon.get code={}", normalized);
        return addOnRepository.findByCode(normalized)
            .orElseThrow(() -> {
                log.warn("addon.not_found code={}", normalized);
                return new ResourceNotFoundException(
                    ErrorCode.ADD_ON_NOT_FOUND, "No add-on with code: " + normalized);
            });
    }

    // ── listAddOns ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<AddOn> listAddOns(Boolean active, AddOnType type) {
        log.debug("addon.list active={} type={}", active, type);
        // findAll() returns all non-deleted add-ons ordered by code ascending
        return addOnRepository.findAll().stream()
            .filter(a -> active == null || a.isActive() == active)
            .filter(a -> type   == null || a.getType()   == type)
            .toList();
    }

    // ── deleteAddOn ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteAddOn(UUID actorId, UUID id) {
        // BR-A8: soft-delete only; repository throws ADD_ON_NOT_FOUND if absent
        addOnRepository.softDelete(id, actorId);
        log.info("AddOn soft-deleted id={}", id);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AddOn findAddOnOrThrow(UUID id) {
        return addOnRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("addon.not_found id={}", id);
                return new ResourceNotFoundException(
                    ErrorCode.ADD_ON_NOT_FOUND, "Add-on not found: " + id);
            });
    }
}
