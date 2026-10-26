/**
 * The `updated` date from a legal file's frontmatter, as "October 26, 2026".
 * YAML may hand it over as a Date, an ISO timestamp or the bare "2026-10-26".
 */
export function formatUpdated(updated: Date | string): string {
  const bareDay = typeof updated === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(updated);
  const date = new Date(bareDay ? `${updated}T00:00:00Z` : updated);
  if (Number.isNaN(date.getTime())) return String(updated);
  return date.toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric', timeZone: 'UTC' });
}
