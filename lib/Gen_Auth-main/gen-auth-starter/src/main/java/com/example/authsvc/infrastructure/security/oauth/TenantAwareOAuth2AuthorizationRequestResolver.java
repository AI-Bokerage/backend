package com.example.authsvc.infrastructure.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Wraps the standard {@link DefaultOAuth2AuthorizationRequestResolver} to
 * carry an optional {@code tenantId} query param (given at
 * {@code /oauth2/authorization/{registrationId}?tenantId=...}) through the
 * authorization-code round trip via {@link OAuth2AuthorizationRequest}'s
 * {@code attributes()} map — this service is multi-tenant and a brand-new
 * account created by {@link OAuthLoginSuccessHandler} needs to know which
 * tenant to assign, the same optional-defaults-to-platform-sentinel behavior
 * {@code RegisterRequest.tenantId} already has.
 *
 * <p>{@code attributes()} rather than {@code additionalParameters()}: the
 * latter is serialized into the authorization URI sent to the identity
 * provider, which would leak the tenant UUID into the provider's request
 * logs. Attributes stay server-side.
 */
public class TenantAwareOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    static final String TENANT_ID_PARAM = "tenantId";
    private static final String DEFAULT_AUTHORIZATION_REQUEST_BASE_URI = "/oauth2/authorization";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public TenantAwareOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return withTenantId(delegate.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return withTenantId(delegate.resolve(request, clientRegistrationId), request);
    }

    private OAuth2AuthorizationRequest withTenantId(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request) {
        if (authorizationRequest == null) {
            return null;
        }
        String tenantId = request.getParameter(TENANT_ID_PARAM);
        if (tenantId == null || tenantId.isBlank()) {
            return authorizationRequest;
        }
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .attributes(attrs -> attrs.put(TENANT_ID_PARAM, tenantId))
                .build();
    }
}
