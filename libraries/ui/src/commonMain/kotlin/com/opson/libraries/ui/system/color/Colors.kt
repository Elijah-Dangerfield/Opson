package com.opson.system.color

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opson.libraries.ui.system.LocalContentColor
import com.opson.libraries.ui.system.color.ColorCard
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.system.color.toHexString
import com.opson.system.Dimension
import com.opson.system.Radii
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The app's color tokens. Read them through `AppTheme.colors`, never as loose values.
 *
 * The first block is the handoff's own vocabulary (`--bg`, `--surface`, `--ink`, ...), which is
 * what new code should use. The older role names below it (`text`, `surfacePrimary`, ...) are kept
 * so existing screens keep compiling, and each one points at a handoff token.
 *
 * There is one scheme, Slate, in light and dark ([SlateLight], [SlateDark]).
 */
@Immutable
@Suppress("LongParameterList")
interface Colors {

    val isDark: Boolean

    /* Handoff base tokens */
    val bg: ColorResource
    val surface: ColorResource
    val ink: ColorResource
    /**
     * Paragraph copy between [ink] and [muted]: screen intros, onboarding leads, dialog text.
     * Design v3 sets those apart from both titles and secondary lines.
     */
    val inkSoft: ColorResource
    val muted: ColorResource
    val line: ColorResource
    /** Behind a bar or a switch that is off. Slate draws it in the line color. */
    val track: ColorResource
    /** Slate's "Raised": icon tiles, Uncategorized, round buttons on a card, the select-mode bar. */
    val soft: ColorResource
    val onAccent: ColorResource

    /* Accent and hero */
    /** A block that sets itself apart from the cards, with [onHero] text. Round 4 has none of its own. */
    val hero: ColorResource
    /** Text and icons on [hero]. White in both themes. */
    val onHero: ColorResource
    /** Secondary text on [hero], e.g. ring units and chevrons. */
    val onHeroMuted: ColorResource
    /** Ring track on [hero]. */
    val heroTrack: ColorResource
    /** Bar track on [hero]. */
    val heroBarTrack: ColorResource
    /** The 14% white wash behind round buttons on [hero]. */
    val heroWash: ColorResource
    val accent: ColorResource
    /** Bars and rings that use the accent. Same value as [accent]. */
    val fill: ColorResource
    /** The soft accent tint behind round + buttons and selected rows. */
    val accentTint: ColorResource

    /* Overlays */
    /** Behind a bottom sheet. */
    val scrim: ColorResource

    /*
     * Energy out. Cool colors on purpose, so Burned never looks like Eaten, whose bar is split by
     * the warm nutrient colors. Like those, neither one means good or bad.
     */
    /** The resting part of Burned. */
    val restingEnergy: ColorResource
    /** The activity part of Burned. */
    val activeEnergy: ColorResource

    /* Nutrients and meals */
    val nutrients: NutrientColors

    /** The on-track calendar's day fills ([PlanColors]). Nothing else means on or off plan. */
    val plan: PlanColors

    fun nutrient(tone: NutrientTone): ToneColors = when (tone) {
        NutrientTone.Protein -> nutrients.protein
        NutrientTone.Carbs -> nutrients.carbs
        NutrientTone.Fat -> nutrients.fat
        NutrientTone.Fiber -> nutrients.fiber
        NutrientTone.Water -> nutrients.water
    }

    /** Breakfast is carbs, Lunch fat, Snack fiber, Dinner protein, Uncategorized muted on soft. */
    fun meal(tone: MealTone): ToneColors = when (tone) {
        MealTone.Breakfast -> nutrients.carbs
        MealTone.Lunch -> nutrients.fat
        MealTone.Snack -> nutrients.fiber
        MealTone.Dinner -> nutrients.protein
        MealTone.Uncategorized -> ToneColors(content = muted, container = soft)
    }

    /* Legacy role names, each mapped onto a handoff token */
    val accentPrimary: ColorResource
    val onAccentPrimary: ColorResource
    val accentSecondary: ColorResource
    val onAccentSecondary: ColorResource

    val shadow: ColorResource
    val background: ColorResource
    val backgroundOverlay: ColorResource
    val onBackground: ColorResource
    val border: ColorResource

    val borderSecondary: ColorResource
    val borderDisabled: ColorResource

    val text: ColorResource
    val textSecondary: ColorResource
    val textDisabled: ColorResource
    val danger: ColorResource

