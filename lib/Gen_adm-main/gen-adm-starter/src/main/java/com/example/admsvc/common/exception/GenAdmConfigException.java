package com.example.admsvc.common.exception;

/**
 * Thrown when a Gen_ADM call has no resolvable tenant context — e.g. no
 * {@link com.example.admsvc.domain.port.GenAdmPrincipal} in
 * {@code SecurityContextHolder} and no {@code @TenantIdParam}-annotated
 * argument. Fails loud rather than letting RLS silently return zero rows
 * for what would look like a "not found" instead of a misconfigured caller.
 */
public class GenAdmConfigException extends GenAdmException {

    public GenAdmConfigException(String message) {
        super("CONFIG_ERROR", message);
    }
}
