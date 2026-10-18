package com.example.admsvc.infrastructure.persistence;

import com.example.admsvc.domain.enums.OffboardingJobStatus;
import com.example.admsvc.domain.enums.OffboardingStepStatus;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingStepEntity;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingJobRepository;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingStepRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = OffboardingPersistenceIntegrationTest.TestApp.class)
class OffboardingPersistenceIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OffboardingJobRepository jobRepository;

    @Autowired
    private OffboardingStepRepository stepRepository;

    @Test
    void savesAndFindsAJobByIdAndTenant() {
        UUID tenantId = UUID.randomUUID();
        OffboardingJobEntity job = OffboardingJobEntity.builder()
                .tenantId(tenantId)
                .userId(UUID.randomUUID())
                .initiatedBy(UUID.randomUUID())
                .reason("left the company")
                .build();
        jobRepository.saveAndFlush(job);

        Optional<OffboardingJobEntity> found = jobRepository.findByIdAndTenantId(job.getId(), tenantId);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(OffboardingJobStatus.PENDING);
        assertThat(found.get().getAttemptCount()).isZero();
        assertThat(found.get().getMaxAttempts()).isEqualTo(5);
    }

    @Test
    void aJobIsInvisibleWhenLookedUpUnderTheWrongTenant() {
        UUID tenantId = UUID.randomUUID();
        OffboardingJobEntity job = OffboardingJobEntity.builder()
                .tenantId(tenantId)
                .userId(UUID.randomUUID())
                .initiatedBy(UUID.randomUUID())
                .reason("left the company")
                .build();
        jobRepository.saveAndFlush(job);

        Optional<OffboardingJobEntity> found = jobRepository.findByIdAndTenantId(job.getId(), UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    void savesStepsAndListsThemOrderedBySequence() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OffboardingJobEntity job = jobRepository.saveAndFlush(OffboardingJobEntity.builder()
                .tenantId(tenantId).userId(userId).initiatedBy(UUID.randomUUID()).reason("r").build());

        stepRepository.saveAndFlush(OffboardingStepEntity.builder()
                .jobId(job.getId()).tenantId(tenantId).userId(userId)
                .sequence(1).stepName("SECOND").build());
        stepRepository.saveAndFlush(OffboardingStepEntity.builder()
                .jobId(job.getId()).tenantId(tenantId).userId(userId)
                .sequence(0).stepName("SESSION_REVOCATION").build());

        List<OffboardingStepEntity> steps = stepRepository.findAllByJobIdOrderBySequenceAsc(job.getId());
        assertThat(steps).extracting(OffboardingStepEntity::getStepName)
                .containsExactly("SESSION_REVOCATION", "SECOND");
        assertThat(steps.get(0).getStatus()).isEqualTo(OffboardingStepStatus.PENDING);
    }
}
