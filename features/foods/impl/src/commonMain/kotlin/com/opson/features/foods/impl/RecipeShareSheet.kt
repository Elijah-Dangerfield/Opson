package com.opson.features.foods.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.NoteBanner
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.qr.QrCodeImage
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.recipes_copy_link
import opson.libraries.resources.generated.resources.recipes_hidden_note
import opson.libraries.resources.generated.resources.recipes_link_body
import opson.libraries.resources.generated.resources.recipes_link_title
import opson.libraries.resources.generated.resources.recipes_qr_cd
import opson.libraries.resources.generated.resources.recipes_save_qr
import opson.libraries.resources.generated.resources.recipes_share_confirm
import opson.libraries.resources.generated.resources.recipes_share_intro
import opson.libraries.resources.generated.resources.recipes_share_item_by_anon
import opson.libraries.resources.generated.resources.recipes_share_item_by_name
import opson.libraries.resources.generated.resources.recipes_share_item_ingredients
import opson.libraries.resources.generated.resources.recipes_share_item_name
import opson.libraries.resources.generated.resources.recipes_share_item_notes
import opson.libraries.resources.generated.resources.recipes_share_item_nutrition
import opson.libraries.resources.generated.resources.recipes_share_later
import opson.libraries.resources.generated.resources.recipes_share_link
import opson.libraries.resources.generated.resources.recipes_share_private
import opson.libraries.resources.generated.resources.recipes_share_removed
import opson.libraries.resources.generated.resources.recipes_share_sharing
import opson.libraries.resources.generated.resources.recipes_share_show_name
import opson.libraries.resources.generated.resources.recipes_share_show_name_sub
import opson.libraries.resources.generated.resources.recipes_share_title
import opson.libraries.resources.generated.resources.recipes_stop_sharing
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The share sheet. Before sharing: exactly what becomes public, the name switch and Share recipe.
 * Once shared: the QR code, the link, Share link, Save or send the QR code, Copy link and Stop
 * sharing. Stateless.
 */
@Composable
fun RecipeShareSheetContent(
    state: ShareSheetState,
    onAction: (RecipeShareAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        when (state) {
            ShareSheetState.Loading -> SheetHeader(title = stringResource(Res.string.recipes_share_title), onClose = onClose)
            is ShareSheetState.Consent -> Consent(state, onAction, onClose)
            is ShareSheetState.Shared -> Shared(state, onAction, onClose)
            ShareSheetState.Removed -> {
                SheetHeader(title = stringResource(Res.string.recipes_link_title), onClose = onClose)
                NoteBanner(stringResource(Res.string.recipes_share_removed))
            }
        }
    }
}

@Composable
private fun Consent(state: ShareSheetState.Consent, onAction: (RecipeShareAction) -> Unit, onClose: () -> Unit) {
    SheetHeader(title = stringResource(Res.string.recipes_share_title), overline = state.name, onClose = onClose)
    Text(stringResource(Res.string.recipes_share_intro), typography = AppTheme.type.body, color = AppTheme.colors.ink)
    EdgeCard(edgeToEdge = false, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PublicItem(stringResource(Res.string.recipes_share_item_name, state.name))
        PublicItem(pluralStringResource(Res.plurals.recipes_share_item_ingredients, state.ingredientCount, state.ingredientCount))
        PublicItem(stringResource(Res.string.recipes_share_item_nutrition))
        if (state.hasNotes) PublicItem(stringResource(Res.string.recipes_share_item_notes))
        PublicItem(
            if (state.showName && state.displayName != null) {
                stringResource(Res.string.recipes_share_item_by_name, state.displayName)
            } else {
                stringResource(Res.string.recipes_share_item_by_anon)
            },
        )
    }
    if (state.displayName != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.recipes_share_show_name), typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
                Text(
                    stringResource(Res.string.recipes_share_show_name_sub, state.displayName),
                    typography = AppTheme.type.secondarySmall,
                    color = AppTheme.colors.muted,
                )
            }
            AppSwitch(checked = state.showName, onCheckedChange = { onAction(RecipeShareAction.ShowNameToggled(it)) })
        }
    }
    Text(stringResource(Res.string.recipes_share_private), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
    Text(stringResource(Res.string.recipes_share_later), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
    state.problem?.let { NoteBanner(stringResource(it)) }
    ButtonPrimary(onClick = { onAction(RecipeShareAction.ShareTapped) }, enabled = state.canShare, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (state.sharing) Res.string.recipes_share_sharing else Res.string.recipes_share_confirm))
    }
}

/** One thing that becomes public, with a dot before it. */
@Composable
private fun PublicItem(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon = AppIcons.Public, size = 18.dp, color = AppTheme.colors.muted)
        Text(text = text, typography = AppTheme.type.body, color = AppTheme.colors.ink)
    }
}

@Composable
private fun Shared(state: ShareSheetState.Shared, onAction: (RecipeShareAction) -> Unit, onClose: () -> Unit) {
    SheetHeader(title = stringResource(Res.string.recipes_link_title), overline = state.name, onClose = onClose)
    if (state.hidden) NoteBanner(stringResource(Res.string.recipes_hidden_note))
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        QrCodeImage(
            code = state.qr,
            contentDescription = stringResource(Res.string.recipes_qr_cd, state.name),
            modifier = Modifier.widthIn(max = QR_SIZE),
        )
    }
    Text(text = state.link, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, modifier = Modifier.fillMaxWidth())
    Text(stringResource(Res.string.recipes_link_body), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
    state.problem?.let { NoteBanner(stringResource(it)) }
    ButtonPrimary(onClick = { onAction(RecipeShareAction.ShareLinkTapped) }, icon = AppIcons.Share, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.recipes_share_link))
    }
    ButtonSecondary(onClick = { onAction(RecipeShareAction.SaveQrTapped) }, icon = AppIcons.Download, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.recipes_save_qr))
    }
    ButtonSecondary(onClick = { onAction(RecipeShareAction.CopyLinkTapped) }, icon = AppIcons.Link, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.recipes_copy_link))
    }
    ButtonGhost(onClick = { onAction(RecipeShareAction.StopTapped) }, enabled = !state.stopping, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.recipes_stop_sharing))
    }
}

private val QR_SIZE = 240.dp
