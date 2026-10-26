package com.opson.features.settings

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/** Modes and numbers: the preset list, the per-metric grid and "Pause before showing more". */
@Serializable
class ModesRoute : Route()

/** The energy range and macro split. */
@Serializable
class TargetsRoute : Route()

/** The goal style picker over Targets: a range, a set deficit, or different by day. */
@Serializable
class GoalStyleRoute : Route()

/**
 * Water goal, glass size and units. [fromMore] picks the back label: Today's water sheet opens
 * it with `false` ("‹ Today"), the More list with `true` ("‹ More").
 */
@Serializable
class WaterSettingsRoute(val fromMore: Boolean = false) : Route()

/** Palette, theme, text size, weight units, tone and the hunger questions. */
@Serializable
class LookAndFeelRoute : Route()

/** Helpline, export, delete, and the sharing that is coming later. */
@Serializable
class SupportRoute : Route()

/**
 * Reminders and celebrations (0.2, behind `release.reminders`): quiet mode, the big-day card,
 * meal reminders and their times, tone and the hunger questions. Opened from More and from the
 * Rhythm card on Today.
 */
@Serializable
class RemindersRoute : Route()
/** Watch and health apps: connect Apple Health or Health Connect, and today's energy from it (0.2). */
@Serializable
class IntegrationsRoute : Route()
