# ══════════════════════════════════════════════════════════════
# AUTH-SVC Dockerfile — Multi-stage monorepo build
# Build context: repo root (needs libs/java-common)
# ══════════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Gradle wrapper + root build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Shared library
COPY libs/java-common libs/java-common

# Service source
COPY apps/auth-svc apps/auth-svc

RUN chmod +x gradlew && ./gradlew :apps:auth-svc:bootJar --no-daemon

# ── Runtime Stage ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=build /app/apps/auth-svc/build/libs/*.jar app.jar

RUN chown -R app:app /app
USER app

EXPOSE 8101

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8101/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
