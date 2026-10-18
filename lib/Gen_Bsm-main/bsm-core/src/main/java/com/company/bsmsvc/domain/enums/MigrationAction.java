package com.company.bsmsvc.domain.enums;

public enum MigrationAction {
    /** Soft-archive: resource is hidden but recoverable. */
    ARCHIVE,
    /** Deactivate: resource is disabled but data retained. */
    DEACTIVATE,
    /** Freeze: resource is read-only; no modifications allowed. */
    FREEZE,
    /** Deletion is not allowed; operator must resolve manually. */
    DELETE_NOT_ALLOWED
}
