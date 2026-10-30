@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.today.impl.kindnote

import com.opson.features.settings.FindSupportEnabled
import com.opson.features.today.impl.KindNoteRoute
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.HardStretch
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.StretchInput
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.navigation.Route
import com.opson.libraries.prompts.PromptAccept
import com.opson.libraries.prompts.PromptCandidate
import com.opson.libraries.prompts.PromptContent
import com.opson.libraries.prompts.PromptFrequency
import com.opson.libraries.prompts.PromptPresentation
import com.opson.libraries.prompts.PromptPriority
import com.opson.libraries.ui.components.icon.AppIcons
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.kind_note_body
import opson.libraries.resources.generated.resources.kind_note_not_now
import opson.libraries.resources.generated.resources.kind_note_support
import opson.libraries.resources.generated.resources.kind_note_title
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration.Companion.days

/**
 * The kind note: when the last weeks of someone's log look like a hard stretch ([HardStretch]),
 * Today offers ways to get support, fewer numbers, or Not now, in its own sheet
 * ([KindNoteSheetContent]). It never says what it saw.
 *
 * Through the prompt processor, so it never lands on another prompt and gets its quiet first
 * days, and above every other prompt ([PromptPriority.CARE]). At most once every [GAP], however
 * it was answered. Offered after numbers have been hard: those are the people it is most for, and
 * it has no number in it. [sensitive]: that it showed is never an app event or a log line.
 *
 * Behind `release.findSupport`, since Find support is the thing it offers, and the person's own
 * switch ([com.opson.libraries.diary.Settings.hardStretchNotes]).
 */
@HiddenFromObjC
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class KindNotePrompt(
    private val findSupport: FindSupportEnabled,
    private val settings: SettingsRepository,
    private val diary: DiaryRepository,
    private val weights: WeightRepository,
    private val looks: CutBackLooks,
    private val calendar: LocalCalendar,
) : PromptCandidate {
    override val id = ID
    override val priority = PromptPriority.CARE
    override val presentation = PromptPresentation.Screen
    override val content = CONTENT
    override val frequency = PromptFrequency(maxShows = Int.MAX_VALUE, maxDismissals = Int.MAX_VALUE, minGap = GAP)
    override val offerWhenNumbersHard = true
    override val sensitive = true

    override suspend fun isEligible(): Boolean {
        if (!findSupport()) return false
        val current = settings.settings.value
        if (!current.hardStretchNotes) return false
        val today = calendar.today()
        val zone = calendar.timeZone()
        val from = today.minus(DatePeriod(days = HardStretch.BASELINE_DAYS - 1))
        val input = StretchInput(
            today = today,
            timeZone = zone,
            days = diary.observeDays(from, today).first(),
            weighIns = weights.observeWeighIns(since = from.atStartOfDayIn(zone)).first(),
            cutBackLooks = looks.recent(),
            sex = current.bodySex,
        )
        return HardStretch.check(input).shows
    }

    override suspend fun accept(): PromptAccept = PromptAccept.Done

    override suspend fun screen(): Route = KindNoteRoute()

    companion object {
        /** Never rename: it keys the prompt's counts on the phone. */
        const val ID = "kind_note"

        /** TODO(clinician): how often the note may come back. */
        val GAP = 21.days

        /** For the QA menu's list; the sheet draws its own words. */
        val CONTENT = PromptContent(
            icon = AppIcons.Favorite,
            title = Res.string.kind_note_title,
            body = Res.string.kind_note_body,
            accept = Res.string.kind_note_support,
            dismiss = Res.string.kind_note_not_now,
        )
    }
}
