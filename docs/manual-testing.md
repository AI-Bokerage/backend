# Manual testing on a Mac — run the backend and test every flow through Swagger

This walks the user flows in `Untitled 2.md` end to end, by hand, through Swagger.
Every step lists what to send and what you should see. All of it was run against the
local Docker stack on 2026-10-14 and behaved as described.

**Time:** about 30 minutes the first time (most of it the first Docker build), then a few
minutes per run.

---

## Part A — Set up and start (once per Mac)

### A1. Install the tools

| Tool                | Why                                  | Install                                                      |
| ------------------- | ------------------------------------ | ------------------------------------------------------------ |
| Docker Desktop      | Runs every service and database      | https://www.docker.com/products/docker-desktop/              |
| Node.js 20 or newer | Only for the one-time database setup | `brew install node`                                          |
| pnpm 10             | Same                                 | `corepack enable && corepack prepare pnpm@10.0.0 --activate` |
| Git                 | Get the code                         | Comes with Xcode Command Line Tools                          |

No Java is needed — the two Java services are built inside Docker.

### A2. Get the code

```bash
git clone https://github.com/Siddharthgautam09/CRM-AI.git
cd CRM-AI
git checkout main && git pull
pnpm install
cp .env.example .env          # Docker Compose reads it; the defaults are fine for local testing
```

### A3. Start everything

Open **Docker Desktop** and wait until it says it's running, then:

```bash
docker compose up --build -d
docker compose ps             # all 12 services should be "running" (databases "healthy")
```

The first build takes several minutes (it compiles the auth and tenant services).

### A4. Create the database tables (first time, or after wiping data)

The app doesn't create its own tables. Run both, from the project folder:

```bash
# CRM database
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/template_db \
  pnpm exec prisma migrate deploy

# AI cost / usage database (without it every AI call returns 500)
DATABASE_URL=postgresql://genusg:genusg@localhost:5435/genusg \
  pnpm exec prisma migrate deploy --schema lib/Gen_Usg-main/packages/gen-usg-starter/prisma/schema.prisma

docker compose restart app
```

Expected: "All migrations have been successfully applied" (or "No pending migrations").
The auth and tenant services create their own tables.

### A5. Check it's up

| Open                          | Expect                                                                       |
| ----------------------------- | ---------------------------------------------------------------------------- |
| http://localhost:4000/health  | `{"status":"ok", …}`                                                         |
| http://localhost:4000/docs    | **CRM + Platform Swagger** — the guide at the top, endpoints grouped by area |
| http://localhost:8102/v1/docs | **Auth Swagger** — Login, Invitations, People, Teams, My profile, Sessions   |
| http://localhost:8026         | **Mailhog** — every email the system sends lands here                        |

Keep these three tabs open, **in the same browser**.

---

## Part B — How to use Swagger here

- **Run a call:** expand an endpoint → **Try it out** → fill the path fields and body →
  **Execute**. The response is shown underneath.
- **Signing in:** log in on the **Auth Swagger**. That sets an `access_token` cookie for
  localhost, and the **CRM Swagger** in the same browser uses it automatically — there's
  nothing to paste. To act as another person, log in as them; you are whoever logged in last.
- **Super Admin:** the Super Admin login also returns `accessToken` in the response body; if
  a Platform call says 401, click **Authorize** on the CRM Swagger and paste it into
  **superAdminBearer**.
- **Tokens last 15 minutes.** On a `401`, log in again.
- **IDs:** copy them from responses (`id`, `userId`, `leadId`) into the next call's path.
- **File uploads** (documents, voice notes): the request body is the file itself. Pick the
  media type in the dropdown (`application/pdf`, `image/png`, `audio/webm`…), then choose
  the file. Document type and file name go in the query fields.
- **Email links** (invitations, document requests): open Mailhog, open the email, copy the
  `token=…` value from the link.

---

## Part C — Test plan, flow by flow

Run the parts in order: each one creates what the next needs. Use these people:

