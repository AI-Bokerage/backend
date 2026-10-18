package com.example.authsvc.infrastructure.security.jwt.util;

import com.example.authsvc.common.exception.JwtSigningException;
import com.example.authsvc.config.properties.JwtProperties;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.domain.model.TokenPair;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyRegistry;
import com.example.authsvc.infrastructure.security.jwt.signer.JwtSigner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final JwtKeyRegistry registry;
    private final JwtProperties  props;
    private final JwtSigner      jwtSigner;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ObjectMapper JWT_MAPPER    = new ObjectMapper();

    // ─── Token Generation ─────────────────────────────────────────────────────

    public String generateAccessToken(JwtClaims claims) {
        long   start = System.currentTimeMillis();
        String kid   = registry.getActiveKid();
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "RS256");
            header.put("kid", kid);

            String jti = UUID.randomUUID().toString();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub",              claims.userId().toString());
            payload.put("iss",              props.getIssuer());
            if (props.getAudience() != null && !props.getAudience().isBlank()) {
                payload.put("aud",          props.getAudience());
            }
            payload.put("jti",              jti);
            payload.put("iat",              claims.issuedAt().getEpochSecond());
            payload.put("exp",              claims.expiresAt().getEpochSecond());
            if (claims.tenantId()  != null) payload.put("tenant_id",        claims.tenantId().toString());
            payload.put("tenant_slug",      claims.tenantSlug() != null ? claims.tenantSlug() : "");
            if (claims.roleId()    != null) payload.put("role_id",          claims.roleId().toString());
            if (claims.userType()  != null) payload.put("user_type",        claims.userType().name());
            payload.put("session_id",       claims.sessionId());
            if (claims.username()   != null && !claims.username().isBlank())   payload.put("username",    claims.username());
            if (claims.userEmail()  != null && !claims.userEmail().isBlank())  payload.put("user_email",  claims.userEmail());
            if (claims.tenantName() != null && !claims.tenantName().isBlank()) payload.put("tenant_name", claims.tenantName());

            String headerEncoded  = base64UrlEncode(JWT_MAPPER.writeValueAsBytes(header));
            String payloadEncoded = base64UrlEncode(JWT_MAPPER.writeValueAsBytes(payload));
            String signingInput   = headerEncoded + "." + payloadEncoded;

            byte[] sigBytes = jwtSigner.sign(kid, signingInput.getBytes(StandardCharsets.US_ASCII));
            String token    = signingInput + "." + base64UrlEncode(sigBytes);

            log.info("perf.jwt.sign.ms={}", System.currentTimeMillis() - start);
            log.debug("jwt.generated userId={} jti={} sessionId={} tenantSlug={} expiresAt={}",
                    claims.userId(), jti, claims.sessionId(), claims.tenantSlug(),
                    claims.expiresAt());
            return token;
        } catch (JsonProcessingException e) {
            throw new JwtSigningException("JWT header/payload serialization failed", e);
        }
    }

    public TokenPair generateTokenPair(JwtClaims baseClaims) {
        Instant now           = Instant.now();
        Instant refreshExpiry = now.plus(props.getRefreshToken().getExpirationDays(), ChronoUnit.DAYS);
        return generateTokenPair(baseClaims, refreshExpiry);
    }

    public TokenPair generateTokenPair(JwtClaims baseClaims, Instant fixedRefreshExpiry) {
        Instant now          = Instant.now();
        Instant accessExpiry = now.plus(props.getAccessToken().getExpirationMinutes(), ChronoUnit.MINUTES);

        JwtClaims timedClaims = new JwtClaims(
                baseClaims.userId(), baseClaims.tenantId(), baseClaims.tenantSlug(),
                baseClaims.roleIds(), baseClaims.userType(),
                now, accessExpiry, baseClaims.sessionId(),
                null,
                baseClaims.username(), baseClaims.userEmail(), baseClaims.tenantName()
        );

        return new TokenPair(
                generateAccessToken(timedClaims),
                generateOpaqueToken(),
                accessExpiry,
                fixedRefreshExpiry
        );
    }

    // ─── Validation & Extraction ──────────────────────────────────────────────

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("jwt.expired: {}", e.getMessage());
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("jwt.invalid: {}", e.getMessage());
            return false;
        }
    }

    public JwtClaims extractClaims(String token) {
        Claims c = parseClaims(token);

        // ── Multi-role extraction (Phase 4) ───────────────────────────────────
        // Prefer role_ids[] if present (Phase 4+ tokens).
        // Fall back to scalar role_id for tokens issued before Phase 4.
        List<UUID> roleIds = extractRoleIds(c);

        return new JwtClaims(
                UUID.fromString(c.getSubject()),
                parseUuid(c.get("tenant_id",    String.class)),
                c.get("tenant_slug",            String.class),
                roleIds,
                parseUserType(c.get("user_type", String.class)),
                c.getIssuedAt().toInstant(),
                c.getExpiration().toInstant(),
                c.get("session_id",             String.class),
                c.get("jti",                    String.class),
                c.get("username",                String.class),
                c.get("user_email",              String.class),
                c.get("tenant_name",             String.class)
        );
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    private Claims parseClaims(String token) {
        long start = System.currentTimeMillis();
        var parser = Jwts.parser()
                .keyLocator(header -> {
                    String kid = (String) header.get("kid");
                    RSAPublicKey key = registry.getPublicKey(kid);
                    if (key == null) throw new JwtException("Unknown JWT kid: " + kid);
                    return key;
                })
                .requireIssuer(props.getIssuer());
        if (props.getAudience() != null && !props.getAudience().isBlank()) {
            parser.requireAudience(props.getAudience());
        }
        Claims result = parser
                .build()
                .parseSignedClaims(token)
                .getPayload();
        log.info("perf.jwt.verify.ms={}", System.currentTimeMillis() - start);
        return result;
    }

    /**
     * Extracts the role list from JWT claims using a forward-compatible strategy:
     * <ol>
     *   <li>If {@code role_ids} (array) is present → parse each element as UUID.</li>
     *   <li>Else if {@code role_id} (scalar string) is present → wrap in a single-element list.</li>
     *   <li>Otherwise → empty list (no roles encoded in token).</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    private static List<UUID> extractRoleIds(Claims c) {
        List<String> roleIdsRaw = c.get("role_ids", List.class);
        if (roleIdsRaw != null && !roleIdsRaw.isEmpty()) {
            List<UUID> result = new ArrayList<>(roleIdsRaw.size());
            for (String raw : roleIdsRaw) {
                UUID parsed = parseUuid(raw);
                if (parsed != null) result.add(parsed);
            }
            return List.copyOf(result);
        }
        // Backward compat: pre-Phase-4 token carries only role_id (scalar)
        UUID single = parseUuid(c.get("role_id", String.class));
        return (single != null) ? List.of(single) : List.of();
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        return UUID.fromString(value);
    }

    private static UserType parseUserType(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UserType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
