# Gen_TNT

Generalized tenant-provisioning service, genericized from CPMS-Platform's `tnt-svc`. Two modules:

- `gen-tnt-starter` — the reusable library (Tenant CRUD + provisioning saga), embeddable in any Spring Boot project.
- `gen-tnt-demo` — thin reference app deploying the starter standalone, for non-Java consumers to call over HTTP.

See `docs/superpowers/specs/2026-07-20-gen-tnt-provisioning-design.md` for the full design.

**Integrating this into your own app? Start with [`docs/integration-guide.md`](docs/integration-guide.md)** — embedding vs. calling over HTTP, required config, the full HTTP API, and the webhook contract your provisioning step targets need to implement.

Interactive API docs (Swagger UI) are available at `/swagger-ui.html` on any running instance (`gen-tnt-demo` locally: `http://localhost:8201/swagger-ui.html`).

## Running it locally

```bash
docker compose up -d   # Postgres on 5435, Redis on 6381
python3 gen-tnt-demo/scripts/mock_step_server.py &
INTERNAL_SERVICE_SECRET=dev-secret \
TNT_DB_URL=jdbc:postgresql://localhost:5435/gentnt \
TNT_DB_USERNAME=postgres TNT_DB_PASSWORD=postgres \
TNT_REDIS_HOST=localhost TNT_REDIS_PORT=6381 \
./gradlew.bat :gen-tnt-demo:bootRun
```

Then in another terminal: `./gen-tnt-demo/scripts/smoke-provisioning.sh`

### One environment gotcha, worth knowing about

**JVM default timezone can break Flyway/Postgres, depending on locale.** On some machines (observed on Windows with an India-region locale), `TimeZone.getDefault()` resolves to a legacy IANA alias (e.g. `Asia/Calcutta` instead of `Asia/Kolkata`) that the Postgres image's tzdata rejects outright — every JDBC connection, including Flyway's, fails at connect time with `FATAL: invalid value for parameter "TimeZone"`. If you hit this, prefix the `bootRun` command above with `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC`. Not universal — a US/UTC-region machine won't need it — but worth trying first if `bootRun` fails immediately on a DB connection. (Same class of gotcha Gen_Auth's own README documents; `gen-tnt-starter`'s own test suite already forces this via a Gradle `jvmArgs` setting, but that only covers `./gradlew test`, not `bootRun`.)
