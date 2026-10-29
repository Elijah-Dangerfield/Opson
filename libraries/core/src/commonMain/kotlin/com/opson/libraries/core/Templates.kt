package com.opson.libraries.core

/**
 * Fills a string resource's positional placeholders (`%1$s`, `%2$d`), as `stringResource(res, a, b)`
 * does, for a template resolved once and filled many times, often off the main thread: a number
 * formatter, a date formatter. A placeholder with no argument is left as written.
 */
fun String.fillTemplate(vararg args: Any): String = PLACEHOLDER.replace(this) { match ->
    args.getOrNull(match.groupValues[1].toInt() - 1)?.toString() ?: match.value
}

private val PLACEHOLDER = Regex("""%(\d+)\$[ds]""")
