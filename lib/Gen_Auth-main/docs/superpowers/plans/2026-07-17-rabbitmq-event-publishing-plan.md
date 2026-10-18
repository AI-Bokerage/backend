# RabbitMQ Event Publishing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore outbound RabbitMQ event publishing (login success/failed, logout, password-changed, impersonation started/ended) as an optional, off-by-default subsystem of `gen-auth-starter`, gated by `app.messaging.enabled`.

**Architecture:** `AuthEventPublisher` becomes a `@Component` conditional on `app.messaging.enabled=true`, publishing to a configurable exchange name (`app.messaging.exchange`, default `auth.events`) via Spring AMQP's `RabbitTemplate`. A small `MessagingConfig` declares the `TopicExchange` bean (routing keys are dot-hierarchical, e.g. `auth.login.success`) so `RabbitAdmin` auto-declares it on the broker. Four existing always-on services (`LoginExecutionServiceImpl`, `RefreshTokenServiceImpl`, `ChangePasswordServiceImpl`, `ImpersonationTokenServiceImpl`) get a nullable `AuthEventPublisher` collaborator and call it at their existing side-effect points — no new call sites, no new business logic, just restoring what pre-trim commit `8f2c5b7` already did, adapted to be conditional. No consumers, no queues, no bindings — this is publish-only, matching pre-trim scope.

**Tech Stack:** Spring Boot 4 auto-configuration (`spring-boot-starter-amqp`), Spring AMQP (`RabbitTemplate`, `RabbitAdmin`, `TopicExchange`), Lombok, JUnit 5 + Mockito, `ApplicationContextRunner` for conditional-bean tests.

## Global Constraints

