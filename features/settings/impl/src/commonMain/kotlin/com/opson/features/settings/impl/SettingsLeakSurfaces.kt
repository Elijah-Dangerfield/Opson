package com.opson.features.settings.impl

import com.opson.features.settings.impl.fasting.FastingFixtures
import com.opson.features.settings.impl.fasting.FastingScreen
import com.opson.features.settings.impl.integrations.IntegrationsFixtures
import com.opson.features.settings.impl.integrations.IntegrationsScreen
import com.opson.features.settings.impl.look.LookAndFeelScreen
import com.opson.features.settings.impl.look.LookAndFeelStateBuilder
import com.opson.features.settings.impl.modes.ModesScreen
import com.opson.features.settings.impl.modes.ModesStateBuilder
import com.opson.features.settings.impl.more.MoreScreen
import com.opson.features.settings.impl.more.MoreStateBuilder
import com.opson.features.settings.impl.pause.PauseSheetContent
import com.opson.features.settings.impl.pause.PauseState
import com.opson.features.settings.impl.pause.PauseSubject
import com.opson.features.settings.impl.support.DeleteStep
import com.opson.features.settings.impl.support.SupportScreen
import com.opson.features.settings.impl.support.SupportState
import com.opson.features.settings.impl.targets.GoalStyleSheetContent
import com.opson.features.settings.impl.targets.GoalStyleStateBuilder
import com.opson.features.settings.impl.targets.TargetsScreen
import com.opson.features.settings.impl.targets.TargetsStateBuilder
import com.opson.features.settings.impl.water.WaterSettingsScreen
import com.opson.features.settings.impl.water.WaterSettingsStateBuilder
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
        // 0.2. Times and a duration, no nutrition number; in a mode without fasting it says so.
        LeakSurface(id = "fasting", label = "Fasting", showsCaloriesInFull = false) { policy ->
            val settings = FastingFixtures.settings(preset = policy.settings.preset)
            FastingScreen(FastingFixtures.state(settings, FastingFixtures.lastNight, FastingFixtures.morning), onAction = {})
        },
        // 0.2: today's energy from Health Connect or Apple Health, every amount energyOut.
        LeakSurface(id = "integrations", label = "Watch and health apps", showsCaloriesInFull = true) { policy ->
            IntegrationsScreen(IntegrationsFixtures.state(policy), onAction = {})
        },
        LeakSurface(id = "look-and-feel", label = "Look and feel", showsCaloriesInFull = false) { policy ->
            LookAndFeelScreen(LookAndFeelStateBuilder.build(SettingsFixtures.settings(policy.settings)), onAction = {})
        },
    )
}
