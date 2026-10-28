// Run with: node --test src/lib/site.test.ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { siteConfig } from './site.ts';

const live = { site: 'https://www.opsontracker.com', base: '/' };

test('a build without the variables targets the live site at its root', () => {
  assert.deepEqual(siteConfig({}), live);
});

test('an unset repo variable arrives empty and still falls back', () => {
  assert.deepEqual(siteConfig({ SITE_URL: '', SITE_BASE: '' }), live);
});

test('the variables win when they are set', () => {
  assert.deepEqual(siteConfig({ SITE_URL: 'https://preview.example.test', SITE_BASE: '/Opson' }), {
    site: 'https://preview.example.test',
    base: '/Opson',
  });
});
