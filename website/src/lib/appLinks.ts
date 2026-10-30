// The files that let a shared recipe's link open the app instead of this site: Apple's
// apple-app-site-association and Android's assetlinks.json, both under /.well-known/. Only
// /r/* (a shared recipe) is claimed; every other page stays the website.
//
// Apple's file names the team and bundle id, which are in the Xcode project. Android's names the
// app's signing certificate, which is Google's (Play App Signing) and not in this repo: the
// deploy passes it in as ANDROID_CERT_SHA256, and without it the file lists no certificate, so
// Android keeps opening the link in the browser. See README.md, "Universal links and App Links".

export const APPLE_APP_ID = 'MSMDV43SUS.com.opson.Opson';
export const ANDROID_PACKAGE = 'com.opson';
export const RECIPE_PATHS = '/r/*';

export function appleAppSiteAssociation() {
  return {
    applinks: {
      details: [
        {
          appIDs: [APPLE_APP_ID],
          components: [{ '/': RECIPE_PATHS, comment: 'A shared recipe' }],
          // iOS 12 and earlier read these two instead.
          appID: APPLE_APP_ID,
          paths: [RECIPE_PATHS],
        },
      ],
    },
  };
}

/** SHA-256 fingerprints, colon-separated hex, from a comma-separated variable. Anything else is dropped. */
export function fingerprints(value: string | undefined): string[] {
  const pattern = /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/;
  return (value ?? '')
    .split(',')
    .map((it) => it.trim().toUpperCase())
    .filter((it) => pattern.test(it));
}

export function assetLinks(certFingerprints: string[]) {
  if (certFingerprints.length === 0) return [];
  return [
    {
      relation: ['delegate_permission/common.handle_all_urls'],
      target: { namespace: 'android_app', package_name: ANDROID_PACKAGE, sha256_cert_fingerprints: certFingerprints },
    },
  ];
}
