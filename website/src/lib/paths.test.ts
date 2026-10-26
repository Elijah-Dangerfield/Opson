// Run with: node --test src/lib/paths.test.ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { joinBase, stripBase } from './paths.ts';

test('joins a project-site base with or without its trailing slash', () => {
  assert.equal(joinBase('/Opson', '/privacy/'), '/Opson/privacy/');
  assert.equal(joinBase('/Opson/', '/privacy/'), '/Opson/privacy/');
  assert.equal(joinBase('/Opson', ''), '/Opson/');
});

test('joins a root base for a custom domain', () => {
  assert.equal(joinBase('/', '/privacy/'), '/privacy/');
  assert.equal(joinBase('/', 'favicon.svg'), '/favicon.svg');
  assert.equal(joinBase('/', '/'), '/');
});

test('strips the base to find the current page', () => {
  assert.equal(stripBase('/Opson', '/Opson/privacy/'), '/privacy/');
  assert.equal(stripBase('/Opson/', '/Opson/'), '/');
  assert.equal(stripBase('/Opson', '/Opson'), '/');
  assert.equal(stripBase('/', '/terms/'), '/terms/');
});
