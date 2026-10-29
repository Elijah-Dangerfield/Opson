@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.today.impl.notes

import com.opson.features.settings.MorePlacement
import com.opson.features.settings.MoreRowContribution
import com.opson.features.today.CheckInRoute
import com.opson.features.today.NotesOrigin
import com.opson.features.today.NotesRoute
import com.opson.libraries.diary.CheckInEnabled
import com.opson.libraries.navigation.Route
import com.opson.libraries.ui.components.icon.AppIcons
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_more_checkin
import opson.libraries.resources.generated.resources.today_more_checkin_sub
import opson.libraries.resources.generated.resources.today_more_journal
import opson.libraries.resources.generated.resources.today_more_notes
import opson.libraries.resources.generated.resources.today_more_notes_sub
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/*
 * More's Journal card: the check-in and the notes, reachable without Today's ⋮ menu or the + sheet.
 * Close the day is one more way into the same data, not the only one. Under the extras card, since
 * both are used day to day. Hidden from ObjC, as MoreRowContribution is.
 */

/** More › Journal › Check in: today's check-in sheet, while `release.checkIn` is on. */
@HiddenFromObjC
@Inject
@ContributesBinding(AppScope::class, multibinding = true)
class CheckInMoreRow(private val enabled: CheckInEnabled) : MoreRowContribution {
    override val section = Res.string.today_more_journal
    override val title = Res.string.today_more_checkin
    override val subtitle = Res.string.today_more_checkin_sub
    override val icon = AppIcons.Mood
    override val placement = MorePlacement.AfterExtras
    override val order = 0

    override fun route(): Route = CheckInRoute()

    override fun visible(): Boolean = enabled()
}

/** More › Journal › Notes: every day note and meal Note, with More as the back label. */
@HiddenFromObjC
@Inject
@ContributesBinding(AppScope::class, multibinding = true)
class NotesMoreRow : MoreRowContribution {
    override val section = Res.string.today_more_journal
    override val title = Res.string.today_more_notes
    override val subtitle = Res.string.today_more_notes_sub
    override val icon = AppIcons.EditNote
    override val placement = MorePlacement.AfterExtras
    override val order = 1

    override fun route(): Route = NotesRoute(from = NotesOrigin.More)
}
