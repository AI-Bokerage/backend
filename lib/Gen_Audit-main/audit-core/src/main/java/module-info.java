/**
 * A framework-agnostic, tamper-evident, hash-chained audit ledger.
 */
module com.company.audit.core {
    requires com.fasterxml.jackson.databind;

    exports com.company.audit.core.api;
    exports com.company.audit.core.api.enums;
    exports com.company.audit.core.api.exception;
    exports com.company.audit.core.port;
    // internal and internal.crypto are NOT exported — compiler-enforced, not just convention
}
