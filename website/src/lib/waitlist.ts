// Sends a waitlist signup to the server and turns the answer into something
// the form can show. The contract is in docs/plans/website.md:
//
//   POST {api}/v1/waitlist  {"email", "source", "company"}
//   204 added, or already on the list (the server never says which)
//   400 {"error":"invalid_email"}
//   429 {"error":"rate_limited"}
//
// `company` is the honeypot. People never see the field, so it stays empty;
// bots that fill every input send a value and the server quietly drops them.
// It is passed through untouched so the server makes that call, not the page.

export type WaitlistResult = 'done' | 'invalid_email' | 'rate_limited' | 'error';

export interface WaitlistSignup {
  email: string;
  source: string | null;
  company: string;
}

/** The server's rule for `source`. Anything else would be stored as null. */
const SOURCE_PATTERN = /^[a-z0-9-]{1,32}$/;

/** A campaign tag from `?c=` if it fits the server's rule, otherwise the page name. */
export function readSource(search: string, page: string): string | null {
  const campaign = new URLSearchParams(search).get('c')?.trim().toLowerCase() ?? '';
  if (SOURCE_PATTERN.test(campaign)) return campaign;
  return SOURCE_PATTERN.test(page) ? page : null;
}

export function waitlistEndpoint(apiUrl: string): string {
  return `${apiUrl.replace(/\/+$/, '')}/v1/waitlist`;
}

export async function joinWaitlist(
  apiUrl: string,
  signup: WaitlistSignup,
  fetchFn: typeof fetch = fetch,
  timeoutMs = 15_000,
): Promise<WaitlistResult> {
  let response: Response;
  try {
    response = await fetchFn(waitlistEndpoint(apiUrl), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: signup.email.trim(),
        source: signup.source,
        company: signup.company,
      }),
      signal: AbortSignal.timeout(timeoutMs),
    });
  } catch {
    // Offline, DNS, CORS, or the timeout.
    return 'error';
  }
  if (response.ok) return 'done';
  if (response.status === 400) return 'invalid_email';
  if (response.status === 429) return 'rate_limited';
  return 'error';
}

/** What the form says for each result, and for the moment it is sending. */
export const MESSAGES: Record<WaitlistResult | 'sending', string> = {
  sending: 'Sending…',
  done: "You're on the list.",
  invalid_email: "That email doesn't look right. Check it and try again.",
  rate_limited: "That's a lot of tries in a short time. Wait a few minutes, then try again.",
  error: "That didn't go through, so you're not on the list yet. Give it a minute and try again.",
};
