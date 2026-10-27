package com.opson.libraries.diary.ui

import com.opson.libraries.diary.ExerciseEntry
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.ExerciseActivities
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.everyday_activity_mostly_sitting
import opson.libraries.resources.generated.resources.everyday_activity_mostly_sitting_detail
import opson.libraries.resources.generated.resources.everyday_activity_on_my_feet
import opson.libraries.resources.generated.resources.everyday_activity_on_my_feet_detail
import opson.libraries.resources.generated.resources.everyday_activity_physical_work
import opson.libraries.resources.generated.resources.everyday_activity_physical_work_detail
import opson.libraries.resources.generated.resources.everyday_activity_some_walking
import opson.libraries.resources.generated.resources.everyday_activity_some_walking_detail
import opson.libraries.resources.generated.resources.exercise_activity_aerobics
import opson.libraries.resources.generated.resources.exercise_activity_backpacking
import opson.libraries.resources.generated.resources.exercise_activity_badminton
import opson.libraries.resources.generated.resources.exercise_activity_basketball
import opson.libraries.resources.generated.resources.exercise_activity_bodyweight
import opson.libraries.resources.generated.resources.exercise_activity_boxing_bag
import opson.libraries.resources.generated.resources.exercise_activity_circuit
import opson.libraries.resources.generated.resources.exercise_activity_climbing
import opson.libraries.resources.generated.resources.exercise_activity_cross_country_skiing
import opson.libraries.resources.generated.resources.exercise_activity_cycling
import opson.libraries.resources.generated.resources.exercise_activity_cycling_easy
import opson.libraries.resources.generated.resources.exercise_activity_cycling_fast
import opson.libraries.resources.generated.resources.exercise_activity_dancing
import opson.libraries.resources.generated.resources.exercise_activity_elliptical
import opson.libraries.resources.generated.resources.exercise_activity_exercise_bike
import opson.libraries.resources.generated.resources.exercise_activity_gardening
import opson.libraries.resources.generated.resources.exercise_activity_golf
import opson.libraries.resources.generated.resources.exercise_activity_hiit
import opson.libraries.resources.generated.resources.exercise_activity_hiking
import opson.libraries.resources.generated.resources.exercise_activity_housework
import opson.libraries.resources.generated.resources.exercise_activity_inline_skating
import opson.libraries.resources.generated.resources.exercise_activity_jogging
import opson.libraries.resources.generated.resources.exercise_activity_jump_rope
import opson.libraries.resources.generated.resources.exercise_activity_kayaking
import opson.libraries.resources.generated.resources.exercise_activity_martial_arts
import opson.libraries.resources.generated.resources.exercise_activity_mountain_biking
import opson.libraries.resources.generated.resources.exercise_activity_mowing
import opson.libraries.resources.generated.resources.exercise_activity_other
import opson.libraries.resources.generated.resources.exercise_activity_paddleboarding
import opson.libraries.resources.generated.resources.exercise_activity_pilates
import opson.libraries.resources.generated.resources.exercise_activity_rowing_machine
import opson.libraries.resources.generated.resources.exercise_activity_rowing_machine_hard
import opson.libraries.resources.generated.resources.exercise_activity_running_10
import opson.libraries.resources.generated.resources.exercise_activity_running_12
import opson.libraries.resources.generated.resources.exercise_activity_running_6
import opson.libraries.resources.generated.resources.exercise_activity_running_7_5
import opson.libraries.resources.generated.resources.exercise_activity_running_8_5
import opson.libraries.resources.generated.resources.exercise_activity_skating
import opson.libraries.resources.generated.resources.exercise_activity_skiing
import opson.libraries.resources.generated.resources.exercise_activity_snow_shoveling
import opson.libraries.resources.generated.resources.exercise_activity_soccer
import opson.libraries.resources.generated.resources.exercise_activity_spin_class
import opson.libraries.resources.generated.resources.exercise_activity_stair_machine
import opson.libraries.resources.generated.resources.exercise_activity_stairs
import opson.libraries.resources.generated.resources.exercise_activity_stretching
import opson.libraries.resources.generated.resources.exercise_activity_swimming
import opson.libraries.resources.generated.resources.exercise_activity_swimming_easy
import opson.libraries.resources.generated.resources.exercise_activity_tai_chi
import opson.libraries.resources.generated.resources.exercise_activity_tennis
import opson.libraries.resources.generated.resources.exercise_activity_volleyball
import opson.libraries.resources.generated.resources.exercise_activity_walking
import opson.libraries.resources.generated.resources.exercise_activity_walking_brisk
import opson.libraries.resources.generated.resources.exercise_activity_walking_dog
import opson.libraries.resources.generated.resources.exercise_activity_walking_slow
import opson.libraries.resources.generated.resources.exercise_activity_water_aerobics
import opson.libraries.resources.generated.resources.exercise_activity_weights
import opson.libraries.resources.generated.resources.exercise_activity_weights_heavy
import opson.libraries.resources.generated.resources.exercise_activity_yoga
import opson.libraries.resources.generated.resources.exercise_activity_yoga_power
import opson.libraries.resources.generated.resources.exercise_activity_zumba
import org.jetbrains.compose.resources.StringResource

/** The label for an activity key, "Walking, brisk"; null for a key this build does not know (a newer phone's). */
fun exerciseActivityLabel(key: String): StringResource? = activityLabels[key]

