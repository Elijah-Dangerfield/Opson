package com.opson.apps.screenshots

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What one preview's render produced, written as `<id>.json` next to the other results.
 *
 * scripts/design_sync.main.kts merges these into the exported manifest, so the field names are a
 * contract with that script. `file` is null exactly when the preview failed to render.
 */
internal data class ScreenResult(
    val id: String,
    val file: String?,
    val module: String,
    val preview: String,
    val previewName: String?,
    val failure: String?,
    val changed: String?,
) {
    fun toJson(): String = json.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        buildJsonObject {
            put("id", id)
            put("file", file)
            put("module", module)
            put("preview", preview)
            put("previewName", previewName)
            put("failure", failure)
            put("changed", changed)
        },
    )

    private companion object {
        val json = Json { prettyPrint = true }
    }
}
