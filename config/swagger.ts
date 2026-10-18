import path from 'node:path';
import process from 'node:process';
import type { Options } from 'swagger-jsdoc';
import swaggerJSDoc from 'swagger-jsdoc';

import { env } from './env';
import { REQUEST_EXAMPLES } from './swagger-examples';

// Local/dev only: the seeded test accounts from docs/manual-testing.md.
const TEST_ACCOUNTS =
  env.nodeEnv === 'production'
    ? ''
    : `
**Test accounts (local only):**

| Sign in as | Email | Password | Use |
| --- | --- | --- | --- |
| Team Lead | \`leah@acme.test\` | \`LeahPassword#2026\` | POST /session/login |
| Tenant Admin | \`tara@acme.test\` | \`TaraPassword#2026\` | POST /session/login |
| Super Admin | \`superadmin@example.com\` | \`SuperAdmin@12345\` | POST /session/super-admin-login |
`;

const GETTING_STARTED = `
Everything the mortgage CRM can do, with a **Try it out** button on each item.
You can test the whole product from this page — no other tool needed.

## How to use this page

1. **Sign in first.** Open **Start here — sign in** below → **POST /session/login** →
   **Try it out** → **Execute**. The example is already filled in with a test account.
2. **Pick anything and try it.** Open an item → **Try it out** → change the example if you
   like → **Execute**. You stay signed in on this page until your session ends.
3. **Read the answer** under *Server response*. Green 2xx = it worked.

Need an id (a person, a task…)? Run the matching **list** item first (for example
**GET /crm/leads**) and copy an \`id\` from its answer.
${TEST_ACCOUNTS}
## Who can do what

| Role | Works on |
| --- | --- |
| **Broker** | Their own clients |
| **Team Lead** | Their own clients plus everyone on their team |
| **Assistant** | Their team's clients (view and edit; never create, delete or reveal hidden values) |
| **Tenant Admin** | Runs one brokerage: people, teams, reports, settings (must fill in the brokerage profile first) |
| **Super Admin** | Runs the platform: brokerages, AI cost, activity log. Never sees client records |

Each item says which roles may use it. Using one you are not allowed to gives **403**.

## AI features — how they work

AI never saves anything by itself. Every AI feature follows the same steps:

1. **Start it** (for example *Analyse notes*) → you get an \`id\` and status \`queued\`.
2. **Check on it** with the matching GET until the status is finished
   (\`completed\`, \`processed\`… or \`failed\`). It usually takes a few seconds.
3. **Decide**: accept, edit or reject each suggestion.
4. **Save**: only what you accepted goes into the CRM.

Anything written by AI is marked \`aiLabel: "AI-assisted processing"\`.
If AI is switched off for you the answer is **403 AI_DISABLED**; if today's AI allowance is
used up it is **429 AI_LIMIT_REACHED** with \`resetsAt\` (when it comes back).

## What the answers look like

- Worked: \`{ "success": true, "data": … }\`
- Didn't: \`{ "success": false, "message": "what went wrong, in words" }\`

| Code | Means |
| --- | --- |
| 400 | Something in the request is missing or wrong — the message says what |
| 401 | Not signed in, or the session ended — sign in again |
| 403 | You are not allowed to do this (role, team, or AI switched off) |
| 404 | Not found (or not yours to see) |
| 409 | Can't right now (already done, or in the wrong state) |
| 429 | Daily AI allowance used up |
| 503 | An AI service is not reachable — try again shortly |

**Uploading a file** (documents, voice notes): pick the file in **Try it out**; the type and file
name go in the boxes above it. **Public** items need no sign-in (the borrower's upload link, the
booking page). **Internal** items are for the system's own services, not for people.
`;

