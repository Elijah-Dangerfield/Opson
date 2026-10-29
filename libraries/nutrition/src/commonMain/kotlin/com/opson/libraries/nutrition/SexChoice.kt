package com.opson.libraries.nutrition

/**
 * Sex for the energy estimate as onboarding and Profile list it: the equation's [sex] and, for
 * pregnant and breastfeeding, the [stage] whose energy is added. Both of those use the female
 * constant, as the equation has no other; "Rather not say" takes the midpoint.
 */
enum class SexChoice(val sex: BodySex, val stage: BodyStage) {
    Female(BodySex.Female, BodyStage.None),
    Male(BodySex.Male, BodyStage.None),
    Pregnant(BodySex.Female, BodyStage.Pregnant),
    Breastfeeding(BodySex.Female, BodyStage.Breastfeeding),
    Unsaid(BodySex.Unsaid, BodyStage.None),
    ;

    companion object {
        /** What settings hold, as the picker shows it. Null until sex was given. */
        fun of(sex: BodySex?, stage: BodyStage): SexChoice? = when {
            stage == BodyStage.Pregnant -> Pregnant
            stage == BodyStage.Breastfeeding -> Breastfeeding
            else -> when (sex) {
                BodySex.Female -> Female
                BodySex.Male -> Male
                BodySex.Unsaid -> Unsaid
                null -> null
            }
        }
    }
}
