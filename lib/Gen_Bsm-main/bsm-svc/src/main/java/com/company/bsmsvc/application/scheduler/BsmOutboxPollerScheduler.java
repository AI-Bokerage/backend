package com.company.bsmsvc.application.scheduler;

import com.company.bsmsvc.infrastructure.outbox.BsmOutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BsmOutboxPollerScheduler {

    private final BsmOutboxPublisher outboxPublisher;

    @Scheduled(fixedDelayString = "${bsm.outbox.poll-interval-ms:5000}")
    public void poll() {
        outboxPublisher.publishPending();
    }
}
