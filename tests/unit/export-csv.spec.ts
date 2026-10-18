import { toCsv } from '../../modules/platform/export.service';

describe('toCsv — "spreadsheets" in the export package', () => {
  it('quotes commas/quotes/newlines and neutralises spreadsheet formulas', () => {
    const csv = toCsv([
      { name: 'Smith, Jo', note: 'said "hi"\nbye', formula: '=HYPERLINK("x")', when: new Date(0) },
      { name: 'Ann', extra: { a: 1 }, skipped: undefined },
    ]);
    const [header, row1, ...rest] = csv.split('\n');
    expect(header).toBe('name,note,formula,when,extra');
    expect(row1).toBe('"Smith, Jo","said ""hi""');
    expect(rest.join('\n')).toContain(`bye","'=HYPERLINK(""x"")",1970-01-01T00:00:00.000Z,`);
    expect(csv).toContain('Ann,,,,"{""a"":1}"');
  });
});
