# Deploying the CRM on Railway (free trial)

## What to expect from "free"

- Railway's trial is a **one-time $5 credit, for 30 days or until it runs out**. After that the
  services stop until you add a card (Hobby plan: $5/month minimum + usage).
- The credit lasts roughly **10 days for the core CRM** (~1.5 GB of memory) and **~4 days with AI
  and the virus scanner** (~4 GB). Watch it under **Project → Usage**.
- Railway has **no Canadian region**. Use it for demos with **made-up data only** — no real client
  documents.
- So: start with the **core** (step 4a). Add the AI services and ClamAV (4b) only when you need to
  show them, and delete them again after.

## How deploying works

```
push to main ──► GitHub CI (tests) ──► passes ──► Railway rebuilds only the services
                                                    whose files changed, then swaps them in
```

Each Railway service watches its own folders (`watchPatterns` in its config file), so a change to
`apps/notes-svc` rebuilds only `notes`. With **Wait for CI** on, nothing deploys from a commit whose
tests failed. A failed health check keeps the previous version running.

The config files are in the repos:

| Repo | Railway config file (path to type in) | Service name (must be exact) |
|---|---|---|
| backend | `/railway/postgres.json` | `postgres` |
| backend | `/railway/modauth.json` | `modauth` |
| backend | `/railway/tenant.json` | `tenant` |
| backend | `/railway/app.json` | `app` |
| backend | `/railway/clamav.json` | `clamav` (optional) |
| frontend | `railway.json` (found automatically) | `web` |
| AI-service | `/railway/<name>.json` | `gateway`, `voice`, `docread`, `notes`, `email`, `assistant`, `search` (optional) |
| — (Docker image `redis:7-alpine`) | — | `redis` |
| — (Docker image `rabbitmq:3.13-alpine`) | — | `rabbitmq` (optional, for AI) |

The names matter: services reach each other at `<name>.railway.internal`.

---

## Step by step (about 45 minutes)

### 1. Account and project

1. Sign up at **railway.com** with your GitHub account. Allow it access to the `AI-Bokerage`
   repos (`backend`, `frontend`, `AI-service`).
2. **New Project → Empty Project.**

### 2. Create `web` first, to get your address

1. In the project: **Create → GitHub Repo → `frontend`**. Rename the service to **`web`**.
2. `web` → **Settings → Networking → Generate Domain** (port **3000**). Copy it, e.g.
   `web-production-1a2b.up.railway.app`.

### 3. Make every service's variables (on your computer)

From the `backend` repo (with `AI-service` checked out next to it as `ai service`):

```bash
python3 deploy/make-env.py --host-template "{name}.railway.internal" --stage staging \
  --ai-repo "../ai service" --domain web-production-1a2b.up.railway.app --out ~/crm-railway
```

You get `~/crm-railway/services/<service>.env`, one per service, with fresh random passwords and
keys that match between services. Then:

- `modauth.env`: set `SUPER_ADMIN_EMAIL`. **Email sending is optional on the trial** — leave the
  `SMTP_*`/`SPRING_MAIL_*` lines as they are and invitations/password-reset emails just won't go out.
  You sign in as Super Admin with `SUPER_ADMIN_EMAIL` + `SUPER_ADMIN_PASSWORD` from this file.
- `app.env`: the two `GOOGLE_*` lines stay empty unless you set up Google sign-in.
- `gateway.env` (only if you add AI): the provider keys — `LLM_*` (Groq), `OCR_*` (OCR.space),
  `EMBEDDING_*` (Jina, plus `EMBEDDING_SEND_JINA_TASK=true`). **Use new keys**, not the ones pasted
  in chat during development.

Keep `~/crm-railway` in a password manager; don't commit it.

### 4a. The core CRM

For each row, in this order: **Create → GitHub Repo** (or **Docker Image**), **rename** the service,
then in the service:

- **Settings → Config-as-code → Railway Config File**: the path from the table above.
- **Settings → Source**: branch `main`, turn on **Wait for CI**.
- **Variables → Raw Editor**: paste the whole matching `.env` file.
- **Volumes** where listed: right-click the service → **Attach Volume** → the mount path.

| # | Service | Source | Volume mount path |
|---|---|---|---|
| 1 | `postgres` | backend repo | `/var/lib/postgresql/data` |
| 2 | `redis` | Docker Image `redis:7-alpine` (no variables) | — |
| 3 | `modauth` | backend repo | — |
| 4 | `tenant` | backend repo | — |
| 5 | `app` | backend repo | `/data/documents` |
| 6 | `web` | (already created) — paste `web.env`, then **Redeploy** | — |

Only `web` has a public address. Open `https://<your web domain>/sign-in` and sign in as the
Super Admin.

> Without `clamav`, document uploads are refused ("virus scanner isn't available") — on purpose:
> files are never stored unscanned.

### 4b. Optional: AI features and the virus scanner

| Service | Source | Volume mount path |
|---|---|---|
| `clamav` | backend repo | `/var/lib/clamav` |
| `rabbitmq` | Docker Image `rabbitmq:3.13-alpine` (paste `rabbitmq.env`) | — |
| `gateway`, then `notes`, `email`, `assistant`, `docread`, `search`, `voice` | AI-service repo | `voice`: `/data/audio` · `docread`: `/data/documents` |

The AI services create their own tables on first start. To save credit, delete them when you're
not demoing — their data goes too; the core CRM keeps working without them.

---

## Every day

- **Push or merge to `main`.** CI runs; when it passes, Railway rebuilds the services whose files
  changed (a few minutes). Watch it in the project's **Deployments**.
- **Pull requests** only run CI.
- **Something broke:** open the service → **Deployments** → the failed one → **View logs**. To go
  back, pick the previous deployment → **Redeploy**.

## Moving off Railway later

Nothing here is Railway-only: the same images, service names and variables run anywhere.
[README.md](README.md) lists the building blocks and how to move (e.g. to AWS EC2 or another
platform): create the same services, rerun `make-env.py` with that platform's `--host-template`,
move the database and documents.
