package com.opson.features.settings.impl

import androidx.compose.foundation.layout.Column
import com.opson.features.settings.impl.energy.EnergyTodayFixtures
import com.opson.features.settings.impl.energy.EnergyTodayScreen
import com.opson.features.settings.impl.energy.EnergyTodayStateBuilder
import com.opson.features.settings.impl.energy.RestingEnergyScreen
import com.opson.features.settings.impl.energy.RestingEnergyStateBuilder
import com.opson.features.settings.impl.fasting.FastingFixtures
import com.opson.features.settings.impl.fasting.FastingScreen
import com.opson.features.settings.impl.integrations.IntegrationsFixtures
import com.opson.features.settings.impl.integrations.IntegrationsScreen
import com.opson.features.settings.impl.integrations.LeaveOutSheetContent
import com.opson.features.settings.impl.display.DisplayScreen
import com.opson.features.settings.impl.look.LookAndFeelStateBuilder
import com.opson.features.settings.impl.micros.MicroTargetSheetContent
import com.opson.features.settings.impl.micros.MicroTargetsScreen
import com.opson.features.settings.impl.micros.MicroTargetsStateBuilder
import com.opson.features.settings.impl.modes.EachNumberScreen
import com.opson.features.settings.impl.modes.ModesStateBuilder
import com.opson.features.settings.impl.more.MoreScreen
import com.opson.features.settings.impl.more.MoreStateBuilder
import com.opson.features.settings.impl.pause.PauseSheetContent
import com.opson.features.settings.impl.pause.PauseState
import com.opson.features.settings.impl.pause.PauseSubject
import com.opson.features.settings.impl.profile.ProfileFixtures
import com.opson.features.settings.impl.profile.ProfileScreen
import com.opson.features.settings.impl.profile.ProfileStateBuilder
import com.opson.features.settings.impl.reminders.ReminderNotifications
import com.opson.features.settings.impl.reminders.RemindersScreen
import com.opson.features.settings.impl.reminders.RemindersStateBuilder
import com.opson.features.settings.impl.summary.ProviderSummaryFixtures
import com.opson.features.settings.impl.summary.ProviderSummaryScreen
import com.opson.features.settings.impl.support.DeleteStep
import com.opson.features.settings.impl.support.ExportStatus
import com.opson.features.settings.impl.support.FindSupportScreen
import com.opson.features.settings.impl.support.FindSupportStateBuilder
import com.opson.features.settings.impl.support.SupportCountry
import com.opson.features.settings.impl.support.SupportScreen
import com.opson.features.settings.impl.support.SupportState
import com.opson.features.settings.impl.support.SupportStateBuilder
import com.opson.features.settings.impl.targets.TargetsScreen
import com.opson.features.settings.impl.targets.TargetsStateBuilder
import com.opson.features.settings.impl.todayscreen.TodayScreenChoice
import com.opson.features.settings.impl.todayscreen.TodayScreenChoiceSheetContent
import com.opson.features.settings.impl.todayscreen.TodayScreenChoiceState
import com.opson.features.settings.impl.todayscreen.TodayScreenSettingsScreen
import com.opson.features.settings.impl.todayscreen.TodayScreenSettingsStateBuilder
import com.opson.features.settings.impl.water.WaterSettingsScreen
import com.opson.features.settings.impl.water.WaterSettingsStateBuilder
import com.opson.features.settings.impl.widgets.WidgetsFixtureScreen
import com.opson.libraries.diary.MacroMode
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.RestingChoices
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.leak.LeakSurface
import com.opson.libraries.widgets.WidgetCard
import com.opson.libraries.widgets.WidgetFixtures
import com.opson.libraries.widgets.WidgetSize
import com.opson.libraries.widgets.WidgetSnapshotBuilder
import com.opson.libraries.widgets.rememberWidgetCopy
import kotlinx.datetime.LocalDate

/**
 * The settings pages for `CaloriesHiddenLeakTest`. Each builds its state with the page's real
 * builder from [SettingsFixtures], with the numbers settings taken from the policy under test,
 * so the page and the policy always agree on the mode.
 */
