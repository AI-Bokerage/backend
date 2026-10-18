jest.mock('../../modules/platform/activity-log');
jest.mock('../../modules/crm/google/google-connection.service');

const mockMessagesSend = jest.fn();
jest.mock('googleapis', () => ({
  google: { gmail: jest.fn(() => ({ users: { messages: { send: mockMessagesSend } } })) },
}));

import { sendEmail } from '../../modules/crm/google/gmail.service';
import { requireAuthenticatedClient } from '../../modules/crm/google/google-connection.service';
import { recordActivity } from '../../modules/platform/activity-log';

const TENANT = 'tenant-1';
const input = {
  to: 'client@example.com',
  subject: 'Following up',
  body: 'Hi there',
  leadId: 'lead-1',
};

beforeEach(() => {
  jest.clearAllMocks();
});

describe('sendEmail — "Email account connected?"', () => {
  it('throws 400 ("Connect Gmail to send") when not connected, never reaching the Gmail API', async () => {
    (requireAuthenticatedClient as jest.Mock).mockRejectedValue(
      Object.assign(new Error('Connect Gmail to send'), { statusCode: 400 }),
    );

    await expect(sendEmail(TENANT, 'broker-1', input)).rejects.toMatchObject({ statusCode: 400 });
    expect(mockMessagesSend).not.toHaveBeenCalled();
  });

  it('sends via the Gmail API and logs it on the activity log when connected', async () => {
    (requireAuthenticatedClient as jest.Mock).mockResolvedValue({
      client: 'fake-client',
      googleEmail: 'broker@gmail.com',
    });
    mockMessagesSend.mockResolvedValue({ data: { id: 'msg-1' } });

    await sendEmail(TENANT, 'broker-1', input);

    expect(mockMessagesSend).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: 'me',
        requestBody: expect.objectContaining({ raw: expect.any(String) }),
      }),
    );
    expect(recordActivity).toHaveBeenCalledWith(
      'crm.email.sent',
      expect.objectContaining({
        actorId: 'broker-1',
        targetId: 'lead-1',
        metadata: expect.objectContaining({ to: input.to }),
      }),
    );
  });

  it('base64url-encodes the raw message with no padding characters', async () => {
    (requireAuthenticatedClient as jest.Mock).mockResolvedValue({
      client: 'fake-client',
      googleEmail: 'broker@gmail.com',
    });
    mockMessagesSend.mockResolvedValue({ data: {} });

    await sendEmail(TENANT, 'broker-1', input);

    const raw = mockMessagesSend.mock.calls[0][0].requestBody.raw as string;
    const decoded = Buffer.from(raw, 'base64url').toString('utf8');
    expect(decoded).toContain('To: client@example.com');
    expect(decoded).toContain('Subject: Following up');
    expect(decoded).toContain('Hi there');
  });
});