    val status: StatusColor

    val surfacePrimary: ColorResource
    val onSurfacePrimary: ColorResource
    val surfaceSecondary: ColorResource
    val onSurfaceSecondary: ColorResource
    val surfaceTertiary: ColorResource
    val onSurfaceTertiary: ColorResource

    val surfaceDisabled: ColorResource
    val onSurfaceDisabled: ColorResource

}

interface StatusColor {
    val okay: ColorResource
    val warning: ColorResource
    val bad: ColorResource
}

/** Builds the full token set for light or dark. */
fun appColors(dark: Boolean = false): Colors = AppColors(dark)

private val defaultColors: Colors = appColors()

@Suppress("MagicNumber")
private class AppColors(
    override val isDark: Boolean,
) : Colors {
    private val scheme = if (isDark) SlateDark else SlateLight
    private val prefix = if (isDark) "dark" else "light"

    private fun named(color: Color, name: String) = ColorResource.FromColor(color, "$prefix-$name")

    override val bg = named(scheme.bg, "bg")
    override val surface = named(scheme.surface, "surface")
    override val ink = named(scheme.ink, "ink")
    override val inkSoft = named(scheme.inkSoft, "ink-soft")
    override val muted = named(scheme.muted, "muted")
    override val line = named(scheme.line, "line")
    override val track = named(scheme.line, "track")
    override val soft = named(scheme.raised, "raised")
    override val onAccent = named(scheme.onAccent, "on-accent")

    override val hero = named(scheme.hero, "hero")
    override val onHero = named(Color.White, "on-hero")
    override val onHeroMuted = named(Color.White.copy(alpha = 0.7f), "on-hero-muted")
    override val heroTrack = named(Color.White.copy(alpha = 0.13f), "hero-track")
    override val heroBarTrack = named(Color.White.copy(alpha = 0.14f), "hero-bar-track")
    override val heroWash = named(Color.White.copy(alpha = 0.14f), "hero-wash")
    override val accent = named(scheme.accent, "accent")
    override val fill = accent
    override val accentTint = named(scheme.accentTint, "accent-tint")

    override val scrim = named(scheme.scrim, "scrim")

    override val restingEnergy = named(scheme.restingEnergy, "resting-energy")
    override val activeEnergy = named(scheme.activeEnergy, "active-energy")

    override val nutrients = scheme.nutrients
    override val plan = scheme.plan

    override val accentPrimary = accent
    override val onAccentPrimary = onAccent
    override val accentSecondary = hero
    override val onAccentSecondary = onHero

    override val shadow = named(Color(0x241E0F28), "shadow")
    override val background = bg
    override val backgroundOverlay = scrim
    override val onBackground = ink
    override val border = line
    override val borderSecondary = line
    override val borderDisabled = line

    override val text = ink
    override val textSecondary = muted
    override val textDisabled = muted
    // Errors and destructive actions only. Never an amount: amounts have no red or green.
    override val danger = named(if (isDark) Color(0xFFF2B8B5) else Color(0xFFB3261E), "danger")

    override val status = object : StatusColor {
        override val okay = accent
        override val warning = nutrients.carbs.content
        override val bad = danger
    }

    override val surfacePrimary = surface
    override val onSurfacePrimary = ink
    override val surfaceSecondary = soft
    override val onSurfaceSecondary = ink
    override val surfaceTertiary = track
    override val onSurfaceTertiary = muted
    override val surfaceDisabled = track
    override val onSurfaceDisabled = muted
}

@Composable
private fun SectionTitle(text: String, colors: Colors) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.textSecondary.color,
        modifier = Modifier.padding(bottom = Dimension.D400)
    )
}

