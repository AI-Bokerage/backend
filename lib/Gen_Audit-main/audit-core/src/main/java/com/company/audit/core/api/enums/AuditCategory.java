package com.company.audit.core.api.enums;

/**
 * The general subject-matter category of an audited event.
 */
public enum AuditCategory {

    /** An authentication attempt or outcome, such as a login or logout. */
    AUTHENTICATION,

    /** An authorization decision, such as a permission grant or denial. */
    AUTHORIZATION,

    /** A creation, update, or deletion of application data. */
    DATA_MUTATION,

    /** An export or bulk read of application data. */
    DATA_EXPORT,

    /** An administrative action performed against the system or its configuration. */
    ADMIN_ACTION,

    /** A security-relevant event, such as a detected anomaly or policy violation. */
    SECURITY_EVENT,

    /** A change to system or application configuration. */
    CONFIGURATION,

    /** An interaction with an external system or integration. */
    INTEGRATION,

    /** An event relevant to data protection or privacy regulation compliance. */
    GDPR
}
