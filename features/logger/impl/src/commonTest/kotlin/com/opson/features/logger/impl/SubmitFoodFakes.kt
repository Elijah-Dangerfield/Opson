package com.opson.features.logger.impl

import com.opson.libraries.foods.BarcodeLinkReporter
import com.opson.libraries.foods.FoodLabels
import com.opson.libraries.foods.FoodSubmission
import com.opson.libraries.foods.LabelParse
import com.opson.libraries.foods.SubmissionOrigin
import com.opson.libraries.foods.SubmitResult

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

    override fun started(origin: SubmissionOrigin) {
        events += "started:${origin.wire}"
    }

    override fun photoTaken(side: PackSide, fromCamera: Boolean) {
        events += "photo:${side.name.lowercase()}:${if (fromCamera) "camera" else "library"}"
    }

    override fun read(outcome: ReadOutcome, checks: Int, unsure: Int) {
        events += "read:${outcome.wire}:$checks:$unsure"
    }

    override fun submitted(origin: SubmissionOrigin, edited: Boolean, kept: Int, numbersShown: Boolean, sent: Boolean) {
        events += "submitted:${origin.wire}:edited=$edited:kept=$kept:shown=$numbersShown:sent=$sent"
    }

    override fun abandoned(stage: String) {
        events += "abandoned:$stage"
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
