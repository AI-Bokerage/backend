import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';

import { leadAccessIds } from './crm-auth.middleware';
import * as emailDraftService from './email-draft.service';
import { asyncHandler } from '../../utils/async-handler';

export const createDraftHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = req.crmUser!;
  const draft = await emailDraftService.createDraft(
    user.tenantId,
    user.userId,
    req.params.id as string,
    await leadAccessIds(user),
    req.body,
  );
  res.status(StatusCodes.CREATED).json({ success: true, data: draft });
});

export const listDraftsHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = req.crmUser!;
  const drafts = await emailDraftService.listDrafts(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
  );
  res.status(StatusCodes.OK).json({ success: true, data: drafts });
});

export const updateDraftHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = req.crmUser!;
  const draft = await emailDraftService.updateDraft(
    user.tenantId,
    req.params.draftId as string,
    await leadAccessIds(user),
    req.body,
  );
  res.status(StatusCodes.OK).json({ success: true, data: draft });
});
