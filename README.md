# Enterprise Node.js Backend Template (TypeScript + PNPM)

Production-grade and enterprise-ready backend template based on a modular architecture.

## Folder Structure

```text
.
├── app.ts
├── server.ts
├── config/
├── docs/
│   └── swagger/
├── middlewares/
├── modules/
│   ├── auth/
│   └── document/
├── prisma/
├── routes/
├── tests/
├── types/
└── utils/
```

This aligns with your requested modular structure (`config`, `docs/swagger`, `middlewares`, `modules/document`, `routes`, `types`, `utils`, `app.ts`, `server.ts`) while extending it for enterprise use.

## Included Best Practices

- **TypeScript strict mode** with strong compile-time safety
- **Layered modular architecture** (route → controller → service → repository)
- **Input validation** using `zod`
- **Sanitization** using `sanitize-html` and `express-mongo-sanitize`
- **Security hardening** (`helmet`, `cors`, `hpp`, rate limiting)
- **Structured logging** with `pino` and request-level context
- **Centralized error handling** with custom operational errors
- **JWT access + refresh token flow** with RBAC middleware
- **Swagger/OpenAPI documentation** served at `/docs`
- **Database patterns** for both Prisma/PostgreSQL and Mongoose/MongoDB
- **Testing stack** with Jest + Supertest (unit + integration)
- **Code quality tooling**: ESLint, Prettier, Husky, lint-staged, commitlint
- **Dockerized runtime and local infra** with `docker-compose`

## Quick Start

### 1) Install dependencies

```bash
pnpm install
```

### 2) Configure environment

```bash
cp .env.example .env
```

Update secrets/URLs before running in production.

### 3) Generate Prisma client

```bash
pnpm prisma:generate
```

### 4) (Optional) Run Prisma migrations

```bash
pnpm prisma:migrate
```

### 5) Run in development

```bash
pnpm dev
```

- Health: `GET /health`
- API Docs: `GET /docs`
- Base API Prefix: `/api/v1`

## Authentication Pattern

### Default mock users for template demo

- `admin@example.com / Admin@12345`
- `editor@example.com / Editor@12345`

### Flow

1. `POST /api/v1/auth/login` → access + refresh tokens
2. Use access token in `Authorization: Bearer <token>`
3. `POST /api/v1/auth/refresh-token` to rotate tokens
4. `POST /api/v1/auth/logout` to revoke refresh token

> Replace in-memory user/refresh token stores with persistent implementations (e.g., Postgres + Redis) in production.

## Database Pattern (Prisma + Mongoose)

Set `DB_CLIENT` in `.env`:

- `prisma`: Use PostgreSQL repository implementation
- `mongoose`: Use MongoDB repository implementation
- `both`: Connect to both databases, default repository uses Prisma implementation

The `modules/document` module demonstrates repository abstraction with interchangeable implementations:

- `document.prisma.repository.ts`
- `document.mongoose.repository.ts`
- `document.repository.factory.ts`

## Testing

```bash
pnpm test
pnpm test:coverage
```

- Unit: `tests/unit`
- Integration: `tests/integration`

## Quality & Git Hooks

```bash
pnpm lint
pnpm format:check
pnpm typecheck
pnpm prepare
sh scripts/init-husky.sh
```

Pre-commit hook runs lint-staged.

## Docker Usage

### Build and run app only

```bash
docker build -t enterprise-backend-template .
docker run --rm -p 4000:4000 --env-file .env enterprise-backend-template
```

### Run full stack (app + postgres + mongo)

```bash
docker compose up --build
```

To set up a Mac from scratch and test every user flow by hand through Swagger, follow
[`docs/manual-testing.md`](docs/manual-testing.md).

## Production Hardening Checklist

- Replace demo users and mock auth stores
- Add refresh token hashing and storage with rotation metadata
- Add CSRF strategy if using cookie-based auth
- Add observability (metrics, tracing) and log forwarding
- Add CI/CD with test + lint + SAST + dependency scanning
- Add secrets manager integration and key rotation strategy
- Add migrations and seeding pipelines for all environments

## Example API Calls

```bash
curl -X POST http://localhost:4000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"Admin@12345"}'
```

```bash
curl -X GET http://localhost:4000/api/v1/documents \
  -H "Authorization: Bearer <access_token>"
```

## Notes for Teams

- Keep business logic in `service`, not controller.
- Keep external data access in repositories.
- Define module contracts with typed interfaces.
- Use centralized `ApiError` and middleware for reliable API responses.
- Prefer feature-folder modularization for scalability.

##  Support

For support, email developer@metaupspace.com or create an issue in this repository.

---

**Made with ❤️ by SiddharthGautam09

