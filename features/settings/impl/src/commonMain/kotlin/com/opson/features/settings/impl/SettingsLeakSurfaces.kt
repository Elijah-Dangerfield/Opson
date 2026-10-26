package com.opson.features.settings.impl

import androidx.compose.foundation.layout.Column
import com.opson.features.settings.impl.look.LookAndFeelScreen
import com.opson.features.settings.impl.look.LookAndFeelStateBuilder
import com.opson.features.settings.impl.modes.ModesScreen
import com.opson.features.settings.impl.modes.ModesStateBuilder
import com.opson.features.settings.impl.more.MoreScreen
import com.opson.features.settings.impl.more.MoreStateBuilder
import com.opson.features.settings.impl.pause.PauseSheetContent
import com.opson.features.settings.impl.pause.PauseState
import com.opson.features.settings.impl.pause.PauseSubject
import com.opson.features.settings.impl.reminders.ReminderNotifications
import com.opson.features.settings.impl.reminders.RemindersScreen
import com.opson.features.settings.impl.reminders.RemindersStateBuilder
import com.opson.features.settings.impl.support.DeleteStep
import com.opson.features.settings.impl.support.SupportScreen
import com.opson.features.settings.impl.support.SupportState
import com.opson.features.settings.impl.targets.GoalStyleSheetContent
import com.opson.features.settings.impl.targets.GoalStyleStateBuilder
import com.opson.features.settings.impl.targets.TargetsScreen
import com.opson.features.settings.impl.targets.TargetsStateBuilder
import com.opson.features.settings.impl.water.WaterSettingsScreen
import com.opson.features.settings.impl.water.WaterSettingsStateBuilder
import com.opson.libraries.diary.Tone
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.leak.LeakSurface

/**
 * The settings pages for `BlindModeLeakTest`. Each builds its state with the page's real
 * builder from [SettingsFixtures], with the numbers settings taken from the policy under test,
 * so the page and the policy always agree on the mode.
 */
object SettingsLeakSurfaces {
    val all: List<LeakSurface> = listOf(
        // v3: no row on More carries an amount, in any mode.
        LeakSurface(id = "more", label = "More", showsCaloriesInFull = false) { policy ->
            MoreScreen(MoreStateBuilder.build(SettingsFixtures.settings(policy.settings)), onAction = {})
        },
        LeakSurface(id = "modes", label = "Modes and numbers", showsCaloriesInFull = false) { policy ->
            ModesScreen(ModesStateBuilder.build(policy.settings, SettingsFixtures.timeZone), onAction = {})
        },
        LeakSurface(id = "pause-sheet", label = "Pause before showing more", showsCaloriesInFull = false) { _ ->
            PauseSheetContent(PauseState(subject = PauseSubject.Preset(PresetId.Full), secondsLeft = 7), onAction = {})
        },
        LeakSurface(id = "targets", label = "Targets", showsCaloriesInFull = true) { policy ->
            TargetsScreen(TargetsStateBuilder.build(SettingsFixtures.settings(policy.settings), policy), onAction = {})
        },
        LeakSurface(id = "targets-goal-style", label = "Goal style", showsCaloriesInFull = false) { policy ->
            GoalStyleSheetContent(GoalStyleStateBuilder.build(SettingsFixtures.settings(policy.settings)), onAction = {})
        },
        LeakSurface(id = "targets-deficit", label = "Targets, set deficit", showsCaloriesInFull = true) { policy ->
            val settings = SettingsFixtures.settings(policy.settings, SettingsFixtures.deficitTargets)
            TargetsScreen(TargetsStateBuilder.build(settings, policy, burnKcal = SettingsFixtures.BURN_KCAL), onAction = {})
        },
        LeakSurface(id = "targets-by-day", label = "Targets, different by day", showsCaloriesInFull = true) { policy ->
            val settings = SettingsFixtures.settings(policy.settings, SettingsFixtures.byDayTargets)
            TargetsScreen(TargetsStateBuilder.build(settings, policy, editingGroup = 1), onAction = {})
        },
        LeakSurface(id = "water-settings", label = "Water settings", showsCaloriesInFull = false) { policy ->
            WaterSettingsScreen(
                WaterSettingsStateBuilder.build(SettingsFixtures.settings(policy.settings), backToMore = false),
                onAction = {},
            )
        },
        LeakSurface(id = "support", label = "Support", showsCaloriesInFull = false) { _ ->
            SupportScreen(SupportState(), onAction = {})
        },
        LeakSurface(id = "support-delete", label = "Support, delete everything", showsCaloriesInFull = false) { _ ->
            SupportScreen(SupportState(delete = DeleteStep.Confirming), onAction = {})
        },
        // 0.2, release.reminders. No amounts on the page, and none can reach a notification:
        // its text is fixed resources (ReminderCopy), scanned here in both tones.
        LeakSurface(id = "reminders", label = "Reminders and celebrations", showsCaloriesInFull = false) { policy ->
            RemindersScreen(RemindersStateBuilder.build(SettingsFixtures.remindersSet.copy(numbers = policy.settings)), onAction = {})
        },
        LeakSurface(id = "reminder-notification", label = "Meal and water reminders", showsCaloriesInFull = false) { _ ->
            Column {
                ReminderNotifications(Tone.Plain)
                ReminderNotifications(Tone.Warm)
            }
        },
        LeakSurface(id = "look-and-feel", label = "Look and feel", showsCaloriesInFull = false) { policy ->
            LookAndFeelScreen(LookAndFeelStateBuilder.build(SettingsFixtures.settings(policy.settings)), onAction = {})
        },
    )
}
