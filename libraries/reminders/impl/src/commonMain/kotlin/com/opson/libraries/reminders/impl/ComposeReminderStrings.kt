package com.opson.libraries.reminders.impl

import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** The device's language, resolved when the plan is made, so a notification never waits on it. */
@ContributesBinding(AppScope::class)
@Inject
class ComposeReminderStrings : ReminderStrings {
    override suspend fun get(resource: StringResource): String = getString(resource)
}
