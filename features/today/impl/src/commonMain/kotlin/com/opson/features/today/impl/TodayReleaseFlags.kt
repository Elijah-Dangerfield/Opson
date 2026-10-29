package com.opson.features.today.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/*
 * The release flags for Today (docs/plans/screens/today.md and today-actions.md, "Flags"). Off,
 * the screen is the refined design with none of the new behaviour. Each is read when Today or the
 * food's sheet builds its inputs; nothing runs at launch. Day close's check-in flag, `CheckInEnabled`, is in
 * `:libraries:diary`, because Reminders and Patterns read it too, and so is `TodaySummaryEnabled`, which
 * More › Today screen reads.
 */

/**
 * Long-press to select foods with Move, Copy to… and Delete, dragging them between meals, swipe to
 * delete, and Move to… on Uncategorized.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class TodaySelectEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Select and swipe on Today"
    override val path = "release.todaySelect"
    override val default = false
    override val description = "Select foods on Today, drag them between meals, swipe a food to delete it, and file Uncategorized foods"
}

/**
 * A ⋮ on every food row on Today, and the food's sheet offering Change amount, Move, Copy to
 * another day, Edit a copy and Add to favorites besides Delete (design sync 4, 13.3d and 13.3e).
 * Off, the sheet offers Delete alone, as before.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class TodayFoodActionsEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Food actions on Today"
    override val path = "release.foodActions"
    override val default = false
    override val description = "A food's menu on Today: change amount, move, copy to another day, edit a copy, favorite"
}
