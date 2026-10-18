package io.cpms.common.messaging;

import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared cross-service audit-exchange topology — declared once here so every Java producer
 * (tnt-svc today, others later) imports the same bean definitions via {@code @Import} instead of
 * re-declaring its own copy that can silently drift (mismatched durability/auto-delete flags
 * break broker-level idempotent declaration when two services both try to declare the same
 * exchange name).
 *
 * <p>{@code cpms.platform.audit}: durable, non-auto-delete fanout exchange. Declared identically
 * to aud-svc's own local declaration ({@code io.cpms.aud.infrastructure.messaging
 * .PlatformAuditRabbitConfig#platformAuditExchange()} — same name, same type, same
 * durability/auto-delete flags — {@code new FanoutExchange(name, true, false)}), so both
 * declarations are compatible/idempotent on the same broker.
 *
 * <p>{@code cpms.audit}: durable, non-auto-delete topic exchange — matches the type Node asserts
 * for {@code MqExchange.AUDIT} (confirmed via {@code apps/reg-svc/src/infra/messaging/event-bus.ts}
 * and {@code apps/sup-svc/src/infra/messaging/event-bus.ts}: {@code assertExchange(MqExchange
 * .AUDIT, 'topic')}).
 */
@Configuration
public class AuditMessagingTopology {

    public static final String PLATFORM_AUDIT_EXCHANGE = "cpms.platform.audit";
    public static final String AUDIT_EXCHANGE = "cpms.audit";

    @Bean
    public FanoutExchange cpmsPlatformAuditExchange() {
        return new FanoutExchange(PLATFORM_AUDIT_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange cpmsAuditExchange() {
        return new TopicExchange(AUDIT_EXCHANGE, true, false);
    }
}