object SettingsLeakSurfaces {
    val all: List<LeakSurface> = listOf(
        // v3: no row on More carries an amount, in any mode.
        LeakSurface(id = "more", label = "More", showsCaloriesInFull = false) { policy ->
            MoreScreen(MoreStateBuilder.build(SettingsFixtures.settings(policy.settings)), onAction = {})
        },
        // More › Display, the ids kept from when its Numbers half was Modes and numbers. The
        // Appearance half (once Look and feel) is on the page, so it is scanned with it.
        LeakSurface(id = "modes", label = "Display", showsCaloriesInFull = false) { policy ->
            DisplayScreen(
                modes = ModesStateBuilder.build(policy.settings, SettingsFixtures.timeZone, photosReleased = true),
                look = LookAndFeelStateBuilder.build(SettingsFixtures.settings(policy.settings)),
                onModesAction = {},
                onLookAction = {},
            )
        },
        // Design sync 4: End after a set time open, its length and next mode named, never an amount.
        LeakSurface(id = "modes-end-after", label = "Display, End after a set time", showsCaloriesInFull = false) { policy ->
            val timed = policy.settings.copy(endAfter = SettingsFixtures.fullForTwoWeeks.endAfter)
            DisplayScreen(
                modes = ModesStateBuilder.build(timed, SettingsFixtures.timeZone, editingEndAfter = true),
                look = LookAndFeelStateBuilder.build(SettingsFixtures.settings(policy.settings)),
                onModesAction = {},
                onLookAction = {},
            )
        },
        LeakSurface(id = "modes-each-number", label = "Each number", showsCaloriesInFull = false) { policy ->
            EachNumberScreen(ModesStateBuilder.build(policy.settings, SettingsFixtures.timeZone, photosReleased = true), onAction = {})
        },
        LeakSurface(id = "pause-sheet", label = "Pause before showing more", showsCaloriesInFull = false) { _ ->
            PauseSheetContent(PauseState(subject = PauseSubject.Preset(PresetId.Full), secondsLeft = 7), onAction = {})
        },
        LeakSurface(id = "targets", label = "Targets", showsCaloriesInFull = true) { policy ->
            TargetsScreen(TargetsStateBuilder.build(SettingsFixtures.settings(policy.settings), policy), onAction = {})
        },
        // Design sync 4: the goal style list with a set deficit picked, its target against Burned,
        // and where Burned comes from.
        LeakSurface(id = "targets-goal-style", label = "Goal style", showsCaloriesInFull = true) { policy ->
            val settings = SettingsFixtures.settings(policy.settings, SettingsFixtures.deficitTargets)
            TargetsScreen(
                TargetsStateBuilder.build(settings, policy, burned = SettingsFixtures.burned, goalsAndEnergy = true, healthSource = HealthSource.HealthConnect),
                onAction = {},
            )
        },
        LeakSurface(id = "resting-energy", label = "Resting energy", showsCaloriesInFull = false) { policy ->
            val choices = RestingChoices(1610, emptySet(), 1560, HealthSource.HealthConnect, 72.0)
            val settings = SettingsFixtures.settings(policy.settings).copy(restingOwnKcal = 1600)
            RestingEnergyScreen(RestingEnergyStateBuilder.build(settings, choices, policy, activitySource = HealthSource.HealthConnect), onAction = {})
        },
        // Design sync 4, release.goalsAndEnergy: More › Today screen arranges, it never shows an amount.
        LeakSurface(id = "today-screen-settings", label = "Today screen", showsCaloriesInFull = false) { policy ->
            val settings = SettingsFixtures.settings(policy.settings)
            Column {
                TodayScreenSettingsScreen(TodayScreenSettingsStateBuilder.build(settings), onAction = {})
                TodayScreenChoiceSheetContent(TodayScreenChoiceState.of(TodayScreenChoice.MainNumber, settings), onAction = {})
            }
        },
        // Vitamins and minerals: daily references, never an amount eaten, and off where the mode hides micros.
        LeakSurface(id = "micro-targets", label = "Vitamins and minerals", showsCaloriesInFull = false) { policy ->
            val settings = SettingsFixtures.settings(policy.settings).copy(birthYear = 1990, bodySex = BodySex.Female)
            MicroTargetsScreen(MicroTargetsStateBuilder.build(settings, policy, SettingsFixtures.today, goalsAndEnergy = true), onAction = {})
        },
        LeakSurface(id = "micro-target-sheet", label = "Vitamins and minerals, one nutrient", showsCaloriesInFull = false) { policy ->
            val settings = SettingsFixtures.settings(policy.settings).withMicroTarget(Micronutrient.Iron, 12.0)
            MicroTargetSheetContent(MicroTargetsStateBuilder.sheet(settings, policy, SettingsFixtures.today, Micronutrient.Iron), onAction = {})
        },
        LeakSurface(id = "energy-today", label = "Energy today", showsCaloriesInFull = true) { policy ->
            val settings = SettingsFixtures.settings(policy.settings)
            EnergyTodayScreen(EnergyTodayStateBuilder.build(EnergyTodayFixtures.inputs(settings), policy), onAction = {})
        },
        // release.goalsAndEnergy: one number, a set surplus, and a day group of each shape.
        LeakSurface(id = "targets-exact", label = "Targets, one number", showsCaloriesInFull = true) { policy ->
            val settings = SettingsFixtures.settings(policy.settings, SettingsFixtures.exactTargets)
            TargetsScreen(TargetsStateBuilder.build(settings, policy), onAction = {})
        },
        LeakSurface(id = "targets-surplus", label = "Targets, set surplus", showsCaloriesInFull = true) { policy ->
            val settings = SettingsFixtures.settings(policy.settings, SettingsFixtures.surplusTargets)
            TargetsScreen(TargetsStateBuilder.build(settings, policy, burned = SettingsFixtures.burned), onAction = {})
        },
        LeakSurface(id = "targets-by-day-shapes", label = "Targets, day groups by shape", showsCaloriesInFull = true) { policy ->
            val settings = SettingsFixtures.settings(policy.settings, SettingsFixtures.shapedByDayTargets)
            val everyShape = SettingsFixtures.settings(policy.settings, SettingsFixtures.everyShapeByDayTargets)
            Column {
                TargetsScreen(TargetsStateBuilder.build(settings, policy, goalsAndEnergy = true), onAction = {})
                TargetsScreen(
                    TargetsStateBuilder.build(everyShape, policy, burned = SettingsFixtures.burned, goalsAndEnergy = true),
                    onAction = {},
                )
            }
        },
        // The low-goal and fast-pace notes, which name no number but must never show without calories.
        LeakSurface(id = "targets-safety-notes", label = "Targets, low goal and fast deficit notes", showsCaloriesInFull = true) { policy ->
            val low = SettingsFixtures.settings(policy.settings, SettingsFixtures.lowRangeTargets)
            val fast = SettingsFixtures.settings(policy.settings, SettingsFixtures.fastDeficitTargets)
            Column {
                TargetsScreen(TargetsStateBuilder.build(low, policy), onAction = {})
                TargetsScreen(TargetsStateBuilder.build(fast, policy, burned = SettingsFixtures.activeBurned, goalsAndEnergy = true), onAction = {})
            }
        },
        // A deficit with no health app: Burned is resting energy plus everyday activity.
        LeakSurface(id = "targets-deficit-estimated", label = "Targets, deficit without a health app", showsCaloriesInFull = true) { policy ->
            val settings = SettingsFixtures.settings(policy.settings, SettingsFixtures.deficitTargets)
            TargetsScreen(
                TargetsStateBuilder.build(settings, policy, burned = SettingsFixtures.estimatedBurned, goalsAndEnergy = true, choosingActivity = true),
                onAction = {},
            )
        },
        // The macro editor in percents and in grams, its presets, and macros that don't add up.
        LeakSurface(id = "targets-macros", label = "Targets, macro editor", showsCaloriesInFull = true) { policy ->
            val ratios = SettingsFixtures.settings(policy.settings)
            val grams = SettingsFixtures.settings(policy.settings, SettingsFixtures.gramsOverTargets).copy(macroMode = MacroMode.Grams)
            Column {
                TargetsScreen(TargetsStateBuilder.build(ratios, policy, editingMacros = true), onAction = {})
                TargetsScreen(TargetsStateBuilder.build(grams, policy, editingMacros = true), onAction = {})
            }
        },
        LeakSurface(id = "targets-deficit", label = "Targets, set deficit", showsCaloriesInFull = true) { policy ->
            val settings = SettingsFixtures.settings(policy.settings, SettingsFixtures.deficitTargets)
            TargetsScreen(TargetsStateBuilder.build(settings, policy, burned = SettingsFixtures.burned), onAction = {})
        },
        LeakSurface(id = "targets-by-day", label = "Targets, different by day", showsCaloriesInFull = true) { policy ->
            val settings = SettingsFixtures.settings(policy.settings, SettingsFixtures.byDayTargets)
            TargetsScreen(TargetsStateBuilder.build(settings, policy), onAction = {})
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
        // The warning names what the file holds, never an amount from it.
        LeakSurface(id = "support-export-numbers", label = "Support, the export has every number", showsCaloriesInFull = false) { _ ->
            SupportScreen(SupportState(export = ExportStatus.ConfirmingNumbers), onAction = {})
        },
        // release.providerSummary, first for someone working with a provider.
        LeakSurface(id = "support-provider-first", label = "Support, a summary for your provider", showsCaloriesInFull = false) { _ ->
            SupportScreen(SupportStateBuilder.build(findSupport = true, SupportCountry.UnitedStates, true, true), onAction = {})
        },
        // release.providerSummary. Every part switched in, numbers and weight too: the preview
        // keeps any number the mode does not show outright for the PDF alone.
        LeakSurface(id = "provider-summary", label = "A summary for your provider", showsCaloriesInFull = true) { policy ->
            ProviderSummaryScreen(ProviderSummaryFixtures.state(policy), onAction = {})
        },
        LeakSurface(id = "provider-summary-warning", label = "Provider summary, numbers your mode hides", showsCaloriesInFull = true) { policy ->
            ProviderSummaryScreen(ProviderSummaryFixtures.warningState(policy), onAction = {})
        },
        LeakSurface(id = "provider-summary-custom", label = "Provider summary, pick dates", showsCaloriesInFull = true) { policy ->
            ProviderSummaryScreen(ProviderSummaryFixtures.customState(policy), onAction = {})
        },
        // Design sync 2, release.findSupport. Phone numbers and no amounts: listed so a later
        // line that ever carries a number of the person's own is caught.
        LeakSurface(id = "support-find", label = "Find support", showsCaloriesInFull = false) { _ ->
            Column {
                FindSupportScreen(FindSupportStateBuilder.build(SupportCountry.UnitedStates), onAction = {})
                SupportScreen(SupportStateBuilder.build(findSupport = true, SupportCountry.UnitedStates), onAction = {})
            }
        },
        // 0.2. Times and a duration, no nutrition number; in a mode without fasting it says so.
        LeakSurface(id = "fasting", label = "Fasting", showsCaloriesInFull = false) { policy ->
            val settings = FastingFixtures.settings(preset = policy.settings.preset)
            FastingScreen(FastingFixtures.state(settings, FastingFixtures.lastNight, FastingFixtures.morning), onAction = {})
        },
        // More › Profile, with release.goalsAndEnergy: the weigh-in and BMI as weight numbers, shown
        // only where the mode shows weight in full; the year, sex and height carry no nutrition number.
        LeakSurface(id = "profile", label = "Profile", showsCaloriesInFull = false) { policy ->
            ProfileScreen(
                ProfileStateBuilder.build(
                    ProfileFixtures.filled.copy(numbers = policy.settings),
                    ProfileFixtures.weighIn,
                    policy,
                    SettingsFixtures.timeZone,
                ),
                onAction = {},
            )
        },
        // 0.2, release.reminders. No amounts on the page, and none can reach a notification:
        // its text is fixed resources (ReminderCopy), every one scanned here.
        LeakSurface(id = "reminders", label = "Reminders and celebrations", showsCaloriesInFull = false) { policy ->
            RemindersScreen(RemindersStateBuilder.build(SettingsFixtures.remindersSet.copy(numbers = policy.settings)), onAction = {})
        },
        LeakSurface(id = "reminder-notification", label = "Meal and water reminders", showsCaloriesInFull = false) { _ ->
            ReminderNotifications()
        },
        // 0.2: today's energy from Health Connect or Apple Health, every amount energyOut.
        LeakSurface(id = "integrations", label = "Watch and health apps", showsCaloriesInFull = true) { policy ->
            IntegrationsScreen(IntegrationsFixtures.state(policy), onAction = {})
        },
        // The Leave out a day sheet: dates and a month, never an amount.
        LeakSurface(id = "integrations-leave-out", label = "Leave out a day", showsCaloriesInFull = false) { _ ->
            LeaveOutSheetContent(IntegrationsFixtures.leaveOutState(), onAction = {})
        },
        // 0.2, release.widgets. The home screen widget as the platforms draw it, from the snapshot
        // the app writes for them: today and tomorrow, both sizes. The snapshot is the leak
        // surface, since a widget renders it and nothing else.
        LeakSurface(id = "widgets", label = "Widgets", showsCaloriesInFull = true) { policy ->
            WidgetsFixtureScreen(policy)
        },
        LeakSurface(id = "widget", label = "Home screen widget", showsCaloriesInFull = true) { policy ->
            val snapshot = WidgetSnapshotBuilder.build(WidgetFixtures.inputs(policy.settings), policy, rememberWidgetCopy())
            Column {
                snapshot.days.forEach { day ->
                    val date = LocalDate.parse(day.day)
                    WidgetCard(snapshot, date, WidgetSize.Medium)
                    WidgetCard(snapshot, date, WidgetSize.Small)
                }
            }
        },
    )
}
