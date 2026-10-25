package com.opson.libraries.opson

import android.app.Activity

/**
 * Host-app contract for handing the foreground [Activity] to bindings that
 * need it. Implement once in `:apps:compose` (`androidMain`) — typically by
 * watching the Application's `ActivityLifecycleCallbacks`.
 *
 * Lives in `:libraries:opson` (api) rather than any single impl so
 * multiple Android-only impls can consume it without `*:impl → *:impl`
 * coupling.
 */
interface ActivityProvider {
    fun currentActivity(): Activity?
}
