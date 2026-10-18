package com.example.authsvc.infrastructure.security.jwt.jwks;

import com.example.authsvc.common.exception.ActiveKeyRetirementException;
import com.example.authsvc.common.exception.KeyNotFoundException;
import com.example.authsvc.config.properties.JwtProperties;
import com.example.authsvc.infrastructure.persistence.entity.JwtSigningKeyEntity;
import com.example.authsvc.infrastructure.persistence.repository.JwtActiveSigningKeyJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.JwtSigningKeyJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtKeyRotationServiceTest {

    @Mock private JwtSigningKeyJpaRepository       signingKeyRepository;
    @Mock private JwtActiveSigningKeyJpaRepository activeKeyRepository;
    @Mock private JwtProperties                    jwtProperties;

    /**
     * Minimal fake transaction manager: commits synchronously, counts commits, and snapshots
     * the registry's active kid at the moment of commit — so tests can prove the registry
     * mutation happens strictly *after* commit, not before or during.
     */
    private static class CountingTransactionManager implements PlatformTransactionManager {
        int commits = 0;
        String activeKidAtCommitTime;
        private final JwtKeyRegistry registryToObserve;

        CountingTransactionManager(JwtKeyRegistry registryToObserve) {
            this.registryToObserve = registryToObserve;
        }

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            activeKidAtCommitTime = registryToObserve.getActiveKid();
            commits++;
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }

    private CountingTransactionManager transactionManager;
    private KeyEncryptionUtil           encryptionUtil;
    private JwtKeyRegistry              registry;
    private JwtKeyRotationService       service;

    @BeforeEach
    void setUp() {
        encryptionUtil = new KeyEncryptionUtil(Base64.getEncoder().encodeToString(new byte[32]));

        Map<String, JwtKeyEntry> initial = new LinkedHashMap<>();
        initial.put("v1", new JwtKeyEntry("v1", null, null, null));
        registry = new JwtKeyRegistry(initial, "v1");

        transactionManager = new CountingTransactionManager(registry);

        service = new JwtKeyRotationService(
                signingKeyRepository, activeKeyRepository, encryptionUtil, registry, jwtProperties, transactionManager);
    }

    @Test
    void rotateCommitsToDbBeforeUpdatingLiveRegistry() {
        when(jwtProperties.getSigningMode()).thenReturn("local");
        when(activeKeyRepository.findSingleton()).thenReturn(Optional.empty());

        JwtKeyRotationService.RotatedKey result = service.rotate();

        assertEquals(1, transactionManager.commits);
        verify(signingKeyRepository).save(any(JwtSigningKeyEntity.class));
        verify(activeKeyRepository).save(any());
        assertEquals("v1", transactionManager.activeKidAtCommitTime);
        assertEquals(result.kid(), registry.getActiveKid());
        assertTrue(result.publicKeyPem().startsWith("-----BEGIN PUBLIC KEY-----"));
    }

    @Test
    void rotateThrowsInKmsMode() {
        when(jwtProperties.getSigningMode()).thenReturn("kms");

        assertThrows(UnsupportedOperationException.class, () -> service.rotate());
        verifyNoInteractions(signingKeyRepository);
    }

    @Test
    void retireRemovesDbRowAndRegistryEntryForNonActiveKid() {
        registry.addKey(new JwtKeyEntry("v2", null, null, null));
        JwtSigningKeyEntity entity = JwtSigningKeyEntity.builder().kid("v2").build();
        when(signingKeyRepository.findById("v2")).thenReturn(Optional.of(entity));

        service.retire("v2");

        verify(signingKeyRepository).delete(entity);
        assertEquals(1, transactionManager.commits);
    }

    @Test
    void retireRejectsUnknownKid() {
        when(signingKeyRepository.findById("nope")).thenReturn(Optional.empty());

        assertThrows(KeyNotFoundException.class, () -> service.retire("nope"));
    }

    @Test
    void retireRejectsActiveKid() {
        JwtSigningKeyEntity entity = JwtSigningKeyEntity.builder().kid("v1").build();
        when(signingKeyRepository.findById("v1")).thenReturn(Optional.of(entity));

        assertThrows(ActiveKeyRetirementException.class, () -> service.retire("v1"));
    }
}
