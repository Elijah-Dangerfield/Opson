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
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/*
 * The hero-coloured cards at the top of Today and on Day close (design v3, 2.1 to 2.8 and 4.2):
 * the summary, the locked card, the meal count, new foods, the big-day reset. They sit inside the
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

/** The small label over a hero figure: "Eaten", "Your range", "So far today". */
@Composable
fun HeroLabel(text: String, modifier: Modifier = Modifier) {
    Text(text = text, typography = AppTheme.type.secondaryStrong.Bold, color = AppTheme.colors.onHero.withAlpha(HERO_LABEL_ALPHA), modifier = modifier)
}

/** A secondary line on the hero: "Dinner is usually around 7:00." */
@Composable
fun HeroBody(text: String, modifier: Modifier = Modifier) {
    Text(text = text, typography = AppTheme.type.body, color = AppTheme.colors.onHero.withAlpha(HERO_BODY_ALPHA), modifier = modifier)
}

/** The line between sections of a [HeroCard]. */
@Composable
fun HeroDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(vertical = AppTheme.spacing.section), color = AppTheme.colors.onHero.withAlpha(HERO_LINE_ALPHA))
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

/** The v3 minimum touch target. */
val MinTouchTarget: Dp = 48.dp

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

/** A label, a big line and an optional body on the hero: Blind's "So far today / Three meals". */
@Composable
fun HeroStatementCard(
    label: String,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
) {
    HeroCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HeroLabel(label)
        Text(text = title, typography = AppTheme.type.subH1, color = AppTheme.colors.onHero)
        if (body != null) HeroBody(body)
    }
}

/**
 * A titled list on the hero (Explorer's "New this month" with "3 foods"): each row a bold name
 * and a muted note on the right, lines between.
 */
@Composable
fun HeroListCard(
    title: String,
    trailing: String,
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    emptyText: String? = null,
) {
    HeroCard(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, typography = AppTheme.type.sheetTitle, color = AppTheme.colors.onHero, modifier = Modifier.weight(1f))
            Text(text = trailing, typography = AppTheme.type.secondaryStrong.Bold, color = AppTheme.colors.onHero.withAlpha(HERO_LABEL_ALPHA))
        }
        if (rows.isEmpty() && emptyText != null) {
            HeroDivider()
            HeroBody(emptyText)
        }
        rows.forEach { (name, note) ->
            HeroDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = name, typography = AppTheme.type.bodyBold, color = AppTheme.colors.onHero, modifier = Modifier.weight(1f))
                Text(text = note, typography = AppTheme.type.body, color = AppTheme.colors.onHero.withAlpha(HERO_BODY_ALPHA))
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

internal const val HERO_LABEL_ALPHA = 0.72f
internal const val HERO_BODY_ALPHA = 0.78f
private const val HERO_LINE_ALPHA = 0.12f
private const val LOCK_TILE_ALPHA = 0.12f

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun HeroPartsPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
            LockedHeroCard(title = "Totals open at 9:00 pm", body = "Log as you go. There's nothing to check until then.")
            HeroStatementCard(label = "So far today", title = "Three meals", body = "Dinner is usually around 7:00.")
            HeroListCard(title = "New this month", trailing = "2 foods", rows = listOf("Chicken kabsa" to "Tue", "Poutine" to "Sat"))
            HeroCard {
                HeroLabel("Eaten")
                HeroDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroWashButton("Back to normal tomorrow", onClick = {}, modifier = Modifier.weight(1f))
                    HeroWashButton("I can do this", onClick = {}, modifier = Modifier.weight(1f))
                }
                HeroLink("Nutrients", onClick = {}, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}
