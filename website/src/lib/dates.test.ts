// Run with: node --test src/lib/dates.test.ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { formatUpdated } from './dates.ts';

test('formats a frontmatter date given as a string or a Date', () => {
  assert.equal(formatUpdated('2026-10-26'), 'October 26, 2026');
  assert.equal(formatUpdated(new Date('2026-10-26')), 'October 26, 2026');
  assert.equal(formatUpdated('2026-10-26T00:00:00.000Z'), 'October 26, 2026');
});

test('leaves something unparseable as it was', () => {
  assert.equal(formatUpdated('soon'), 'soon');
});
