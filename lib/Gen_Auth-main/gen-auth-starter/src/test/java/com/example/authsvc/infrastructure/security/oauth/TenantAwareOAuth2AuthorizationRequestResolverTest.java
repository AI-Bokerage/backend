package com.example.authsvc.infrastructure.security.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;

class TenantAwareOAuth2AuthorizationRequestResolverTest {

    private static ClientRegistration googleRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .scope("openid", "email")
                .build();
    }

    private TenantAwareOAuth2AuthorizationRequestResolver resolver() {
        ClientRegistrationRepository repo = new InMemoryClientRegistrationRepository(googleRegistration());
        return new TenantAwareOAuth2AuthorizationRequestResolver(repo);
    }

    @Test
    void resolve_withTenantIdParam_addsToAttributes() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        request.setServletPath("/oauth2/authorization/google");
        request.setParameter("tenantId", "11111111-1111-1111-1111-111111111111");

        OAuth2AuthorizationRequest authorizationRequest = resolver().resolve(request);

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getAttributes())
                .containsEntry("tenantId", "11111111-1111-1111-1111-111111111111");
        // Must NOT ride along in the URI sent to the provider.
        assertThat(authorizationRequest.getAdditionalParameters()).doesNotContainKey("tenantId");
        assertThat(authorizationRequest.getAuthorizationRequestUri()).doesNotContain("tenantId");
    }

    @Test
    void resolve_withoutTenantIdParam_noTenantAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        request.setServletPath("/oauth2/authorization/google");

        OAuth2AuthorizationRequest authorizationRequest = resolver().resolve(request);

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getAttributes()).doesNotContainKey("tenantId");
    }

    @Test
    void resolve_unrelatedPath_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/some/other/path");
        request.setServletPath("/some/other/path");

        assertThat(resolver().resolve(request)).isNull();
    }
}
