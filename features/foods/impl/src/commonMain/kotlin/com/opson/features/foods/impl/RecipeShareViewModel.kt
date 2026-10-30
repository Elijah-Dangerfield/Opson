package com.opson.features.foods.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.foods.RecipeShareRoute
import com.opson.libraries.core.Catching
import com.opson.libraries.diary.Recipe
import com.opson.libraries.diary.RecipeLinks
import com.opson.libraries.diary.RecipeRepository
import com.opson.libraries.diary.RecipeShareState
import com.opson.libraries.diary.RecipeSharing
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.PublicRecipes
import com.opson.libraries.foods.PublishResult
import com.opson.libraries.foods.RecipeActionResult
import com.opson.libraries.identity.profile.ProfileRepository
import com.opson.libraries.identity.profile.Profile
import com.opson.libraries.ui.qr.QrCode
import com.opson.libraries.ui.qr.QrPng
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.recipes_link_copied
import opson.libraries.resources.generated.resources.recipes_share_empty
import opson.libraries.resources.generated.resources.recipes_share_failed
import opson.libraries.resources.generated.resources.recipes_share_offline
import opson.libraries.resources.generated.resources.recipes_share_removed
import opson.libraries.resources.generated.resources.recipes_share_restricted
import opson.libraries.resources.generated.resources.recipes_share_too_many
import opson.libraries.resources.generated.resources.recipes_stop_failed
import opson.libraries.resources.generated.resources.recipes_stopped
import org.jetbrains.compose.resources.StringResource

/** Sharing a recipe: what becomes public first, then its link and QR code. */
sealed interface ShareSheetState {
    data object Loading : ShareSheetState

    /**
     * Before anything is public: exactly what will be. [displayName] is null when the person has
     * no name of their own to show (none, or the one the server made up), and then the switch is
     * not offered.
     */
    data class Consent(
        val name: String,
        val ingredientCount: Int,
        val hasNotes: Boolean,
        val displayName: String?,
        val showName: Boolean = false,
        val sharing: Boolean = false,
        val problem: StringResource? = null,
    ) : ShareSheetState {
        val canShare: Boolean get() = ingredientCount > 0 && !sharing
    }

    /** Shared: [link] and its [qr]. [hidden] while reports on it wait for someone at Opson. */
    data class Shared(
        val name: String,
        val link: String,
        val qr: QrCode,
        val hidden: Boolean = false,
        val stopping: Boolean = false,
        val problem: StringResource? = null,
    ) : ShareSheetState

    /** Opson took it down; nothing to share. */
    data object Removed : ShareSheetState
}

sealed interface RecipeShareAction {
    data class Loaded(val recipe: Recipe?, val displayName: String?) : RecipeShareAction
    data class ShowNameToggled(val on: Boolean) : RecipeShareAction
    data object ShareTapped : RecipeShareAction
    data object CopyLinkTapped : RecipeShareAction
    data object ShareLinkTapped : RecipeShareAction
    data object SaveQrTapped : RecipeShareAction
    data object StopTapped : RecipeShareAction
}

sealed interface RecipeShareEvent {
    data class CopyLink(val link: String) : RecipeShareEvent
    data class ShareLink(val link: String) : RecipeShareEvent
    class ShareQr(val fileName: String, val png: ByteArray) : RecipeShareEvent
    data class ShowToast(val toast: Toast) : RecipeShareEvent
    data object Close : RecipeShareEvent
}

/**
 * Sharing is online and asked for: nothing is public until the person reads what will be and taps
 * Share recipe, and the server's answer, not the app, says it is.
 */
