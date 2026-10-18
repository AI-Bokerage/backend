import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';

import * as contactsService from './contacts.service';
import { resolveTeam } from './modauth.client';
import { ApiError } from '../../utils/api-error';
import { asyncHandler } from '../../utils/async-handler';

function crmUser(req: Request) {
  // Guaranteed set by requireModAuthRole before any handler here runs.
  return req.crmUser!;
}

async function requireOwnTeam(req: Request) {
  const user = crmUser(req);
  if (!user.teamId) {
    throw new ApiError('You are not assigned to a team', StatusCodes.FORBIDDEN);
  }
  return resolveTeam(user.teamId, user.bearerToken);
}

export const createContactHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const contact = await contactsService.createContact(user.tenantId, user.userId, req.body);
  res.status(StatusCodes.CREATED).json({ success: true, data: contact });
});

export const listMyContactsHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const contacts = await contactsService.listContacts(user.tenantId, user.userId, {
    search: req.query.search as string | undefined,
    category: req.query.category as contactsService.ContactCategory | undefined,
  });
  res.status(StatusCodes.OK).json({ success: true, data: contacts });
});

export const contactCategoryCountsHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const counts = await contactsService.categoryCounts(user.tenantId, user.userId);
  res.status(StatusCodes.OK).json({ success: true, data: counts });
});

export const updateContactHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const contact = await contactsService.updateContact(
    user.tenantId,
    req.params.id as string,
    [user.userId],
    req.body,
  );
  res.status(StatusCodes.OK).json({ success: true, data: contact });
});

export const deleteContactHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  await contactsService.deleteContact(
    user.tenantId,
    req.params.id as string,
    [user.userId],
    user.userId,
  );
  res.status(StatusCodes.NO_CONTENT).send();
});

export const teamContactsHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const team = await requireOwnTeam(req);
  const contacts = await contactsService.listForBrokers(user.tenantId, team.brokerIds);
  res.status(StatusCodes.OK).json({ success: true, data: contacts });
});
