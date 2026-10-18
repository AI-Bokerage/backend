package com.example.authsvc.infrastructure.security.jwt.util;

import com.example.authsvc.config.properties.JwtProperties;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.domain.model.TokenPair;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyRegistry;
import com.example.authsvc.infrastructure.security.jwt.signer.JwtSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtUtilsTest {

    @Mock private JwtKeyRegistry registry;
    @Mock private JwtProperties  props;
    @Mock private JwtSigner      jwtSigner;

    private JwtUtils jwtUtils;
    private RSAPublicKey publicKey;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtils = new JwtUtils(registry, props, jwtSigner);

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();
        publicKey = (RSAPublicKey) keyPair.getPublic();

        when(registry.getActiveKid()).thenReturn("kid-1");
        when(registry.getPublicKey("kid-1")).thenReturn(publicKey);
        when(props.getIssuer()).thenReturn("gen-auth");
        when(jwtSigner.sign(anyString(), any(byte[].class))).thenAnswer(inv -> {
            byte[] signingInput = inv.getArgument(1);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyPair.getPrivate());
            signature.update(signingInput);
            return signature.sign();
        });
    }

    @Test
    void generateAccessToken_carriesUsernameUserEmailTenantNameThroughToExtractedClaims() {
        // This test exists specifically to catch generateTokenPair's intermediate
        // timedClaims reconstruction silently dropping a field it doesn't
        // explicitly thread through — a real risk any time JwtClaims grows a field.
        Instant now = Instant.now();
        JwtClaims baseClaims = new JwtClaims(
                UUID.randomUUID(), UUID.randomUUID(), "acme",
                List.of(UUID.randomUUID()), UserType.TENANT_USER,
                now, now.plus(15, ChronoUnit.MINUTES), "session-1", null,
                "Jane Doe", "jane@example.com", "Acme Corp");

        String token = jwtUtils.generateAccessToken(baseClaims);

        JwtClaims extracted = jwtUtils.extractClaims(token);

        assertThat(extracted.username()).isEqualTo("Jane Doe");
        assertThat(extracted.userEmail()).isEqualTo("jane@example.com");
        assertThat(extracted.tenantName()).isEqualTo("Acme Corp");
    }

    @Test
    void generateAccessToken_blankOptionalClaims_omittedFromPayload() {
        Instant now = Instant.now();
        JwtClaims baseClaims = new JwtClaims(
                UUID.randomUUID(), UUID.randomUUID(), "acme",
                List.of(), UserType.CLIENT,
                now, now.plus(15, ChronoUnit.MINUTES), "session-2", null,
                "", "   ", "");

        String token = jwtUtils.generateAccessToken(baseClaims);

        JwtClaims extracted = jwtUtils.extractClaims(token);

        assertThat(extracted.username()).isNull();
        assertThat(extracted.userEmail()).isNull();
        assertThat(extracted.tenantName()).isNull();
    }

    @Test
    void generateTokenPair_viaTokenPairPath_stillCarriesTheThreeClaims() {
        // Exercises generateTokenPair(JwtClaims) -> internal timedClaims rebuild ->
        // generateAccessToken, which is the ONLY path real call sites use.
        Instant now = Instant.now();
        when(props.getAccessToken()).thenReturn(accessTokenProps(15));
        when(props.getRefreshToken()).thenReturn(refreshTokenProps(7));

        JwtClaims baseClaims = new JwtClaims(
                UUID.randomUUID(), UUID.randomUUID(), "acme",
                List.of(), UserType.TENANT_USER,
                now, now.plus(15, ChronoUnit.MINUTES), "session-3", null,
                "Jane Doe", "jane@example.com", "Acme Corp");

        TokenPair pair = jwtUtils.generateTokenPair(baseClaims);
        JwtClaims extracted = jwtUtils.extractClaims(pair.accessToken());

        assertThat(extracted.username()).isEqualTo("Jane Doe");
        assertThat(extracted.userEmail()).isEqualTo("jane@example.com");
        assertThat(extracted.tenantName()).isEqualTo("Acme Corp");
    }

    private static JwtProperties.AccessToken accessTokenProps(int minutes) {
        JwtProperties.AccessToken t = new JwtProperties.AccessToken();
        t.setExpirationMinutes(minutes);
        return t;
    }

    private static JwtProperties.RefreshToken refreshTokenProps(int days) {
        JwtProperties.RefreshToken t = new JwtProperties.RefreshToken();
        t.setExpirationDays(days);
        return t;
    }
}
