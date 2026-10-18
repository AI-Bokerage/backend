package com.company.bsmsvc.domain.enums;

public enum RefundStatus {
    /** RefundRequest created; provider call not yet attempted. */
    PENDING,
    /** Provider refund succeeded; local accounting (credit note, ledger) not yet done. */
    PROVIDER_REFUND_SUCCEEDED,
    /** Provider refund + all local accounting complete. */
    COMPLETED,
    /** Provider refund failed; nothing was charged back. */
    FAILED,
    /** Provider refund succeeded but local accounting failed; recovery scheduler will retry. */
    RECOVERY_REQUIRED
}
