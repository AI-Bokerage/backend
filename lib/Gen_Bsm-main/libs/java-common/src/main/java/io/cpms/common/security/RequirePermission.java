package io.cpms.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the authorization requirement for a controller method.
 *
 * <p>Enforced by {@link PermissionCheckAspect}. The enforcement order is:
 * <ol>
 *   <li>If {@link #requireSuperAdmin()} → only SUPER_ADMIN (not IMPERSONATING) may proceed</li>
 *   <li>PROSPECT → 403 unless {@link #allowProspect()}</li>
 *   <li>CLIENT → 403 unless {@link #allowClient()}</li>
 *   <li>SUPER_ADMIN → always passes (no permission check)</li>
 *   <li>SUPER_ADMIN_IMPERSONATING → passes if within impersonated tenant scope</li>
 *   <li>TENANT_USER → permission code resolved from JWT hash and checked</li>
 * </ol>
 *
 * <p>Examples:
 * <pre>
 *   // Standard permission check — any user with tenant.read passes
 *   {@literal @}GetMapping("/{id}")
 *   {@literal @}RequirePermission("tenant.read")
 *   public ResponseEntity<...> getTenant(...) { ... }
 *
 *   // Platform-only operation — SUPER_ADMIN only
 *   {@literal @}PostMapping("/{id}/suspend")
 *   {@literal @}RequirePermission(value = "tenant.write", requireSuperAdmin = true)
 *   public ResponseEntity<...> suspendTenant(...) { ... }
 * </pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** The permission code that must be present in the user's permission set. */
    String value();

    /**
     * When {@code true}, only {@code SUPER_ADMIN} may call this endpoint.
     * {@code SUPER_ADMIN_IMPERSONATING} is also denied — impersonation is
     * read-scoped and must not perform platform lifecycle operations.
     */
    boolean requireSuperAdmin() default false;

    /** When {@code true}, PROSPECT users are not rejected before the permission check. */
    boolean allowProspect() default false;

    /** When {@code true}, CLIENT users are not rejected before the permission check. */
    boolean allowClient() default false;
}
