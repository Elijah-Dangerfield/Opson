package com.opson.features.logger.impl

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.foods.CarbBasis
import com.opson.libraries.foods.LabelChecks
import com.opson.libraries.foods.LabelFacts
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.LabelParse
import com.opson.libraries.foods.ServingUnit
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.scanner.MealPhoto

/**
 * "Add this food" for a granola bar whose label reads 280 kcal on macros that
 * make 234 (4 x 10 + 4 x 35 + 9 x 6), with a protein figure the model was unsure of. For previews,
 * tests and `CaloriesHiddenLeakTest`; every state is the real builder over the
 * real form rules.
 */
object SubmitFoodFixtures {
    const val BARCODE = "012345678905"

    /** Not a decodable JPEG: thumbnails draw their placeholder, as before a photo loads. */
    val front = MealPhoto(byteArrayOf(1, 2, 3))
    val label = MealPhoto(byteArrayOf(4, 5, 6))

    val facts = LabelFacts(
        name = "Oats & honey granola bar",
        brand = "Hillside",
        servingSize = 60.0,
        servingUnit = ServingUnit.Gram,
        servingLabel = "1 bar",
        servingsPerContainer = 6.0,
        basis = CarbBasis.Us,
        perServing = mapOf(
            LabelNutrient.Energy to 280.0,
            LabelNutrient.Protein to 10.0,
            LabelNutrient.Carbs to 35.0,
            LabelNutrient.Sugars to 12.0,
            LabelNutrient.Fiber to 3.0,
            LabelNutrient.Fat to 6.0,
            LabelNutrient.SaturatedFat to 1.0,
            LabelNutrient.Sodium to 150.0,
            LabelNutrient.Calcium to 20.0,
            LabelNutrient.Iron to 1.1,
        ),
    )

    val read = LabelParse.Read(
        parseId = "parse-1",
        facts = facts,
        confidence = mapOf("name" to 0.95, "protein_g" to 0.55, "energy_kcal" to 0.9),
        checks = LabelChecks.check(facts),
        readable = true,
    )

    fun model(step: SubmitStep = SubmitStep.Photos, photos: Int = 0, barcode: String? = BARCODE): SubmitModel = SubmitModel(
        slot = MealSlot.Breakfast,
        barcodeLocked = barcode != null,
        canTakePhoto = true,
        step = step,
        front = if (photos >= 1) front else null,
        label = if (photos >= 2) label else null,
    )

    fun reviewModel(barcode: String? = BARCODE, read: LabelParse.Read? = this.read, notice: ReviewNotice? = null): SubmitModel =
        SubmitFoodForm.fromReading(model(SubmitStep.Review, photos = 2, barcode = barcode), read, barcode).copy(notice = notice)

    fun state(model: SubmitModel, policy: VisibilityPolicy): SubmitFoodState = SubmitFoodStateBuilder.build(model, policy)

    fun photosState(policy: VisibilityPolicy, photos: Int = 0): SubmitFoodState = state(model(photos = photos), policy)

    fun reviewState(policy: VisibilityPolicy): SubmitFoodState = state(reviewModel(), policy)

    /** From search: no barcode, and the energy fixed with "Use 234 kcal". */
    fun fixedState(policy: VisibilityPolicy): SubmitFoodState {
        val model = reviewModel(barcode = null)
        val check = SubmitFoodForm.checks(model).first()
        return state(SubmitFoodForm.fix(model, check), policy)
    }

    /** The server could not read it: the form is empty under its notice. */
    fun byHandState(policy: VisibilityPolicy): SubmitFoodState =
        state(reviewModel(read = null, notice = ReviewNotice.Unavailable), policy)

    fun calmState(step: SubmitStep, policy: VisibilityPolicy): SubmitFoodState = state(model(step, photos = 2), policy)

    /** From the Foods tab's Add a food: no meal, so it says Save food and logs nothing. */
    fun saveOnlyPhotosState(policy: VisibilityPolicy): SubmitFoodState = state(model(barcode = null).copy(slot = null), policy)

    fun saveOnlyReviewState(policy: VisibilityPolicy): SubmitFoodState = state(reviewModel(barcode = null).copy(slot = null), policy)

    /** The label photo picked from the library would not open: a line under the photos says so. */
    fun photoFailedState(policy: VisibilityPolicy): SubmitFoodState =
        state(model(photos = 1).copy(photoFailed = PackSide.Label), policy)
}
