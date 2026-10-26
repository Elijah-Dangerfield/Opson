package com.opson.features.settings.impl

import com.opson.features.settings.impl.look.LookAndFeelScreen
import com.opson.features.settings.impl.look.LookAndFeelStateBuilder
import com.opson.features.settings.impl.modes.ModesScreen
import com.opson.features.settings.impl.modes.ModesStateBuilder
import com.opson.features.settings.impl.more.MoreScreen
import com.opson.features.settings.impl.more.MoreStateBuilder
import com.opson.features.settings.impl.pause.PauseSheetContent
import com.opson.features.settings.impl.pause.PauseState
import com.opson.features.settings.impl.pause.PauseSubject
import com.opson.features.settings.impl.support.SupportScreen
import com.opson.features.settings.impl.support.SupportState
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
        LeakSurface(id = "more", label = "More", showsCaloriesInFull = true) { policy ->
            MoreScreen(MoreStateBuilder.build(SettingsFixtures.settings(policy.settings), policy), onAction = {})
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
        LeakSurface(id = "water-settings", label = "Water settings", showsCaloriesInFull = false) { policy ->
            WaterSettingsScreen(
                WaterSettingsStateBuilder.build(SettingsFixtures.settings(policy.settings), backToMore = false),
                onAction = {},
            )
        },
        LeakSurface(id = "support", label = "Support", showsCaloriesInFull = false) { _ ->
            SupportScreen(SupportState(), onAction = {})
        },
        LeakSurface(id = "look-and-feel", label = "Look and feel", showsCaloriesInFull = false) { policy ->
            LookAndFeelScreen(LookAndFeelStateBuilder.build(SettingsFixtures.settings(policy.settings)), onAction = {})
        },
    )
}
