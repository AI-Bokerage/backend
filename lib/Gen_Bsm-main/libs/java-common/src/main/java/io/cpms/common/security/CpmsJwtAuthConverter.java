package io.cpms.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

/**
 * Converts a validated {@link Jwt} into a {@link UsernamePasswordAuthenticationToken}
 * whose principal is {@link CpmsAuthenticatedPrincipal}.
 *
 * <p>Only identity claims are extracted from the JWT. Permission resolution
 * happens at request time via {@link RolePermissionResolver} — Redis is the
 * runtime source of truth for permissions, not the token.
 */
public class CpmsJwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        CpmsAuthenticatedPrincipal principal = extractPrincipal(jwt);
        return new UsernamePasswordAuthenticationToken(
                principal,
                jwt.getTokenValue(),
                List.of(new SimpleGrantedAuthority("ROLE_" + (principal.userType() != null
                        ? principal.userType().name()
                        : "TENANT_USER")))
        );
    }

    private CpmsAuthenticatedPrincipal extractPrincipal(Jwt jwt) {
        UUID   userId     = parseUuid(jwt.getSubject());
        UUID   tenantId   = parseUuid(jwt.getClaimAsString("tenant_id"));
        String tenantSlug = jwt.getClaimAsString("tenant_slug");
        UUID   roleId     = parseUuid(jwt.getClaimAsString("role_id"));
        String sessionId  = jwt.getClaimAsString("session_id");
        String jti        = jwt.getId();
        String username   = jwt.getClaimAsString("username");
        String userEmail  = jwt.getClaimAsString("user_email");
        String tenantName = jwt.getClaimAsString("tenant_name");

        CpmsUserType userType = null;
        String rawType = jwt.getClaimAsString("user_type");
        if (rawType != null && !rawType.isBlank()) {
            try { userType = CpmsUserType.valueOf(rawType); } catch (IllegalArgumentException ignored) {}
        }

        return new CpmsAuthenticatedPrincipal(
                userId, tenantId, tenantSlug, roleId,
                userType, sessionId, jti,
                jwt.getExpiresAt(),
                username, userEmail, tenantName
        );
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException e) { return null; }
    }
}
