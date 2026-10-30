@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.prompts.impl.candidates

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.DayMark
import com.opson.libraries.diary.DayMarks
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.prompts.PromptAccept
import com.opson.libraries.prompts.PromptCandidate
import com.opson.libraries.prompts.PromptContent
import com.opson.libraries.prompts.PromptEvent
import com.opson.libraries.prompts.PromptFrequency
import com.opson.libraries.prompts.PromptPresentation
import com.opson.libraries.prompts.PromptPriority
import com.opson.libraries.review.ReviewMoment
import com.opson.libraries.review.ReviewPromptCoordinator
import com.opson.libraries.ui.components.icon.AppIcons
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.prompt_not_now
import opson.libraries.resources.generated.resources.prompt_review_accept
import opson.libraries.resources.generated.resources.prompt_review_body
import opson.libraries.resources.generated.resources.prompt_review_title
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration

/**
 * The store's rating sheet, right after a happy moment (Cards' approach: ask when someone is most
 * likely to be glad they have the app). Each install asks at one moment only, by the experiment
 * in [ReviewPromptCoordinator], so the moments can be compared:
 *
 * - [ReviewMoment.Celebration]: "I can do this" on a milestone. A moment that answers the prompt
 *   just shown, so it may follow the celebration in the same session.
 * - [ReviewMoment.DayFeltGood]: today wrapped up with "Good" for how it felt.
 * - [ReviewMoment.ShowingUp]: the first thing of the day from someone who showed up on
 *   [SHOWING_UP_DAYS] of the last [SHOWING_UP_WINDOW] days.
 * - [ReviewMoment.FoodApproved]: a food they sent from a pack was approved.
 *
 * Moments only, never as Today opens, and never after numbers have been hard: a rating ask is a
 * favour to us, and that is not the moment for one. The processor's rules keep it out of
 * onboarding and to one prompt a session; the coordinator keeps the first week quiet, the asks
 * 122 days apart, and none offline or within half an hour of something going wrong.
 */
@HiddenFromObjC
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class ReviewPrompt(
    private val coordinator: ReviewPromptCoordinator,
    private val diary: DiaryRepository,
    private val marks: DayMarks,
    private val calendar: LocalCalendar,
) : PromptCandidate {
    override val id = ID
    override val priority = PromptPriority.LOW
    override val presentation = PromptPresentation.System
    override val content = CONTENT

    // The coordinator paces asks per phone, as the stores do; the prompt's own counts are no limit.
    override val frequency = PromptFrequency(maxShows = Int.MAX_VALUE, maxDismissals = Int.MAX_VALUE, minGap = Duration.ZERO)
    override val onTodayOpen = false
    override val events: Set<PromptEvent> = MOMENTS.keys

    /** The moment that passed [isEligible], for [accept]. Null when the QA menu forced it. */
    private var pending: ReviewMoment? = null

    /** For the QA menu: whether this phone would ask at its moment now. */
    override suspend fun isEligible(): Boolean = coordinator.couldAsk()

    override suspend fun isEligible(moment: PromptEvent?): Boolean {
        val review = MOMENTS[moment] ?: return isEligible()
        if (!shouldAsk(review, coordinator, ::happened)) return false
        pending = review
        return true
    }

    override suspend fun accept(): PromptAccept {
        val moment = pending
        pending = null
        if (moment != null) {
            coordinator.ask(moment)
        } else {
            coordinator.ask(ReviewMoment.Celebration, fromQa = true)
        }
        return PromptAccept.Done
    }

    /** Whether the event really was the happy moment, where the event alone doesn't say. */
    private suspend fun happened(moment: ReviewMoment): Boolean = when (moment) {
        ReviewMoment.DayFeltGood -> feltGood(diary.observeDay(calendar.today()).first())
        ReviewMoment.ShowingUp -> showsUp(marks.observe().first(), calendar.today())
        ReviewMoment.Celebration, ReviewMoment.FoodApproved -> true
    }

    companion object {
        /** Never rename: it keys the prompt counts and the `prompt` attribute. */
        const val ID = "review"

        /** Showed up on this many days of the last [SHOWING_UP_WINDOW], today included. */
        const val SHOWING_UP_DAYS = 10
        const val SHOWING_UP_WINDOW = 14

        /** The prompt moments that can be a rating moment. A wrap-up is one only when the day felt good. */
        val MOMENTS: Map<PromptEvent, ReviewMoment> = mapOf(
            PromptEvent.CelebrationCheered to ReviewMoment.Celebration,
            PromptEvent.DayClosed to ReviewMoment.DayFeltGood,
            PromptEvent.ShowedUp to ReviewMoment.ShowingUp,
            PromptEvent.FoodApproved to ReviewMoment.FoodApproved,
        )

        /**
         * The order that keeps a held-back ask honest: only this install's moment is looked at, only
         * a moment that really happened is checked against the pacing, and so only those are
         * logged as `review.skipped`.
         */
        internal suspend fun shouldAsk(
            moment: ReviewMoment,
            coordinator: ReviewPromptCoordinator,
            happened: suspend (ReviewMoment) -> Boolean,
        ): Boolean = coordinator.asksAt(moment) && happened(moment) && coordinator.clearToAsk(moment)

        /** Wrapped up, and "Good" was how it felt. */
        internal fun feltGood(day: Day): Boolean = day.closedAt != null && day.feeling == DayFeeling.Good

        /** Showed up on [SHOWING_UP_DAYS] of the [SHOWING_UP_WINDOW] days ending [today]. */
        internal fun showsUp(marks: List<DayMark>, today: LocalDate): Boolean {
            val from = today.minus(SHOWING_UP_WINDOW - 1, DateTimeUnit.DAY)
            return marks.count { it.date in from..today && it.showedUp } >= SHOWING_UP_DAYS
        }

        val CONTENT = PromptContent(
            icon = AppIcons.Star,
            title = Res.string.prompt_review_title,
            body = Res.string.prompt_review_body,
            accept = Res.string.prompt_review_accept,
            dismiss = Res.string.prompt_not_now,
        )
    }
}
