package com.company.audit.spring.verification;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.company.audit.core.api.VerificationResult;
import com.company.audit.core.api.enums.AuditChainStatus;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ChainBreakLoggingListenerTest {

    private final ChainBreakLoggingListener listener = new ChainBreakLoggingListener();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(ChainBreakLoggingListener.class);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void logsAtErrorSeverityWithExpectedFields() {
        VerificationResult result =
                VerificationResult.brokenAt("partition-1", AuditChainStatus.HASH_MISMATCH, 7L, "event hash mismatch");
        Instant detectedAt = Instant.parse("2024-06-01T12:00:00Z");
        AuditChainBreakDetectedEvent event = new AuditChainBreakDetectedEvent("partition-1", result, detectedAt);

        listener.onChainBreak(event);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent logged = appender.list.get(0);
        assertThat(logged.getLevel()).isEqualTo(Level.ERROR);
        String message = logged.getFormattedMessage();
        assertThat(message).contains("partition-1").contains("HASH_MISMATCH").contains("7").contains(detectedAt.toString());
    }
}
