package io.cpms.common.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * AOP aspect enforcing {@link RequirePermission} on controller methods.
 *
 * <h3>Runtime permission resolution</h3>
 * Calls {@link RolePermissionResolver#resolve(java.util.UUID)} with the
 * {@code role_id} from the JWT. The resolver reads from Redis key
 * {@code role:{roleId}} — no DB lookup, no hash decoding.
 *
 * <h3>Enforcement order</h3>
 * <ol>
 *   <li>{@link RequirePermission#requireSuperAdmin()} — only {@code SUPER_ADMIN} passes</li>
 *   <li>PROSPECT — 403 unless opted in</li>
 *   <li>CLIENT — 403 unless opted in</li>
 *   <li>SUPER_ADMIN — bypasses permission check</li>
 *   <li>SUPER_ADMIN_IMPERSONATING — full permission check; audit logged</li>
 *   <li>TENANT_USER / CLIENT — Redis lookup → code membership check</li>
 * </ol>
 */
@Aspect
@Component
public class PermissionCheckAspect {

    private static final Logger log = LoggerFactory.getLogger(PermissionCheckAspect.class);

    private final RolePermissionResolver rolePermissionResolver;

    public PermissionCheckAspect(RolePermissionResolver rolePermissionResolver) {
        this.rolePermissionResolver = rolePermissionResolver;
    }

    @Around("@annotation(io.cpms.common.security.RequirePermission)")
    public Object checkPermission(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature   sig        = (MethodSignature) pjp.getSignature();
        Method            method     = sig.getMethod();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        if (!(auth.getPrincipal() instanceof CpmsAuthenticatedPrincipal principal)) {
            throw new AccessDeniedException("Invalid principal type");
        }

        CpmsUserType userType = principal.userType();

        // ── requireSuperAdmin — only pure SUPER_ADMIN; IMPERSONATING explicitly denied ─
        if (annotation.requireSuperAdmin()) {
            if (userType != CpmsUserType.SUPER_ADMIN) {
                log.warn("rbac.superadmin_required userId={} userType={} endpoint={}",
                        principal.userId(), userType, method.getName());
                throw new AccessDeniedException(
                        "SUPER_ADMIN required. Current: " + userType);
            }
            return pjp.proceed();
        }

        // ── PROSPECT guard ────────────────────────────────────────────────────────────
        if (userType == CpmsUserType.PROSPECT && !annotation.allowProspect()) {
            log.warn("rbac.blocked userType=PROSPECT userId={} required={}",
                    principal.userId(), annotation.value());
            throw new AccessDeniedException("PROSPECT users are not permitted here");
        }

        // ── CLIENT guard ──────────────────────────────────────────────────────────────
        if (userType == CpmsUserType.CLIENT && !annotation.allowClient()) {
            log.warn("rbac.blocked userType=CLIENT userId={} required={}",
                    principal.userId(), annotation.value());
            throw new AccessDeniedException("CLIENT users require explicit grant");
        }

        // ── SUPER_ADMIN — full bypass ─────────────────────────────────────────────────
        if (userType == CpmsUserType.SUPER_ADMIN) {
            return pjp.proceed();
        }

        // ── SUPER_ADMIN_IMPERSONATING — permission check still applies; audit logged ──
        if (userType == CpmsUserType.SUPER_ADMIN_IMPERSONATING) {
            log.info("rbac.impersonation.check userId={} tenantId={} required={} method={}",
                    principal.userId(), principal.tenantId(), annotation.value(), method.getName());
        }

        // ── Redis role lookup ─────────────────────────────────────────────────────────
        String required = annotation.value();
        Set<String> permissions = rolePermissionResolver.resolve(principal.roleId());

        if (permissions.isEmpty()) {
            // Resolver returned empty — Redis miss or error, fail closed
            log.warn("rbac.denied userId={} tenantId={} required={} roleId={} reason=empty_permission_set",
                    principal.userId(), principal.tenantId(), required, principal.roleId());
            throw new AccessDeniedException(
                    "Permission set unresolvable for role " + principal.roleId()
                    + " — ensure ADM-SVC has published this role to Redis");
        }

        if (!permissions.contains(required)) {
            log.warn("rbac.denied userId={} tenantId={} required={} roleId={} resolvedCodes={}",
                    principal.userId(), principal.tenantId(), required,
                    principal.roleId(), permissions.size());
            throw new AccessDeniedException("Missing required permission: " + required);
        }

        return pjp.proceed();
    }
}
