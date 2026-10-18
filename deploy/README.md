# Deploying the CRM — on any platform

The CRM is built to move between hosts without changes to the code. **Right now it runs on
Railway: follow [RAILWAY.md](RAILWAY.md).** This page is the part that stays the same everywhere,
plus how to run it on a plain Docker server (AWS EC2, any VPS, Oracle Cloud) later.

## The building blocks (identical on every platform)

Every service is a Docker image, built from a Dockerfile in one of the three repos. Use **these
service names**: services find each other by name.

| Service | Built from | Port | Keep a disk at | Public? |
|---|---|---|---|---|
| `web` | frontend: `Dockerfile` | 3000 | — | **yes** (the website) |
| `app` | backend: `Dockerfile` | 4000 | `/data/documents` | only via `web` |
| `modauth` | backend: `modules/auth/Dockerfile` | 8102 | — | only via `web` |
| `tenant` | backend: `modules/tenant/Dockerfile` | 8103 | — | no |
| `postgres` | backend: `deploy/postgres/Dockerfile` (pgvector + all databases) | 5432 | `/var/lib/postgresql/data` | no |
| `redis` | image `redis:7-alpine` | 6379 | — | no |
| `clamav` *(virus scan)* | backend: `deploy/clamav/Dockerfile` | 3310 | `/var/lib/clamav` | no |
| `rabbitmq` *(AI only)* | image `rabbitmq:3.13-alpine` | 5672 | — | no |
| `gateway`, `voice`, `docread`, `notes`, `email`, `assistant`, `search` *(AI)* | AI-service: `apps/<name>-svc/Dockerfile`, start command in `AI-service/railway/<name>.json` | 4201–4207 | `voice`: `/data/audio`, `docread`: `/data/documents` | no |

- **Database changes** run automatically: the backend with `prisma migrate deploy` before it starts
  (`railway/app.json` `preDeployCommand`; `deploy.sh` on a Docker host), each AI service on start.
- **Health checks:** `app` `/health`, `modauth`/`tenant` `/actuator/health`, AI `/health/live`,
  `web` `/sign-in`.
- **Optional parts:** without `clamav`, uploads are refused (never stored unscanned); without the
  AI services, the CRM works and the AI buttons answer "not available".

## The variables (one command, any platform)

`make-env.py` writes one file per service (`services/<name>.env`) with fresh random passwords and
keys, matching wherever two services share one. The only platform-specific part is how services
address each other — `--host-template`:

| Platform | `--host-template` |
|---|---|
| Docker host (EC2, VPS) with `compose.yml`, or Render | `"{name}"` (the default) |
| Railway | `"{name}.railway.internal"` |
| Fly.io | `"{name}.internal"` |
| Kubernetes | `"{name}.<namespace>.svc"` |

```bash
python3 deploy/make-env.py --ai-repo "../ai service" --stage staging \
  --domain crm.example.com --host-template "{name}" --out ~/crm-staging
grep -rn CHANGE_ME ~/crm-staging      # fill these in
```

- `--domain` is the public address of `web`.
- `--stage production` also switches the AI services to production mode. They then refuse mock
  providers and accept callbacks over https only, through the public address. **Two privacy
  approvals** in `gateway.env` (`OCR_EXTERNAL_PROVIDER_APPROVED`, `EMBEDDING_PROVIDER_APPROVED`)
  must be set to `true` by you, only once your client has agreed, or the gateway won't start.
- Put each file into the platform's variables for that service (most have a "paste .env" box), or
  use them as env files (`compose.yml` does). Keep the folder in a password manager, never in git.
- Use **new** AI provider keys (Groq, OCR.space, Jina), not the ones shared during development.

## Moving to another platform

1. Create the services from the table, with the same names, disks and health checks.
2. Run `make-env.py` with that platform's `--host-template` and paste the files.
3. Make `web` public and point your domain at it.
4. Move the data: `pg_dump` the old `postgres` (all databases) and restore into the new one, and
   copy the `documents` disk. Do it with the CRM stopped, or in a quiet hour.

Most platforms (Railway, Render, Fly) can build straight from the repos and redeploy on every push
to `main` after CI passes. That is the whole CD — no extra pipeline needed.

---

## On a plain Docker server (AWS EC2, a VPS, Oracle Cloud)

`compose.yml` runs every service above on one machine, with Caddy in front for automatic HTTPS
(`Caddyfile`). Only ports 80/443 are open.

1. **Server:** Ubuntu 24.04 x86, at least **8 GB of memory** (the whole system needs ~4 GB),
   40 GB disk, a fixed public IP. Firewall: 22 from your IP only, 80 and 443 from anywhere.
   (EC2: `t3.large` in `ca-central-1` keeps data in Canada.)
2. **Domain:** an A record pointing at the server.
3. **Prepare it:** copy `server-setup.sh` over and run
   `sudo bash server-setup.sh "<deploy user's SSH public key>"`. It installs Docker, adds swap,
   rotates logs and creates the `deploy` user and `/opt/crm`.
4. **Files:** copy `compose.yml`, `Caddyfile`, `deploy.sh`, `postgres/` and `clamav/` into
   `/opt/crm`, plus the output of `make-env.py --host-template "{name}"` (`.env` and `services/`).
5. **Images:** a Docker host pulls ready-made images, so something must build and publish them:
   - a GitHub Actions workflow per repo that pushes to `ghcr.io/ai-bokerage/<image>:<commit>`
     after CI, then runs `deploy.sh` over SSH (ask to have these added back when you move);
   - or build on the server.
6. **Deploy:** `./deploy.sh <backend|frontend|ai> <tag> <github-user>` with a registry token on
   stdin. It pulls that part's images, migrates, restarts, waits until healthy, and puts the
   previous version back if the new one isn't healthy within 5 minutes.

**Before real clients:** daily disk snapshots (AWS Lifecycle Manager), an uptime check on the
domain, real email (SMTP lines in `modauth.env`).
