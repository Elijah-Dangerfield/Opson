package com.opson.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtImportDirective

/**
 * Flags Compose Foundation's `BasicTextField` imported outside the design system.
 *
 * ## What this looks like when it bites
 *
 * The food detail's amount field used the raw field and fed it straight from state. A value
 * that round-trips through a view model replaces what the field holds, and the keystrokes typed
 * while it was on its way are dropped: a quick "150" logged "15" or "10", wrong calories with
 * nothing on screen to say so. It was the only raw field in `features/`.
 *
 * ## The fix
 *
 * Use `com.opson.libraries.ui.components.text.BasicTextField` (or a field built on it), which
 * buffers keystrokes through `rememberTypedText` and only settles to state once typing stops.
 * `libraries/ui` itself is excluded in `detekt.yml`, since that is where the wrapper lives.
 *
 * ## Limit
 *
 * This reads imports, so a call spelled with the fully qualified name gets past it. That is not
 * how anyone writes Compose.
 */
class RawBasicTextField(config: Config) : Rule(
    config,
    "Compose's BasicTextField fed from state drops keystrokes typed while a value round-trips; " +
        "use the libraries/ui field, which buffers them.",
) {
    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        if (importDirective.importedFqName?.asString() != RAW_FIELD) return
        report(
            Finding(
                Entity.from(importDirective),
                "`$RAW_FIELD` outside libraries/ui. Fed from state, it drops the keystrokes typed " +
                    "while a value round-trips (a quick \"150\" became \"15\"). Use " +
                    "`com.opson.libraries.ui.components.text.BasicTextField`, which buffers them.",
            ),
        )
    }

    private companion object {
        const val RAW_FIELD = "androidx.compose.foundation.text.BasicTextField"
    }
}
