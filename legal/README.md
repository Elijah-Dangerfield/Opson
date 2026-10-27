# Legal documents

These four files are the **source of truth** for what Opson publishes at:

- `privacy.md`: <https://nightjarlabs.llc/opson/privacy>
- `terms.md`: <https://nightjarlabs.llc/opson/terms>
- `health-data.md`: <https://nightjarlabs.llc/opson/health-data>, the consumer
  health data policy that Washington's My Health My Data Act and Nevada's
  SB 370 want as its own page, linked from the homepage.
- `credits.md`: <https://nightjarlabs.llc/opson/credits>, the food data,
  typeface and open-source licenses.

The app's Support page links all four, and the Opson website renders them too.
`docs/legal-review-2026-10.md` records what was checked and what still needs
a lawyer.

They live here, next to the code, because a privacy policy is a claim about what
a specific binary does. Keeping it in a marketing repo guarantees the day comes
when the app collects something the policy does not mention, and nobody notices
until a store reviewer does. That is not hypothetical: it is why this folder
exists rather than a `pages/` folder of hand-written HTML.

## Before you ship

Every claim in these files is a claim about the code. When a change adds a data
flow, a provider or a feature flag that sends something new, the matching
paragraph changes in the same commit. `credits.md` changes when a food source
or a shipped library with a new license is added.

## How they get published

`.github/workflows/legal-sync.yml` watches `legal/**` on `main`. When a file
changes it opens a pull request against the website repository, copying every
file in its `LEGAL_FILES` list into `src/content/legal/opson/`. A new document
needs adding to that list, or it never leaves this repo. That repo merges the PR as
soon as the site builds, and publishes.

Run `./scripts/setup_legal_sync.sh` once to create the two secrets it needs.

**So editing these files is publishing them.** There is no click between your
commit and the live site, give or take a couple of minutes. Write them as if
they are already live, because shortly they are.

The one gate is the build. The site validates frontmatter against a zod schema,
so a malformed file leaves the PR open and the live site serving the last good
version. It does not check whether the words are true. Nothing does. That part
is on you, which is the whole reason these files sit next to the code.

## Rules

- **Frontmatter is a contract.** `app`, `title`, `updated` and `contact` are
  validated by a zod schema in the website repo. A missing or misspelled key
  fails *that* repo's build, not this one, so the failure shows up somewhere
  confusing. Bump `updated` whenever the text changes; both stores expect a
  policy to carry a date.
- **Do not edit the copies in the website repo.** The next sync silently reverts
  them.
- **The URLs get filed with Apple and Google.** The app also compiles them in
  (`SupportViewModel.kt`, and the Android Health Connect rationale). Changing the slug after submission
  means re-filing on both stores and waiting out two reviews.
