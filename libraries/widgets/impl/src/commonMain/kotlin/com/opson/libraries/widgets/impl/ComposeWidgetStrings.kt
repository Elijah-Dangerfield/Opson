package com.opson.libraries.widgets.impl

import com.opson.libraries.widgets.WidgetStrings
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** The device's language, resolved once per snapshot stream, so a widget never waits on it. */
@ContributesBinding(AppScope::class)
@Inject
class ComposeWidgetStrings : WidgetStrings {
    override suspend fun get(resource: StringResource, vararg args: Any): String = getString(resource, *args)

    override suspend fun plural(resource: PluralStringResource, quantity: Int, vararg args: Any): String =
        getPluralString(resource, quantity, *args)
}
