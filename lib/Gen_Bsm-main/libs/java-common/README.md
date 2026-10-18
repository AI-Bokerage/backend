# java-common

Shared code consumed by all 9 Java services:
- `errors/` — standard error response, exception hierarchy
- `auth/` — JWT verification, permission evaluator client
- `messaging/` — RabbitMQ envelope, event publisher
- `audit/` — audit event emitter
- `tenant/` — tenant context filter, RLS helper
