package com.company.audit.core.api.enums;

/**
 * The general category of actor that initiated an audited action.
 *
 * <p>This vocabulary is intentionally generic. Consumers with more specific actor taxonomies
 * (for example, distinguishing a super-administrator from an integration like a payment
 * processor) are expected to map their specific actor kinds onto these general categories.
 */
public enum ActorType {

    /** A human end user acting directly. */
    HUMAN,

    /** An internal service or automated process acting on its own behalf. */
    SERVICE,

    /** The system itself, acting without an identifiable external initiator. */
    SYSTEM,

    /** An external third party or integration. */
    EXTERNAL
}
