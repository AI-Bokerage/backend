package com.example.authsvc.infrastructure.messaging.outbox;

import com.example.authsvc.config.properties.MessagingProperties;
import com.example.authsvc.infrastructure.persistence.entity.AuthOutboxEventEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthOutboxEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthOutboxRelayJobTest {

    @Mock private AuthOutboxEventJpaRepository repository;
    @Mock private RabbitTemplate rabbitTemplate;

    private AuthOutboxRelayJob job(RabbitTemplate template) {
        MessagingProperties props = new MessagingProperties();
        props.setExchange("auth.events");
        AuthOutboxRelayJob job = new AuthOutboxRelayJob(repository, template, props);
        setBatchSize(job, 50);
        return job;
    }

    private static AuthOutboxEventEntity pendingRow(String targetExchange, int retryCount) {
        return AuthOutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .eventType("auth.login.success")
                .payload("{}")
                .targetExchange(targetExchange)
                .status("PENDING")
                .retryCount(retryCount)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void relay_nullRabbitTemplate_neverQueriesRepository() {
        AuthOutboxRelayJob job = job(null);
        job.relay();
        verifyNoInteractions(repository);
    }

    @Test
    void relay_sendSucceeds_marksPublishedWithMessageIdSetToEventId() {
        AuthOutboxEventEntity row = pendingRow(null, 0);
        when(repository.findPendingForUpdate(50)).thenReturn(List.of(row));

        job(rabbitTemplate).relay();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("auth.events"), eq("auth.login.success"), messageCaptor.capture());
        assertThat(messageCaptor.getValue().getMessageProperties().getMessageId())
                .isEqualTo(row.getEventId().toString());
        verify(repository).markPublished(eq(row.getId()), any(Instant.class));
    }

    @Test
    void relay_nonNullTargetExchange_sendsToOverrideExchangeNotDefault() {
        AuthOutboxEventEntity row = pendingRow("auth.audit.tenant", 0);
        when(repository.findPendingForUpdate(50)).thenReturn(List.of(row));

        job(rabbitTemplate).relay();

        verify(rabbitTemplate).send(eq("auth.audit.tenant"), anyString(), any(Message.class));
    }

    @Test
    void relay_sendThrowsOnFirstAttempt_incrementsRetryNotFailed() {
        AuthOutboxEventEntity row = pendingRow(null, 0);
        when(repository.findPendingForUpdate(50)).thenReturn(List.of(row));
        doThrow(new RuntimeException("broker unreachable"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

        job(rabbitTemplate).relay();

        verify(repository).incrementRetry(eq(row.getId()), anyString());
        verify(repository, never()).markFailed(any(), anyString());
    }

    @Test
    void relay_sendThrowsOnThirdAttempt_marksFailedNotRetry() {
        // retryCount == 2 means this is attempt #3 (0-indexed) — MAX_RETRIES=3, so this exhausts it.
        AuthOutboxEventEntity row = pendingRow(null, 2);
        when(repository.findPendingForUpdate(50)).thenReturn(List.of(row));
        doThrow(new RuntimeException("broker unreachable"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

        job(rabbitTemplate).relay();

        verify(repository).markFailed(eq(row.getId()), anyString());
        verify(repository, never()).incrementRetry(any(), anyString());
    }

    private static void setBatchSize(AuthOutboxRelayJob job, int value) {
        try {
            var field = AuthOutboxRelayJob.class.getDeclaredField("batchSize");
            field.setAccessible(true);
            field.setInt(job, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
