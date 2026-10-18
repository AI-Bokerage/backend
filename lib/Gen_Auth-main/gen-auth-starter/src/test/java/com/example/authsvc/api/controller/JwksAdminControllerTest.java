package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.response.KeyListResponse;
import com.example.authsvc.api.dto.response.RotateKeyResponse;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyEntry;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyRegistry;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyRotationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwksAdminControllerTest {

    @Mock private JwtKeyRotationService rotationService;

    private JwtKeyRegistry      registry;
    private JwksAdminController controller;

    @BeforeEach
    void setUp() {
        Map<String, JwtKeyEntry> initial = new LinkedHashMap<>();
        initial.put("v1", new JwtKeyEntry("v1", null, null, null));
        registry = new JwtKeyRegistry(initial, "v1");
        controller = new JwksAdminController(rotationService, registry);
    }

    @Test
    void rotateReturnsNewKidAndPublicKeyPem() {
        when(rotationService.rotate())
                .thenReturn(new JwtKeyRotationService.RotatedKey("v2", "-----BEGIN PUBLIC KEY-----\n...\n"));

        ResponseEntity<RotateKeyResponse> response = controller.rotate();

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("v2", response.getBody().kid());
    }

    @Test
    void retireDelegatesToServiceAndReturns204() {
        ResponseEntity<Void> response = controller.retire("v2");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(rotationService).retire("v2");
    }

    @Test
    void listReturnsActiveKidAndAllKids() {
        ResponseEntity<KeyListResponse> response = controller.list();

        assertEquals("v1", response.getBody().activeKid());
        assertEquals(1, response.getBody().kids().size());
    }
}