// Sample copy for the palette gallery, which only ever renders from the
// @Preview below and from ColorResource.kt's. None of it reaches a user, so a
// string resource for it would be a resource nothing translates. `VerifyStrings`
// exempts a literal written *inside* an @Preview function; these live one call
// deeper, in the helpers the preview composes.
@Suppress("VerifyStrings")
@Composable
private fun HeroPanel(colors: Colors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radii.Card.shape)
            .background(colors.surfacePrimary.color)
            .border(1.dp, colors.border.color, Radii.Card.shape)
            .padding(Dimension.D700)
    ) {
        Text(
            text = "Color palette",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurfacePrimary.color
        )
        Text(
            text = "Modern light theme",
            fontSize = 14.sp,
            color = colors.textSecondary.color,
            modifier = Modifier.padding(top = Dimension.D200)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimension.D600),
            horizontalArrangement = Arrangement.spacedBy(Dimension.D500)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(Radii.Card.shape)
                    .background(colors.surfaceSecondary.color)
                    .padding(Dimension.D500)
            ) {
                Text(
                    text = "Active session",
                    fontSize = 14.sp,
                    color = colors.onSurfaceSecondary.color
                )
                Text(
                    text = "42m remaining",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceSecondary.color,
                    modifier = Modifier.padding(top = Dimension.D200)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(Radii.Card.shape)
                    .background(colors.backgroundOverlay.color)
                    .padding(Dimension.D500)
            ) {
                Text(
                    text = "Status",
                    fontSize = 14.sp,
                    color = colors.onBackground.color
                )
                Text(
                    text = "All good",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accentSecondary.color,
                    modifier = Modifier.padding(top = Dimension.D200)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimension.D600),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Recent activity",
                fontSize = 14.sp,
                color = colors.textSecondary.color
            )
            Box(
                modifier = Modifier
                    .clip(Radii.Button.shape)
                    .background(colors.accentPrimary.color)
                    .padding(horizontal = Dimension.D800, vertical = Dimension.D400)
            ) {
                Text(
                    text = "View all",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onAccentPrimary.color
                )
            }
        }
    }
}

@Composable
private fun AccentPalette(colors: Colors) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionTitle("Accent stack", colors)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimension.D500)
        ) {
            AccentChip(
                label = "Primary",
                background = colors.accentPrimary,
                foreground = colors.onAccentPrimary,
                supporting = colors.accentPrimary.toHexString()
            )
            AccentChip(
                label = "Secondary",
                background = colors.accentSecondary,
                foreground = colors.onAccentSecondary,
                supporting = colors.accentSecondary.toHexString()
            )
        }
    }
}

@Composable
private fun RowScope.AccentChip(
    label: String,
    background: ColorResource,
    foreground: ColorResource,
    supporting: String
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(Radii.Card.shape)
            .background(background.color.copy(alpha = 0.15f))
            .border(1.dp, background.color, Radii.Card.shape)
            .padding(Dimension.D500)
    ) {
        Box(
            modifier = Modifier
                .clip(Radii.Button.shape)
                .background(background.color)
                .padding(horizontal = Dimension.D800, vertical = Dimension.D400)
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = foreground.color
            )
        }
        Text(
            text = background.designSystemName,
            fontSize = 12.sp,
            color = background.color,
            modifier = Modifier.padding(top = Dimension.D300)
        )
        Text(
            text = supporting,
            fontSize = 10.sp,
            color = foreground.color.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun SurfaceStack(colors: Colors) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionTitle("Surface ladder", colors)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimension.D500)
        ) {
            SurfaceCard(
                title = "Primary",
                background = colors.surfacePrimary,
                foreground = colors.onSurfacePrimary,
                border = colors.border,
                supporting = colors.surfacePrimary.toHexString()
            )
            SurfaceCard(
                title = "Secondary",
                background = colors.surfaceSecondary,
                foreground = colors.onSurfaceSecondary,
                border = colors.border,
                supporting = colors.surfaceSecondary.toHexString()
            )
            SurfaceCard(
                title = "Tertiary",
                background = colors.surfaceTertiary,
                foreground = colors.onSurfaceTertiary,
                border = colors.border,
                supporting = colors.surfaceTertiary.toHexString()
            )

            SurfaceCard(
                title = "Disabled",
                background = colors.surfaceDisabled,
                foreground = colors.onSurfaceDisabled,
                border = colors.border,
                supporting = colors.surfaceTertiary.toHexString()
            )
        }
    }
}

// Same as HeroPanel: "Card content" is filler that shows what text looks like
// on each surface, not copy.
@Suppress("VerifyStrings")
@Composable
private fun RowScope.SurfaceCard(
    title: String,
    background: ColorResource,
    foreground: ColorResource,
    border: ColorResource,
    supporting: String
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(Radii.Card.shape)
            .background(background.color)
            .border(1.dp, border.color, Radii.Card.shape)
            .padding(Dimension.D500)
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            color = foreground.color.copy(alpha = 0.9f)
        )
        Text(
            text = "Card content",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = foreground.color,
            modifier = Modifier.padding(top = Dimension.D200)
        )
        Text(
            text = supporting,
            fontSize = 10.sp,
            color = foreground.color.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = Dimension.D300)
        )
    }
}

