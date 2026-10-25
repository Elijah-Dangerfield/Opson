# :libraries:config

Remote config / feature flags for the client. The API module defines the
`ConfiguredValue` convention and the repository interfaces; `:libraries:config:impl`
owns fetching, caching, and QA overrides.

## The one convention: injectable `ConfiguredValue` subclasses

Every flag is its own injectable class, contributed into the app graph's
`Set<QaConfigValue>` multibinding so it appears in the QA menu automatically.
The typed bases in `TypedConfiguredValues.kt` (`FlagConfigValue`,
`IntConfigValue`, `LongConfigValue`, `DoubleConfigValue`, `StringConfigValue`)
fill in `resolveValue` for scalars, so a concrete flag only declares
`name` / `path` / `default`:

```kotlin
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class GoogleSignInEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Google sign-in enabled"
    override val path = "identity.googleSignInEnabled"
    override val default = false
}

@Inject
class SignInViewModel(googleSignInEnabled: GoogleSignInEnabled) {
    val showGoogle = googleSignInEnabled()   // invoke() resolves the current value
}
```

Metadata on `ConfiguredValue` drives the QA menu: `group` (defaults to the
first path segment), `allowedValues` (renders a chip selector for enum-like
strings), `showInQADashboard` (default true), `description`, and a
debug-build-only `debugOverride`.

For structured payloads (a level ladder, a reward table) subclass
`JsonConfigValue<T>` with a `@Serializable` model and a serializer. It resolves
one path to a JSON subtree and falls back to the bundled `default` on any
decode failure, so a bad remote value can never brick the client. JSON values
hide from the QA dashboard by default.

## Resolution order

`AppConfigMap` is the merged snapshot a flag reads from. Highest wins:

1. QA override (`ConfigOverrideRepository`, persisted locally, editable from
   the QA menu)
2. `debugOverride` (debug builds only)
3. Server value from `GET /v1/app-config` (a sparse tree keyed by dotted
   `ConfiguredValue.path`)
4. The in-code `default`

An empty server response is legitimate: the client always works on defaults
alone.

## Offline-first, throttled-foreground refresh

`OfflineFirstAppConfigRepository` (impl) persists the last fetched tree via
`CacheFactory`, so the first frame never blocks on the network. It refetches on
every app foreground (cold boot included), gated by `ConfigRefreshThrottleMs`
(itself a config value: 5 min default in code, set to 0 in the dev database so
flag flips show up on the next foreground). Fetches carry a 5s timeout. If a
fetch fails and there is no cached snapshot, the bundled
`fallback_app_config.json` is persisted so subscribers still get a usable map;
with a cached snapshot, failures are logged and the cache stays.

The fetch is unauthenticated (kill-switch flags must load before sign-in), but
attaches a bearer token best-effort when one is on hand so the server can do
per-user targeting and rollout bucketing.

## Observing changes

- `AppConfigMap` for point-in-time reads (what the typed bases use).
- `AppConfigFlow` / `AppConfigRepository.configStream()` for reactive
  consumers that need to react to a mid-session override edit.

## Server side

The tree comes from the server's `app_config_values` + `app_config_rules`
tables (see `apps/server`), managed through the admin console in `apps/admin`.
When you add a scalar flag, also add it to
`apps/admin/config-manifest-registry.json` so the admin tool knows the in-code
default it is overriding. `ConfigManifestRegistryDriftTest` in `:apps:integration`
instantiates the real value classes and fails when the two disagree, and prints
the JSON line to paste.
