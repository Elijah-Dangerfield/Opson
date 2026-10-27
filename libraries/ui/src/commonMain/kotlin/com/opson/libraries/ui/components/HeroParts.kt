package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/*
 * The hero-coloured cards at the top of Today and on Day close (design v3, 2.1 to 2.8 and 4.2):
 * the summary, the locked card, the big-day reset. They sit inside the
 * page padding with all four corners rounded. White content; secondary text at 72 to 78%.
 */

/** A hero-coloured card: radius 22 (`AppTheme.shapes.card`), padding 20, white content. */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.card)
            .background(AppTheme.colors.hero)
            .padding(HeroCardPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** The padding inside a [HeroCard]. */
val HeroCardPadding: Dp = 20.dp

/** A secondary line on the hero: "Dinner is usually around 7:00." */
@Composable
fun HeroBody(text: String, modifier: Modifier = Modifier) {
    Text(text = text, typography = AppTheme.type.body, color = AppTheme.colors.onHero.withAlpha(HERO_BODY_ALPHA), modifier = modifier)
}

/** A bold white link with a chevron at the end of a [HeroCard]: "Nutrients ›". At least 48 tall. */
@Composable
fun HeroLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = MinTouchTarget)
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.96f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = text, typography = AppTheme.type.bodyBold.ExtraBold, color = AppTheme.colors.onHero)
        Icon(icon = AppIcons.ChevronRight, size = 22.dp, color = AppTheme.colors.onHero)
    }
}

/**
 * The card for delayed numbers: a lock-clock tile, [title] ("Totals open at 9:00 pm") and
 * [body]. Says when numbers open, never what they are.
 */
@Composable
fun LockedHeroCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: IconResource = AppIcons.LockClock,
) {
    HeroCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppTheme.colors.onHero.withAlpha(LOCK_TILE_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon = icon, size = 28.dp, color = AppTheme.colors.onHero)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, typography = AppTheme.type.heroTitle, color = AppTheme.colors.onHero)
                HeroBody(body)
            }
        }
    }
}

/** A quiet panel inside a [HeroCard] on the white wash: the committed big-day line, the commitment buttons. */
@Composable
fun HeroWashPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(AppTheme.shapes.inputLarge)
            .background(AppTheme.colors.onHero.withAlpha(LOCK_TILE_ALPHA))
            .padding(horizontal = HeroCardPadding, vertical = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

/** A button on the hero wash, centred white text, at least 56 tall. Both commitment buttons. */
@Composable
fun HeroWashButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .clip(AppTheme.shapes.inputLarge)
            .background(AppTheme.colors.onHero.withAlpha(LOCK_TILE_ALPHA))
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.97f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            typography = AppTheme.type.bodyBold.ExtraBold,
            color = AppTheme.colors.onHero,
            textAlign = TextAlign.Center,
        )
    }
}

private const val HERO_BODY_ALPHA = 0.78f
private const val LOCK_TILE_ALPHA = 0.12f

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun HeroPartsPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
            LockedHeroCard(title = "Totals open at 9:00 pm", body = "Log as you go. There's nothing to check until then.")
            HeroCard(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
                HeroBody("Dinner is usually around 7:00.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroWashButton("Back to normal tomorrow", onClick = {}, modifier = Modifier.weight(1f))
                    HeroWashButton("I can do this", onClick = {}, modifier = Modifier.weight(1f))
                }
                HeroLink("Nutrients", onClick = {}, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}
