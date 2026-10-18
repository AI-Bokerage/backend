import type { Request, Response } from 'express';
import { Router } from 'express';
import { z } from 'zod';

import { env } from '../../config/env';
import { validate } from '../../middlewares/validate.middleware';
import { asyncHandler } from '../../utils/async-handler';

/**
 * Sign-in from this API's own Swagger page (/docs), so it can be tested end to
 * end in one browser tab. Forwards to the auth service (modauth) and passes its
 * HttpOnly access_token cookie back; every other route here already accepts
 * that cookie. Same behaviour as the frontend's own sign-in — just reachable here.
 */
const sessionRouter = Router();

const loginSchema = z.object({
  body: z.object({
    email: z.string().email(),
    password: z.string().min(1),
    acceptTerms: z.boolean().optional(),
  }),
  query: z.object({}).optional(),
  params: z.object({}).optional(),
});

function forwardTo(authPath: string) {
  return asyncHandler(async (req: Request, res: Response) => {
    const upstream = await fetch(`${env.platform.authServiceBaseUrl}${authPath}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'User-Agent': req.get('user-agent') ?? 'swagger' },
      body: JSON.stringify(req.body),
    });
    for (const cookie of upstream.headers.getSetCookie()) res.append('Set-Cookie', cookie);
    const body = (await upstream.json().catch(() => ({}))) as Record<string, unknown>;
    // The token is in the cookie; don't echo it into the Swagger page.
    delete body.accessToken;
    delete body.refreshToken;
    res.status(upstream.status).json(body);
  });
}

sessionRouter.post('/login', validate(loginSchema), forwardTo('/api/v1/modauth/login'));
sessionRouter.post(
  '/super-admin-login',
  validate(loginSchema),
  forwardTo('/api/v1/auth/super-admin/login'),
);

export { sessionRouter };