- `app.messaging.enabled` defaults to `false`. When false: no `AuthEventPublisher` bean, no `TopicExchange` bean registered, zero required env vars.
- `app.messaging.exchange` defaults to `auth.events`, only read when messaging is enabled.
- `spring-boot-starter-amqp` being on the classpath means Spring Boot's own `RabbitAutoConfiguration` always registers a `ConnectionFactory`/`RabbitTemplate` bean pair, regardless of `app.messaging.enabled` — this is unavoidable with Boot's autoconfiguration model and exactly mirrors the already-accepted precedent from the email slice (`spring-boot-starter-mail`'s `JavaMailSender` bean is registered the same unconditional way). Those beans are lazy — no TCP connection is attempted until something actually calls them. Since `AuthEventPublisher` (the only caller) and the `TopicExchange` bean are both gated behind `app.messaging.enabled`, no connection is ever attempted when the flag is off. Do not try to prevent Boot's autoconfiguration from registering `ConnectionFactory`/`RabbitTemplate` — that is out of scope and not how Boot's model works.
- Package root stays `com.example.authsvc` (unchanged from pre-trim, matches every prior restored subsystem).
- Do not restore `AuthConsumerTopologyConstants` or any `@RabbitListener` — permanently removed during genericization, explicitly out of scope per the shared spec.
- Do not touch `GlobalExceptionHandler`'s 500-vs-404 bug — known, explicitly out of scope, same as every prior slice.
- Reuse the existing `authAsync`-qualified `Executor` bean for async publish sites — do not introduce a second executor.

---

### Task 1: Messaging properties + build dependency

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/config/properties/MessagingProperties.java`
- Modify: `gen-auth-starter/build.gradle:154-157` (after the "Email (SMTP + SES)" dependency block, before "Testing")
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/config/properties/MessagingPropertiesTest.java`

**Interfaces:**
- Produces: `MessagingProperties` — `@ConfigurationProperties(prefix = "app.messaging")`, `boolean isEnabled()`/`setEnabled(boolean)` (default `false`), `String getExchange()`/`setExchange(String)` (default `"auth.events"`). Picked up automatically via the starter's existing `@ConfigurationPropertiesScan("com.example.authsvc")` in `GenAuthAutoConfiguration` — no manual `@EnableConfigurationProperties` needed, same as `JwtProperties`/`CookieProperties`/`CorsProperties`.

- [ ] **Step 1: Write the failing test**

```java
package com.example.authsvc.config.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessagingPropertiesTest {

    @Test
    void defaults_disabledWithDefaultExchange() {
        MessagingProperties props = new MessagingProperties();

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getExchange()).isEqualTo("auth.events");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.config.properties.MessagingPropertiesTest"`
Expected: FAIL (compilation error) — `MessagingProperties` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.authsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gates and configures optional RabbitMQ event publishing, bound from the
 * {@code app.messaging.*} namespace. See {@code AuthEventPublisher} (only
 * registered when {@code enabled=true}) and {@code MessagingConfig} (declares
 * the exchange bean).
 */
@Data
@ConfigurationProperties(prefix = "app.messaging")
public class MessagingProperties {

    /** Off by default — no beans registered, no connection attempted. */
    private boolean enabled = false;

    /** Topic exchange name events are published to. Only read when enabled. */
    private String exchange = "auth.events";
}
```

Also add the dependency to `gen-auth-starter/build.gradle`, immediately after the existing email block (currently ending at line 157 with `implementation 'software.amazon.awssdk:ses'`):

```groovy
    // ─────────────────────────────────────────────────────────────────────
    // Messaging (RabbitMQ) — optional, gated by app.messaging.enabled
    // ─────────────────────────────────────────────────────────────────────
    implementation 'org.springframework.boot:spring-boot-starter-amqp'
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.config.properties.MessagingPropertiesTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add gen-auth-starter/build.gradle gen-auth-starter/src/main/java/com/example/authsvc/config/properties/MessagingProperties.java gen-auth-starter/src/test/java/com/example/authsvc/config/properties/MessagingPropertiesTest.java
git commit -m "add MessagingProperties and spring-boot-starter-amqp dependency"
```

---

### Task 2: Restore exchange constants and event records

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/constant/AuthExchangeConstants.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/LoginSuccessEvent.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/LoginFailedEvent.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/LogoutEvent.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/PasswordChangedEvent.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/ImpersonationStartedEvent.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/ImpersonationEndedEvent.java`

**Interfaces:**
- Produces: `AuthExchangeConstants.RK_LOGIN_SUCCESS`, `RK_LOGIN_FAILED`, `RK_LOGOUT`, `RK_PASSWORD_CHANGED`, `RK_IMPERSONATION_STARTED`, `RK_IMPERSONATION_ENDED` (all `String`) — used by `AuthEventPublisher` in Task 3. Note: unlike pre-trim, this class does **not** define `EVENTS_EXCHANGE`/`AUTH_EXCHANGE` — the exchange name is configurable now (`MessagingProperties.getExchange()`), not a compile-time constant.
- Produces: six event `record` types, used as `AuthEventPublisher` method parameters/payloads in Task 3.

These are plain data classes with no conditional logic, so this task has no dedicated unit test — they're exercised through `AuthEventPublisherTest` in Task 3.

- [ ] **Step 1: Restore `AuthExchangeConstants`**

```java
package com.example.authsvc.infrastructure.messaging.constant;

public final class AuthExchangeConstants {

    private AuthExchangeConstants() {}

    // ─── Routing keys ────────────────────────────────────────────────────────
    public static final String RK_LOGIN_SUCCESS         = "auth.login.success";
    public static final String RK_LOGIN_FAILED          = "auth.login.failed";
    public static final String RK_LOGOUT                = "auth.logout";
    public static final String RK_PASSWORD_CHANGED      = "auth.password.changed";
    public static final String RK_IMPERSONATION_STARTED = "auth.impersonation.started";
    public static final String RK_IMPERSONATION_ENDED   = "auth.impersonation.ended";
}
```

- [ ] **Step 2: Restore the six event records**

```java
// LoginSuccessEvent.java
package com.example.authsvc.infrastructure.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record LoginSuccessEvent(
        UUID userId,
        UUID tenantId,
        UUID sessionId,
        String ip,
        String userAgent,
        Instant timestamp
) {}
```

```java
// LoginFailedEvent.java
package com.example.authsvc.infrastructure.messaging.event;

import java.time.Instant;

/**
 * SHA-256 hash of the email is stored — raw PII must never appear in event payloads.
 */
public record LoginFailedEvent(
        String emailHash,
        String ip,
        String reason,
        Instant timestamp
) {}
```

```java
// LogoutEvent.java
package com.example.authsvc.infrastructure.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record LogoutEvent(
        UUID userId,
        UUID sessionId,
        Instant timestamp
) {}
```

```java
// PasswordChangedEvent.java
package com.example.authsvc.infrastructure.messaging.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record PasswordChangedEvent(
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("session_id") UUID sessionId,
        @JsonProperty("ts") Instant ts
) {}
```

```java
// ImpersonationStartedEvent.java
package com.example.authsvc.infrastructure.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record ImpersonationStartedEvent(
        UUID superAdminId,
        UUID tenantId,
        String reason,
        Instant expiresAt,
        Instant timestamp
) {}
```

```java
// ImpersonationEndedEvent.java
package com.example.authsvc.infrastructure.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record ImpersonationEndedEvent(
        UUID superAdminId,
        UUID tenantId,
        Instant timestamp
) {}
```

- [ ] **Step 3: Compile to verify no errors**

Run: `./gradlew :gen-auth-starter:compileJava`
Expected: BUILD SUCCESSFUL (these are standalone types, nothing references them yet)

- [ ] **Step 4: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/constant/AuthExchangeConstants.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/event/
git commit -m "restore exchange routing-key constants and event records"
```

---

### Task 3: `AuthEventPublisher` + `MessagingConfig` (conditional beans)

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/producer/AuthEventPublisher.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/config/MessagingConfig.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/messaging/producer/AuthEventPublisherTest.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/config/MessagingConfigTest.java`

**Interfaces:**
- Consumes: `AuthExchangeConstants.RK_*` and the six event records (Task 2), `MessagingProperties` (Task 1).
- Produces: `AuthEventPublisher` with public methods `publishLoginSuccess(UUID userId, UUID tenantId, UUID sessionId, String ip, String userAgent)`, `publishLoginFailed(String email, String ip, String reason)`, `publishLogout(UUID userId, UUID sessionId)`, `publishPasswordChanged(UUID userId, UUID sessionId, Instant timestamp)`, `publishImpersonationStarted(UUID superAdminId, UUID tenantId, String reason, Instant expiresAt)`, `publishImpersonationEnded(UUID superAdminId, UUID tenantId)` — all `void`, all swallow-and-log on failure (never throw). Consumed by Task 4-7's service classes as a **nullable** collaborator (`@Autowired(required = false)`).
- Produces: `MessagingConfig` — declares the `TopicExchange` bean so `RabbitAdmin` auto-declares the configured exchange on the broker.

Unlike pre-trim, `AuthEventPublisher` is now `@Component` + `@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")` — the bean itself does not exist when messaging is disabled (this is what lets Task 4-7's tests assert "no bean registered" instead of relying on an internal null check). Since the bean only exists when enabled, its `RabbitTemplate` constructor argument can be a required (non-nullable) dependency — Boot's `RabbitAutoConfiguration` always provides one once `spring-boot-starter-amqp` is on the classpath (see Global Constraints).

- [ ] **Step 1: Write the failing tests**

```java
package com.example.authsvc.infrastructure.messaging.producer;

import com.example.authsvc.config.properties.MessagingProperties;
import com.example.authsvc.infrastructure.messaging.constant.AuthExchangeConstants;
import com.example.authsvc.infrastructure.messaging.event.LoginFailedEvent;
import com.example.authsvc.infrastructure.messaging.event.LoginSuccessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthEventPublisherTest {

    @Mock private RabbitTemplate rabbitTemplate;

    private AuthEventPublisher publisher;

    @BeforeEach
    void setUp() {
        MessagingProperties props = new MessagingProperties();
        props.setExchange("auth.events");
        publisher = new AuthEventPublisher(rabbitTemplate, props);
    }

    @Test
    void publishLoginSuccess_sendsToConfiguredExchangeWithRoutingKey() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        publisher.publishLoginSuccess(userId, tenantId, sessionId, "1.2.3.4", "curl/8.0");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(
                eq("auth.events"), eq(AuthExchangeConstants.RK_LOGIN_SUCCESS), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isInstanceOf(LoginSuccessEvent.class);
        LoginSuccessEvent event = (LoginSuccessEvent) payloadCaptor.getValue();
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.sessionId()).isEqualTo(sessionId);
    }

    @Test
    void publishLoginFailed_hashesEmailInsteadOfStoringRaw() {
        publisher.publishLoginFailed("User@Example.com", "1.2.3.4", "INVALID_CREDENTIALS");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(
                eq("auth.events"), eq(AuthExchangeConstants.RK_LOGIN_FAILED), payloadCaptor.capture());
        LoginFailedEvent event = (LoginFailedEvent) payloadCaptor.getValue();
        assertThat(event.emailHash()).doesNotContain("user@example.com").hasSize(64);
    }

    @Test
    void publish_usesConfiguredExchangeName_notHardcoded() {
        MessagingProperties props = new MessagingProperties();
        props.setExchange("custom.exchange");
        publisher = new AuthEventPublisher(rabbitTemplate, props);

        publisher.publishLogout(UUID.randomUUID(), UUID.randomUUID());

        verify(rabbitTemplate).convertAndSend(
                eq("custom.exchange"), eq(AuthExchangeConstants.RK_LOGOUT), any());
    }

    @Test
    void publish_rabbitTemplateThrows_doesNotPropagate() {
        doThrow(new RuntimeException("broker down"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any());

        publisher.publishLogout(UUID.randomUUID(), UUID.randomUUID());
        // must not throw — auth flow must never fail because RabbitMQ is unavailable
    }
}
```

```java
package com.example.authsvc.config;

import com.example.authsvc.config.properties.MessagingProperties;
import com.example.authsvc.infrastructure.messaging.producer.AuthEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class MessagingConfigTest {

    /**
     * {@code withUserConfiguration} registers each class as plain {@code @Configuration} —
     * a bare {@code @ConfigurationProperties} class like {@link MessagingProperties} needs
     * either this scan or {@code @EnableConfigurationProperties} to actually get its fields
     * bound from {@code withPropertyValues(...)}; production code relies on the starter's
     * {@code @ConfigurationPropertiesScan("com.example.authsvc")} in {@code GenAuthAutoConfiguration},
     * which this isolated test context doesn't load.
     */
    @Configuration
    @ConfigurationPropertiesScan("com.example.authsvc.config.properties")
    static class PropertiesTestConfig {}

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
            .withUserConfiguration(PropertiesTestConfig.class, MessagingConfig.class, AuthEventPublisher.class);

    // NOTE for the JWKS/starter-library-era Spring Boot 4 module split: RabbitAutoConfiguration
    // moved from org.springframework.boot.autoconfigure.amqp to org.springframework.boot.amqp.autoconfigure
    // (same class of relocation as EntityScan/FlywayAutoConfiguration hit earlier in this project) —
    // if this import doesn't resolve, check the actual package inside the resolved
    // spring-boot-amqp-*.jar on the classpath rather than guessing.

    @Test
    void messagingDisabled_noExchangeOrPublisherBean() {
        contextRunner
                .withPropertyValues("app.messaging.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TopicExchange.class);
                    assertThat(context).doesNotHaveBean(AuthEventPublisher.class);
                });
    }

    @Test
    void messagingEnabled_exchangeAndPublisherBeanPresent() {
        contextRunner
                .withPropertyValues("app.messaging.enabled=true", "app.messaging.exchange=auth.events")
                .run(context -> {
                    assertThat(context).hasSingleBean(TopicExchange.class);
                    assertThat(context.getBean(TopicExchange.class).getName()).isEqualTo("auth.events");
                    assertThat(context).hasSingleBean(AuthEventPublisher.class);
                });
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.infrastructure.messaging.producer.AuthEventPublisherTest" --tests "com.example.authsvc.config.MessagingConfigTest"`
Expected: FAIL (compilation error) — `AuthEventPublisher`/`MessagingConfig` don't exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.authsvc.infrastructure.messaging.producer;

import com.example.authsvc.config.properties.MessagingProperties;
import com.example.authsvc.infrastructure.messaging.constant.AuthExchangeConstants;
import com.example.authsvc.infrastructure.messaging.event.ImpersonationEndedEvent;
import com.example.authsvc.infrastructure.messaging.event.ImpersonationStartedEvent;
import com.example.authsvc.infrastructure.messaging.event.LoginFailedEvent;
import com.example.authsvc.infrastructure.messaging.event.LoginSuccessEvent;
import com.example.authsvc.infrastructure.messaging.event.LogoutEvent;
import com.example.authsvc.infrastructure.messaging.event.PasswordChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Only registered when {@code app.messaging.enabled=true} — see
 * {@code MessagingConfig} for the companion {@code TopicExchange} bean.
 * Every {@code publishX} method swallows and logs failures instead of
 * propagating them: auth flow must never fail because RabbitMQ is
 * unavailable.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AuthEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties messagingProperties;

    public void publishLoginSuccess(UUID userId, UUID tenantId, UUID sessionId,
                                    String ip, String userAgent) {
        LoginSuccessEvent event = new LoginSuccessEvent(
                userId, tenantId, sessionId, ip, userAgent, Instant.now());
        publish(AuthExchangeConstants.RK_LOGIN_SUCCESS, event);
    }

    /**
     * Email is SHA-256 hashed before inclusion in the event payload.
     * Raw PII must never appear in event messages.
     */
    public void publishLoginFailed(String email, String ip, String reason) {
        LoginFailedEvent event = new LoginFailedEvent(
                sha256(email), ip, reason, Instant.now());
        publish(AuthExchangeConstants.RK_LOGIN_FAILED, event);
    }

    public void publishLogout(UUID userId, UUID sessionId) {
        LogoutEvent event = new LogoutEvent(userId, sessionId, Instant.now());
        publish(AuthExchangeConstants.RK_LOGOUT, event);
    }

    public void publishPasswordChanged(UUID userId, UUID sessionId, Instant timestamp) {
        PasswordChangedEvent event = new PasswordChangedEvent(userId, sessionId, timestamp);
        publish(AuthExchangeConstants.RK_PASSWORD_CHANGED, event);
    }

    public void publishImpersonationStarted(UUID superAdminId, UUID tenantId,
                                             String reason, Instant expiresAt) {
        ImpersonationStartedEvent event = new ImpersonationStartedEvent(
                superAdminId, tenantId, reason, expiresAt, Instant.now());
        publish(AuthExchangeConstants.RK_IMPERSONATION_STARTED, event);
    }

    public void publishImpersonationEnded(UUID superAdminId, UUID tenantId) {
        ImpersonationEndedEvent event = new ImpersonationEndedEvent(
                superAdminId, tenantId, Instant.now());
        publish(AuthExchangeConstants.RK_IMPERSONATION_ENDED, event);
    }

    private void publish(String routingKey, Object event) {
        try {
            rabbitTemplate.convertAndSend(messagingProperties.getExchange(), routingKey, event);
            log.info("event.published routingKey={} eventType={}",
                    routingKey, event.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("event.publish_failed routingKey={} eventType={} reason={}",
                    routingKey, event.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    input.toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JVM spec — this can never happen.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
```

```java
package com.example.authsvc.config;

import com.example.authsvc.config.properties.MessagingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares only the exchange events publish to — no queues, no bindings.
 * Consuming applications own their own queue/binding topology; that is not
 * this starter's concern. Only active when {@code app.messaging.enabled=true}.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MessagingConfig {

    private final MessagingProperties messagingProperties;

    @Bean
    public TopicExchange authEventsExchange() {
        return new TopicExchange(messagingProperties.getExchange());
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.infrastructure.messaging.producer.AuthEventPublisherTest" --tests "com.example.authsvc.config.MessagingConfigTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/producer/AuthEventPublisher.java gen-auth-starter/src/main/java/com/example/authsvc/config/MessagingConfig.java gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/messaging/producer/AuthEventPublisherTest.java gen-auth-starter/src/test/java/com/example/authsvc/config/MessagingConfigTest.java
git commit -m "restore AuthEventPublisher and MessagingConfig as conditional beans gated on app.messaging.enabled"
```

---

### Task 4: Wire into `LoginExecutionServiceImpl` (login success + login failed)

**Files:**
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/LoginExecutionServiceImpl.java`
- Modify: `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/LoginExecutionServiceImplTest.java`

**Interfaces:**
- Consumes: `AuthEventPublisher.publishLoginSuccess(...)`, `AuthEventPublisher.publishLoginFailed(...)` (Task 3), the existing `authAsync`-qualified `Executor asyncExecutor` field already on this class.
- No new interface produced — `executeLogin`/`handleFailure` signatures are unchanged.

This class uses an explicit (non-Lombok) constructor. Add `AuthEventPublisher authEventPublisher` as a new final field, injected via `@Autowired(required = false)` so the class still boots when messaging is disabled (no `AuthEventPublisher` bean exists in that case). Both call sites wrap in `asyncExecutor.execute(...)` with a null-check, mirroring the existing `refreshTokenAuditRepo` async block already in `executeLogin`.

- [ ] **Step 1: Write the failing test**

Add to `LoginExecutionServiceImplTest.java`: a new mock field, pass it into the constructor call in `setUp()`, and two new test methods that capture the `Runnable` passed to `asyncExecutor.execute(...)` and run it manually (the mock executor doesn't run submitted work on its own).

```java
    @Mock private AuthEventPublisher authEventPublisher;
```

Add `authEventPublisher` as the last argument to the existing `new LoginExecutionServiceImpl(...)` call in `setUp()`.

```java
    @Test
    void loginSuccess_publishesLoginSuccessEventAsync() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId)
                .tenantId(tenantId)
                .email("existing@example.com")
                .passwordHash("stored-hash")
                .userType(UserType.TENANT_USER)
                .active(true)
                .build();

        LoginRequest request = new LoginRequest();
        request.setEmail("existing@example.com");
        request.setPassword("Password123!");

        when(passwordHasher.verify(request.getPassword(), user.getPasswordHash())).thenReturn(true);
        when(authUserRoleRepository.findRoleIdsByUserId(userId)).thenReturn(List.of());
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        Instant now = Instant.now();
        when(jwtUtils.generateTokenPair(any(JwtClaims.class))).thenReturn(
                new TokenPair("access-token", "refresh-token", now.plusSeconds(900), now.plusSeconds(604800)));
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("");

        service.executeLogin(user, request, "1.2.3.4", "curl/8.0", System.currentTimeMillis());

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(asyncExecutor, org.mockito.Mockito.atLeastOnce()).execute(runnableCaptor.capture());
        runnableCaptor.getAllValues().forEach(Runnable::run);

        verify(authEventPublisher).publishLoginSuccess(eq(userId), eq(tenantId), any(UUID.class),
                eq("1.2.3.4"), eq("curl/8.0"));
    }

    @Test
    void loginFailure_publishesLoginFailedEventAsync() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        service.handleFailure(tenantId, userId, "bad@example.com", "1.2.3.4", "curl/8.0", "INVALID_CREDENTIALS");

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(asyncExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        verify(authEventPublisher).publishLoginFailed("bad@example.com", "1.2.3.4", "INVALID_CREDENTIALS");
    }
```

Add the missing imports (`AuthEventPublisher`, `eq`) at the top of the test file.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.application.impl.LoginExecutionServiceImplTest"`
Expected: FAIL — constructor call doesn't match (wrong arg count), and `authEventPublisher` is unresolved.

- [ ] **Step 3: Write minimal implementation**

In `LoginExecutionServiceImpl.java`, add the import, field, constructor parameter, and assignment:

```java
import com.example.authsvc.infrastructure.messaging.producer.AuthEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
```

```java
    private final AuthSessionJpaRepository      sessionRepo;
    private final AuthRefreshTokenJpaRepository refreshTokenAuditRepo;
    private final AuthUserRoleJpaRepository     authUserRoleRepository;
    private final RefreshTokenStore             refreshTokenStore;
    private final LoginAttemptService           loginAttemptService;
    private final LockoutService                lockoutService;
    private final AuditLogService               auditLogService;
    private final JwtUtils                      jwtUtils;
    private final PasswordHasher                passwordHasher;
    private final Executor                      asyncExecutor;
    private final TransactionTemplate           txTemplate;
    private final TenantSlugResolver            tenantSlugResolver;
    private final AuthEventPublisher            authEventPublisher;

    public LoginExecutionServiceImpl(
            AuthSessionJpaRepository sessionRepo,
            AuthRefreshTokenJpaRepository refreshTokenAuditRepo,
            AuthUserRoleJpaRepository authUserRoleRepository,
            RefreshTokenStore refreshTokenStore,
            LoginAttemptService loginAttemptService,
            LockoutService lockoutService,
            AuditLogService auditLogService,
            JwtUtils jwtUtils,
            PasswordHasher passwordHasher,
            @Qualifier("authAsync") Executor asyncExecutor,
            PlatformTransactionManager txManager,
            TenantSlugResolver tenantSlugResolver,
            @Autowired(required = false) AuthEventPublisher authEventPublisher) {
        this.sessionRepo            = sessionRepo;
        this.refreshTokenAuditRepo  = refreshTokenAuditRepo;
        this.authUserRoleRepository = authUserRoleRepository;
        this.refreshTokenStore      = refreshTokenStore;
        this.loginAttemptService    = loginAttemptService;
        this.lockoutService         = lockoutService;
        this.auditLogService        = auditLogService;
        this.jwtUtils               = jwtUtils;
        this.passwordHasher         = passwordHasher;
        this.asyncExecutor          = asyncExecutor;
        this.txTemplate             = new TransactionTemplate(txManager);
        this.tenantSlugResolver     = tenantSlugResolver;
        this.authEventPublisher     = authEventPublisher;
    }
```

In `executeLogin`, insert a new async block right before the existing `refreshTokenAuditRepo` archive block (after the `auditLogService.log(...)` call, before `asyncExecutor.execute(() -> { refreshTokenAuditRepo... })`):

```java
        asyncExecutor.execute(() -> {
            if (authEventPublisher != null) {
                authEventPublisher.publishLoginSuccess(fUserId, fTenantId, fSessionId, fIp, fUa);
            }
        });
```

In `handleFailure`, add an async block after the existing `auditLogService.log(...)` call:

```java
    @Override
    public void handleFailure(UUID tenantId, UUID userId, String email,
                              String ip, String userAgent, String reason) {
        log.warn("login.failed email={} ip={} reason={}", email, ip, reason);

        lockoutService.recordFailure(email, ip);

        loginAttemptService.record(new LoginAttemptRequest(
                tenantId, userId, email, ip, userAgent, false, reason));
        auditLogService.log(new AuditLogRequest(
                tenantId, userId, "LOGIN_FAILED", ip, userAgent, reason));

        asyncExecutor.execute(() -> {
            if (authEventPublisher != null) {
                authEventPublisher.publishLoginFailed(email, ip, reason);
            }
        });
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.application.impl.LoginExecutionServiceImplTest"`
Expected: PASS (all tests, including the two new ones and the pre-existing ones — the mock `authEventPublisher` defaults to non-null in Mockito, so existing tests that don't stub/verify it still pass unaffected)

- [ ] **Step 5: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/application/impl/LoginExecutionServiceImpl.java gen-auth-starter/src/test/java/com/example/authsvc/application/impl/LoginExecutionServiceImplTest.java
git commit -m "publish login-success and login-failed events from LoginExecutionServiceImpl"
```

---

### Task 5: Wire into `ChangePasswordServiceImpl` and `ImpersonationTokenServiceImpl`

**Files:**
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ChangePasswordServiceImpl.java`
- Modify: `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/ChangePasswordServiceImplTest.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ImpersonationTokenServiceImpl.java`
- Modify: `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/ImpersonationTokenServiceImplTest.java`

**Interfaces:**
- Consumes: `AuthEventPublisher.publishPasswordChanged(...)`, `AuthEventPublisher.publishImpersonationStarted(...)` (Task 3).
- No new interfaces produced — both classes' public method signatures are unchanged.

Both classes use Lombok `@RequiredArgsConstructor`. Lombok copies field annotations whose `@Target` includes both `FIELD` and `PARAMETER` onto the generated constructor parameter — `@Autowired`'s target includes both, so annotating the field directly is sufficient (no explicit constructor needed), matching the nullable-collaborator pattern already used inside `AuthEventPublisher` itself pre-Task-3-rewrite.

`ImpersonationTokenServiceImpl.issue(...)` currently has no impersonation-started publish call at all (pre-trim never wired one either — `publishImpersonationEnded` has no call site anywhere in this codebase, pre-trim or otherwise, since there is no end-impersonation flow; do not invent one).

- [ ] **Step 1: Write the failing tests**

In `ChangePasswordServiceImplTest.java`, add the mock and update `setUp()`:

```java
    @Mock private AuthEventPublisher authEventPublisher;
```

```java
        service = new ChangePasswordServiceImpl(
                userRepo,
                sessionRepo,
                refreshTokenRepo,
                refreshTokenStore,
                passwordHasher,
                authEventPublisher
        );
```

Add a new test:

```java
    @Test
    void changePassword_publishesPasswordChangedEvent() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        AuthenticatedUser principal = new AuthenticatedUser(
                userId,
                tenantId,
                "test-tenant",
                List.of(roleId),
                UserType.TENANT_USER,
                sessionId.toString(),
                Instant.now().plusSeconds(900),
                UUID.randomUUID().toString()
        );

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId)
                .tenantId(tenantId)
                .email("user@example.com")
                .passwordHash("stored-hash")
                .userType(UserType.TENANT_USER)
                .roleId(roleId)
                .active(true)
                .build();

        ChangePasswordRequest request = new ChangePasswordRequest(
                "OldPassword123", "NewPassword123!", "NewPassword123!");

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(request.currentPassword(), user.getPasswordHash())).thenReturn(true);
        when(passwordHasher.hash(request.newPassword())).thenReturn("new-hash");
        when(refreshTokenRepo.findActiveFamilyIdsByUserIdExcludingSession(userId, sessionId))
                .thenReturn(List.of());

        service.changePassword(principal, request);

        verify(authEventPublisher).publishPasswordChanged(eq(userId), eq(sessionId), any(Instant.class));
    }
```

This mirrors the existing `changePasswordRevokesSessionsAndTokens` test already in this file (same `AuthenticatedUser`/`ChangePasswordRequest` construction pattern) — add `import com.example.authsvc.domain.enums.UserType;` and `import java.util.List;` if not already present at the top of the test file.

In `ImpersonationTokenServiceImplTest.java`, add the mock and update all three `new ImpersonationTokenServiceImpl(...)` calls:

```java
    @Mock private AuthEventPublisher authEventPublisher;
```

```java
        ImpersonationTokenServiceImpl service =
                new ImpersonationTokenServiceImpl(jwtUtils, authSessionRepository, tenantSlugResolver, authEventPublisher);
```

Add a new test:

```java
    @Test
    void issue_publishesImpersonationStartedEvent() throws Exception {
        ImpersonationTokenServiceImpl service =
                new ImpersonationTokenServiceImpl(jwtUtils, authSessionRepository, tenantSlugResolver, authEventPublisher);
        setExpirationMinutes(service, 60);

        UUID superAdminId = UUID.randomUUID();
        UUID tenantId     = UUID.randomUUID();
        UUID roleId       = UUID.randomUUID();
        when(jwtUtils.generateAccessToken(any(JwtClaims.class))).thenReturn("signed-jwt");

        ImpersonationTokenRequest request = new ImpersonationTokenRequest(
                superAdminId, tenantId, "acme", roleId, "session-abc", false);

        service.issue(request);

        verify(authEventPublisher).publishImpersonationStarted(
                eq(superAdminId), eq(tenantId), any(), any());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.application.impl.ChangePasswordServiceImplTest" --tests "com.example.authsvc.application.impl.ImpersonationTokenServiceImplTest"`
Expected: FAIL — constructor arg count mismatch.

- [ ] **Step 3: Write minimal implementation**

In `ChangePasswordServiceImpl.java`, add the import and field:

```java
import com.example.authsvc.infrastructure.messaging.producer.AuthEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
```

```java
    private final AuthUserJpaRepository userRepo;
    private final AuthSessionJpaRepository sessionRepo;
    private final AuthRefreshTokenJpaRepository refreshTokenRepo;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordHasher passwordHasher;

    @Autowired(required = false)
    private final AuthEventPublisher authEventPublisher;
```

And add the publish call where the log line already reports success:

```java
            if (authEventPublisher != null) {
                authEventPublisher.publishPasswordChanged(user.getId(), currentSessionId, now);
            }
            log.info("password.change.success userId={} sessionId={}", user.getId(), currentSessionId);
```

In `ImpersonationTokenServiceImpl.java`, add the import and field:

```java
import com.example.authsvc.infrastructure.messaging.producer.AuthEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
```

```java
    private final JwtUtils                 jwtUtils;
    private final AuthSessionJpaRepository authSessionRepository;
    private final TenantSlugResolver       tenantSlugResolver;

    @Autowired(required = false)
    private final AuthEventPublisher authEventPublisher;
```

And add the publish call right before the existing `log.info("impersonation.token.issued ...")` line in `issue(...)`:

```java
        if (authEventPublisher != null) {
            authEventPublisher.publishImpersonationStarted(
                    request.superAdminId(), request.tenantId(), request.writeConsent() ? "write-consent" : null, expiresAt);
        }

        int expiresInSeconds = ttlMinutes * 60;
        log.info("impersonation.token.issued superAdminId={} tenantId={} sessionId={} expiresIn={}",
                request.superAdminId(), request.tenantId(), request.sessionId(), expiresInSeconds);
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.application.impl.ChangePasswordServiceImplTest" --tests "com.example.authsvc.application.impl.ImpersonationTokenServiceImplTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ChangePasswordServiceImpl.java gen-auth-starter/src/test/java/com/example/authsvc/application/impl/ChangePasswordServiceImplTest.java gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ImpersonationTokenServiceImpl.java gen-auth-starter/src/test/java/com/example/authsvc/application/impl/ImpersonationTokenServiceImplTest.java
git commit -m "publish password-changed and impersonation-started events"
```

---

### Task 6: Wire into `RefreshTokenServiceImpl` (logout, 4 call sites)

**Files:**
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/RefreshTokenServiceImpl.java`

**Interfaces:**
- Consumes: `AuthEventPublisher.publishLogout(UUID userId, UUID sessionId)` (Task 3).
- No new interface produced — public method signatures unchanged.

This class has no existing test file (`RefreshTokenServiceImplTest` does not exist in this repo — it was not restored/recreated in the email or super-admin slices either). Given the change here is a one-line null-checked call at four existing call sites — not new business logic — this task does not create a new test class from scratch; `AuthEventPublisherTest` (Task 3) already covers `publishLogout`'s correctness, and the manual verification in Task 8 confirms the wiring end-to-end against a real broker. If a future slice adds a `RefreshTokenServiceImplTest`, it should cover this at that point.

- [ ] **Step 1: Add the field**

```java
import com.example.authsvc.infrastructure.messaging.producer.AuthEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
```

```java
    /** Primary active-token store — Redis/Valkey. */
    private final RefreshTokenStore refreshTokenStore;

    @Autowired(required = false)
    private final AuthEventPublisher authEventPublisher;
```

- [ ] **Step 2: Wire the four `publishLogout` call sites**

There are four places in this file where a session is deactivated — natural-expiry cleanup (in `refresh()`), absolute-expiry enforcement (in `refresh()`), replay-attack revocation (`handleReplayAttack`), and explicit `logout()`. Each publishes a `LogoutEvent` right after the session is marked inactive.

**Site 1 — natural-expiry cleanup, inside `refresh()`:**

```java
                    sessionRepo.findById(auditRecord.getSessionId()).ifPresent(s -> {
                        if (s.isActive()) {
                            s.setActive(false);
                            s.setRevokedAt(now);
                            sessionRepo.save(s);
                            log.info("session.revoked sessionId={} reason=natural_expiry",
                                    s.getId());
                            if (authEventPublisher != null) {
                                authEventPublisher.publishLogout(auditRecord.getUserId(), auditRecord.getSessionId());
                            }
                        }
                    });
```

**Site 2 — absolute-expiry enforcement, inside `refresh()`:**

```java
            sessionRepo.findById(cached.getSessionId()).ifPresent(s -> {
                s.setActive(false);
                s.setRevokedAt(now);
                sessionRepo.save(s);
                log.info("session.revoked sessionId={} reason=absolute_expiry", s.getId());
                if (authEventPublisher != null) {
                    authEventPublisher.publishLogout(cached.getUserId(), cached.getSessionId());
                }
            });
```

**Site 3 — replay-attack revocation, in `handleReplayAttack(...)`:**

```java
        sessionRepo.findById(auditRecord.getSessionId()).ifPresent(session -> {
            session.setActive(false);
            session.setRevokedAt(Instant.now());
            sessionRepo.save(session);
            log.warn("session.revoked sessionId={} reason=replay_attack familyId={}",
                    session.getId(), auditRecord.getFamilyId());
            if (authEventPublisher != null) {
                authEventPublisher.publishLogout(auditRecord.getUserId(), auditRecord.getSessionId());
            }
        });
```

**Site 4 — explicit `logout(...)`:**

```java
        log.info("auth.logout userId={} sessionId={} ip={}",
                cached.getUserId(), cached.getSessionId(), ipAddress);

        if (authEventPublisher != null) {
            authEventPublisher.publishLogout(cached.getUserId(), cached.getSessionId());
        }

        auditLogService.log(new AuditLogRequest(
                cached.getTenantId(), cached.getUserId(), "LOGOUT",
                ipAddress, userAgent, "sessionId=" + cached.getSessionId()));
```

- [ ] **Step 3: Compile and run the full starter test suite**

Run: `./gradlew :gen-auth-starter:test`
Expected: BUILD SUCCESSFUL — no existing test exercises `RefreshTokenServiceImpl` directly, so this step is a regression check on the rest of the suite (nothing else should reference this class's constructor).

- [ ] **Step 4: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/application/impl/RefreshTokenServiceImpl.java
git commit -m "publish logout events from RefreshTokenServiceImpl"
```

---

### Task 7: docker-compose + demo app example config

**Files:**
- Modify: `docker-compose.yml`
- Modify: `gen-auth-demo/src/main/resources/application.yaml`

**Interfaces:**
- None — this is manual-verification infrastructure, not code exercised by any automated test.

- [ ] **Step 1: Add the RabbitMQ service to `docker-compose.yml`**

```yaml
  rabbitmq:
    image: rabbitmq:3-management
    ports:
      - '5673:5672'   # AMQP
      - '15673:15672'  # Management UI — open http://localhost:15673 (guest/guest)
```

Add it after the existing `mailhog` service, before the top-level `volumes:` key.

- [ ] **Step 2: Add `spring.rabbitmq` connection config to `gen-auth-demo/src/main/resources/application.yaml`**

Insert after the existing `mail:` block under `spring:` (currently ending at line 78):

```yaml
  # ===================================================================
  # RABBITMQ (optional — see app.messaging.enabled below)
  # ===================================================================
  rabbitmq:
    host: localhost
    port: 5673
```

- [ ] **Step 3: Add the `app.messaging` example block**

Insert after the existing `super-admin:` block under `app:` (currently ending at line 155), before the `# JWT CONFIG` section header:

```yaml
  # ===================================================================
  # RABBITMQ EVENT PUBLISHING (optional — off by default)
  # ===================================================================
  # To try this locally: start RabbitMQ (docker compose up -d rabbitmq),
  # flip the flag to true, then either open http://localhost:15673
  # (guest/guest) or bind a throwaway consumer to the auth.events exchange
  # to see login/logout/password-changed/impersonation events land.
  messaging:
    enabled: false
    exchange: auth.events
```

- [ ] **Step 4: Verify the demo app still boots with messaging disabled**

Run: `./gradlew :gen-auth-demo:bootRun` (with Postgres/Redis up per the existing local-dev setup; `Ctrl+C` once it logs successful startup)
Expected: starts cleanly with no RabbitMQ connection attempted (no rabbit-related log lines) — confirms the off-by-default path is truly zero-cost.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml gen-auth-demo/src/main/resources/application.yaml
git commit -m "add RabbitMQ to docker-compose and example app.messaging config to demo app"
```

---

### Task 8: Manual end-to-end verification

**Files:** None modified — this is a verification pass, not a code change.

- [ ] **Step 1: Start supporting infra**

Run: `docker compose up -d postgres redis rabbitmq`

- [ ] **Step 2: Boot the demo app with messaging enabled**

Set `app.messaging.enabled=true` (env var or command-line override: `--app.messaging.enabled=true`) and start `gen-auth-demo`. Confirm in the logs that no connection errors appear and the app boots cleanly.

- [ ] **Step 3: Bind a throwaway consumer to observe events**

Using the RabbitMQ management UI at `http://localhost:15673` (guest/guest): create a temporary queue, bind it to the `auth.events` exchange with routing key `auth.#`, then exercise register → login → change-password → logout via the demo app's HTTP endpoints. Confirm `auth.login.success`, `auth.password.changed`, and `auth.logout` messages arrive on the queue with the expected JSON payload shape (matching the six event records from Task 2). Delete the temporary queue afterward.

- [ ] **Step 4: Confirm the off state is still silent**

Set `app.messaging.enabled=false`, reboot the demo app, repeat the same login/logout flow, and confirm no messages arrive on any exchange and no RabbitMQ-related errors appear in the logs.

- [ ] **Step 5: Record the outcome**

No commit for this task — note the verification result in the whole-branch review summary when this plan's execution wraps up.

---

## Post-plan

Once all tasks are merged, the deferred-features restoration project (email+magic-link → super-admin/impersonation → RabbitMQ) is complete. Per `docs/superpowers/specs/2026-07-16-restore-deferred-features-design.md`, the original punch list resumes at **MFA** (spec already written at `docs/superpowers/specs/2026-07-15-mfa-design.md`, not yet planned) then **OAuth** (not started).
