# Permission matrix

Generated from the actual guard code (not hand-written) — every row below is traced to a
specific `requireX` call, a route-level `requireModAuthRole(...)`, or an internal-secret
check. SOW deliverable **2.D1**. Roles: **SA** = Super Admin, **TA** = Tenant Admin,
**TL** = Team Lead, **BR** = Broker, **AS** = Assistant.

Legend: ✅ allowed · ✅\* allowed, scoped to the caller's own data/team/tenant · 🚫 blocked

## Auth & sessions (`modules/auth`, gen-auth-starter)

| Action                               | SA   | TA   | TL   | BR   | AS   | Guard                                                             |
| ------------------------------------ | ---- | ---- | ---- | ---- | ---- | ----------------------------------------------------------------- |
| Log in, forgot/reset password        | ✅   | ✅   | ✅   | ✅   | ✅   | Public — no role gate                                             |
| Accept an invitation                 | ✅   | ✅   | ✅   | ✅   | ✅   | Token-gated, not role-gated (`InvitationServiceImpl.accept`)      |
| List / revoke my own sessions        | ✅\* | ✅\* | ✅\* | ✅\* | ✅\* | `SessionController` — any authenticated caller, own sessions only |
| View / edit my profile (name, phone) | ✅\* | ✅\* | ✅\* | ✅\* | ✅\* | `MyProfileController` — own row only                              |

Sign-in is refused (403) at login and on token refresh for everyone in a suspended or cancelled
brokerage (`TenantAccessService`), and every CRM call returns 403 for them too. A refresh more than
`SESSION_IDLE_TIMEOUT_MINUTES` (30) after the previous one signs the session out
(`SessionPolicyRefreshTokenService`). People, Teams and Invitations return 403 to a Tenant Admin
until the brokerage profile is complete (`TenantAdminProfileGate`).

Password rules on every set/change/reset path (SOW 1.4): 12+ characters, and no reuse of the
current or last 5 passwords — `PasswordPolicy`, applied by `PolicyChangePasswordService`,
`PolicyMagicLinkService` and `AcceptInvitationRequest` (modauth wrappers around the starter).

## Invitations (`modauth_invitations`)

| Action                                   | SA   | TA   | TL  | BR  | AS  | Guard                                                                                    |
| ---------------------------------------- | ---- | ---- | --- | --- | --- | ---------------------------------------------------------------------------------------- |
| Invite a Tenant Admin                    | ✅   | 🚫   | 🚫  | 🚫  | 🚫  | `InvitationServiceImpl.requireCanInvite`                                                 |
| Invite a Team Lead, Broker, or Assistant | 🚫   | ✅   | 🚫  | 🚫  | 🚫  | same                                                                                     |
| Invite a Broker (only)                   | 🚫   | 🚫   | ✅  | 🚫  | 🚫  | same                                                                                     |
| Resend / cancel an invitation            | ✅\* | ✅\* | 🚫  | 🚫  | 🚫  | `requireManageableInvitation` — same tenant, or Super Admin for a brokerage-owner invite |

## People & Teams (`modauth_user_roles`, `modauth_teams`) — Tenant Admin only

| Action                                     | SA  | TA   | TL   | BR  | AS   | Guard                                                                                                                                 |
| ------------------------------------------ | --- | ---- | ---- | --- | ---- | ------------------------------------------------------------------------------------------------------------------------------------- |
| List people in the brokerage               | 🚫  | ✅\* | 🚫   | 🚫  | 🚫   | `RoleResolver.requireTenantAdmin`                                                                                                     |
| Deactivate a person                        | 🚫  | ✅\* | 🚫   | 🚫  | 🚫   | same — 🚫 on a Tenant Admin target; blocked (409) while they still have assigned leads (SOW 2.11, checked live against `modules/crm`) |
| Create / list / delete a team              | 🚫  | ✅\* | 🚫   | 🚫  | 🚫   | same                                                                                                                                  |
| Add/remove a Broker or Assistant on a team | 🚫  | ✅\* | 🚫   | 🚫  | 🚫   | same                                                                                                                                  |
| View one team's detail                     | 🚫  | ✅\* | ✅\* | 🚫  | ✅\* | `TeamServiceImpl.get` — Tenant Admin (any team), that team's own Team Lead, or an Assistant on that team                              |

