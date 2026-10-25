# Legal documents

`privacy.md` and `terms.md` are the **source of truth** for what Opson
publishes at:

- <https://nightjarlabs.llc/opson/privacy>
- <https://nightjarlabs.llc/opson/terms>

They live here, next to the code, because a privacy policy is a claim about what
a specific binary does. Keeping it in a marketing repo guarantees the day comes
when the app collects something the policy does not mention, and nobody notices
until a store reviewer does. That is not hypothetical: it is why this folder
exists rather than a `pages/` folder of hand-written HTML.

## Before you ship

Both files are **starting points, not documents**. The template cannot know what
your app collects. Read each one against the code, and expect the "no ad or
analytics SDKs" and "no account" claims in `privacy.md` to be the first two that
stop being true.

## How they get published

`.github/workflows/legal-sync.yml` watches `legal/**` on `main`. When either
file changes it opens a pull request against the website repository, copying
both files into `src/content/legal/opson/`. That repo merges the PR as
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
- **The URLs get filed with Apple and Google.** They are also the compiled
  defaults in `LaunchGateConfigValues.kt`. Changing the slug after submission
  means re-filing on both stores and waiting out two reviews.
