package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.PpmSnapshotIntegrityService;
import com.company.bsmsvc.domain.port.PpmVersionService;
import com.company.bsmsvc.domain.enums.PpmSnapshotStatus;
import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.exception.SubscriptionNotFoundException;
import com.company.bsmsvc.domain.model.PpmSnapshotDiagnostics;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.service.PpmReferenceValidator;
import com.company.bsmsvc.domain.model.PpmPlanVersionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PpmSnapshotIntegrityServiceImpl implements PpmSnapshotIntegrityService {

    private final SubscriptionRepositoryPort subscriptionRepository;
    private final PpmVersionService ppmVersionService;
    private final PpmReferenceValidator referenceValidator;

    @Override
    public PpmSnapshotDiagnostics diagnose(UUID subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new SubscriptionNotFoundException(
                "Subscription not found: " + subscriptionId));

        PpmSnapshotStatus referenceStatus = referenceValidator.check(sub);

        if (referenceStatus == PpmSnapshotStatus.NOT_PPM_BACKED) {
            return new PpmSnapshotDiagnostics(subscriptionId, false,
                PpmSnapshotStatus.NOT_PPM_BACKED, null, null, null, null);
        }

        if (referenceStatus == PpmSnapshotStatus.PARTIAL_REFERENCE) {
            log.warn("Partial PPM reference detected for subscription {}", subscriptionId);
            return new PpmSnapshotDiagnostics(subscriptionId, true,
                PpmSnapshotStatus.PARTIAL_REFERENCE, null, null,
                sub.getPpmPlanVersionId(), null);
        }

        // All 4 fields populated — probe PPM for the latest catalog version
        UUID lockedVersionId = sub.getPpmPlanVersionId();
        PpmPlanVersionResult latest;
        try {
            latest = ppmVersionService.getLatestVersion(sub.getPpmPlanId());
        } catch (PpmIntegrationException ex) {
            log.warn("PPM unavailable while diagnosing subscription {}: {}", subscriptionId, ex.getMessage());
            // Cannot distinguish MISSING_PLAN from network failure at service layer — report UNAVAILABLE
            return new PpmSnapshotDiagnostics(subscriptionId, true,
                PpmSnapshotStatus.PPM_UNAVAILABLE, null, null, lockedVersionId, null);
        }

        UUID latestVersionId = latest.id();
        Integer catalogVersionNo = latest.versionNo();
        boolean grandfathered = !lockedVersionId.equals(latestVersionId);

        return new PpmSnapshotDiagnostics(subscriptionId, true,
            PpmSnapshotStatus.HEALTHY, grandfathered, catalogVersionNo,
            lockedVersionId, latestVersionId);
    }
}
