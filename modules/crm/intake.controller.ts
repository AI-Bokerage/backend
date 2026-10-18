import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';

import { leadAccessIds } from './crm-auth.middleware';
import * as intakeService from './intake.service';
import { asyncHandler } from '../../utils/async-handler';

function crmUser(req: Request) {
  // Guaranteed set by requireModAuthRole before any handler here runs.
  return req.crmUser!;
}

export const getIntakeBundleHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const bundle = await intakeService.getIntakeBundle(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
  );
  res.status(StatusCodes.OK).json({ success: true, data: bundle });
});

export const addApplicantHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const applicant = await intakeService.addApplicant(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.body,
  );
  res.status(StatusCodes.CREATED).json({ success: true, data: applicant });
});

export const updateApplicantHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const applicant = await intakeService.updateApplicant(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.params.applicantId as string,
    req.body,
  );
  res.status(StatusCodes.OK).json({ success: true, data: applicant });
});

export const removeApplicantHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  await intakeService.removeApplicant(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.params.applicantId as string,
  );
  res.status(StatusCodes.NO_CONTENT).send();
});

export const addEmploymentHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const employment = await intakeService.addEmployment(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.params.applicantId as string,
    req.body,
  );
  res.status(StatusCodes.CREATED).json({ success: true, data: employment });
});

export const addNoaIncomeHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const noa = await intakeService.addNoaIncome(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.params.applicantId as string,
    req.body,
  );
  res.status(StatusCodes.CREATED).json({ success: true, data: noa });
});

export const addDebtHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const debt = await intakeService.addDebt(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.body,
  );
  res.status(StatusCodes.CREATED).json({ success: true, data: debt });
});

export const updateDebtHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const debt = await intakeService.updateDebt(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.params.debtId as string,
    req.body,
  );
  res.status(StatusCodes.OK).json({ success: true, data: debt });
});

export const removeDebtHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  await intakeService.removeDebt(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.params.debtId as string,
  );
  res.status(StatusCodes.NO_CONTENT).send();
});

export const addPropertyRecordHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const property = await intakeService.addPropertyRecord(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.body,
  );
  res.status(StatusCodes.CREATED).json({ success: true, data: property });
});

export const updatePropertyRecordHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const property = await intakeService.updatePropertyRecord(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.params.propertyId as string,
    req.body,
  );
  res.status(StatusCodes.OK).json({ success: true, data: property });
});

export const removePropertyRecordHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  await intakeService.removePropertyRecord(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.params.propertyId as string,
  );
  res.status(StatusCodes.NO_CONTENT).send();
});

export const addFundsSourceHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const fundsSource = await intakeService.addFundsSource(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.body,
  );
  res.status(StatusCodes.CREATED).json({ success: true, data: fundsSource });
});

export const removeFundsSourceHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  await intakeService.removeFundsSource(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.params.fundsSourceId as string,
  );
  res.status(StatusCodes.NO_CONTENT).send();
});

export const addFollowUpHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const followUp = await intakeService.addFollowUp(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.body,
  );
  res.status(StatusCodes.CREATED).json({ success: true, data: followUp });
});

export const removeFollowUpHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  await intakeService.removeFollowUp(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.params.followUpId as string,
  );
  res.status(StatusCodes.NO_CONTENT).send();
});
