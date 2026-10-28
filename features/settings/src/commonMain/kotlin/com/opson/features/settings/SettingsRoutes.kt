package com.opson.features.settings

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/** Modes and numbers: the four presets, End after a set time, Each number and "Pause before showing more". */
@Serializable
class ModesRoute : Route()

/** Each number: the per-metric grid, opened from Modes and numbers. */
@Serializable
class EachNumberRoute : Route()

/** The energy range and macro split, and the goal style. */
@Serializable
class TargetsRoute : Route()

/**
 * Where Burned's resting energy comes from (design sync 4, 13.2d): calculated, the health app or
 * a number of the person's own, and whether activity counts. `release.goalsAndEnergy`.
 * [fromEnergyToday] picks the back label: Energy today opens it with `true`, Targets with `false`.
 */
@Serializable
class RestingEnergyRoute(val fromEnergyToday: Boolean = false) : Route()

/**
 * Energy today (design sync 4, 13.2c): eaten with its ±, Burned with where each part comes from,
 * and the deficit so far against the range. `release.goalsAndEnergy`; Today's summary card links
 * here. Check `GoalsAndEnergyEnabled` before linking.
 */
@Serializable
class EnergyTodayRoute : Route()

/** More › Today screen (design sync 4, 13.6): the summary card's preset, main number and pages, the list's rows and the header's Close the day. */
@Serializable
class TodayScreenSettingsRoute : Route()

/** The main number or card pages sheet over More › Today screen; [choice] is `main_number` or `card_pages`. */
@Serializable
class TodayScreenChoiceRoute(val choice: String = "main_number") : Route()

/**
 * Water goal, glass size and units. [fromMore] picks the back label: Today's water sheet opens
 * it with `false` ("‹ Today"), the More list with `true` ("‹ More").
 */
@Serializable
class WaterSettingsRoute(val fromMore: Boolean = false) : Route()

/** Appearance, text size, weight units, tone and the hunger questions. */
@Serializable
class LookAndFeelRoute : Route()

/** Helplines, export, feedback and deleting everything. */
@Serializable
class SupportRoute : Route()

/**
 * Reminders and celebrations (0.2, behind `release.reminders`): quiet mode, the big-day card,
 * meal reminders and their times, tone and the hunger questions. Opened from More.
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
 * eating window. Only reached where fasting is offered (All numbers, and never for someone
 * who agreed numbers have been hard).
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

/**
 * A summary for your provider (`release.providerSummary`): pick a range and what goes in, look it
 * over, share a PDF. One-off and made by the person; there is no link anyone else can open.
 * Opened from Support. Check [ProviderSummaryEnabled] before linking here.
 */
@Serializable
class ProviderSummaryRoute : Route()

/**
 * Credits and licenses: food data, the typeface and the open-source licences in the shipped
 * builds. Opened from Support's About card and More.
 */
@Serializable
class CreditsRoute : Route()
