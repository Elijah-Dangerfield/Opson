@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.settings.impl.support

import androidx.lifecycle.viewModelScope
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.flowroutines.SEAViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import opson.libraries.resources.generated.resources.Res

/**
 * The open-source list, written by `./gradlew :apps:compose:exportLibraryDefinitions` from the
 * shipped builds' dependencies and committed, and the typeface's licence, which sits beside the
 * font files.
 */
internal const val LICENSES_FILE = "files/licenses.json"
internal const val FONT_LICENSE_FILE = "font/atkinson_hyperlegible_next_OFL.txt"

internal const val USDA_URL = "https://fdc.nal.usda.gov/"
internal const val NEVO_URL = "https://www.rivm.nl/en/dutch-food-composition-database"
internal const val ODBL_URL = "https://opendatacommons.org/licenses/odbl/1-0/"
internal const val OFL_URL = "https://openfontlicense.org/"

/**
 * Credits and licenses: the food data and the typeface, then every open-source library with
 * the full text of its licence. Apache 2.0 asks for its text to travel with the binary, which
 * a web page alone doesn't do.
 *
 * @property libraries sorted by name; empty until the list loads.
 * @property licenses each licence once, the typeface's first. One without [CreditedLicense.text]
 *   opens its [CreditedLicense.url] instead.
 * @property openLicense the licence whose text is showing.
 *
 * These types are hidden from the iOS framework's header (`@HiddenFromObjC`), as Swift never
 * uses them. Exported, they broke the Xcode build on an unrelated line: `'swift_name' and
 * 'swift_name' attributes are not compatible` on `ImplHealthKitReader.hasAccess`, where SKIE's
 * API notes rename the method. Why these new declarations set that off wasn't pinned down: the
 * header and API notes for that class were identical either way.
 */
@HiddenFromObjC
data class CreditsState(
    val libraries: List<CreditedLibrary> = emptyList(),
    val licenses: List<CreditedLicense> = emptyList(),
    val openLicense: String? = null,
)

@HiddenFromObjC
data class CreditedLibrary(val id: String, val name: String, val version: String?, val licenses: List<String>)

@HiddenFromObjC
data class CreditedLicense(val id: String, val name: String, val text: String?, val url: String?)

internal object CreditsStateBuilder {

    private val json = Json { ignoreUnknownKeys = true }

    fun build(licensesJson: String, fontLicense: String): CreditsState {
        val file = json.decodeFromString<LicensesFile>(licensesJson)
        val names = file.licenses.mapValues { (id, license) -> license.name ?: id }
        val font = CreditedLicense(id = FONT_LICENSE_ID, name = FONT_LICENSE_NAME, text = fontLicense.trim(), url = OFL_URL)
        return CreditsState(
            libraries = file.libraries
                .map { CreditedLibrary(it.uniqueId, it.name ?: it.uniqueId, it.artifactVersion, it.licenses.map { id -> names[id] ?: id }) }
                .sortedBy { it.name.lowercase() },
            licenses = listOf(font) + file.licenses
                .map { (id, license) -> CreditedLicense(id, names.getValue(id), license.content?.trim()?.ifEmpty { null }, license.url) }
                .sortedBy { it.name.lowercase() },
        )
    }

    @Serializable
    private class LicensesFile(
        val libraries: List<LibraryJson> = emptyList(),
        val licenses: Map<String, LicenseJson> = emptyMap(),
    )

    @Serializable
    private class LibraryJson(
        val uniqueId: String,
        val name: String? = null,
        val artifactVersion: String? = null,
        val licenses: List<String> = emptyList(),
    )

    @Serializable
    private class LicenseJson(
        val name: String? = null,
        val url: String? = null,
        val content: String? = null,
    )

    const val FONT_LICENSE_ID = "OFL-1.1"
    private const val FONT_LICENSE_NAME = "SIL Open Font License 1.1"
}

@Inject
@HiddenFromObjC
class CreditsViewModel : SEAViewModel<CreditsState, CreditsEvent, CreditsAction>(initialStateArg = CreditsState()) {

    init {
        viewModelScope.launch {
            Catching {
                val licenses = Res.readBytes(LICENSES_FILE).decodeToString()
                val font = Res.readBytes(FONT_LICENSE_FILE).decodeToString()
                withContext(Dispatchers.Default) { CreditsStateBuilder.build(licenses, font) }
            }
                // The file ships inside the app, so failing to read it is a packaging bug.
                .logOnFailure("Credits: the licence list didn't load")
                .onSuccess { takeAction(CreditsAction.Loaded(it)) }
        }
    }

    override suspend fun handleAction(action: CreditsAction) {
        when (action) {
            is CreditsAction.Loaded -> action.updateState { action.credits.copy(openLicense = it.openLicense) }
            is CreditsAction.LicenseTapped -> {
                val license = state.licenses.firstOrNull { it.id == action.id } ?: return
                when {
                    license.text != null -> action.updateState {
                        it.copy(openLicense = if (it.openLicense == license.id) null else license.id)
                    }
                    license.url != null -> sendEvent(CreditsEvent.OpenLink(license.url))
                }
            }
            is CreditsAction.LinkTapped -> sendEvent(CreditsEvent.OpenLink(action.url))
            CreditsAction.BackTapped -> sendEvent(CreditsEvent.Close)
        }
    }
}

@HiddenFromObjC
sealed interface CreditsEvent {
    data class OpenLink(val url: String) : CreditsEvent
    data object Close : CreditsEvent
}

@HiddenFromObjC
sealed interface CreditsAction {
    data class Loaded(val credits: CreditsState) : CreditsAction
    data class LicenseTapped(val id: String) : CreditsAction
    data class LinkTapped(val url: String) : CreditsAction
    data object BackTapped : CreditsAction
}
