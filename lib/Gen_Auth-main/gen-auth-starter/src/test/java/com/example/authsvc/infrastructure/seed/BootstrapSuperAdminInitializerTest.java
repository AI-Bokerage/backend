package com.example.authsvc.infrastructure.seed;

import com.example.authsvc.application.service.EmailService;
import com.example.authsvc.config.properties.SuperAdminProperties;
import com.example.authsvc.infrastructure.persistence.entity.PlatformSuperAdminEntity;
import com.example.authsvc.infrastructure.persistence.repository.PlatformSuperAdminJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapSuperAdminInitializerTest {

    private static final UUID SUPER_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock private SuperAdminProperties            superAdminProperties;
    @Mock private PlatformSuperAdminJpaRepository superAdminRepo;
    @Mock private PasswordHasher                  passwordHasher;
    @Mock private EmailService                    emailService;

    @InjectMocks
    private BootstrapSuperAdminInitializer initializer;

    @Test
    void run_noExistingRecord_createsAccountAndSendsEmail() {
        when(superAdminProperties.getEmail()).thenReturn("admin@example.com");
        when(superAdminProperties.getPassword()).thenReturn("TempPass123!");
        when(superAdminRepo.findById(SUPER_ADMIN_ID)).thenReturn(Optional.empty());
        when(passwordHasher.hash(anyString())).thenReturn("hashed");

        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<PlatformSuperAdminEntity> captor = ArgumentCaptor.forClass(PlatformSuperAdminEntity.class);
        verify(superAdminRepo).save(captor.capture());
        PlatformSuperAdminEntity saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(SUPER_ADMIN_ID);
        assertThat(saved.getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.isActive()).isTrue();
        verify(emailService).sendSuperAdminBootstrapCredentials("admin@example.com", "TempPass123!");
    }

    @Test
    void run_existingRecordSameEmail_doesNothing() {
        when(superAdminProperties.getEmail()).thenReturn("admin@example.com");
        PlatformSuperAdminEntity existing = PlatformSuperAdminEntity.builder()
                .id(SUPER_ADMIN_ID).email("admin@example.com").passwordHash("hashed").active(true).build();
        when(superAdminRepo.findById(SUPER_ADMIN_ID)).thenReturn(Optional.of(existing));

        initializer.run(new DefaultApplicationArguments());

        verify(superAdminRepo, never()).save(org.mockito.ArgumentMatchers.any());
        verify(emailService, never()).sendSuperAdminBootstrapCredentials(anyString(), anyString());
    }

    @Test
    void run_existingRecordDifferentEmail_updatesAndResendsEmail() {
        when(superAdminProperties.getEmail()).thenReturn("new-admin@example.com");
        when(superAdminProperties.getPassword()).thenReturn("TempPass123!");
        PlatformSuperAdminEntity existing = PlatformSuperAdminEntity.builder()
                .id(SUPER_ADMIN_ID).email("old-admin@example.com").passwordHash("old-hash").active(true).build();
        when(superAdminRepo.findById(SUPER_ADMIN_ID)).thenReturn(Optional.of(existing));
        when(passwordHasher.hash(anyString())).thenReturn("new-hash");

        initializer.run(new DefaultApplicationArguments());

        verify(superAdminRepo).save(existing);
        assertThat(existing.getEmail()).isEqualTo("new-admin@example.com");
        assertThat(existing.getPasswordHash()).isEqualTo("new-hash");
        verify(emailService).sendSuperAdminBootstrapCredentials("new-admin@example.com", "TempPass123!");
    }

    @Test
    void run_saveThrows_exceptionPropagatesAndAbortsStartup() {
        when(superAdminProperties.getEmail()).thenReturn("admin@example.com");
        when(superAdminProperties.getPassword()).thenReturn("TempPass123!");
        when(superAdminRepo.findById(SUPER_ADMIN_ID)).thenReturn(Optional.empty());
        when(passwordHasher.hash(anyString())).thenThrow(new RuntimeException("hashing failed"));

        assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("hashing failed");

        verify(superAdminRepo, never()).save(org.mockito.ArgumentMatchers.any());
        verify(emailService, never()).sendSuperAdminBootstrapCredentials(anyString(), anyString());
    }
}