/** Area tags, in the order Swagger UI shows them. First matching rule wins. */
const TAGS: { name: string; description: string; match: RegExp }[] = [
  {
    name: 'Start here — sign in',
    description: 'Sign in once, then every other section on this page works for you',
    match: /^\/session\//,
  },
  {
    name: 'Dashboard & search',
    description: 'Broker, Team Lead, Assistant: the home screen and searching your clients',
    match: /^\/crm\/(dashboard|search)$/,
  },
  {
    name: 'Leads & clients',
    description: 'Add a person, list leads and clients, move them through stages, fund, delete',
    match: /^\/crm\/(leads(\/\{id\}(\/(stage|fund|mortgages))?)?|clients)$/,
  },
  {
    name: 'AI features',
    description:
      'Voice notes, "Analyse notes" and the AI review, email drafts, suggested tasks, AI Assistant. ' +
      'Start → check status → decide → save (see "AI features — how they work" above)',
    match: /^\/crm\/(ai\/|voice-notes|leads\/\{id\}\/ai\/)/,
  },
  {
    name: 'Person panel',
    description: 'One person\'s profile, affordability, notes, tags; reveal hidden values',
    match: /^\/crm\/leads\/(\{id\}\/(panel|affordability|notes|tags|profile|reveal)|affordability)/,
  },
  {
    name: 'Client intake',
    description: 'The full application: applicants, jobs, income, debts, properties, savings, follow-ups',
    match: /^\/crm\/leads\/\{id\}\/(intake|applicants|debts|properties|funds-sources|follow-ups)/,
  },
  {
    name: 'Documents',
    description: 'Which documents a person must send, the upload link, uploads, using what was read',
    match: /^\/crm\/(leads\/\{id\}\/documents|documents\/)/,
  },
  {
    name: 'Email',
    description: 'Send from your own inbox; saved drafts (nothing is ever sent automatically)',
    match: /^\/crm\/(emails|email-drafts|leads\/\{id\}\/email-drafts)/,
  },
  {
    name: 'Tasks',
    description: 'Your tasks, suggested tasks to approve, tasks whose person was deleted',
    match: /^\/crm\/(team\/)?tasks/,
  },
  {
    name: 'Calendar & Google',
    description: 'Connect Google, appointments, the team calendar',
    match: /^\/crm\/(integrations\/google|appointments|team\/calendar)/,
  },
  {
    name: 'Contacts',
    description: 'Realtors, lawyers and other people you work with',
    match: /^\/crm\/(team\/)?contacts/,
  },
  { name: 'Notifications', description: 'Reminders and the morning digest', match: /^\/crm\/notifications/ },
  { name: 'My settings', description: 'Switch AI features on or off for yourself', match: /^\/crm\/settings/ },
  {
    name: 'Team Lead',
    description: 'Team Lead only: team dashboard, the team\'s clients, moving a client to another broker',
    match: /^\/crm\/team\//,
  },
  {
    name: 'Tenant Admin',
    description: 'Tenant Admin only: brokerage dashboard, profile, booking page, all clients, data export',
    match: /^\/crm\/tenant\//,
  },
  {
    name: 'Reports',
    description: 'Tenant Admin (whole brokerage) and Team Lead (own team): reports and spreadsheet export',
    match: /^\/crm\/reports/,
  },
  {
    name: 'Platform — Brokerages',
    description: 'Super Admin: create, suspend, reactivate and cancel brokerages; their data exports',
    match: /^\/platform\/(brokerages(?!\/\{id\}\/usage)|exports)/,
  },
  {
    name: 'Platform — AI cost',
    description: 'Super Admin: today\'s AI cost per brokerage against its daily cap; "allow extra for today"',
    match: /^\/platform\/brokerages\/\{id\}\/usage/,
  },
  {
    name: 'Platform — Activity log',
    description: 'Super Admin: everything that happened, filter and export',
    match: /^\/platform\/activity-log/,
  },
  { name: 'Platform — My account', description: 'Super Admin: who you are signed in as', match: /^\/platform\/me/ },
  {
    name: 'Public (no login)',
    description: 'What a borrower or a visitor uses: the document-upload link and the booking page',
    match: /^\/crm\/public\//,
  },
  {
    name: 'Internal (service-to-service)',
    description: 'Used by the system\'s own services with a secret header — not for people',
    match: /^\/(crm\/(internal|webhooks)|platform\/webhooks)\//,
  },
  { name: 'Health', description: 'Is the server up?', match: /^\/health/ },
];