@Inject
class RecipeShareViewModel(
    @Assisted private val route: RecipeShareRoute,
    private val recipes: RecipeRepository,
    private val publicRecipes: PublicRecipes,
    private val profiles: ProfileRepository,
) : SEAViewModel<ShareSheetState, RecipeShareEvent, RecipeShareAction>(initialStateArg = ShareSheetState.Loading) {

    private var recipe: Recipe? = null

    init {
        viewModelScope.launch {
            val recipe = recipes.get(route.recipeId)
            // The server shows the account's own name, so only a signed-in profile's counts.
            val name = Catching { (profiles.current() as? Profile.Authenticated)?.displayName }.getOrNull()
                ?.takeUnless { it.isBlank() || GENERATED_NAME.matches(it) }
            takeAction(RecipeShareAction.Loaded(recipe, name))
        }
    }

    override suspend fun handleAction(action: RecipeShareAction) {
        when (action) {
            is RecipeShareAction.Loaded -> {
                val current = action.recipe ?: return sendEvent(RecipeShareEvent.Close)
                recipe = current
                action.updateState { stateFor(current, action.displayName) }
            }
            is RecipeShareAction.ShowNameToggled -> action.updateState { (it as? ShareSheetState.Consent)?.copy(showName = action.on) ?: it }
            RecipeShareAction.ShareTapped -> share(action)
            RecipeShareAction.CopyLinkTapped -> shared()?.let {
                sendEvent(RecipeShareEvent.CopyLink(it.link))
                sendEvent(RecipeShareEvent.ShowToast(Toast(Res.string.recipes_link_copied)))
            }
            RecipeShareAction.ShareLinkTapped -> shared()?.let { sendEvent(RecipeShareEvent.ShareLink(it.link)) }
            RecipeShareAction.SaveQrTapped -> shared()?.let { sendEvent(RecipeShareEvent.ShareQr(fileName(it.name), QrPng.encode(it.qr))) }
            RecipeShareAction.StopTapped -> stop(action)
        }
    }

    private suspend fun share(action: RecipeShareAction) {
        val consent = state as? ShareSheetState.Consent ?: return
        val current = recipe ?: return
        if (!consent.canShare) {
            if (consent.ingredientCount == 0) action.updateState { consent.copy(problem = Res.string.recipes_share_empty) }
            return
        }
        action.updateState { consent.copy(sharing = true, problem = null) }
        val showName = consent.showName && consent.displayName != null
        when (val result = publicRecipes.publish(current, showName)) {
            is PublishResult.Published -> {
                recipes.markShared(current.id, result.sharing)
                recipe = current.copy(sharing = result.sharing)
                action.updateState { sharedState(current.name, result.sharing) }
            }
            PublishResult.Removed -> {
                recipes.markShared(current.id, current.sharing.copy(state = RecipeShareState.Removed))
                action.updateState { consent.copy(sharing = false, problem = Res.string.recipes_share_removed) }
            }
            PublishResult.Restricted -> action.updateState { consent.copy(sharing = false, problem = Res.string.recipes_share_restricted) }
            PublishResult.TooMany -> action.updateState { consent.copy(sharing = false, problem = Res.string.recipes_share_too_many) }
            is PublishResult.Failed -> action.updateState {
                consent.copy(sharing = false, problem = if (result.offline) Res.string.recipes_share_offline else Res.string.recipes_share_failed)
            }
        }
    }

    private suspend fun stop(action: RecipeShareAction) {
        val shared = shared() ?: return
        val current = recipe ?: return
        if (shared.stopping) return
        action.updateState { shared.copy(stopping = true, problem = null) }
        when (publicRecipes.unpublish(current.id)) {
            // NotFound: the server never had it shared, or no longer has the recipe. Either way it's not shared.
            RecipeActionResult.Done, RecipeActionResult.NotFound -> {
                recipes.markShared(current.id, current.sharing.copy(state = RecipeShareState.Private))
                sendEvent(RecipeShareEvent.ShowToast(Toast(Res.string.recipes_stopped)))
                sendEvent(RecipeShareEvent.Close)
            }
            else -> action.updateState { shared.copy(stopping = false, problem = Res.string.recipes_stop_failed) }
        }
    }

    private fun shared(): ShareSheetState.Shared? = state as? ShareSheetState.Shared

    private fun stateFor(recipe: Recipe, displayName: String?): ShareSheetState {
        val sharing = recipe.sharing
        return when {
            sharing.state == RecipeShareState.Removed -> ShareSheetState.Removed
            sharing.code != null && (sharing.state == RecipeShareState.Live || sharing.state == RecipeShareState.Hidden) ->
                sharedState(recipe.name, sharing)
            else -> ShareSheetState.Consent(
                name = recipe.name,
                ingredientCount = recipe.ingredients.size,
                hasNotes = !recipe.notes.isNullOrBlank(),
                displayName = displayName,
                showName = sharing.showName && displayName != null,
            )
        }
    }

    private fun sharedState(name: String, sharing: RecipeSharing): ShareSheetState {
        val code = sharing.code ?: return ShareSheetState.Removed
        val link = RecipeLinks.web(code)
        return ShareSheetState.Shared(name = name, link = link, qr = QrCode.encode(link), hidden = sharing.state == RecipeShareState.Hidden)
    }

    internal companion object {
        /** The name the server gives an account that never chose one: nothing to show. */
        val GENERATED_NAME = Regex("user-[0-9a-f]{8}")

        /** "opson-weeknight-chili.png": the recipe's name, kept to what a file name can hold. */
        fun fileName(recipeName: String): String {
            val slug = recipeName.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
                .split('-').filter { it.isNotEmpty() }.joinToString("-").take(MAX_SLUG)
            return if (slug.isEmpty()) "opson-recipe.png" else "opson-$slug.png"
        }

        private const val MAX_SLUG = 40
    }
}
