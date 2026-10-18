# ppm-svc — Plan & Pricing Management Service

Manages subscription plan catalog, plan features, pricing tiers, and plan-change lifecycle. Consumed by reg-svc (plan resolution at signup) and usg-svc (limit enforcement).

## Tech Stack

- **Runtime**: Java 21 (virtual threads)
- **Framework**: Spring Boot + Spring Security (OAuth2 Resource Server)
- **Database**: PostgreSQL via JPA + Flyway
- **Cache**: Valkey (Spring Data Redis)
- **Messaging**: RabbitMQ (Spring AMQP)
- **Observability**: OpenTelemetry (OTLP), Prometheus
- **API Docs**: Springdoc OpenAPI 3

## Port

`8106`

## Quick Start

```bash
# 1. Start shared infra
make infra-up

# 2. Copy and fill env
cp apps/ppm-svc/.env.example apps/ppm-svc/.env

# 3. Run
./gradlew :apps:ppm-svc:bootRun
```

## Environment Variables

| Key | Required | Default | Description |
|-----|----------|---------|-------------|
| `PPM_DB_URL` | no | `jdbc:postgresql://localhost:5432/ppmdb` | PostgreSQL JDBC URL |
| `PPM_DB_USERNAME` | no | `ppm_owner` | DB username |
| `PPM_DB_PASSWORD` | no | `changeme` | DB password |
| `AUTH_SVC_JWKS_URI` | no | `http://localhost:8101/.well-known/jwks.json` | auth-svc JWKS URI |
| `RABBITMQ_ADDRESSES` | no | `amqp://guest:guest@localhost:5672` | RabbitMQ AMQP address |
| `PPM_REDIS_HOST` | no | `localhost` | Valkey/Redis host |
| `PPM_REDIS_PORT` | no | `6379` | Valkey/Redis port |
| `PPM_DB_MAX_POOL_SIZE` | no | `20` | Hikari max pool size |
| `PPM_DB_MIN_IDLE` | no | `5` | Hikari min idle |
| `OTLP_TRACING_ENDPOINT` | no | `http://localhost:4318/v1/traces` | OTLP traces endpoint |
| `TRACING_SAMPLING_PROBABILITY` | no | `1.0` | Tracing sample rate |
| `SPRING_PROFILES_ACTIVE` | no | `local` | Active Spring profile |
| `SERVER_PORT` | no | `8106` | HTTP port |

## API

- **Base path**: `/api/v1`
- **Swagger UI**: `http://localhost:8106/v1/docs`
- **Health**: `GET /actuator/health`

## RabbitMQ

- **Exchange**: `cpms.events` (configured, connection + template only)
- No publishers are implemented yet — this is platform-standard wiring (matches adm-svc/bsm-svc/pmt-svc/tnt-svc conventions) reserved for future plan-change event publishing.

## Database

PostgreSQL: Flyway migrations at `classpath:db/migration` (table: `flyway_schema_history_ppm`)

## Makefile Commands

```bash
# No dedicated make target — run directly:
./gradlew :apps:ppm-svc:bootRun

make build-java   # build all Java services
make infra-up     # start shared Valkey + pgweb
```

## Docker

```bash
./gradlew :apps:ppm-svc:bootJar
docker build -f apps/ppm-svc/Dockerfile -t cpms/ppm-svc:dev .
```
