package com.opson.libraries.ui

/**
 * Keeps a `@Preview` out of the preview screenshots that `:apps:screenshots` renders.
 *
 * Every preview is a screenshot by default, so this is the exception, not the rule. Reach for it
 * when a preview cannot render off-device (it needs a platform service, or it animates forever
 * without honouring `LocalInspectionMode`) or when its image would mean nothing to a designer
 * (a debug-only harness). Say why next to the annotation, so the next person can tell whether the
 * reason still holds. A preview that merely fails to render is already recorded as a failure in
 * the manifest, so this is for previews you have decided should never be exported.
 *
 * Binary retention because the scanner reads it from compiled classes. Nothing reads it at runtime.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class NoScreenshot
