package com.example.admsvc.domain.port;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code UUID} method parameter as the tenant ID
 * {@link com.example.admsvc.infrastructure.security.TenantContextAspect}
 * should use directly, instead of resolving one from a
 * {@link GenAdmPrincipal} in {@code SecurityContextHolder}. Used by the
 * in-process tenant-bootstrap path, which runs with no HTTP request or
 * authenticated principal at all.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantIdParam {
}
