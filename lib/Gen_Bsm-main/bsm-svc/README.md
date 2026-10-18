# bsm-svc — Business Service Management

Core business service layer: manages business entities, configurations, and cross-cutting operations for the CPMS platform. Integrates with RabbitMQ for async event processing.

## Tech Stack

- **Runtime**: Java 21 (virtual threads)
- **Framework**: Spring Boot + Spring Security
- **Database**: PostgreSQL via JPA + Flyway
- **Cache**: Valkey (Spring Data Redis + Lettuce)
- **Messaging**: RabbitMQ (Spring AMQP)
- **Email**: SMTP (Gmail default)
- **Observability**: OpenTelemetry (OTLP), Prometheus metrics
- **API Docs**: Springdoc OpenAPI 3

## Port

`8085`

## Quick Start

```bash
# 1. Start shared infra
make infra-up

# 2. Copy and fill env
cp apps/bsm-svc/.env.example apps/bsm-svc/.env

# 3. Run
make bsm-up
```

## Environment Variables

| Key | Required | Default | Description |
|-----|----------|---------|-------------|
| `BSM_DB_URL` | yes | — | PostgreSQL JDBC URL |
| `BSM_DB_USERNAME` | yes | — | DB username |
| `BSM_DB_PASSWORD` | yes | — | DB password |
| `MAIL_USERNAME` | yes | — | SMTP username |
| `MAIL_PASSWORD` | yes | — | SMTP password |
| `BSM_REDIS_HOST` | no | `localhost` | Valkey/Redis host |
| `BSM_REDIS_PORT` | no | `6379` | Valkey/Redis port |
| `BSM_REDIS_SSL` | no | `false` | Valkey TLS |
| `BSM_RABBITMQ_HOST` | no | `localhost` | RabbitMQ host |
| `BSM_RABBITMQ_PORT` | no | `5672` | RabbitMQ port |
| `BSM_RABBITMQ_USER` | no | `guest` | RabbitMQ username |
| `BSM_RABBITMQ_PASS` | no | `guest` | RabbitMQ password |
| `BSM_RABBITMQ_VHOST` | no | `/` | RabbitMQ virtual host |
| `BSM_DB_MAX_POOL_SIZE` | no | `20` | Hikari max pool size |
| `BSM_DB_MIN_IDLE` | no | `5` | Hikari min idle |
| `OTLP_TRACING_ENDPOINT` | no | `http://localhost:4318/v1/traces` | OTLP traces endpoint |
| `TRACING_SAMPLING_PROBABILITY` | no | `1.0` | Tracing sample rate |
| `SPRING_PROFILES_ACTIVE` | no | `local` | Active Spring profile |
| `SERVER_PORT` | no | `8085` | HTTP port |

## API

- **Base path**: `/api/v1`
- **Swagger UI**: `http://localhost:8085/swagger-ui.html`
- **Health**: `GET /actuator/health`

## RabbitMQ

- **Exchange**: `cpms.events`
- Consumes and publishes business domain events

## Database

PostgreSQL: Flyway migrations at `classpath:db/migration` (table: `flyway_schema_history_bsm`)

## Makefile Commands

| Command | Description |
|---------|-------------|
| `make bsm-up` | Start Spring Boot dev server (port 8085) |
| `make build-java` | Build all Java services |
| `make infra-up` | Start shared Valkey + pgweb |

## Docker

```bash
./gradlew :apps:bsm-svc:bootJar
docker build -f apps/bsm-svc/Dockerfile -t cpms/bsm-svc:dev .
```
