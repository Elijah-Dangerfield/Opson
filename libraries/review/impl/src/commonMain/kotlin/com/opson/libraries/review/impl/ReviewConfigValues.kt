package com.opson.libraries.review.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.IntConfigValue
import com.opson.libraries.config.QaConfigValue
import com.opson.libraries.config.StringConfigValue
import com.opson.libraries.review.ReviewMoment
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/*
 * The levers for asking for a rating (docs/practices/review-asks.md). Paths are literals on
 * purpose: ConfigManifestRegistryDriftTest reads declared paths from source.
 */

/** `release.reviewAsk`: Opson asks for a store rating at a happy moment. Off asks nobody. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ReviewAskEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Ask for a rating"
    override val path = "release.reviewAsk"
    override val default = false
    override val description = "Ask for a store rating at one happy moment per install, split by the review.weight* values"
}

/**
 * The experiment's name. Installs keep the moment they were given under a name; a new name gives
 * every install a new moment from the current weights.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ReviewExperimentName(appConfigMap: AppConfigMap) : StringConfigValue(appConfigMap) {
    override val name = "Rating ask experiment"
    override val path = "review.experiment"
    override val default = "moments-1"
    override val description = "Rename to give every install a new rating-ask moment from the current weights"
}

/** A moment's share of installs. Shares are relative: 1, 1, 1, 0 is a third each and none for the last. */
abstract class ReviewMomentWeight(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    abstract val moment: ReviewMoment
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ReviewWeightCelebration(appConfigMap: AppConfigMap) : ReviewMomentWeight(appConfigMap) {
    override val moment = ReviewMoment.Celebration
    override val name = "Rating ask: after a celebration"
    override val path = "review.weightCelebration"
    override val default = 1
    override val description = "Share of installs asked for a rating after I can do this on a milestone celebration"
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ReviewWeightDayFeltGood(appConfigMap: AppConfigMap) : ReviewMomentWeight(appConfigMap) {
    override val moment = ReviewMoment.DayFeltGood
    override val name = "Rating ask: a day that felt good"
    override val path = "review.weightDayFeltGood"
    override val default = 1
    override val description = "Share of installs asked for a rating after wrapping up today as Good"
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ReviewWeightShowingUp(appConfigMap: AppConfigMap) : ReviewMomentWeight(appConfigMap) {
    override val moment = ReviewMoment.ShowingUp
    override val name = "Rating ask: showing up"
    override val path = "review.weightShowingUp"
    override val default = 1
    override val description = "Share of installs asked for a rating at the first log of the day, once they come back most days"
}

/**
 * Off by default: only people who send foods from a pack ever get this moment, so an install given
 * it would almost never be asked. Turn it up once there are enough of them to compare.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ReviewWeightFoodApproved(appConfigMap: AppConfigMap) : ReviewMomentWeight(appConfigMap) {
    override val moment = ReviewMoment.FoodApproved
    override val name = "Rating ask: a food approved"
    override val path = "review.weightFoodApproved"
    override val default = 0
    override val description = "Share of installs asked for a rating when a food they sent is approved"
}
