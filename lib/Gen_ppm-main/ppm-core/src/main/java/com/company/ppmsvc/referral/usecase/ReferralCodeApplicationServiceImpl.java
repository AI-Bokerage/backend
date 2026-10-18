package com.company.ppmsvc.referral.usecase;

import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.promotion.model.ReferralCodeStatus;
import com.company.ppmsvc.referral.model.ReferralCode;
import com.company.ppmsvc.referral.port.ReferralCodeRepositoryPort;
import com.company.ppmsvc.referral.port.ReferralProgramRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralCodeApplicationServiceImpl implements ReferralCodeApplicationService {

    private final ReferralCodeRepositoryPort    codeRepository;
    private final ReferralProgramRepositoryPort programRepository;

    // ── createCode ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ReferralCode createCode(UUID actorId, String code, UUID referralProgramId, String referrerCustomerId,
                                   ReferralCodeStatus status) {
        String normalizedCode = code.strip().toUpperCase();

        if (codeRepository.existsByCode(normalizedCode)) {
            log.warn("referral_code.conflict code={}", normalizedCode);
            throw new BusinessException(ErrorCode.REFERRAL_CODE_ALREADY_EXISTS,
                "A referral code with code '" + normalizedCode + "' already exists.");
        }

        programRepository.findById(referralProgramId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.REFERRAL_PROGRAM_NOT_FOUND, "Referral program not found: " + referralProgramId));

        if (codeRepository.existsByProgramAndReferrer(referralProgramId, referrerCustomerId)) {
            log.warn("referral_code.duplicate_referrer programId={} referrer={}", referralProgramId, referrerCustomerId);
            throw new BusinessException(ErrorCode.ILLEGAL_ARGUMENT,
                "A referral code already exists for this program and referrer.");
        }

        ReferralCodeStatus effectiveStatus = status != null ? status : ReferralCodeStatus.ACTIVE;

        Instant now = Instant.now();
        ReferralCode referralCode = ReferralCode.builder()
            .id(UUID.randomUUID())
            .code(normalizedCode)
            .referralProgramId(referralProgramId)
            .referrerCustomerId(referrerCustomerId)
            .status(effectiveStatus)
            .createdAt(now).updatedAt(now)
            .createdBy(actorId).updatedBy(actorId)
            .build();

        ReferralCode saved = codeRepository.save(referralCode);
        log.info("Referral code created id={} code={}", saved.getId(), saved.getCode());
        return saved;
    }

    // ── updateCode ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ReferralCode updateCode(UUID actorId, UUID id, ReferralCodeStatus status) {
        ReferralCode existing = codeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.REFERRAL_CODE_NOT_FOUND, "Referral code not found: " + id));

        ReferralCode updated = ReferralCode.builder()
            .id(existing.getId())
            .version(existing.getVersion())
            .code(existing.getCode())
            .referralProgramId(existing.getReferralProgramId())
            .referrerCustomerId(existing.getReferrerCustomerId())
            .status(status != null ? status : existing.getStatus())
            .createdAt(existing.getCreatedAt())
            .createdBy(existing.getCreatedBy())
            .updatedAt(Instant.now())
            .updatedBy(actorId)
            .build();

        ReferralCode saved = codeRepository.save(updated);
        log.info("Referral code updated id={}", saved.getId());
        return saved;
    }

    // ── getCode ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ReferralCode getCode(UUID id) {
        log.debug("referral_code.get id={}", id);
        return codeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.REFERRAL_CODE_NOT_FOUND, "Referral code not found: " + id));
    }

    // ── getCodeByCode ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<ReferralCode> getCodeByCode(String code) {
        return codeRepository.findByCode(code);
    }

    // ── listCodes ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ReferralCode> listCodes() {
        log.debug("referral_code.list");
        return codeRepository.findAll();
    }

    // ── deleteCode ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteCode(UUID actorId, UUID id) {
        codeRepository.softDelete(id, actorId);
        log.info("Referral code soft-deleted id={}", id);
    }
}
