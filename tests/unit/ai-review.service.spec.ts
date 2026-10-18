import * as ai from '../../modules/crm/ai-review.service';

describe('draftEmail — Flow 4.7 purposes', () => {
  it('offers exactly the flow purposes', () => {
    expect(Object.keys(ai.EMAIL_INTENTS)).toEqual([
      'FOLLOW_UP',
      'RENEWAL',
      'BIRTHDAY',
      'DOCUMENTS',
      'ANNUAL_REVIEW',
      'REFINANCE',
    ]);
  });
});
