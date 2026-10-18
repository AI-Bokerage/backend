/**
 * The body Swagger's "Try it out" starts with, per route ("METHOD /path" as in
 * the docs). Realistic values a person can send as-is or tweak; an id they must
 * supply says where to get it. tests/unit/swagger-coverage.spec.ts fails when
 * a JSON body has no example here or in its YAML.
 */
const PASTE_LEAD = 'PASTE-A-LEAD-ID (from GET /crm/leads)';
const PASTE_USER = 'PASTE-A-USER-ID (a broker on your team)';
const NEXT_MONTH = '2026-11-20T15:00:00.000Z';

export const REQUEST_EXAMPLES: Record<string, unknown> = {
  // ── Sign in ──
  'POST /session/login': { email: 'leah@acme.test', password: 'LeahPassword#2026', acceptTerms: true },
  'POST /session/super-admin-login': { email: 'superadmin@example.com', password: 'SuperAdmin@12345' },

  // ── Leads & clients ──
  'POST /crm/leads': {
    name: 'Sam Patel',
    email: 'sam.patel@example.com',
    phone: '+1 416 555 0142',
    income: 92000,
    monthlyDebts: 450,
    downPayment: 80000,
    propertyAddress: '12 King St W, Toronto, ON',
    propertyValue: 650000,
    notes: 'Met at the open house. First-time buyer, wants a condo downtown.',
    tags: ['first-time buyer'],
  },
  'DELETE /crm/leads/{id}': { confirmName: 'Sam Patel' },
  'PATCH /crm/leads/{id}/stage': { stage: 'QUALIFIED' },
  'POST /crm/leads/{id}/fund': {
    lender: 'TD Bank',
    interestRate: 4.79,
    balance: 570000,
    monthlyPayment: 3240,
    maturityDate: '2031-11-01T00:00:00.000Z',
  },
  'POST /crm/leads/{id}/mortgages': {
    lender: 'RBC',
    interestRate: 5.1,
    balance: 120000,
    monthlyPayment: 890,
    maturityDate: '2029-06-01T00:00:00.000Z',
  },

  // ── Client intake ──
  'POST /crm/leads/{id}/applicants': {
    firstName: 'Sam',
    lastName: 'Patel',
    email: 'sam.patel@example.com',
    phone: '+1 416 555 0142',
    address: '88 Queen St E, Toronto, ON',
    dateOfBirth: '1988-04-12T00:00:00.000Z',
  },
  'POST /crm/leads/{id}/applicants/{applicantId}/employment': {
    type: 'EMPLOYED',
    employer: 'Northwind Health',
    position: 'Registered Nurse',
    startDate: '2019-02-01T00:00:00.000Z',
    baseSalary: 92000,
  },
  'POST /crm/leads/{id}/applicants/{applicantId}/noa-income': { taxYear: 2025, totalIncome: 94500 },
  'POST /crm/leads/{id}/debts': { description: 'Car loan', balance: 14000, monthlyPayment: 380 },
  'POST /crm/leads/{id}/properties': {
    address: '45 Elm Ave, Mississauga, ON',
    propertyTaxes: 4200,
    isCondo: false,
    isRental: true,
    mortgageBalance: 310000,
    mortgageLender: 'Scotiabank',
    maturityDate: '2028-03-01T00:00:00.000Z',
    ownership: 'Joint',
  },
  'POST /crm/leads/{id}/funds-sources': { accountType: 'TFSA', amount: 40000, isGift: false },
  'POST /crm/leads/{id}/follow-ups': { type: 'Call', scheduledAt: NEXT_MONTH },

  // ── Person panel ──
  'POST /crm/leads/affordability/recalculate': {},
  'PATCH /crm/leads/{id}/notes': { brokerNotes: 'Prefers email. Pre-approval meeting booked.' },
  'PATCH /crm/leads/{id}/tags': { tags: ['first-time buyer', 'condo'] },
  'PATCH /crm/leads/{id}/profile': { city: 'Toronto', phone: '+1 416 555 0199' },
  'POST /crm/leads/{id}/reveal': { field: 'sin' },

  // ── AI features ──
  'POST /crm/ai/review-notes': {
    notes:
      'Met Sam Patel today. Works at Northwind Health as a nurse, earns about $92,000 a year. ' +
      'Wants a condo around $650,000 with $80,000 down. Follow up Friday about pre-approval documents.',
    fields: { name: 'Sam Patel', email: 'sam.patel@example.com' },
  },
  'POST /crm/ai/runs/{id}/decisions': {
    decisions: [
      { suggestionId: 'PASTE-A-SUGGESTION-ID (from GET /crm/ai/runs/{id})', decision: 'approved' },
    ],
  },
  'POST /crm/leads/{id}/ai/apply': { aiRunId: 'PASTE-THE-RUN-ID (from POST /crm/leads/{id}/ai/review)' },
  'POST /crm/leads/{id}/ai/draft-email': {
    intent: 'FOLLOW_UP',
    purpose: 'Check in after our call about pre-approval',
    tone: 'warm',
  },
  'POST /crm/ai/email-drafts/{id}/refine': { instruction: 'Make it shorter and friendlier' },
  'POST /crm/ai/search': { query: 'cottage on the lake' },
  'POST /crm/ai/recommendations/{id}/task': { dueDate: '2026-11-20', priority: 'high' },
  'POST /crm/ai/assistant': { question: 'Which files are missing tax documents?' },

  // ── Tasks ──
  'POST /crm/tasks': { title: 'Call Sam about pre-approval', dueDate: NEXT_MONTH, priority: 'HIGH' },
  'PATCH /crm/tasks/{id}/link': { leadId: PASTE_LEAD },

  // ── Team / Tenant admin ──
  'PATCH /crm/team/leads/{id}/reassign': { toBrokerUserId: PASTE_USER },
  'PATCH /crm/tenant/profile': {
    displayName: 'Acme Mortgages',
    supportEmail: 'help@acme.test',
    phone: '+1 416 555 0100',
    address: '100 Bay St, Toronto, ON',
  },
  'PATCH /crm/tenant/booking-settings': { slug: 'acme', enabled: true, bufferMinutes: 15 },
  'POST /crm/tenant/people/{userId}/reassign-clients': { toBrokerUserId: PASTE_USER },

  // ── Calendar, email, contacts ──
  'POST /crm/appointments': {
    title: 'Pre-approval consultation with Sam',
    type: 'CONSULTATION',
    startTime: NEXT_MONTH,
    endTime: '2026-11-20T15:30:00.000Z',
  },
  'POST /crm/emails/send': {
    to: 'sam.patel@example.com',
    subject: 'Your pre-approval documents',
    body: 'Hi Sam,\n\nCould you send your last two pay stubs?\n\nThanks,\nLeah',
  },
  'POST /crm/leads/{id}/email-drafts': {
    intent: 'DOCUMENTS',
    subject: 'Documents for your application',
    body: 'Hi Sam,\n\nTo move ahead we need your T4 and two recent pay stubs.\n\nThanks,\nLeah',
  },
  'PATCH /crm/email-drafts/{draftId}': { subject: 'Documents for your mortgage application' },
  'POST /crm/contacts': {
    category: 'REALTOR',
    name: 'Jordan Lee',
    company: 'Lakeshore Realty',
    phone: '+1 416 555 0177',
    email: 'jordan@lakeshore.example',
  },

  // ── Documents ──
  'PUT /crm/leads/{id}/documents/required': { types: ['T4', 'PAY_STUB', 'ID'] },
  'POST /crm/leads/{id}/documents/request': { useSystemAddress: true },
  'POST /crm/documents/{documentId}/decisions': {
    decisions: [
      { fieldId: 'PASTE-A-FIELD-ID (from GET /crm/documents/{documentId}/review)', decision: 'approved' },
    ],
  },
  'PUT /crm/documents/{documentId}/mortgage': { createNew: true },

  // ── Settings ──
  'PATCH /crm/settings': { aiEnabled: true },

  // ── Public (no login) ──
  'POST /crm/public/bookings/{slug}': {
    bookingRef: 'calendly-123456',
    name: 'Ava Chen',
    email: 'ava.chen@example.com',
    startTime: NEXT_MONTH,
    endTime: '2026-11-20T15:30:00.000Z',
  },

  // ── Platform (Super Admin) ──
  'POST /platform/brokerages': {
    name: 'Lakeview Mortgages',
    ownerName: 'Priya Nair',
    ownerEmail: 'priya@lakeview.test',
  },
  'PATCH /platform/brokerages/{id}/suspend': { reason: 'Unpaid invoice' },
  'PATCH /platform/brokerages/{id}/reactivate': { note: 'Invoice paid' },

  // ── Internal (service to service) ──
  'POST /crm/webhooks/ai/{service}': {
    id: 'delivery-id',
    event: 'document.processed',
    occurredAt: '2026-10-16T12:00:00.000Z',
    data: { id: 'ai-document-id', brokerageId: 'brokerage-id', status: 'processed', errorCode: null },
  },
  'POST /platform/webhooks/auth-events': { action: 'auth.login.success', ip: '203.0.113.7' },
  'POST /platform/webhooks/owner-accepted': { tenantId: 'PASTE-A-BROKERAGE-ID' },
};
