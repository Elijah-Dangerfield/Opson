@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.prompts

import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.tip_flip_figure
import opson.libraries.resources.generated.resources.tip_food_details
import opson.libraries.resources.generated.resources.tip_pull_refresh
import opson.libraries.resources.generated.resources.tip_targets_goal
import org.jetbrains.compose.resources.StringResource
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * A first-run tip: one line pointing at something new, shown once per account on this phone.
 * [id] keys that memory, so renaming one shows it again to everyone.
 */
@HiddenFromObjC
data class Tip(val id: String, val text: StringResource)

/**
 * Every tip, most useful first: when a screen has several on it, the visit shows the first one
 * here not seen yet (`CoachMarks.pick`). A feature puts one on screen with `CoachMarkAnchor`
 * inside its screen's `CoachMarkScreen`; adding one is a line here and its copy in
 * strings_tips.xml. Rules: at most one a screen visit, never during onboarding, never twice.
 */
@HiddenFromObjC
object Tips {
    val FlipFigure = Tip("today_flip_figure", Res.string.tip_flip_figure)
    val FoodDetails = Tip("today_food_details", Res.string.tip_food_details)
    val PullToRefresh = Tip("today_pull_refresh", Res.string.tip_pull_refresh)
    val TargetsGoal = Tip("targets_weight_goal", Res.string.tip_targets_goal)

    val all: List<Tip> = listOf(FlipFigure, FoodDetails, PullToRefresh, TargetsGoal)
}

/** The QA menu's handle on tips: forget which this account has seen on this phone. */
@HiddenFromObjC
interface TipsDebug {
    suspend fun reset()
}
