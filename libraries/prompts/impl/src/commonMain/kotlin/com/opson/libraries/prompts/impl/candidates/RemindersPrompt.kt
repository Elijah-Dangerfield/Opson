@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.prompts.impl.candidates

import com.opson.features.settings.RemindersRoute
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.prompts.PromptAccept
import com.opson.libraries.prompts.PromptCandidate
import com.opson.libraries.prompts.PromptContent
import com.opson.libraries.prompts.PromptPresentation
import com.opson.libraries.prompts.PromptPriority
import com.opson.libraries.reminders.NotificationPermission
import com.opson.libraries.reminders.NotificationPermissions
import com.opson.libraries.reminders.RemindersEnabled
import com.opson.libraries.ui.components.icon.AppIcons
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.prompt_not_now
import opson.libraries.resources.generated.resources.prompt_reminders_accept
import opson.libraries.resources.generated.resources.prompt_reminders_body
import opson.libraries.resources.generated.resources.prompt_reminders_title
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * "A nudge at mealtimes?", a dialog that opens Reminders and celebrations, where turning a meal on
 * asks for notification permission from that tap. Only for someone who has never chosen a reminder
 * level ([com.opson.libraries.diary.Settings.remindersChosen]) and has not already said no to
 * notifications, since the page could only send them to Settings.
 *
 * Kept from someone who said numbers have been hard: a reminder to log reads as pressure there,
 * and they can still find the page on More.
 */
@HiddenFromObjC
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class RemindersPrompt(
    private val flag: RemindersEnabled,
    private val settings: SettingsRepository,
    private val permissions: NotificationPermissions,
) : PromptCandidate {
    override val id = ID
    override val priority = PromptPriority.NORMAL
    override val presentation = PromptPresentation.Dialog
    override val content = CONTENT

    override suspend fun isEligible(): Boolean {
        if (!flag()) return false
        if (settings.settings.value.remindersChosen) return false
        return permissions.status() != NotificationPermission.Denied
    }

    override suspend fun accept(): PromptAccept = PromptAccept.Open(RemindersRoute())

    companion object {
        const val ID = "meal_reminders"

        val CONTENT = PromptContent(
            icon = AppIcons.Notifications,
            title = Res.string.prompt_reminders_title,
            body = Res.string.prompt_reminders_body,
            accept = Res.string.prompt_reminders_accept,
            dismiss = Res.string.prompt_not_now,
        )
    }
}
