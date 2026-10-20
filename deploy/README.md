# Deploying the CRM — on any platform

The CRM is built to move between hosts without changes to the code. **It runs on AWS EC2: follow
"On AWS EC2" below.** This page is also the part that stays the same everywhere. ([RAILWAY.md](RAILWAY.md)
is kept for reference; Railway's free plan can't run the 6 core services.)

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

## On AWS EC2 (staging + production)

### How deploying works

```
push to main (any repo) ──► CI: lint, types, tests, images build ── fails? nothing deploys
        │ passes
        ▼
Deploy workflow ──► builds that repo's images, stores them on GitHub (ghcr.io), tagged with the commit
        │
        ▼
STAGING server  ── automatic: pulls the new images, migrates, restarts, checks health
        │           (not healthy within 5 minutes → puts the previous version back)
        ▼
PRODUCTION server ── after you click "Approve" in GitHub → the same, with the same images
```

Each repo deploys only its own part: `backend` → `app`, `modauth`, `tenant` (and copies the
server files from this folder); `frontend` → `web`; `AI-service` → the 7 AI services. Everything
runs on one server per environment with `compose.yml`; only Caddy (HTTPS) is open to the internet.

### One-time setup (about an hour per environment; do it for staging, then production)

**1. Create the server** — AWS console → EC2 → Launch instance:
- Region **Canada (Central) `ca-central-1`** (client data stays in Canada).
- Image **Ubuntu Server 24.04 LTS, 64-bit (x86)** — not a Graviton/`t4g` type (the images are x86).
- Type **`t3.large`** (2 vCPU, 8 GB). Storage **40 GB gp3**. Key pair: your own, for logging in.
- Security group: **22 from your IP only**; **80 and 443 from anywhere**.
- Then **Elastic IPs → Allocate → Associate** it with the instance (a fixed address).

**2. Domain** — at your DNS provider, an **A record**, e.g. `staging.crm.yourdomain.com` → the
staging Elastic IP (later `crm.yourdomain.com` → production). Caddy gets HTTPS by itself.

**3. Prepare the server** — make the key GitHub logs in with (no passphrase):
```bash
ssh-keygen -t ed25519 -N "" -C "github-deploy-staging" -f ~/.ssh/crm_deploy_staging
scp -i <your-key.pem> deploy/server-setup.sh ubuntu@<elastic-ip>:
ssh -i <your-key.pem> ubuntu@<elastic-ip> "sudo bash server-setup.sh '$(cat ~/.ssh/crm_deploy_staging.pub)'"
```
It installs Docker, adds swap, rotates logs, and creates the `deploy` user and `/opt/crm`.

**4. Make and copy the settings** (from the `backend` repo, with `AI-service` next to it):
```bash
python3 deploy/make-env.py --stage staging --host-template "{name}" \
  --ai-repo "../ai service" --domain staging.crm.yourdomain.com --out ~/crm-staging
grep -rn CHANGE_ME ~/crm-staging        # fill these in (see "The variables" above)
scp -r -i ~/.ssh/crm_deploy_staging ~/crm-staging/. deploy@<elastic-ip>:/opt/crm/
```
Keep `~/crm-staging` in a password manager. For **production** use `--stage production` and
decide the two privacy approvals in `services/gateway.env`.

**5. Tell GitHub about the server** — in **each** of the 3 repos (`backend`, `frontend`,
`AI-service`): **Settings → Environments → New environment**:
- `staging`, with these **environment secrets**:
  - `SSH_HOST` — the Elastic IP
  - `SSH_USER` — `deploy`
  - `SSH_PRIVATE_KEY` — the contents of `~/.ssh/crm_deploy_staging` (the private key)
  - `SSH_KNOWN_HOSTS` — the output of `ssh-keyscan -t ed25519 <elastic-ip>`
- `production` — the same four for the production server, plus **Required reviewers** (you):
  that is the "Approve" button.

**6. First deploy, in this order** — GitHub → **Actions → Deploy → Run workflow** (branch `main`):
1. `backend` — also copies the server files and starts Postgres, Redis, RabbitMQ, ClamAV, Caddy
   (ClamAV's first start downloads its virus database, a few minutes);
2. `AI-service`;
3. `frontend`.

Open `https://staging.crm.yourdomain.com/sign-in` and sign in with `SUPER_ADMIN_EMAIL` /
`SUPER_ADMIN_PASSWORD` from `services/modauth.env`.

### Every day
- **Push or merge to `main`** → CI → staging updates by itself (about 10 minutes).
- Check staging, then GitHub → **Actions → Deploy → the run → Review deployments → Approve**
  to send the same images to production. Pull requests only run CI.

### When something goes wrong
- The failed run's log says why; an unhealthy new version was already rolled back by `deploy.sh`.
- **Go back to an older version:** Actions → Deploy → an older successful run → **Re-run all jobs**.
- **Look on the server:**
  ```bash
  ssh -i ~/.ssh/crm_deploy_staging deploy@<ip>
  cd /opt/crm && docker compose ps                  # what is running
  docker compose logs --tail 100 app                # one service's log
  ```

### Before real clients
- **Backups:** EC2 → **Lifecycle Manager** → daily EBS snapshots, kept 14 days.
- **Uptime check** on the domain (Route 53 health check or UptimeRobot).
- **Real email:** the SMTP lines in `services/modauth.env` (e.g. Amazon SES in `ca-central-1`).

**Cost (rough, on-demand):** one `t3.large` with disk and IP ≈ US$75/month; staging + production
≈ US$150. Staging can be stopped outside working hours; a 1-year Savings Plan cuts ~30–40%.
