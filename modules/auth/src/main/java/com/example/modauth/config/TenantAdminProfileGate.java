package com.example.modauth.config;

import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.domain.Role;
import com.example.modauth.repository.ModAuthUserRoleJpaRepository;
import com.example.modauth.service.CrmClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Flow 0 "Tenant Admin → complete the brokerage profile, required before
 * anything else" — the same gate the CRM applies to its own Tenant Admin
 * routes, here for People, Teams and Invitations. Only acts on a signed-in
 * Tenant Admin; everyone else (and the public invitation-accept links) pass.
 */
@Configuration
@RequiredArgsConstructor
public class TenantAdminProfileGate implements WebMvcConfigurer, HandlerInterceptor {

    private final ModAuthUserRoleJpaRepository roleRepo;
    private final CrmClient crmClient;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns(
                "/api/v1/modauth/people/**",
                "/api/v1/modauth/teams/**",
                "/api/v1/modauth/invitations",
                "/api/v1/modauth/invitations/**");
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            return true;
        }
        boolean isTenantAdmin = roleRepo.findById(user.getUserId())
                .map(r -> r.getRole() == Role.TENANT_ADMIN)
                .orElse(false);
        if (isTenantAdmin && !crmClient.isBrokerageProfileComplete(user.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Complete the brokerage profile first");
        }
        return true;
    }
}
