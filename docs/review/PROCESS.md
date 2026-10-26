# How the whole-codebase review runs

1. **Rubric:** [`PRACTICES.md`](PRACTICES.md) plus
   [`docs/practices/review-checklist.md`](../practices/review-checklist.md).
2. **Slices.** The codebase is split into slices that don't share files, so
   reviewers can fix in parallel in their own worktrees. Each slice owns a set
   of modules (see [`LEDGER.md`](LEDGER.md)).
3. **Per slice, a reviewer** (briefed by [`REVIEWER-BRIEF.md`](REVIEWER-BRIEF.md)):
   - reads every Kotlin, Swift, SQL, script and config file in the slice;
   - runs the unslop-code scanner over it
     (`python3 ~/.claude/skills/unslop-code/scripts/unslop_code_scan.py <path>`);
   - records every finding in `docs/review/findings/<slice>.md` with file:line,
     the rule from PRACTICES.md, the failure it causes, and its fate: **fixed**
     (commit), **not a problem** (why), or **deferred** (why, and a
     `TODO(review)` in code);
   - fixes what it can in small commits, each with a test when behaviour
     changes;
   - marks each file reviewed in `LEDGER.md` (slice section only);
   - writes product ideas (not code issues) under `## Product notes` in its
     findings file; the orchestrator gathers them into `product-notes.md`.
4. **Orchestrator** merges each slice, runs the full check (detekt, all module
   tests, leak test, server tests, integration tests, iOS build, launch
   smoke), and walks the touched screens in the simulator.
5. **Done** when every file in the ledger is marked, every finding has a fate,
   and the full check is green.
