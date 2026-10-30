import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { APPLE_APP_ID, appleAppSiteAssociation, assetLinks, fingerprints } from './appLinks.ts';

const FINGERPRINT = Array.from({ length: 32 }, (_, i) => (i * 7 % 256).toString(16).padStart(2, '0').toUpperCase()).join(':');

test('Apple claims shared recipes only, for the one app', () => {
  const details = appleAppSiteAssociation().applinks.details;
  assert.equal(details.length, 1);
  assert.deepEqual(details[0].appIDs, [APPLE_APP_ID]);
  assert.deepEqual(details[0].components, [{ '/': '/r/*', comment: 'A shared recipe' }]);
});

test('fingerprints keep only well-formed SHA-256s', () => {
  assert.deepEqual(fingerprints(undefined), []);
  assert.deepEqual(fingerprints(` ${FINGERPRINT.toLowerCase()} , nonsense`), [FINGERPRINT]);
});

test('with no certificate Android is given nothing to verify', () => {
  assert.deepEqual(assetLinks([]), []);
  const links = assetLinks([FINGERPRINT]);
  assert.equal(links[0].target.package_name, 'com.opson');
  assert.deepEqual(links[0].target.sha256_cert_fingerprints, [FINGERPRINT]);
});

test('the file the site serves is the one this module describes', () => {
  const served = readFileSync(new URL('../../public/.well-known/apple-app-site-association', import.meta.url), 'utf8');
  assert.deepEqual(JSON.parse(served), appleAppSiteAssociation());
});
