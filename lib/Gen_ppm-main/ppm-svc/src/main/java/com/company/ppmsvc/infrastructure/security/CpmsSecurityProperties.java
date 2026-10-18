package com.company.ppmsvc.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for CPMS downstream service JWT validation.
 *
 * <pre>
 * cpms:
 *   security:
 *     jwks-uri:  http://localhost:8101/.well-known/jwks.json
 *     issuer:    cpms-auth-svc
 *     internal-token: ${INTERNAL_TOKEN:change-me-in-production}
 * </pre>
 */
@ConfigurationProperties(prefix = "cpms.security")
public class CpmsSecurityProperties {

    /** JWKS endpoint of auth-svc — used to fetch public keys for RS256 validation. */
    private String jwksUri = "http://localhost:8101/.well-known/jwks.json";

    /** Expected {@code iss} claim value — tokens with a different issuer are rejected. */
    private String issuer = "cpms-auth-svc";

    /**
     * Pre-shared secret for X-Internal-Token service-to-service calls.
     * Must be the same value across all services in the same environment.
     */
    private String internalToken = "change-me-in-production";

    public String getJwksUri()       { return jwksUri; }
    public void setJwksUri(String v) { this.jwksUri = v; }

    public String getIssuer()        { return issuer; }
    public void setIssuer(String v)  { this.issuer = v; }

    public String getInternalToken()        { return internalToken; }
    public void setInternalToken(String v)  { this.internalToken = v; }
}