## Super Admin console (`modules/platform` + `modules/tenant`)

| Action                                                   | SA  | Everyone else | Guard                                                                |
| -------------------------------------------------------- | --- | ------------- | -------------------------------------------------------------------- |
| Create/list/suspend/reactivate/cancel a brokerage tenant | ✅  | 🚫            | `requireSuperAdmin` (Node) / internal-secret (Java `modules/tenant`) |
| View tenant usage, AI cost, activity log, "my settings"  | ✅  | 🚫            | `requireSuperAdmin`                                                  |

## CRM — own book (`modules/crm`, Broker + Team Lead; Assistant on the team's book)

An Assistant has no book of their own: on these routes their scope is every lead belonging to
their team (`bookBrokerIds` in `crm-auth.middleware.ts`, 403 if not on a team, 403 outside it).

| Action                                                                                                                           | TA  | TL   | BR   | AS   | Guard                                                                                             |
| -------------------------------------------------------------------------------------------------------------------------------- | --- | ---- | ---- | ---- | ------------------------------------------------------------------------------------------------- |
| List/view leads & clients, change stage, fund, add mortgage                                                                      | 🚫  | ✅\* | ✅\* | ✅\* | `requireModAuthRole('BROKER','TEAM_LEAD','ASSISTANT')`                                            |
| Person panel, affordability (+ recalculate), notes, tags, profile edit                                                           | 🚫  | ✅\* | ✅\* | ✅\* | same                                                                                              |
| Structured intake: view, add, edit                                                                                               | 🚫  | ✅\* | ✅\* | ✅\* | same                                                                                              |
| AI (stubs): voice transcript, analyse notes, review, draft email, apply blank fields, suggest tasks, AI Assistant, email intents | 🚫  | ✅\* | ✅\* | ✅\* | same; every AI call also 403 when the user switched AI off, 429 when the brokerage's AI is paused |
| Saved email drafts (create, list, edit)                                                                                          | 🚫  | ✅\* | ✅\* | ✅\* | same                                                                                              |
| Dashboard, search, my settings                                                                                                   | 🚫  | ✅\* | ✅\* | ✅\* | same                                                                                              |
| Documents: view, tick checklist, upload, apply read values, download                                                             | 🚫  | ✅\* | ✅\* | ✅\* | same                                                                                              |
| Request documents (emails the borrower a 7-day link)                                                                             | 🚫  | ✅\* | ✅\* | 🚫   | `requireModAuthRole('BROKER','TEAM_LEAD')` — own book only                                        |
| Delete a person (typed-name confirmation)                                                                                        | 🚫  | ✅\* | ✅\* | 🚫   | same — own book only                                                                              |
| Approve/dismiss suggested tasks, relink/delete unlinked tasks                                                                    | 🚫  | ✅\* | ✅\* | 🚫   | same — own tasks only                                                                             |
| Create a lead                                                                                                                    | 🚫  | ✅\* | ✅\* | 🚫   | `requireModAuthRole('BROKER','TEAM_LEAD')`                                                        |
| Delete intake records (applicant, debt, property, funds source, follow-up)                                                       | 🚫  | ✅\* | ✅\* | 🚫   | same                                                                                              |
| Reveal SIN / bank details / DOB                                                                                                  | 🚫  | ✅\* | ✅\* | 🚫   | same                                                                                              |
| Create/list/complete own tasks                                                                                                   | 🚫  | ✅\* | ✅\* | 🚫   | same                                                                                              |
| Contacts/COI (add, edit, delete)                                                                                                 | 🚫  | ✅\* | ✅\* | 🚫   | same                                                                                              |
| Connect/disconnect Google, send email, create appointment                                                                        | 🚫  | ✅\* | ✅\* | 🚫   | same                                                                                              |

