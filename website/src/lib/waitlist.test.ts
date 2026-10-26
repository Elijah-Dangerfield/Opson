// Run with: node --test src/lib/waitlist.test.ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { joinWaitlist, readSource, waitlistEndpoint } from './waitlist.ts';

const API = 'https://api.example.test';

interface Call {
  url: string;
  init: RequestInit;
}

function fakeFetch(respond: () => Response | Promise<Response>) {
  const calls: Call[] = [];
  const fn = (async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    return respond();
  }) as typeof fetch;
  return { fn, calls };
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const signup = { email: '  a@b.co ', source: 'home', company: '' };

test('posts JSON to /v1/waitlist with a trimmed email', async () => {
  const { fn, calls } = fakeFetch(() => new Response(null, { status: 204 }));
  await joinWaitlist(API, signup, fn);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, 'https://api.example.test/v1/waitlist');
  assert.equal(calls[0].init.method, 'POST');
  assert.equal(new Headers(calls[0].init.headers).get('Content-Type'), 'application/json');
  assert.deepEqual(JSON.parse(String(calls[0].init.body)), { email: 'a@b.co', source: 'home', company: '' });
});

test('204 is done, which also covers an email already on the list', async () => {
  const { fn } = fakeFetch(() => new Response(null, { status: 204 }));
  assert.equal(await joinWaitlist(API, signup, fn), 'done');
  assert.equal(await joinWaitlist(API, signup, fn), 'done');
});

test('passes a filled honeypot through for the server to drop', async () => {
  const { fn, calls } = fakeFetch(() => new Response(null, { status: 204 }));
  const result = await joinWaitlist(API, { ...signup, company: 'Acme Inc' }, fn);
  assert.equal(JSON.parse(String(calls[0].init.body)).company, 'Acme Inc');
  // The server answers 204 to bots too, so the page looks the same to them.
  assert.equal(result, 'done');
});

test('400 is an invalid email', async () => {
  const { fn } = fakeFetch(() => json(400, { error: 'invalid_email' }));
  assert.equal(await joinWaitlist(API, signup, fn), 'invalid_email');
});

test('429 is rate limited', async () => {
  const { fn } = fakeFetch(() => json(429, { error: 'rate_limited' }));
  assert.equal(await joinWaitlist(API, signup, fn), 'rate_limited');
});

test('a server error is an error', async () => {
  for (const status of [500, 502, 503, 404]) {
    const { fn } = fakeFetch(() => new Response('nope', { status }));
    assert.equal(await joinWaitlist(API, signup, fn), 'error', `status ${status}`);
  }
});

test('a network failure is an error, not a throw', async () => {
  const fn = (async () => {
    throw new TypeError('Failed to fetch');
  }) as typeof fetch;
  assert.equal(await joinWaitlist(API, signup, fn), 'error');
});

test('a request that never answers times out as an error', async () => {
  const fn = ((_url: string, init: RequestInit) =>
    new Promise((_resolve, reject) => {
      init.signal?.addEventListener('abort', () => reject(init.signal?.reason));
    })) as typeof fetch;
  assert.equal(await joinWaitlist(API, signup, fn, 20), 'error');
});

test('endpoint tolerates a trailing slash on the API base', () => {
  assert.equal(waitlistEndpoint('https://x.dev/'), 'https://x.dev/v1/waitlist');
  assert.equal(waitlistEndpoint('https://x.dev'), 'https://x.dev/v1/waitlist');
});

test('source is the ?c= campaign when it fits the server rule', () => {
  assert.equal(readSource('?c=reddit-oct', 'home'), 'reddit-oct');
  assert.equal(readSource('?c=Reddit-Oct', 'home'), 'reddit-oct');
});

test('source falls back to the page name for a missing or bad campaign', () => {
  assert.equal(readSource('', 'home'), 'home');
  assert.equal(readSource('?c=', 'home'), 'home');
  assert.equal(readSource('?c=' + 'a'.repeat(33), 'home'), 'home');
  assert.equal(readSource('?c=<script>', 'home'), 'home');
  assert.equal(readSource('?c=ig_bio', 'home'), 'home');
});

test('source is null when neither fits', () => {
  assert.equal(readSource('?c=%20', 'Not A Page'), null);
});
