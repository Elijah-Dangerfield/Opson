package com.opson.libraries.foods.impl

import com.opson.libraries.foods.LabelModelResult
import com.opson.libraries.foods.LabelTextLine
import com.opson.libraries.foods.LabelTextModel
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/**
 * No on-device model on Android yet. Gemini Nano through ML Kit's GenAI Prompt API is the
 * candidate (docs/research/on-device-label-model.md); until then the parser reads alone.
 */
@ContributesBinding(AppScope::class)
@Inject
class NoLabelTextModel : LabelTextModel {
    override suspend fun extract(lines: List<LabelTextLine>): LabelModelResult = LabelModelResult.Unavailable
}
