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

/** Leave out a day (0.2, behind `release.health`): a month of days to leave out of the math, or count again. */
@Serializable
class LeaveOutDayRoute : Route()

/**
 * Fasting (0.2, `release.fasting`): the status card, the timer switch, the Today card and the
 * eating window. Only reached where fasting is offered (Full numbers and Sprint, and never for
 * someone who agreed numbers have been hard).
 */
@Serializable
class FastingRoute : Route()

/**
 * Widgets (0.2, `release.widgets`): what the home screen widget looks like in the person's mode,
 * and how to add one. Opened from More.
 */
@Serializable
class WidgetsRoute : Route()

/**
 * Find support (design sync 2, `release.findSupport`): crisis and eating-disorder lines for the
 * phone's country, from a list that ships with the app. Opened from Support, the pause sheet,
 * the onboarding statement and the big-day card. Check [FindSupportEnabled] before linking here.
 *
 * Opening it is never logged, synced or shared: no app event, and the app shell keeps this route
 * out of crash and jank attribution.
 */
@Serializable
class FindSupportRoute : Route()
