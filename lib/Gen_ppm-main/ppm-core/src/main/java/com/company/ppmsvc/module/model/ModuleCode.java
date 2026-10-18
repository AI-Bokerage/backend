package com.company.ppmsvc.module.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Canonical identifiers for platform capability modules.
 *
 * <p>Each constant carries a stable {@code value} that is used as both the
 * JSON wire format and the database storage value.  Using a fixed string rather
 * than the enum name means the constant can be renamed without a schema migration
 * or a breaking API change.
 *
 * <p>Persistence: stored as a VARCHAR via
 * {@link com.company.ppmsvc.infrastructure.persistence.converter.ModuleCodeConverter}.
 */
@Getter
@RequiredArgsConstructor
public enum ModuleCode {

    LEAD_MANAGEMENT    ("lead_management"),
    PROJECT_MANAGEMENT ("project_management"),
    CLIENT_PORTAL      ("client_portal"),
    INVOICING          ("invoicing"),
    TIME_TRACKING      ("time_tracking"),
    DOCUMENT_MANAGEMENT("document_management"),
    E_SIGNATURE        ("e_signature"),
    MESSAGING          ("messaging"),
    REPORTING          ("reporting"),
    API_ACCESS         ("api_access"),
    SSO                ("sso"),
    AUDIT_LOG_EXPORT   ("audit_log_export"),
    WHITE_LABEL        ("white_label"),
    CUSTOM_ROLES       ("custom_roles");

    /** Stable wire / DB value — never changes even if the constant is renamed. */
    @JsonValue
    private final String value;

    @JsonCreator
    public static ModuleCode fromValue(String value) {
        for (ModuleCode code : values()) {
            if (code.value.equals(value)) return code;
        }
        throw new IllegalArgumentException("Unknown ModuleCode value: " + value);
    }
}