| Who          | Email                    | Password                                       |
| ------------ | ------------------------ | ---------------------------------------------- |
| Super Admin  | `superadmin@example.com` | `SuperAdmin@12345` (from `docker-compose.yml`) |
| Tenant Admin | `tara@acme.test`         | `TaraPassword#2026`                            |
| Team Lead    | `leah@acme.test`         | `LeahPassword#2026`                            |
| Broker       | `bob@acme.test`          | `BobPassword#2026`                             |
| Assistant    | `ann@acme.test`          | `AnnPassword#2026`                             |

Passwords must be at least 12 characters.

### C1. Flow 1 — Super Admin creates a brokerage

| #   | Where   | Call                                           | Send                                                                               | Expect                                                                  |
| --- | ------- | ---------------------------------------------- | ---------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| 1   | Auth    | `POST /api/v1/auth/super-admin/login`          | `{"email":"superadmin@example.com","password":"SuperAdmin@12345"}`                 | 200 with `accessToken`                                                  |
| 2   | CRM     | `POST /platform/brokerages`                    | `{"name":"Acme Mortgages","ownerName":"Tara Admin","ownerEmail":"tara@acme.test"}` | 201, `onboardingStatus: "PENDING"`. Copy `id` (the brokerage/tenant id) |
| 3   | CRM     | same, again with `ownerEmail` `tara@acme.test` |                                                                                    | 409 "That email already belongs to another account"                     |
| 4   | Mailhog |                                                |                                                                                    | "You're invited to join the team" for tara@acme.test                    |

### C2. Flow 0 — Getting in (owner accepts)

| #   | Where | Call                                                                        | Send                                                                                                                                | Expect                                                                                                                                                                       |
| --- | ----- | --------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | Auth  | `POST /api/v1/modauth/invitations/{token}/accept`                           | token from Mailhog in the path **and** body: `{"token":"…","newPassword":"Short1!","confirmPassword":"Short1!","acceptTerms":true}` | 400 "Password must be at least 12 characters"                                                                                                                                |
| 2   | Auth  | same                                                                        | `newPassword` / `confirmPassword` = `TaraPassword#2026`                                                                             | 200, `role: "TENANT_ADMIN"`, `nextStep: "complete_brokerage_profile"`                                                                                                        |
| 3   | Auth  | `POST /api/v1/modauth/login`                                                | `{"email":"tara@acme.test","password":"TaraPassword#2026","acceptTerms":true}`                                                      | 200, `dashboard: "brokerage_dashboard"`                                                                                                                                      |
| 4   | Auth  | `POST /api/v1/modauth/login` with a wrong password, 5 times, then once more |                                                                                                                                     | 401 ×5 (the 5th locks the account), then 429 even with the right password. Mailhog: "Your account was temporarily locked". Wait 15 min — do this last, or on a spare account |
| 5   | Auth  | `POST /api/v1/auth/magic-link/issue`                                        | `{"email":"tara@acme.test"}`                                                                                                        | Same reply whether or not the email exists; reset email in Mailhog                                                                                                           |

### C3. Flow 2 — Tenant Admin sets up the brokerage (logged in as Tara)

