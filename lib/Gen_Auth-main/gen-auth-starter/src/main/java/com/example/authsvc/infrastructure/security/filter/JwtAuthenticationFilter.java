package com.example.authsvc.infrastructure.security.filter;

import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain) throws ServletException, IOException {

        Optional<String> tokenOpt = extractToken(request);

        if (tokenOpt.isEmpty()) {
            log.debug("jwt.missing path={}", request.getRequestURI());
        } else {
            String token = tokenOpt.get();
            if (jwtUtils.isTokenValid(token)) {
                JwtClaims claims = jwtUtils.extractClaims(token);

                // Phase 4: AuthenticatedUser carries role_ids[] from the JWT.
                // getRoleId() on the principal returns the first element for
                // backward-compat callers.
                AuthenticatedUser user = new AuthenticatedUser(
                        claims.userId(),
                        claims.tenantId(),
                        claims.tenantSlug(),
                        claims.roleIds(),   // List<UUID> — may be empty for pre-Phase-4 tokens
                        claims.userType(),
                        claims.sessionId(),
                        claims.expiresAt(),
                        claims.jti()
                );

                var auth = new UsernamePasswordAuthenticationToken(
                        user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getUserType().name()))
                );
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);

                MDC.put("userId",     user.getUserId().toString());
                MDC.put("tenantId",   user.getTenantId() != null ? user.getTenantId().toString() : "");
                MDC.put("tenantSlug", user.getTenantSlug() != null ? user.getTenantSlug() : "");
                MDC.put("sessionId",  user.getSessionId() != null ? user.getSessionId() : "");

                log.debug("jwt.authenticated userId={} sessionId={} jti={} roleCount={}",
                        user.getUserId(), user.getSessionId(), user.getJti(),
                        user.getRoleIds().size());
            } else {
                log.debug("jwt.invalid path={}", request.getRequestURI());
            }
        }

        chain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (AuthCookieFactory.ACCESS_TOKEN_COOKIE.equals(c.getName())
                        && StringUtils.hasText(c.getValue())) {
                    return Optional.of(c.getValue());
                }
            }
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            String t = header.substring(BEARER_PREFIX.length()).strip();
            if (StringUtils.hasText(t)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
