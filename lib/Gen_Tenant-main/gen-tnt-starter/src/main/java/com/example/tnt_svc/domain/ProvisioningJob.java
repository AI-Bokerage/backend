package com.example.tnt_svc.domain;

import com.example.tnt_svc.persistence.JsonMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "provisioning_job")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisioningJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProvisioningJobStatus status;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "callback_token", nullable = false)
    private String callbackToken;

    @Version
    private Long version;

    @Column(name = "last_error")
    private String lastError;

    @Convert(converter = JsonMapConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    @Builder.Default
    private Map<String, Object> context = new HashMap<>();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @jakarta.persistence.PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void markInProgress() {
        status = ProvisioningJobStatus.IN_PROGRESS;
        startedAt = Instant.now();
    }

    public void markCompleted() {
        status = ProvisioningJobStatus.COMPLETED;
        completedAt = Instant.now();
    }

    public void markFailed(String error) {
        status = ProvisioningJobStatus.FAILED;
        lastError = error;
    }

    public void markDead() {
        status = ProvisioningJobStatus.DEAD;
        completedAt = Instant.now();
    }

    public boolean canRetry() {
        return status == ProvisioningJobStatus.FAILED && retryCount < maxRetries;
    }

    public void incrementRetry() {
        retryCount++;
    }

    public void mergeContext(Map<String, Object> updates) {
        Map<String, Object> merged = new HashMap<>(context);
        merged.putAll(updates);
        context = merged;
    }
}