const ERROR = { $ref: '#/components/schemas/Error' };
const errorResponse = (description: string) => ({
  description,
  content: { 'application/json': { schema: ERROR } },
});

type Operation = {
  summary?: string;
  tags?: string[];
  security?: unknown[];
  parameters?: { in?: string }[];
  requestBody?: { content?: Record<string, { example?: unknown; examples?: unknown }> };
  responses?: Record<string, unknown>;
};

/**
 * Tags every operation by area and gives every one the standard error
 * responses that apply to it, so the docs stay uniform as routes are added
 * (tests/unit/swagger-coverage.spec.ts fails on any undocumented route).
 */
function enrich(spec: Record<string, unknown>) {
  const paths = spec.paths as Record<string, Record<string, Operation>>;
  for (const [route, ops] of Object.entries(paths)) {
    const tag = TAGS.find((t) => t.match.test(route))?.name;
    for (const [method, op] of Object.entries(ops)) {
      if (tag) op.tags = [tag];
      // Internal spec references mean nothing to the reader of this page.
      if (op.summary) op.summary = op.summary.replace(/\s*\((?:SOW|Flow)[^)]*\)|\s*— STUB\b/g, '');
      const json = op.requestBody?.content?.['application/json'];
      const example = REQUEST_EXAMPLES[`${method.toUpperCase()} ${route}`];
      if (json && example !== undefined && json.example === undefined && !json.examples) {
        json.example = example;
      }
      const responses = (op.responses ??= {});
      const add = (code: string, text: string) => {
        responses[code] ??= errorResponse(text);
      };
      const isInternal = /^\/(crm\/internal|platform\/webhooks)\//.test(route);
      if (isInternal) op.security = [{ internalSecret: [] }];
      if (op.requestBody || route.includes('{') || op.parameters?.some((p) => p.in === 'query')) {
        add('400', 'Validation failed — the message names each bad field');
      }
      if (op.security?.length || isInternal) {
        add(
          '401',
          isInternal ? 'Missing or wrong X-Internal-Secret' : 'Missing, invalid or expired token',
        );
      }
      if (op.security?.length) {
        add('403', 'Not allowed — wrong role, outside your scope, or your brokerage is suspended');
      }
      if (route.includes('{')) add('404', 'Not found');
      if (tag === 'AI features') {
        add('429', "AI_LIMIT_REACHED — today's AI allowance is used up; resetsAt says when it comes back");
        add('503', 'An AI service is not reachable right now — try again shortly');
      }
      add('500', 'Something failed on the server');
    }
  }
  spec.tags = TAGS.map(({ name, description }) => ({ name, description }));
  return spec;
}

export const swaggerOptions: Options = {
  definition: {
    openapi: '3.0.3',
    info: {
      title: 'Mortgage CRM — API (try everything here)',
      version: '1.0.0',
      description: GETTING_STARTED,
    },
    servers: [{ url: `http://localhost:${env.port}${env.apiPrefix}`, description: 'Local' }],
    components: {
      securitySchemes: {
        internalSecret: {
          type: 'apiKey',
          in: 'header',
          name: 'X-Internal-Secret',
          description: 'Shared INTERNAL_SERVICE_SECRET — service-to-service calls only',
        },
      },
    },
  },
  // process.cwd() (always the repo root — dev's `tsx watch server.ts` and
  // prod's `node dist/server.js` both run from /app), not __dirname: __dirname
  // shifts to dist/config once compiled, silently resolving to a docs/
  // directory that doesn't exist there and never surfacing as an error —
  // just an empty spec (confirmed: /docs rendered a valid but path-less
  // Swagger UI in the built image before this fix).
  apis: [
    path.join(process.cwd(), 'docs/swagger/*.yaml'),
    path.join(process.cwd(), 'modules/**/*.ts'),
  ],
};

export function buildSwaggerSpec() {
  return enrich(swaggerJSDoc(swaggerOptions) as Record<string, unknown>);
}
