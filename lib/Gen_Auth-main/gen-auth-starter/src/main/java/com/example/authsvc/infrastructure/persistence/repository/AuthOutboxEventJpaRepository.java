package com.example.authsvc.infrastructure.persistence.repository;

import com.example.authsvc.infrastructure.persistence.entity.AuthOutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuthOutboxEventJpaRepository extends JpaRepository<AuthOutboxEventEntity, UUID> {

    @Query(value = "SELECT * FROM auth_outbox_events WHERE status = 'PENDING' " +
                   "ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<AuthOutboxEventEntity> findPendingForUpdate(@Param("limit") int limit);

    @Modifying
    @Query("UPDATE AuthOutboxEventEntity e SET e.status = 'PUBLISHED', e.publishedAt = :publishedAt WHERE e.id = :id")
    int markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);

    @Modifying
    @Query("UPDATE AuthOutboxEventEntity e SET e.retryCount = e.retryCount + 1, e.lastError = :error WHERE e.id = :id")
    int incrementRetry(@Param("id") UUID id, @Param("error") String error);

    @Modifying
    @Query("UPDATE AuthOutboxEventEntity e SET e.status = 'FAILED', e.lastError = :error WHERE e.id = :id")
    int markFailed(@Param("id") UUID id, @Param("error") String error);
}
