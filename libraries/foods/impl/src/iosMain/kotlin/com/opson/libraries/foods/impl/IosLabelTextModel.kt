@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.foods.impl

import com.opson.libraries.foods.LabelModelBridge
import com.opson.libraries.foods.LabelModelCallback
import com.opson.libraries.foods.LabelModelResult
import com.opson.libraries.foods.LabelTextLine
import com.opson.libraries.foods.LabelTextModel
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.coroutines.resume
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration.Companion.seconds

/**
 * [LabelTextModel] through the Swift [LabelModelBridge] (Foundation Models). Only the OCR text
 * crosses, never the photo. A slow answer counts as none: the person is waiting on the form.
 */
@HiddenFromObjC
@ContributesBinding(AppScope::class)
class IosLabelTextModel @Inject constructor(
    private val bridge: LabelModelBridge,
) : LabelTextModel {

    override suspend fun extract(lines: List<LabelTextLine>): LabelModelResult {
        if (lines.isEmpty()) return LabelModelResult.Failed
        val text = lines.joinToString("\n") { it.text }
        return withTimeoutOrNull(TIMEOUT) {
            suspendCancellableCoroutine { continuation -> bridge.extract(text, Answer(continuation)) }
        } ?: LabelModelResult.Failed
    }

    private class Answer(private val continuation: CancellableContinuation<LabelModelResult>) : LabelModelCallback {
        override fun finished(json: String?, model: String?, error: String?) {
            val result = when {
                error == UNAVAILABLE -> LabelModelResult.Unavailable
                error != null || json == null -> LabelModelResult.Failed
                else -> LabelModelJson.read(json)?.let { LabelModelResult.Read(it, model ?: "unknown") } ?: LabelModelResult.Failed
            }
            if (continuation.isActive) continuation.resume(result)
        }
    }

    private companion object {
        const val UNAVAILABLE = "unavailable"
        val TIMEOUT = 15.seconds
    }
}
