// ComposablePreviewScanner deprecates its `common` scanner in favour of androidx previews, which
// this repo cannot use (see the version catalog note), so the warning is known and accepted.
@file:Suppress("DEPRECATION")

package com.opson.apps.screenshots

import sergio.sastre.composable.preview.scanner.common.CommonPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

/**
 * Screen ids: the join key between a rendered preview and a design screen.
 *
 * Claude Design names its exports by this rule and scripts/design_sync.main.kts pairs files by it,
 * so changing it orphans every design that was named under the old one. The rule is written out in
 * docs/practices/design-sync.md; keep the two in step.
 *
 *   `<file>__<preview>[--<@Preview name>][--<parameter index>]`
 *
 * - `<file>` is the Kotlin file (or the enclosing class, for a preview declared inside one),
 *   kebab-cased: `FeedbackScreen.kt` gives `feedback-screen`.
 * - `<preview>` is the preview function's name, kebab-cased, with every `preview` word dropped
 *   because it is on every one of them: `FeedbackScreenPreviewDisabled` gives
 *   `feedback-screen-disabled`. A function named just `Preview` becomes `default`.
 * - `--<@Preview name>` only when the annotation sets `name`, which is how one function carries
 *   several previews. `--<index>` only for a `@PreviewParameter` preview, one per value.
 * - A clash after all that (two files with the same name in different packages) gets `-2`, `-3`
 *   in scan order, which is sorted, so it is stable.
 */
internal class ScreenIds {
    private val seen = mutableMapOf<String, Int>()

    fun next(preview: ComposablePreview<CommonPreviewInfo>): String {
        val base = screenId(
            declaringClass = preview.declaringClass,
            methodName = preview.methodName,
            previewName = preview.previewInfo.name,
            parameterIndex = preview.previewIndex,
        )
        val count = (seen[base] ?: 0) + 1
        seen[base] = count
        return if (count == 1) base else "$base-$count"
    }
}

internal fun screenId(
    declaringClass: String,
    methodName: String,
    previewName: String?,
    parameterIndex: Int?,
): String {
    val file = declaringClass
        .substringAfterLast('.')
        .substringAfterLast('$')
        .removeSuffix("Kt")
    // Internal functions carry a `$module` suffix in bytecode; it is not part of the name.
    val function = kebab(methodName.substringBefore('$'))
        .split('-')
        .filter { it.isNotEmpty() && it != "preview" }
        .joinToString("-")
        .ifEmpty { "default" }

    return buildString {
        append(kebab(file))
        append("__")
        append(function)
        if (!previewName.isNullOrBlank()) append("--").append(kebab(previewName))
        if (parameterIndex != null) append("--").append(parameterIndex)
    }
}

/** `FeedbackScreen` to `feedback-screen`, `HTMLText2Row` to `html-text2-row`. */
internal fun kebab(value: String): String = value
    .replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
    .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1-$2")
    .replace(Regex("[^A-Za-z0-9]+"), "-")
    .trim('-')
    .lowercase()
