@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.prompts.impl.candidates

import com.opson.libraries.core.AutoInit
import com.opson.libraries.diary.DayMark
import com.opson.libraries.diary.DayMarks
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.foods.SubmittedFoods
import com.opson.libraries.prompts.PromptEvent
import com.opson.libraries.prompts.PromptEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Moments that no single screen owns, noticed as they happen on this phone and announced as
 * [PromptEvent]s: today becoming a day someone showed up ([PromptEvent.ShowedUp]), and a food they
 * sent being approved ([PromptEvent.FoodApproved]). What was already true at launch is not a
 * moment; only a change seen while the app runs is.
 */
@HiddenFromObjC
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class MomentWatcher(
    marks: DayMarks,
    submitted: SubmittedFoods,
    calendar: LocalCalendar,
    events: PromptEvents,
    appScope: AppCoroutineScope,
) : AutoInit {
    init {
        showedUp(marks.observe().map { list -> today(list, calendar.today()) })
            .onEach { events.emit(PromptEvent.ShowedUp) }
            .launchIn(appScope)
        approvals(submitted.reviews)
            .onEach { events.emit(PromptEvent.FoodApproved) }
            .launchIn(appScope)
    }

    internal companion object {
        /** Today as the streak sees it. */
        data class Today(val date: LocalDate, val showedUp: Boolean)

        fun today(marks: List<DayMark>, today: LocalDate) =
            Today(today, marks.firstOrNull { it.date == today }?.showedUp == true)

        /**
         * One emission each time today turns into a day they showed up: false to true on the same
         * date, or a new date that starts out true. The first reading is where things stand, not a
         * moment.
         */
        fun showedUp(days: Flow<Today>): Flow<Unit> =
            days.runningFold(Pair<Today?, Today?>(null, null)) { (_, previous), next -> previous to next }
                .filter { (before, now) ->
                    before != null && now != null && now.showedUp && (before.date != now.date || !before.showedUp)
                }
                .map { }

        /**
         * One emission each time a food seen waiting is seen approved. A food first seen already
         * approved (the stored copy loading, a sign-in) is not a moment.
         */
        fun approvals(reviews: Flow<Map<String, ReviewStatus>>): Flow<Unit> =
            reviews.runningFold(NO_REVIEWS to NO_REVIEWS) { (_, previous), next -> previous to next }
                .filter { (before, now) -> now.any { (id, status) -> status == ReviewStatus.Approved && before.waiting(id) } }
                .map { }

        private val NO_REVIEWS = emptyMap<String, ReviewStatus>()

        /** Seen before, and not approved then. */
        private fun Map<String, ReviewStatus>.waiting(id: String): Boolean =
            this[id].let { it != null && it != ReviewStatus.Approved }
    }
}
