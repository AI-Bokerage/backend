package com.company.audit.spring.anchor;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AnchorPublishFailureLoggingListenerTest {

    private final AnchorPublishFailureLoggingListener listener = new AnchorPublishFailureLoggingListener();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(AnchorPublishFailureLoggingListener.class);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void logsAtErrorSeverityWithExpectedFields() {
        RuntimeException cause = new RuntimeException("bucket does not exist");
        Instant detectedAt = Instant.parse("2024-06-01T12:00:00Z");
        AnchorPublishFailedEvent event = new AnchorPublishFailedEvent("partition-1", cause, detectedAt);

        listener.onAnchorPublishFailed(event);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent logged = appender.list.get(0);
        assertThat(logged.getLevel()).isEqualTo(Level.ERROR);
        String message = logged.getFormattedMessage();
        assertThat(message).contains("partition-1").contains(detectedAt.toString());
        assertThat(logged.getThrowableProxy().getMessage()).isEqualTo("bucket does not exist");
    }
}
