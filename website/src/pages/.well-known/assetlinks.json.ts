// Android's App Links file, built with the site. ANDROID_CERT_SHA256 is a repo variable the
// deploy passes in (the Play app signing certificate's SHA-256, from the Play Console); without
// it the file is an empty list and Android keeps opening recipe links in the browser.
import type { APIRoute } from 'astro';
import { assetLinks, fingerprints } from '../../lib/appLinks';

export const GET: APIRoute = () =>
  new Response(JSON.stringify(assetLinks(fingerprints(process.env.ANDROID_CERT_SHA256)), null, 2), {
    headers: { 'Content-Type': 'application/json' },
  });
