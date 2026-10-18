import { Router } from 'express';

import { aiDataRouter } from '../modules/crm/ai-data.routes';
import { crmRouter } from '../modules/crm/crm.routes';
import { platformRouter } from '../modules/platform/platform.routes';
import { sessionRouter } from '../modules/platform/session.routes';

const rootRouter = Router();

// Auth lives in modules/auth as its own Java service (gen-auth-starter +
// this repo's role/terms/invitation additions) — see docker-compose.yml's
// `modauth` service. Nothing under /auth is proxied from this Node app.
//
// modules/document was removed in a prior commit (before this session) with
// no replacement wired in here — its router import was left dangling
// (routes/index.ts referenced a module that no longer exists, which would
// fail to compile). Removed rather than restored; ask if it needs to come back.
// Sign-in from this API's Swagger page — forwards to modauth (see session.routes.ts).
rootRouter.use('/session', sessionRouter);
rootRouter.use('/platform', platformRouter);
// Flow 3 (Team Lead) + the minimal Flow 4 (Broker) core it needs — see
// modules/crm's own scope notes for what's a deliberate stand-in.
rootRouter.use('/crm', crmRouter);
// Flow 4.10: the AI Assistant's read-only window onto the CRM (service key, not a sign-in).
rootRouter.use('/ai-data/v1', aiDataRouter);

export { rootRouter };
