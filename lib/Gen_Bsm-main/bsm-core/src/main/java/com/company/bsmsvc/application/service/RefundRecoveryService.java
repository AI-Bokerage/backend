package com.company.bsmsvc.application.service;

import java.util.UUID;

public interface RefundRecoveryService {
    /** Process all RECOVERY_REQUIRED refund requests. Idempotent. */
    void recoverAll();

    /** Recover a single refund request by ID. Idempotent. */
    void recover(UUID refundRequestId);
}