private val activityLabels: Map<String, StringResource> = mapOf(
    ExerciseActivities.OTHER to Res.string.exercise_activity_other,
    "walking" to Res.string.exercise_activity_walking,
    "walking_brisk" to Res.string.exercise_activity_walking_brisk,
    "walking_slow" to Res.string.exercise_activity_walking_slow,
    "walking_dog" to Res.string.exercise_activity_walking_dog,
    "hiking" to Res.string.exercise_activity_hiking,
    "backpacking" to Res.string.exercise_activity_backpacking,
    "jogging" to Res.string.exercise_activity_jogging,
    "running_12" to Res.string.exercise_activity_running_12,
    "running_10" to Res.string.exercise_activity_running_10,
    "running_8_5" to Res.string.exercise_activity_running_8_5,
    "running_7_5" to Res.string.exercise_activity_running_7_5,
    "running_6" to Res.string.exercise_activity_running_6,
    "cycling_easy" to Res.string.exercise_activity_cycling_easy,
    "cycling" to Res.string.exercise_activity_cycling,
    "cycling_fast" to Res.string.exercise_activity_cycling_fast,
    "mountain_biking" to Res.string.exercise_activity_mountain_biking,
    "exercise_bike" to Res.string.exercise_activity_exercise_bike,
    "spin_class" to Res.string.exercise_activity_spin_class,
    "elliptical" to Res.string.exercise_activity_elliptical,
    "rowing_machine" to Res.string.exercise_activity_rowing_machine,
    "rowing_machine_hard" to Res.string.exercise_activity_rowing_machine_hard,
    "stair_machine" to Res.string.exercise_activity_stair_machine,
    "stairs" to Res.string.exercise_activity_stairs,
    "weights" to Res.string.exercise_activity_weights,
    "weights_heavy" to Res.string.exercise_activity_weights_heavy,
    "circuit" to Res.string.exercise_activity_circuit,
    "hiit" to Res.string.exercise_activity_hiit,
    "bodyweight" to Res.string.exercise_activity_bodyweight,
    "yoga" to Res.string.exercise_activity_yoga,
    "yoga_power" to Res.string.exercise_activity_yoga_power,
    "pilates" to Res.string.exercise_activity_pilates,
    "stretching" to Res.string.exercise_activity_stretching,
    "tai_chi" to Res.string.exercise_activity_tai_chi,
    "aerobics" to Res.string.exercise_activity_aerobics,
    "zumba" to Res.string.exercise_activity_zumba,
    "dancing" to Res.string.exercise_activity_dancing,
    "swimming" to Res.string.exercise_activity_swimming,
    "swimming_easy" to Res.string.exercise_activity_swimming_easy,
    "water_aerobics" to Res.string.exercise_activity_water_aerobics,
    "tennis" to Res.string.exercise_activity_tennis,
    "badminton" to Res.string.exercise_activity_badminton,
    "basketball" to Res.string.exercise_activity_basketball,
    "soccer" to Res.string.exercise_activity_soccer,
    "volleyball" to Res.string.exercise_activity_volleyball,
    "golf" to Res.string.exercise_activity_golf,
    "boxing_bag" to Res.string.exercise_activity_boxing_bag,
    "martial_arts" to Res.string.exercise_activity_martial_arts,
    "climbing" to Res.string.exercise_activity_climbing,
    "jump_rope" to Res.string.exercise_activity_jump_rope,
    "skiing" to Res.string.exercise_activity_skiing,
    "cross_country_skiing" to Res.string.exercise_activity_cross_country_skiing,
    "skating" to Res.string.exercise_activity_skating,
    "inline_skating" to Res.string.exercise_activity_inline_skating,
    "kayaking" to Res.string.exercise_activity_kayaking,
    "paddleboarding" to Res.string.exercise_activity_paddleboarding,
    "gardening" to Res.string.exercise_activity_gardening,
    "mowing" to Res.string.exercise_activity_mowing,
    "housework" to Res.string.exercise_activity_housework,
    "snow_shoveling" to Res.string.exercise_activity_snow_shoveling,
)

/**
 * The entry's activity label: Other's for a key this build does not know, rather than a raw key.
 * Other's own name is [ExerciseEntry.name].
 */
val ExerciseEntry.labelResource: StringResource
    get() = exerciseActivityLabel(activity) ?: Res.string.exercise_activity_other

/** Everyday activity's choice on Resting energy, with its detail line. */
val EverydayActivity.label: StringResource
    get() = when (this) {
        EverydayActivity.MostlySitting -> Res.string.everyday_activity_mostly_sitting
        EverydayActivity.SomeWalking -> Res.string.everyday_activity_some_walking
        EverydayActivity.OnMyFeet -> Res.string.everyday_activity_on_my_feet
        EverydayActivity.PhysicalWork -> Res.string.everyday_activity_physical_work
    }

val EverydayActivity.detail: StringResource
    get() = when (this) {
        EverydayActivity.MostlySitting -> Res.string.everyday_activity_mostly_sitting_detail
        EverydayActivity.SomeWalking -> Res.string.everyday_activity_some_walking_detail
        EverydayActivity.OnMyFeet -> Res.string.everyday_activity_on_my_feet_detail
        EverydayActivity.PhysicalWork -> Res.string.everyday_activity_physical_work_detail
    }
