// Where the site is served from: https://www.opsontracker.com on Cloudflare
// Pages, at the domain root (docs/plans/0.2/release-checklist.md, "Website
// go-live"). The deploy workflow passes SITE_URL and SITE_BASE from repo
// variables, which win when set. An unset variable arrives as an empty string,
// and a missing or empty one falls back to the live site, because a build for
// the wrong base ships pages whose every link and stylesheet is a 404.

export const LIVE_SITE = 'https://www.opsontracker.com';

export function siteConfig(env: Record<string, string | undefined>): { site: string; base: string } {
  return {
    site: env.SITE_URL || LIVE_SITE,
    base: env.SITE_BASE || '/',
  };
}