| #   | Where | Call                                  | Send                                                                                                         | Expect                                             |
| --- | ----- | ------------------------------------- | ------------------------------------------------------------------------------------------------------------ | -------------------------------------------------- |
| 1   | CRM   | `GET /crm/tenant/dashboard`           |                                                                                                              | 403 "Complete the brokerage profile first"         |
| 2   | Auth  | `GET /api/v1/modauth/people`          |                                                                                                              | 403, same message                                  |
| 3   | CRM   | `PATCH /crm/tenant/profile`           | `{"displayName":"Acme Mortgages","supportEmail":"help@acme.test","phone":"+1 416 555 0100"}`                 | 200                                                |
| 4   | CRM   | `GET /crm/tenant/dashboard`           |                                                                                                              | 200: pipeline, renewals, AI usage                  |
| 5   | Auth  | `POST /api/v1/modauth/invitations` ×3 | `{"name":"Leah Lead","email":"leah@acme.test","role":"TEAM_LEAD"}`, then Bob as `BROKER`, Ann as `ASSISTANT` | 201 each; 3 emails in Mailhog                      |
| 6   | Auth  | accept each invitation (as in C2 #2)  | their passwords from the table                                                                               | 200; `nextStep: "connect_email_calendar_optional"` |
| 7   | Auth  | `GET /api/v1/modauth/people`          |                                                                                                              | Leah, Bob, Ann with their `userId`s                |
| 8   | Auth  | `POST /api/v1/modauth/teams`          | `{"name":"Toronto Team","teamLeadUserId":"<Leah>","brokerUserIds":["<Bob>","<Ann>"]}`                        | 201                                                |
| 9   | CRM   | `PATCH /crm/tenant/booking-settings`  | `{"slug":"acme","enabled":true,"defaultBrokerUserId":"<Bob>"}`                                               | 200                                                |

Reports, export and "removing someone" are tested in C7 once there is data.

### C4. Flow 4 — Broker (log in as Bob)

**4.1 / 4.2 — New person with AI review** (AI output is placeholder data marked `"stub": true`)

| #   | Call                               | Send                                                                                                                                                                                                                                                                                                                                                          | Expect                                                             |
| --- | ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| 1   | `POST /crm/voice-notes`            | media type `audio/webm`, any small file                                                                                                                                                                                                                                                                                                                       | 201, `transcribed: true`, placeholder transcript                   |
| 2   | `POST /crm/ai/review-notes`        | `{"name":"Jamie Rivera","notes":"First-time buyer, wants pre-approval","fields":{"email":"jamie@example.com"}}`                                                                                                                                                                                                                                               | 200: summary, urgency, missing fields, proposed tasks, draft email |
| 3   | `POST /crm/leads`                  | `{"name":"Jamie Rivera","email":"jamie@example.com","phone":"416-555-0199","income":95000,"monthlyDebts":800,"downPayment":60000,"propertyValue":650000,"notes":"First-time buyer","dateOfBirth":"1990-10-16T00:00:00.000Z","acceptedTasks":[{"title":"Follow up with Jamie"}],"draftEmail":{"intent":"FOLLOW_UP","subject":"Next steps","body":"Hi Jamie"}}` | 201, stage `NEW`. Copy the lead `id`                               |
| 4   | `GET /crm/tasks`                   |                                                                                                                                                                                                                                                                                                                                                               | "Follow up with Jamie"                                             |
| 5   | `GET /crm/leads/{id}/email-drafts` |                                                                                                                                                                                                                                                                                                                                                               | The draft, `aiAssisted: true`, not sent                            |

**4.3 / 4.4 — Person panel and affordability**

| #   | Call                                        | Send                      | Expect                                                   |
| --- | ------------------------------------------- | ------------------------- | -------------------------------------------------------- |
| 6   | `GET /crm/leads/{id}/panel`                 |                           | profile, mortgages, notes, activity, documents           |
| 7   | `POST /crm/leads/{id}/reveal`               | `{"field":"dateOfBirth"}` | The value; recorded in the activity log                  |
| 8   | `POST /crm/leads/{id}/ai/review`            |                           | Stub review; `missingFields` lists the real blank fields |
| 9   | `GET /crm/leads/{id}/affordability`         |                           | GDS/TDS, max purchase price, passing or over the limit   |
| 10  | `POST /crm/leads/affordability/recalculate` | `{}`                      | `{"total":1,"succeeded":1,"failures":[]}`                |

**4.5 — Documents**

| #   | Call                                               | Send                                                                | Expect                                                                                |
| --- | -------------------------------------------------- | ------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| 11  | `PUT /crm/leads/{id}/documents/required`           | `{"types":["T4","NOA","BANK_STATEMENT"]}`                           | `missing` = all three                                                                 |
| 12  | `POST /crm/leads/{id}/documents/request`           | `{}`                                                                | 409 "Connect your email, or send from the system address instead" (no Gmail locally)  |
| 13  | same                                               | `{"useSystemAddress":true}`                                         | 201 with `url`. Copy the last part of the url — the borrower **token**                |
| 14  | `GET /crm/public/document-requests/{token}`        | (no login needed)                                                   | First name and the missing list                                                       |
| 15  | `POST /crm/public/document-requests/{token}/files` | `type=T4`, `fileName=t4.pdf`, media type `application/pdf`, any PDF | 201                                                                                   |
| 16  | `GET /crm/leads/{id}/documents`                    |                                                                     | T4 `readStatus: READ` with placeholder `extracted` values; `missing` no longer has T4 |
| 17  | `POST /crm/documents/{documentId}/apply`           | `{"fields":{"income":95000,"city":"Toronto"}}`                      | `applied: ["city"]`, `skipped: ["income"]` (already filled — never overwritten)       |
| 18  | `GET /crm/notifications`                           |                                                                     | "Review the details read from t4.pdf"                                                 |

To test the virus check, upload a text file containing the EICAR test string
(`X5O!P%@AP[4\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*`): 422, not stored,
notification sent.

**4.6 — Moving the deal along**

| #   | Call                                   | Send                                                                                                                    | Expect                                                                         |
| --- | -------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| 19  | `PATCH /crm/leads/{id}/stage`          | `{"stage":"QUALIFIED"}`                                                                                                 | 200                                                                            |
| 20  | same                                   | `{"stage":"FUNDED"}`                                                                                                    | 400 — Funded only through the fund call                                        |
| 21  | `POST /crm/leads/{id}/fund`            | `{"lender":"RBC","interestRate":4.79,"balance":520000,"monthlyPayment":2950,"maturityDate":"2027-02-01T00:00:00.000Z"}` | stage `FUNDED`; now in `GET /crm/clients`, not `GET /crm/leads`                |
| 22  | `POST /crm/leads` then `PATCH …/stage` | `{"stage":"LOST"}` on a second lead                                                                                     | 400 "A reason is required…"; then with `"lostReason":"…","lostNote":"…"` → 200 |

**4.7 — Email**

| #   | Call                                                                          | Send                                                                          | Expect                                                            |
| --- | ----------------------------------------------------------------------------- | ----------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| 23  | `GET /crm/ai/email-intents`                                                   |                                                                               | follow-up, renewal, birthday, documents, annual review, refinance |
| 24  | `POST /crm/leads/{id}/ai/draft-email`                                         | `{"intent":"REFINANCE"}`                                                      | Stub draft                                                        |
| 25  | `POST /crm/leads/{id}/email-drafts`, then `PATCH /crm/email-drafts/{draftId}` | `{"subject":"Refi idea","body":"Hi","aiAssisted":true}` / `{"body":"edited"}` | Saved, edited, nothing sent                                       |
| 26  | `POST /crm/emails/send`                                                       | `{"to":"jamie@example.com","subject":"x","body":"y","leadId":"<id>"}`         | "Connect Gmail to send" (needs Google keys — Part E)              |

**4.8 — Calendar and bookings**

| #   | Call                                                        | Send                                                                                                                                              | Expect                                           |
| --- | ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------ |
| 27  | `POST /crm/appointments`                                    | `{"title":"Review docs","type":"DOCUMENT_REVIEW","leadId":"<id>","startTime":"2026-10-20T15:00:00.000Z","endTime":"2026-10-20T15:30:00.000Z"}`    | 201, `pushedToGoogle: false` (no Google locally) |
| 28  | same with `"type":"LUNCH"`                                  |                                                                                                                                                   | 400 — only call / consultation / document review |
| 29  | `POST /crm/public/bookings/acme` (no login)                 | `{"bookingRef":"cal-001","name":"Sam New","email":"sam@example.com","startTime":"2026-10-21T14:00:00.000Z","endTime":"2026-10-21T14:30:00.000Z"}` | 201, `newLead: true`                             |
| 30  | same body again                                             |                                                                                                                                                   | 200, `duplicate: true` — nothing created         |
| 31  | with `bookingRef` `cal-002` and `email` `JAMIE@example.com` |                                                                                                                                                   | Attached to Jamie, `newLead: false`              |

**4.9 — Tasks**

| #   | Call                                                        | Send                                                        | Expect                                                  |
| --- | ----------------------------------------------------------- | ----------------------------------------------------------- | ------------------------------------------------------- |
| 32  | `POST /crm/tasks`                                           | `{"title":"Call lender","leadId":"<id>","priority":"HIGH"}` | 201                                                     |
| 33  | `POST /crm/leads/{id}/ai/suggest-tasks`                     |                                                             | 202 while reading; call again → Suggested tasks         |
| 34  | `PATCH /crm/tasks/{suggestedId}/complete`                   |                                                             | 409 — approve it first                                  |
| 35  | `PATCH /crm/tasks/{suggestedId}/approve`, then `…/complete` |                                                             | Approved, then completed; shows in the panel's activity |

**4.10–4.12**

| #   | Where | Call                                | Send                                                   | Expect                                                                |
| --- | ----- | ----------------------------------- | ------------------------------------------------------ | --------------------------------------------------------------------- |
| 36  | CRM   | `POST /crm/ai/assistant`            | `{"question":"Who has renewals in the next 90 days?"}` | Stub, `readOnly: true`                                                |
| 37  | CRM   | `DELETE /crm/leads/{secondLeadId}`  | `{"confirmName":"wrong"}`                              | 400; with the exact name → 204, gone; its tasks show `unlinked: true` |
| 38  | CRM   | `PATCH /crm/settings`               | `{"aiEnabled":false}`                                  | Any AI call → 403 "AI features are switched off…". Turn it back on    |
| 39  | Auth  | `PATCH /api/v1/modauth/me`          | `{"phone":"+1 416 555 0123"}`                          | Updated profile                                                       |
| 40  | Auth  | `GET /api/v1/modauth/sessions`      |                                                        | Your signed-in devices                                                |
| 41  | Auth  | `POST /api/v1/auth/change-password` | current password as the new one                        | 400 "You can't reuse your current password or any of your last 5"     |

**Dashboard (Flow 4)**

| #   | Call                                                                 | Expect                                                                                                                                                                     |
| --- | -------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 42  | `GET /crm/dashboard`                                                 | Cards: hot leads, pipeline, portfolio, renewals, today's meetings, follow-ups, birthdays, tasks, missing documents; AI cards are stubs. `links` gives each card's list URL |
| 43  | Open any `links` URL (e.g. `GET /crm/clients?renewalWithinDays=180`) | The list's rows match the card                                                                                                                                             |

### C5. Flow 3 — Team Lead (log in as Leah)

| #   | Call                                                                              | Send                                             | Expect                                                                             |
| --- | --------------------------------------------------------------------------------- | ------------------------------------------------ | ---------------------------------------------------------------------------------- |
| 1   | `GET /crm/team/dashboard`                                                         |                                                  | Pipeline, overdue tasks, workload per person (empty people shown, not hidden)      |
| 2   | `GET /crm/team/book`                                                              |                                                  | Bob's clients                                                                      |
| 3   | `PATCH /crm/leads/{jamieId}/notes`                                                | `{"brokerNotes":"Checked by Leah"}`              | 200 — same access as the owning broker                                             |
| 4   | `GET /crm/team/tasks`, `GET /crm/team/calendar`, `GET /crm/reports?type=pipeline` |                                                  | Team-scoped data                                                                   |
| 5   | `PATCH /crm/team/leads/{jamieId}/reassign`                                        | `{"toBrokerUserId":"<someone not on the team>"}` | 400 "That broker is not on this team"                                              |
| 6   | same                                                                              | `{"toBrokerUserId":"<Leah>"}`                    | 200; `GET /crm/notifications` for both Bob and Leah shows it. Reassign back to Bob |
| 7   | log in as Bob, `GET /crm/team/book`                                               |                                                  | 403 — Brokers can't see team views                                                 |

### C6. Assistant (log in as Ann)

| Call                                                                | Expect                                    |
| ------------------------------------------------------------------- | ----------------------------------------- |
| `GET /crm/leads/{jamieId}/panel`                                    | 200 — sees the team's clients             |
| `PATCH /crm/leads/{jamieId}/notes`                                  | 200 — can edit                            |
| `DELETE /crm/leads/{jamieId}` / `POST …/reveal` / `POST /crm/leads` | 403 each — can't delete, reveal or create |

### C7. Flow 2 again — reports, export, removing someone (log in as Tara)

| #   | Where | Call                                                                                           | Send                          | Expect                                                  |
| --- | ----- | ---------------------------------------------------------------------------------------------- | ----------------------------- | ------------------------------------------------------- |
| 1   | CRM   | `GET /crm/reports?type=` `pipeline` / `renewals` / `team-performance` / `ai-usage`             |                               | 200 each                                                |
| 2   | CRM   | `GET /crm/reports/export?type=pipeline`                                                        |                               | A CSV download                                          |
| 3   | CRM   | `GET /crm/tenant/book`                                                                         |                               | Every lead in the brokerage                             |
| 4   | CRM   | `POST /crm/tenant/export`, `GET /crm/tenant/export`, `GET /crm/tenant/export/{jobId}/download` |                               | A zip: one CSV per record type + the uploaded documents |
| 5   | Auth  | `PATCH /api/v1/modauth/people/{bobId}/deactivate`                                              |                               | 409 "Reassign their N clients first"                    |
| 6   | CRM   | `POST /crm/tenant/people/{bobId}/reassign-clients`                                             | `{"toBrokerUserId":"<Leah>"}` | `{"reassigned":N}`; both notified                       |
| 7   | Auth  | deactivate Bob again                                                                           |                               | 204; Bob's login now says "Account deactivated"         |

### C8. Flow 1 again — the Super Admin's view (log in as the Super Admin)

| #   | Call                                                                      | Send                                                | Expect                                                                                                                                                               |
| --- | ------------------------------------------------------------------------- | --------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | `GET /platform/brokerages/{id}/usage`                                     |                                                     | Daily AI cost vs limit; `allow-extra-today` raises it for today                                                                                                      |
| 2   | `GET /platform/activity-log`                                              |                                                     | Logins (`auth.login.success` / `failed`), reveals, reassignments, deletions, exports, admin actions; filter with `?action=`                                          |
| 3   | `PATCH /platform/brokerages/{id}/suspend`                                 | `{"reason":"Testing suspension of this brokerage"}` | Status SUSPENDED. Leah's next CRM call → 403 "…access is suspended"; her login is refused too                                                                        |
| 4   | `PATCH /platform/brokerages/{id}/reactivate`                              | `{}`                                                | ACTIVE; Leah can log in again                                                                                                                                        |
| 5   | Create a second brokerage, then `PATCH /platform/brokerages/{id2}/cancel` |                                                     | CANCELLED + export READY; `GET …/export` → `GET /platform/exports/{jobId}/download` gives the zip. Its data is deleted after `BROKERAGE_RETENTION_DAYS` (default 90) |

Use a throwaway second brokerage for the cancellation — cancelling is permanent.

### C9. Reminders and the 9am digest

They run when the app starts and then every hour. After funding Jamie in C4 #21:

```bash
docker compose restart app
```

then `GET /crm/notifications` as Bob shows "Jamie Rivera's RBC mortgage renews in under 6
months". Birthdays, follow-ups due today, appointments 24h/1h ahead and the 9am (UTC) digest
work the same way.

---

## Part D — Day to day

| I want to…                                   | Run                                                                    |
| -------------------------------------------- | ---------------------------------------------------------------------- |
| Start again tomorrow                         | Open Docker Desktop → `docker compose up -d`                           |
| Stop (keep data)                             | `docker compose down`                                                  |
| Wipe all data and start fresh                | `docker compose down -v` → `docker compose up -d` → Part A4 again      |
| Test after changing code                     | `docker compose up --build -d app` (CRM) or `… modauth` (auth service) |
| See errors                                   | `docker compose logs -f app` (or `modauth`, `tenant`)                  |
| See an email the system "sent" without Gmail | `docker compose logs app \| grep system_email`                         |

## Part E — Troubleshooting

| Symptom                                        | Fix                                                                                                                                                                   |
| ---------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `401` "Missing bearer token" / "Invalid token" | Token expired (15 min) or you're in a different browser — log in again on the Auth Swagger                                                                            |
| `403` "Complete the brokerage profile first"   | You're the Tenant Admin — do C3 #3                                                                                                                                    |
| `403` "… cannot access this"                   | That role isn't allowed — expected; see `docs/permission-matrix.md`                                                                                                   |
| `500` on any AI call                           | The usage database wasn't migrated — Part A4, second command                                                                                                          |
| `500` / "relation … does not exist"            | The CRM database wasn't migrated — Part A4                                                                                                                            |
| A port is already in use                       | Something else uses 4000/5432/8102/8103/8026 — stop it, or change the left-hand port in `docker-compose.yml`                                                          |
| "Connect Gmail" / `pushedToGoogle: false`      | Expected locally. To test real Gmail and Calendar, add `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` to `.env` (see the comments in `.env.example`) and rebuild the app |
| Uploaded document shows `readStatus: FAILED`   | Choose `application/pdf` or an image type in the media-type dropdown — other types count as unreadable                                                                |

## Part F — Using Neon instead of local Postgres

The stack can use hosted Postgres on [Neon](https://neon.tech) for all four databases, while
Redis, Mongo and Mailhog stay local in Docker. `docker-compose.neon.yml` does the switch; your
credentials stay in `.env` (git-ignored).

### F1. Put the Neon details in `.env`

From the Neon connection string `postgresql://USER:PASSWORD@ep-xxxx-pooler.<region>.aws.neon.tech/neondb?...`:

```bash
NEON_POOLER_HOST=ep-xxxx-pooler.<region>.aws.neon.tech
NEON_DIRECT_HOST=ep-xxxx.<region>.aws.neon.tech        # same host without "-pooler"
NEON_USER=neondb_owner
NEON_PASSWORD=...
NEON_USAGE_APP_PASSWORD=...   # make one: python3 -c "import secrets;print('Gu-'+secrets.token_urlsafe(32)+'-9Z')"
```

The Node app connects through the pooler; migrations and the Java services (Flyway) need the
direct host.

### F2. Create the databases and the usage app role (once)

```bash
set -a; . ./.env; set +a
psql_neon() { docker run --rm -e PGPASSWORD="$NEON_PASSWORD" postgres:16-alpine \
  psql "host=$NEON_DIRECT_HOST user=$NEON_USER dbname=$1 sslmode=require" -c "$2"; }

for db in crm auth_db tenant_db genusg; do psql_neon neondb "CREATE DATABASE $db"; done

# gen-usg-starter's migration would create this role with a weak built-in password,
# which Neon rejects; creating it first with a strong one makes the migration skip it.
psql_neon genusg "CREATE ROLE genusg_app LOGIN PASSWORD '$NEON_USAGE_APP_PASSWORD'"
```

### F3. Create the tables (once, and after pulling new migrations)

```bash
set -a; . ./.env; set +a
DATABASE_URL="postgresql://$NEON_USER:$NEON_PASSWORD@$NEON_DIRECT_HOST/crm?sslmode=require" \
  pnpm exec prisma migrate deploy
DATABASE_URL="postgresql://$NEON_USER:$NEON_PASSWORD@$NEON_DIRECT_HOST/genusg?sslmode=require" \
  pnpm exec prisma migrate deploy --schema lib/Gen_Usg-main/packages/gen-usg-starter/prisma/schema.prisma
```

The auth and tenant services create their own tables on Neon when they start.

### F4. Start the stack on Neon

```bash
docker compose down
docker compose -f docker-compose.yml -f docker-compose.neon.yml up --build -d
```

Only 8 services start — no local Postgres. Everything in Parts B and C works the same. Use the
same `-f docker-compose.yml -f docker-compose.neon.yml` on every `docker compose` command
(`ps`, `logs`, `restart`, `down`). To go back to local Postgres, drop the second `-f`.

| Symptom                                                | Fix                                                                                                                                                                                                                                                          |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `create_app_role` migration fails: "insecure password" | Do the `CREATE ROLE genusg_app …` step in F2, then `pnpm exec prisma migrate resolve --rolled-back 20260728091000_create_app_role --schema lib/Gen_Usg-main/packages/gen-usg-starter/prisma/schema.prisma` (with the genusg `DATABASE_URL`) and run F3 again |
| First request after a while is slow or times out       | Neon suspends an idle database; the first connection wakes it (a few seconds). Retry                                                                                                                                                                         |
| `auth=` / `tenant=` services restart in a loop         | Check `docker compose … logs modauth` — usually a wrong `NEON_DIRECT_HOST` or password                                                                                                                                                                       |

## What is a placeholder (on purpose)

- **AI** — review, drafts, transcripts, suggested tasks, AI Assistant, dashboard AI cards:
  stub output marked `"stub": true` until the AI team connects a model. The AI switch and the
  daily cost limit are real.
- **Document reading (OCR)** and **virus scan** — stubs (the scan only catches the EICAR test file).
- **System-address email** — written to the app log, not sent.
- **Outlook** — not built (needs a Microsoft app registration).
