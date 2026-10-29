@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.prompts.impl.candidates

import com.opson.features.today.CheckInRoute
import com.opson.libraries.diary.CheckInEnabled
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.prompts.PromptAccept
import com.opson.libraries.prompts.PromptCandidate
import com.opson.libraries.prompts.PromptContent
import com.opson.libraries.prompts.PromptFrequency
import com.opson.libraries.prompts.PromptPresentation
import com.opson.libraries.prompts.PromptPriority
import com.opson.libraries.ui.components.icon.AppIcons
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.prompt_checkin_accept
import opson.libraries.resources.generated.resources.prompt_checkin_body
import opson.libraries.resources.generated.resources.prompt_checkin_title
import opson.libraries.resources.generated.resources.prompt_not_now
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration.Companion.days

/**
 * "Try a check-in", a card in Today's list that opens today's check-in. For someone who has
 * logged on a few days of the last two weeks and has not checked in on any of them. A card rather
 * than a dialog, because it can wait: it may come back up to three times, days apart, until it is
 * answered either way.
 *
 * Offered after numbers have been hard too. Hunger, energy and mood carry no number, and noticing
 * them is the point of the check-in.
 */
@HiddenFromObjC
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class CheckInPrompt(
    private val flag: CheckInEnabled,
    private val diary: DiaryRepository,
    private val calendar: LocalCalendar,
) : PromptCandidate {
    override val id = ID
    override val priority = PromptPriority.LOW
    override val presentation = PromptPresentation.Card
    override val content = CONTENT
    override val frequency = PromptFrequency(maxShows = 3, maxDismissals = 1, minGap = 3.days)
    override val offerWhenNumbersHard = true

    override suspend fun isEligible(): Boolean {
        if (!flag()) return false
        val today = calendar.today()
        val days = diary.observeDays(today.minus(WINDOW_DAYS - 1, DateTimeUnit.DAY), today).first()
        return worthOffering(days)
    }

    override suspend fun accept(): PromptAccept = PromptAccept.Open(CheckInRoute(calendar.today().toString()))

    companion object {
        const val ID = "try_check_in"

        /** Two weeks back, today included. */
        const val WINDOW_DAYS = 14

        /** "A few days of logging": enough that the check-in has a routine to sit in. */
        const val MIN_LOGGED_DAYS = 3

        val CONTENT = PromptContent(
            icon = AppIcons.Mood,
            title = Res.string.prompt_checkin_title,
            body = Res.string.prompt_checkin_body,
            accept = Res.string.prompt_checkin_accept,
            dismiss = Res.string.prompt_not_now,
        )

        fun worthOffering(days: List<Day>): Boolean =
            days.none { it.checkIn != null } && days.count { it.hasLogged && !it.untracked } >= MIN_LOGGED_DAYS
    }
}
