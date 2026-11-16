import { describe, expect, it } from 'vitest';
import { parseCriteria } from './api';

describe('parseCriteria', () => {
  it('takes one criterion per line', () => {
    expect(parseCriteria('One\nTwo')).toEqual(['One', 'Two']);
  });

  it('skips blank lines and trims the edges', () => {
    expect(parseCriteria('  One  \n\n   \nTwo\n')).toEqual(['One', 'Two']);
  });

  it('returns an empty list when there is nothing', () => {
    expect(parseCriteria('   \n  ')).toEqual([]);
  });
});
