package com.opson.features.settings.impl.profile

import com.opson.features.settings.ProfileRoute
import com.opson.libraries.diary.weightPlansOff
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.health.HealthProfile
import com.opson.libraries.health.HealthSource
import com.opson.libraries.nutrition.BodyFat
import com.opson.libraries.nutrition.SexChoice
import kotlinx.datetime.LocalDate
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.HeightInput
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.Heights
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.time.DateCopy
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/** Where Profile was opened from, for its back label. [wire] is `ProfileRoute.from`. */
enum class ProfileBack(val wire: String) {
    More("more"),
    RestingEnergy("resting"),
    Micros("micros"),

    /** A sheet outside settings opened it in its place, so the back label names no page. */
    Elsewhere(ProfileRoute.FROM_ELSEWHERE),
    ;

    companion object {
        /** An unknown value goes back to More, the page's home. */
        fun fromWire(wire: String): ProfileBack = entries.firstOrNull { it.wire == wire } ?: More
    }
}

/**
 * More › Profile, the one place the body details live. Resting energy, Burned and the vitamin
 * and mineral references read the same settings ([Settings.birthYear], [Settings.heightCm],
 * [Settings.bodySex]) and the latest weigh-in, so a change here is a change everywhere.
 *
 * Weight is a [Metric.Weight] number, resolved on [Surface.Settings]: shown only where the mode
 * shows it in full. BMI and body fat go with it, and never show for someone who said numbers have
 * been hard; the fields above them carry no nutrition number.
 */
data class ProfileState(
    val loaded: Boolean = false,
    val back: ProfileBack = ProfileBack.More,
    /** The full date of birth, when it is known ([Settings.knownBirthDate]). */
    val birthDate: LocalDate? = null,
    /** The year every estimate reads; set alone by older builds, so it can be known without the day. */
    val birthYear: Int? = null,
    /** Sex for the estimate, with pregnant and breastfeeding, as onboarding lists it. */
    val sex: SexChoice? = null,
    /** In the unit picked, or else [Settings.heightUnitOr]'s. */
    val height: HeightInput = HeightInput(HeightUnit.Cm),
    val weight: ProfileWeight = ProfileWeight.None,
    /** "23.4"; Hidden without a weight and height, or wherever weight is not on show. */
    val bmi: Display = Display.Hidden,
    /** The body fat field, as typed; null where weight is not on show, and then there is no field. */
    val bodyFatText: String? = null,
    /** "Fill in from Apple Health": the store to fill from, null with `release.health` off. */
    val fillFrom: HealthSource? = null,
    /** Reading the store for it, so the button doesn't take a second tap. */
    val filling: Boolean = false,
    val today: LocalDate = ProfileStateBuilder.PREVIEW_TODAY,
)

/** The Current weight row. Weight is never typed here: it is the latest weigh-in, logged on Weight. */
sealed interface ProfileWeight {
    data object None : ProfileWeight

    /** [display] is Hidden in a mode that hides weight; the row still says when it was logged. */
    data class Logged(val display: Display, val onText: String) : ProfileWeight
}

/**
 * What a typed field is still in the middle of, kept by the view model between settings updates:
 * a field writes its setting only once it holds a sensible value.
 */
data class ProfileDrafts(val height: HeightInput? = null, val bodyFat: String? = null)

object ProfileStateBuilder {

    fun build(
        settings: Settings,
        latest: WeighIn?,
        policy: VisibilityPolicy,
        timeZone: TimeZone,
        drafts: ProfileDrafts = ProfileDrafts(),
        regionHeight: HeightUnit = HeightUnit.Cm,
        back: ProfileBack = ProfileBack.More,
        fillFrom: HealthSource? = null,
        filling: Boolean = false,
        /** The phone's day, which bounds the birthday picker. */
        today: LocalDate = PREVIEW_TODAY,
    ): ProfileState {
        val weightShown = policy.effectiveLevel(Metric.Weight, Surface.Settings) == Visibility.Show
        // BMI and body fat are body-composition numbers: never offered after "numbers have been hard",
        // or under 18, where adult BMI lines don't apply and weight talk is what guidance says to avoid.
        val composition = weightShown && !settings.weightPlansOff(today)
        return ProfileState(
            loaded = true,
            back = back,
            birthDate = settings.knownBirthDate,
            birthYear = settings.birthYear,
            sex = SexChoice.of(settings.bodySex, settings.bodyStage),
            height = height(settings, drafts, regionHeight),
            weight = latest?.let {
                ProfileWeight.Logged(
                    display = if (weightShown) {
                        policy.resolve(Metric.Weight, it.kg, Surface.Settings, FormatContext(weightUnit = settings.weightUnit))
                    } else {
                        Display.Hidden
                    },
                    onText = DateCopy.monthDay(it.measuredAt.toLocalDateTime(timeZone).date),
                )
            } ?: ProfileWeight.None,
            bmi = if (composition) policy.bmi(latest?.kg, settings.heightCm, Surface.Settings) else Display.Hidden,
            bodyFatText = (drafts.bodyFat ?: settings.bodyFat?.let(BodyFat::text).orEmpty()).takeIf { composition },
            fillFrom = fillFrom,
            filling = filling,
            today = today,
        )
    }

    /** The fixtures' day, for previews and tests; the view model passes the real one. */
    val PREVIEW_TODAY: LocalDate = LocalDate(2026, 10, 25)

    /** The height fields: what is being typed, or else the stored height in the unit to show. */
    fun height(settings: Settings, drafts: ProfileDrafts, regionHeight: HeightUnit): HeightInput =
        drafts.height ?: HeightInput.of(settings.heightCm, settings.heightUnitOr(regionHeight))
}

/**
 * What "Fill in from" writes: each detail the store has and Opson can use, over what is there.
 * The person asked for it by tapping, so a stored value is replaced; one the store doesn't have,
 * or an implausible one (a birth year outside [birthYears], a height the equation can't take),
 * is left as it was. Null when the store gave nothing usable.
 */
object ProfileFill {
    fun apply(settings: Settings, profile: HealthProfile, birthYears: IntRange): Settings? {
        // The store's whole date: the birthday line on Today needs the day, and the year follows it.
        val born = profile.birthDate?.takeIf { it.year in birthYears }
        val cm = profile.heightCm?.let(Heights::inRange)?.let { (it * TENTHS).roundToInt() / TENTHS }
        val sex = profile.sex
        if (born == null && cm == null && sex == null) return null
        return (born?.let(settings::withBirthDate) ?: settings).copy(
            heightCm = cm ?: settings.heightCm,
            bodySex = sex ?: settings.bodySex,
        )
    }

    private const val TENTHS = 10.0
}
