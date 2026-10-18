package com.example.admsvc.infrastructure.security;

import com.example.admsvc.common.exception.GenAdmConfigException;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.domain.port.TenantIdParam;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Sets the {@code app.tenant_id} Postgres session GUC for the duration of
 * every {@code @Transactional} method in {@code com.example.admsvc.application.impl},
 * so the RLS policies from V2__enable_rls.sql actually filter queries —
 * this is the piece {@code tbr-svc} (a sibling CPMS service) never wired up,
 * leaving its own RLS silently dead.
 *
 * <p>Resolves the tenant ID two ways, in order: (1) a {@code @TenantIdParam}-
 * annotated {@code UUID} argument, if present — used by the in-process
 * tenant-bootstrap path, which has no HTTP request or authenticated
 * principal; (2) the {@link GenAdmPrincipal#tenantId()} of the current
 * {@code SecurityContextHolder} authentication, for every normal
 * request-driven call. If neither is available, throws
 * {@link GenAdmConfigException} before any query runs.
 *
 * <p>Gated on an {@link EntityManager} bean, same as {@code
 * GenAdmAutoConfiguration.JpaRepositoriesConfiguration} (Task 2) — otherwise
 * unconditional component-scan pickup of this class breaks any host app
 * with no persistence configured yet (e.g. gen-adm-demo before Task 7),
 * since the constructor hard-requires an EntityManager. (ponytail: reuses
 * the exact conditional pattern already established in Task 2 rather than
 * inventing a new one.)
 */
@Aspect
@Component
@ConditionalOnBean(EntityManager.class)
public class TenantContextAspect {

    private final EntityManager entityManager;

    public TenantContextAspect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Around("execution(* com.example.admsvc.application.impl..*(..)) && @annotation(org.springframework.transaction.annotation.Transactional)")
    public Object setTenantContext(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID tenantId = resolveTenantId(joinPoint);
        if (tenantId == null) {
            throw new GenAdmConfigException(
                    "No tenant context available for " + joinPoint.getSignature());
        }
        // Postgres' SET is a utility statement and cannot take a bind
        // parameter for its value; set_config() is a regular SQL function
        // (third arg true = transaction-local, same effect as SET LOCAL) and
        // accepts one normally.
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :tenantId, true)")
                .setParameter("tenantId", tenantId.toString())
                .getSingleResult();
        return joinPoint.proceed();
    }

    private UUID resolveTenantId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation instanceof TenantIdParam && args[i] instanceof UUID uuidArg) {
                    return uuidArg;
                }
            }
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof GenAdmPrincipal principal) {
            return principal.tenantId();
        }
        return null;
    }
}
