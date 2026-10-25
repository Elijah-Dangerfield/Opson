# The outbox pattern (offline writes that must not be lost)

The template ships one worked example of this pattern, the profile edit
queue (`PendingProfileEditStore` + `ProfileEditFlusher` in
`:libraries:identity:impl`), and this doc so you can build the next one
without reverse-engineering it.

## When you need it

A user takes an action while offline (or session-less) that MUST eventually
reach the server: an edit, a purchase acknowledgment, a consumable grant.
Dropping it silently is a bug; blocking the UI on connectivity is worse. The
answer is an outbox: persist the intent locally, apply it optimistically,
flush when a session + connectivity exist.

## The shape

1. **A persisted event store.** One table (or `Cache`) of pending events.
   Fields: a client-generated **idempotency key** (UUID minted when the event
   is created, the server dedupes on it), the payload, and created-at. For a
   Room table, implement `ClearableDao` so a user switch wipes it (an outbox
   is account-scoped by definition).
2. **Optimistic local apply.** The UI reflects the change immediately; the
   caller gets a `Queued` outcome and treats it as success.
3. **A flusher driven by the sync triggers.** Don't invent your own timing:
   implement `UserScopedSyncer` (or hang off the same triggers,
   `activeAccount` level, `warmForeground`, `cameOnline`) so the flush fires
   when a session becomes active, on warm resume, and on reconnect. The
   flush must be **idempotent and re-entrant**: take a mutex, read all
   pending events, attempt each in order.
4. **Per-event reconciliation.** For each event: send with the idempotency
   key → on success delete it; on a *rejection* (4xx that means "the server
   will never accept this") delete it, revert the optimistic state, and emit
   a user-visible rejection signal (see `ProfileRepository
   .observeEditRejections`); on a *transient* failure (offline, 5xx) stop,
   leave the rest queued for the next trigger.
5. **Coalescing (optional).** If newer events supersede older ones (a second
   rename), collapse them at enqueue time so the flush sends only the latest.

## The reference (~50 lines, read it)

`ProfileEditFlusher` is the distilled loop: it observes the sync triggers,
takes the repo's mutex, reads the single pending edit, PATCHes `/v1/me`,
and maps the outcome, success → clear, `DisplayNameTaken`/`Invalid…` →
clear + revert + rejection event, network error → keep queued. A
multi-event outbox is the same loop over a list.

## What the production app did that we deliberately did NOT port

The origin app's high-value outbox (an economy ledger) added per-event
server-side dedup tables and reconciliation-on-read. That machinery is
worth building only when events carry money-like weight. Start with the
shape above; graduate when an event's loss or double-apply would be
user-visible harm.
