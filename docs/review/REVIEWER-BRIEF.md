# Brief for a slice reviewer

You review one or more slices of the codebase and fix what you find, in your
own git worktree. Read these first:

- [`PRACTICES.md`](PRACTICES.md): the rubric. Every finding cites a rule from it.
  Sections 9 (environments and the release path) and 10 (journeys) are new
  in round two and are where round one missed the worst bug.
- [`PROCESS.md`](PROCESS.md): the steps.
- [`LEDGER.md`](LEDGER.md): your slices' files. Review every one.
- `AGENTS.md` at the repo root, and `docs/plans/0.2/README.md` (including
  "Learned in wave 1").
- The unslop-code skill: `~/.claude/skills/unslop-code/SKILL.md`, and its scanner
  `python3 ~/.claude/skills/unslop-code/scripts/unslop_code_scan.py <path>`.

## What a finding is

A real problem with evidence: a file and line, and the input or sequence that
breaks, or a concrete cost (a recomposition every frame, an N+1, a lie in a
comment). Don't invent problems. Equally, don't skip a real maybe when the fix
is small. Stale or lying comments, dead code, and names that need a comment to
explain them are findings.

Look hardest at: cancellation swallowed by `catch`/`runCatching`; work at app
launch that the user didn't turn on; numbers that bypass the visibility policy;
PII in logs; unstable Compose parameters and work in composition; missing
loading and error states (a blank screen while something loads is a bug);
missing tests for behaviour that matters, especially journeys that cross the
wire (those belong in `:apps:integration`, real client to real in-process
server, no mocks).

## Rules

- **Stay in your slices.** Don't edit files outside them. If a fix needs
  another slice's file, record it as **deferred (cross-slice)** with the exact
  change, and the orchestrator will make it. Exception: `libraries/resources`
  strings you add for your own screens.
- **Every behaviour change gets a test** that fails without the fix.
- **Don't break 0.1 clients.** The server must keep accepting what 0.1.0 sends;
  migrations are new files, additive, never edits to applied ones (highest is
  V23; check `apps/server/src/main/resources/db/migration` for the latest). New user settings are new top-level keys (the server merges settings
  shallowly with `||`).
- **Round one's findings** are in `round-1/findings/<slice>.md`. Don't
  re-report what was fixed; do check that its fixes still hold.
- **Release flags stay off by default.** Anything new goes behind one. Don't
  touch `iosApp.entitlements` or the Release build configuration.
- **Never** push, deploy, touch prod, or print secrets. Don't connect to any
  database except through tests (Testcontainers).
- Commits: small, conventional (`fix(scope):`, `refactor(scope):`, `test(scope):`,
  `perf(scope):`, `docs(review):`), each ending with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- If something is too big or unclear, leave a `TODO(review): <why>` and record
  it as deferred rather than stalling.

## Output

1. `docs/review/findings/<slice>.md` per slice, in the format of
   `round-1/findings/integration.md`: numbered findings, each with **Where**, **Rule**,
   **Failure**, and its fate (**fixed** with the commit subject, **not a
   problem** with why, or **deferred** with why). End with a `## Product notes`
   section: ideas about the product rather than the code (a feature to add, cut
   or change; behaviour that will surprise people), each with how sure you are.
2. Tick every file you reviewed in `LEDGER.md` (your sections only).
3. Before you finish, run and get green: `./gradlew detekt`, the tests of
   every module you touched (`:<module>:allTests` or `testDebugUnitTest`;
   `:apps:server:test` for the server; `:apps:integration:testDebugUnitTest`
   and `:apps:leaktest:testDebugUnitTest` if you touched anything they cover),
   and `./gradlew :apps:compose:compileKotlinIosSimulatorArm64` if you touched
   shared UI or iOS code.
4. Your final message: the commits, the count of findings by fate, anything
   deferred cross-slice (exact file and change), and any check you could not
   get green.
