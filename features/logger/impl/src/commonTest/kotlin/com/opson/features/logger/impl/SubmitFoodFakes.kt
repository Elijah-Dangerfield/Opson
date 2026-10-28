package com.opson.features.logger.impl

import com.opson.features.logger.SubmitEntry
import com.opson.libraries.foods.BarcodeLinkReporter
import com.opson.libraries.foods.FoodLabels
import com.opson.libraries.foods.FoodSubmission
import com.opson.libraries.foods.LabelParse
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.foods.SubmissionOrigin
import com.opson.libraries.foods.SubmitResult
import com.opson.libraries.foods.SubmittedFoods
import kotlinx.coroutines.flow.MutableStateFlow

/** Answers [parse] and [submit] in turn, and remembers every submission. */
class FakeFoodLabels(
    var parse: LabelParse = SubmitFoodFixtures.read,
    var submit: SubmitResult = SubmitResult.Submitted("submission-1", "food-1"),
) : FoodLabels {
    val parsed = mutableListOf<Pair<ByteArray, ByteArray>>()
    val submitted = mutableListOf<FoodSubmission>()

    override suspend fun parse(front: ByteArray, label: ByteArray): LabelParse {
        parsed += front to label
        return parse
    }

    override suspend fun submit(submission: FoodSubmission): SubmitResult {
        submitted += submission
        return submit
    }
}

class RecordingSubmitFoodTelemetry : SubmitFoodTelemetry {
    val events = mutableListOf<String>()

    override fun started(origin: SubmissionOrigin, entry: SubmitEntry) {
        events += "started:${origin.wire}:${entry.wire}"
    }

    override fun photoTaken(side: PackSide, fromCamera: Boolean) {
        events += "photo:${side.name.lowercase()}:${if (fromCamera) "camera" else "library"}"
    }

    override fun read(outcome: ReadOutcome, checks: Int, unsure: Int) {
        events += "read:${outcome.wire}:$checks:$unsure"
    }

    override fun submitted(origin: SubmissionOrigin, entry: SubmitEntry, edited: Boolean, kept: Int, numbersShown: Boolean, sent: Boolean) {
        events += "submitted:${origin.wire}:${entry.wire}:edited=$edited:kept=$kept:shown=$numbersShown:sent=$sent"
    }

    override fun abandoned(stage: String) {
        events += "abandoned:$stage"
    }
}

/** [SubmittedFoods] in memory: what [sent] was told, and the marks a screen reads. */
class FakeSubmittedFoods(initial: Map<String, ReviewStatus> = emptyMap()) : SubmittedFoods {
    override val reviews = MutableStateFlow(initial)
    val sent = mutableListOf<Triple<String, String, String>>()

    override suspend fun sent(customFoodId: String, submissionId: String, foodId: String) {
        sent += Triple(customFoodId, submissionId, foodId)
        reviews.value = reviews.value + (customFoodId to ReviewStatus.Pending)
    }
}

/** Records misses and logged picks instead of pairing them. */
class RecordingPairing : MissedBarcodePairing {
    val misses = mutableListOf<String>()
    val logged = mutableListOf<List<LogPick>>()

    override fun missed(code: String) {
        misses += code
    }

    override suspend fun logged(picks: List<LogPick>) {
        logged += picks
    }
}

class FakeBarcodeLinkReporter : BarcodeLinkReporter {
    val reports = mutableListOf<Pair<String, String>>()

    override suspend fun report(barcode: String, foodId: String) {
        reports += barcode to foodId
    }
}