@Composable
private fun TextHierarchy(colors: Colors) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionTitle("Typography contrast", colors)
        Column(
            modifier = Modifier
                .clip(Radii.Card.shape)
                .background(colors.background.color)
                .border(1.dp, colors.border.color, Radii.Card.shape)
                .padding(Dimension.D600),
            verticalArrangement = Arrangement.spacedBy(Dimension.D500)
        ) {
            TextSample("Primary", colors.text, colors.text)
            TextSample("Secondary", colors.textSecondary, colors.textSecondary)
            TextSample("Disabled", colors.textDisabled, colors.textDisabled)
            TextSample("Danger", colors.danger, colors.danger)
        }
    }
}

@Composable
private fun TextSample(label: String, swatch: ColorResource, hexColor: ColorResource) {
    Column {
        Text(
            text = label,
            fontSize = 14.sp,
            color = swatch.color
        )
        Text(
            text = hexColor.toHexString(),
            fontSize = 11.sp,
            color = swatch.color.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun SemanticStrip(colors: Colors) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionTitle("System states", colors)
        Row(
            modifier = Modifier
                .clip(Radii.Card.shape)
                .border(1.dp, colors.border.color, Radii.Card.shape)
                .background(colors.surfaceSecondary.color)
                .padding(Dimension.D400),
            horizontalArrangement = Arrangement.spacedBy(Dimension.D400)
        ) {
            SemanticBadge("Background", colors.background, colors.onBackground)
            SemanticBadge("Overlay", colors.backgroundOverlay, colors.onBackground)
            SemanticBadge("Shadow", colors.shadow, colors.onBackground)
            SemanticBadge("Danger", colors.danger, colors.onAccentSecondary)
        }
    }
}

@Composable
private fun RowScope.SemanticBadge(
    label: String,
    background: ColorResource,
    content: ColorResource
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(Radii.Card.shape)
            .background(background.color)
            .padding(Dimension.D400)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = content.color.copy(alpha = 0.8f)
        )
        Text(
            text = background.designSystemName,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = content.color,
            modifier = Modifier.padding(top = Dimension.D200)
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PaletteGridSection(colors: Colors) {
    val palette = listOf(
        colors.background,
        colors.backgroundOverlay,
        colors.onBackground,
        colors.surfacePrimary,
        colors.onSurfacePrimary,
        colors.surfaceSecondary,
        colors.onSurfaceSecondary,
        colors.surfaceTertiary,
        colors.onSurfaceTertiary,
        colors.surfaceDisabled,
        colors.onSurfaceDisabled,
        colors.accentPrimary,
        colors.onAccentPrimary,
        colors.accentSecondary,
        colors.onAccentSecondary,
        colors.text,
        colors.textSecondary,
        colors.textDisabled,
        colors.danger,
        colors.border,
        colors.borderDisabled,
        colors.shadow
    ).distinctBy { it.designSystemName }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionTitle("Palette grid", colors)
        Box(
            modifier = Modifier
                .clip(Radii.Card.shape)
                .background(colors.surfaceSecondary.color)
                .border(1.dp, colors.border.color, Radii.Card.shape)
                .padding(Dimension.D300)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
                verticalArrangement = Arrangement.spacedBy(Dimension.D300)
            ) {
                palette.forEach { swatch ->
                    ColorCard(
                        colorResource = swatch,
                        title = swatch.designSystemName,
                        description = swatch.toHexString()
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewColorSwatch(colors: Colors) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background.color)
            .padding(horizontal = Dimension.D800, vertical = Dimension.D600),
        verticalArrangement = Arrangement.spacedBy(Dimension.D700)
    ) {
        item { HeroPanel(colors) }
        item { AccentPalette(colors) }
        item { SurfaceStack(colors) }
        item { TextHierarchy(colors) }
        item { SemanticStrip(colors) }
        item { PaletteGridSection(colors) }
    }
}

@Preview(widthDp = 600, heightDp = 2000)
@Composable
private fun PreviewDefaultColors() {
    PreviewColorSwatch(defaultColors)
}

@Composable
fun ProvideContentColor(color: ColorResource, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides color,
        androidx.compose.material3.LocalContentColor provides color.color,
        content = content
    )
}