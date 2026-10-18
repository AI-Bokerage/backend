jest.mock('../../config/database');
jest.mock('../../modules/platform/activity-log');
jest.mock('../../modules/crm/ai.client');

import { createHmac } from 'node:crypto';

import { getPrismaClient } from '../../config/database';
import { onDocreadEvent, planDocumentApply } from '../../modules/crm/ai-documents.service';
import { verifyAiSignature } from '../../modules/crm/ai-webhooks';

const mockPrisma = {
  leadDocument: { findUnique: jest.fn(), update: jest.fn() },
  lead: { findUnique: jest.fn() },
  notification: { create: jest.fn() },
};

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
  mockPrisma.lead.findUnique.mockResolvedValue({ id: 'lead-1', name: 'Pat Lee', brokerUserId: 'me' });
  mockPrisma.leadDocument.update.mockImplementation(async ({ data }) => ({ ...DOC, ...data }));
});

const DOC = {
  id: 'doc-1',
  tenantId: 'tenant-1',
  leadId: 'lead-1',
  aiDocumentId: 'ai-1',
  readStatus: 'PENDING',
  fileName: 't4.pdf',
  type: 'T4',
};
const callback = (data: Record<string, unknown>) => ({
  id: 'delivery-1',
  event: 'document.processed',
  data: { id: 'ai-1', brokerageId: 'tenant-1', crmDocumentId: 'doc-1', status: 'processed', errorCode: null, ...data },
});

describe('verifyAiSignature — AI service callbacks', () => {
  const secret = 'a-webhook-secret-of-at-least-32-chars!!';
  const body = Buffer.from('{"id":"d1"}');
  const sign = (t: number, raw = body) =>
    `t=${t},v1=${createHmac('sha256', secret).update(`${t}.`).update(raw).digest('hex')}`;
  const now = 1_792_000_000_000;

  it('accepts a fresh, correctly signed body', () => {
    expect(verifyAiSignature(secret, sign(now / 1000), body, now)).toBe(true);
  });
  it('refuses a changed body, a wrong secret, or an old timestamp', () => {
    expect(verifyAiSignature(secret, sign(now / 1000), Buffer.from('{"id":"d2"}'), now)).toBe(false);
    expect(verifyAiSignature('another-secret', sign(now / 1000), body, now)).toBe(false);
    expect(verifyAiSignature(secret, sign(now / 1000 - 301), body, now)).toBe(false);
    expect(verifyAiSignature(secret, 'garbage', body, now)).toBe(false);
  });
});

describe('onDocreadEvent — "Could the system read it?"', () => {
  it('marks the document read and tells the broker, once', async () => {
    mockPrisma.leadDocument.findUnique.mockResolvedValueOnce(DOC);
    await onDocreadEvent(callback({}));
    expect(mockPrisma.leadDocument.update).toHaveBeenCalledWith({ where: { id: 'doc-1' }, data: { readStatus: 'READ' } });
    expect(mockPrisma.notification.create).toHaveBeenCalledWith({
      data: expect.objectContaining({ userId: 'me', type: 'document.ready_for_review' }),
    });

    // The same delivery again (at least once): nothing changes, nobody is told twice.
    mockPrisma.leadDocument.findUnique.mockResolvedValueOnce({ ...DOC, readStatus: 'READ' });
    await onDocreadEvent(callback({}));
    expect(mockPrisma.notification.create).toHaveBeenCalledTimes(1);
  });

  it('a rejected or failed read becomes "couldn\'t read it — retry, or type the values in"', async () => {
    mockPrisma.leadDocument.findUnique.mockResolvedValue(DOC);
    await onDocreadEvent(callback({ status: 'rejected' }));
    // Rejected (blank, not a document) is final: the broker is told to upload a clearer copy, not to retry.
    expect(mockPrisma.notification.create).toHaveBeenCalledWith({
      data: expect.objectContaining({ type: 'document.unreadable', title: expect.stringContaining('upload a clearer copy') }),
    });
  });

  it("ignores a callback about another brokerage's or another reading's document", async () => {
    mockPrisma.leadDocument.findUnique.mockResolvedValue(DOC);
    await onDocreadEvent(callback({ brokerageId: 'tenant-2' }));
    await onDocreadEvent(callback({ id: 'ai-other' }));
    expect(mockPrisma.leadDocument.update).not.toHaveBeenCalled();
  });
});

describe('planDocumentApply — approved values into the CRM', () => {
  const v = (target: string, value: string | number, extra: Record<string, unknown> = {}) => ({
    key: target,
    label: target,
    instance: 0,
    target,
    value,
    display: String(value),
    replaceExisting: false,
    ...extra,
  });
  const lead = { name: 'Pat Lee', income: 80000, propertyAddress: null, propertyValue: null };

  it('fills blank lead fields only, unless the broker chose to replace', () => {
    const plan = planDocumentApply(
      {
        mortgage: null,
        values: [
          v('income.annualAmount', 95000),
          v('property.address', '12 King St W'),
        ],
      },
      lead,
    );
    expect(plan.leadData).toEqual({ propertyAddress: '12 King St W' });
    expect(plan.skipped).toEqual(['income.annualAmount']);

    const replaced = planDocumentApply(
      { mortgage: null, values: [v('income.annualAmount', 95000, { replaceExisting: true })] },
      lead,
    );
    expect(replaced.leadData).toEqual({ income: 95000 });
  });

  it('groups debts by instance and keeps only complete ones; job details go to the main applicant', () => {
    const plan = planDocumentApply(
      {
        mortgage: null,
        values: [
          v('debts[].creditor', 'Visa'),
          v('debts[].balance', 3000),
          v('debts[].monthlyPayment', 90),
          v('debts[].creditor', 'Car loan', { instance: 1 }),
          v('employment.employerName', 'Shopify'),
          v('income.taxYear', 2025),
        ],
      },
      lead,
    );
    expect(plan.debtRows).toEqual([{ description: 'Visa', balance: 3000, monthlyPayment: 90 }]);
    expect(plan.employment).toMatchObject({ employer: 'Shopify', type: 'EMPLOYED' });
    expect(plan.applicant).toEqual({ firstName: 'Pat', lastName: 'Lee' });
    expect(plan.notApplied.map((n) => n.label)).toEqual(['income.taxYear']);
  });
});
