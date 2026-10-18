jest.mock('../../config/database');
jest.mock('../../modules/crm/ai-deletion.service');
jest.mock('../../modules/crm/document-storage');

import { getPrismaClient } from '../../config/database';
import { deleteClientsAiData } from '../../modules/crm/ai-deletion.service';
import { purgeTenantCrmData } from '../../modules/platform/export.service';

const many = () => ({ deleteMany: jest.fn() });
const mockPrisma = {
  leadDocument: { findMany: jest.fn().mockResolvedValue([]) },
  lead: { findMany: jest.fn().mockResolvedValue([{ id: 'lead-1' }, { id: 'lead-2' }]), deleteMany: jest.fn() },
  task: many(), appointment: many(), contact: many(), notification: many(), googleConnection: many(),
  crmUserSettings: many(), tenantProfile: many(), bookingPageSettings: many(),
  $transaction: jest.fn(),
};

it('Flow 1 purge of a cancelled brokerage also wipes every person\'s copies in the AI services', async () => {
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
  await purgeTenantCrmData('tenant-old');
  expect(mockPrisma.lead.findMany).toHaveBeenCalledWith({ where: { tenantId: 'tenant-old' }, select: { id: true } });
  expect(deleteClientsAiData).toHaveBeenCalledWith('tenant-old', ['lead-1', 'lead-2']);
});