## CRM — team-scoped (`modules/crm`, Team Lead only)

On every single-person route above (open, edit, intake, documents, AI, reveal, delete, request
documents) a Team Lead has the owning broker's access to every person on their team
(`leadAccessIds`); lists stay their own book.

| Action                                               | TA  | TL   | BR  | Guard                                            |
| ---------------------------------------------------- | --- | ---- | --- | ------------------------------------------------ |
| Team dashboard, team book, team tasks, team calendar | 🚫  | ✅\* | 🚫  | `requireModAuthRole('TEAM_LEAD')`                |
| Open any lead/panel/affordability in the team's book | 🚫  | ✅\* | 🚫  | same, 403 outside the team (`getLeadWithAccess`) |
| Reassign a lead to another broker on the team        | 🚫  | ✅\* | 🚫  | same, 400 if the target isn't on the team        |

## CRM — tenant-wide (`modules/crm`, Tenant Admin only)

| Action                                                                          | TA   | TL   | BR  | Guard                                                                                              |
| ------------------------------------------------------------------------------- | ---- | ---- | --- | -------------------------------------------------------------------------------------------------- |
| Reports (pipeline/renewals/team-performance/AI-usage) + export                  | ✅\* | ✅\* | 🚫  | `requireModAuthRole('TENANT_ADMIN','TEAM_LEAD')` — Tenant Admin tenant-wide, Team Lead team-scoped |
| Brokerage profile, booking settings, tenant-wide book, self-service export      | ✅\* | 🚫   | 🚫  | `requireModAuthRole('TENANT_ADMIN')`                                                               |
| Brokerage dashboard, reassign all of a person's clients (both brokers notified) | ✅\* | 🚫   | 🚫  | same                                                                                               |

Every Tenant Admin CRM route except the brokerage profile is also behind `requireCompleteBrokerageProfile`
(403 until name, support email and phone are set — Flow 0). Notifications (`/crm/notifications`) are
open to every CRM role, each person seeing only their own.

## Public, no login

| Route                                                    | Key                                                                 |
| -------------------------------------------------------- | ------------------------------------------------------------------- |
| `GET/POST /crm/public/document-requests/{token}[/files]` | The borrower's one-person, 7-day link token (stored only as a hash) |
| `POST /crm/public/bookings/{slug}`                       | The brokerage's enabled booking-page slug                           |

## Internal, service-to-service (no JWT — `X-Internal-Secret` only)

| Caller → Callee                                                                       | Purpose                                                               |
| ------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `modules/crm` → `modules/auth` `GET /internal/modauth/users/{userId}`                 | Resolve a modauth Role from a user id (JWT has no role claim)         |
| `modules/auth` → `modules/crm` `GET /internal/brokers/{userId}/lead-count`            | Deactivation gate (SOW 2.11) — "do they still have clients assigned?" |
| `modules/auth` → `modules/crm` `GET /internal/tenants/{tenantId}/profile-status`      | Tenant Admin profile gate on People/Teams/Invitations (Flow 0)        |
| `modules/platform` → `modules/auth` `PUT /internal/modauth/tenants/{tenantId}/access` | Suspend/reactivate/cancel → block or restore sign-in (Flow 1)         |
| `modules/auth` → `modules/platform` `POST /webhooks/auth-events`                      | Sign-in and security events into the activity log (Flow 1)            |
| `modules/auth` → `modules/platform` `POST /webhooks/owner-accepted`                   | Flip a brokerage's onboarding status from Pending to Active           |
| `modules/platform`/`modules/crm` → `modules/tenant`                                   | Tenant registry CRUD (Gen_TNT)                                        |

All four are gated by the same shared secret convention (`X-Internal-Secret` header, compared
against `INTERNAL_SERVICE_SECRET`), never a JWT — these are calls between this app's own
services, not something a browser or a CRM user ever reaches directly.
