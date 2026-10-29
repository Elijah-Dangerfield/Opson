package com.opson.features.medication.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.medication.InjectionSite
import com.opson.libraries.medication.label
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.medication_body_your_left
import opson.libraries.resources.generated.resources.medication_body_your_right
import opson.libraries.resources.generated.resources.medication_site_off_cd
import opson.libraries.resources.generated.resources.medication_site_on_cd
import opson.libraries.resources.generated.resources.medication_site_picked_cd
import org.jetbrains.compose.resources.stringResource

/** How a site is drawn on the body map. */
enum class SiteMark {
    /** Settings: a site the person uses. */
    On,

    /** Settings: a site they don't. */
    Off,

    /** The dose sheet: where this dose went. */
    Picked,

    /** The dose sheet: a site they use, not picked. */
    Available,
}

/**
 * A plain front-facing outline with the eight injection sites as round buttons, so "lower left
 * abdomen" is a place to tap rather than a word to parse. Front-facing means the person's left
 * is on the right of the picture; the labels under it say so. A site missing from [marks] is not
 * drawn.
 */
@Composable
fun InjectionSiteMap(
    marks: Map<InjectionSite, SiteMark>,
    onTap: (InjectionSite) -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 280.dp,
) {
    val body = AppTheme.colors.soft.color
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.widthIn(max = maxWidth).fillMaxWidth().aspectRatio(ASPECT)) {
            val width = this.maxWidth
            val height = this.maxHeight
            Canvas(Modifier.matchParentSize()) { drawBody(body) }
            marks.forEach { (site, mark) ->
                val (x, y) = SITE_POSITIONS.getValue(site)
                SiteButton(
                    site = site,
                    mark = mark,
                    onTap = { onTap(site) },
                    modifier = Modifier.offset(x = width * x - TOUCH / 2, y = height * y - TOUCH / 2),
                )
            }
        }
        Row(Modifier.widthIn(max = maxWidth).fillMaxWidth()) {
            Muted(stringResource(Res.string.medication_body_your_right), Modifier.weight(1f))
            Muted(stringResource(Res.string.medication_body_your_left))
        }
    }
}

@Composable
private fun SiteButton(site: InjectionSite, mark: SiteMark, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val label = stringResource(site.label)
    val description = when (mark) {
        SiteMark.On -> stringResource(Res.string.medication_site_on_cd, label)
        SiteMark.Off -> stringResource(Res.string.medication_site_off_cd, label)
        SiteMark.Picked -> stringResource(Res.string.medication_site_picked_cd, label)
        SiteMark.Available -> label
    }
    val selected = mark == SiteMark.On || mark == SiteMark.Picked
    Box(
        modifier = modifier
            .size(TOUCH)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                role = if (mark == SiteMark.On || mark == SiteMark.Off) Role.Checkbox else Role.RadioButton,
                onClick = onTap,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val dot = Modifier.size(DOT).clip(CircleShape)
        when (mark) {
            SiteMark.On, SiteMark.Picked -> Box(dot.background(colors.accent), contentAlignment = Alignment.Center) {
                Icon(icon = AppIcons.Check, size = 18.dp, color = colors.onAccent)
            }
            SiteMark.Available -> Box(dot.background(colors.surface).border(2.dp, colors.accent.color, CircleShape))
            SiteMark.Off -> Box(dot.background(colors.surface).border(2.dp, colors.line.color, CircleShape))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBody(color: androidx.compose.ui.graphics.Color) {
    val w = size.width
    val h = size.height
    fun part(left: Float, top: Float, right: Float, bottom: Float, radius: Float) = drawRoundRect(
        color = color,
        topLeft = Offset(w * left, h * top),
        size = Size(w * (right - left), h * (bottom - top)),
        cornerRadius = CornerRadius(w * radius),
    )
    drawCircle(color = color, radius = w * HEAD_RADIUS, center = Offset(w * 0.5f, h * HEAD_Y))
    // Torso, then arms and legs, each a rounded bar.
    part(0.29f, 0.17f, 0.71f, 0.58f, 0.10f)
    part(0.12f, 0.19f, 0.26f, 0.53f, 0.07f)
    part(0.74f, 0.19f, 0.88f, 0.53f, 0.07f)
    part(0.31f, 0.56f, 0.49f, 0.98f, 0.08f)
    part(0.51f, 0.56f, 0.69f, 0.98f, 0.08f)
}

/** Centres as fractions of the map. The person's right is the picture's left. */
private val SITE_POSITIONS: Map<InjectionSite, Pair<Float, Float>> = mapOf(
    InjectionSite.ArmRight to (0.19f to 0.33f),
    InjectionSite.ArmLeft to (0.81f to 0.33f),
    InjectionSite.AbdomenUpperRight to (0.40f to 0.35f),
    InjectionSite.AbdomenUpperLeft to (0.60f to 0.35f),
    InjectionSite.AbdomenLowerRight to (0.40f to 0.48f),
    InjectionSite.AbdomenLowerLeft to (0.60f to 0.48f),
    InjectionSite.ThighRight to (0.40f to 0.72f),
    InjectionSite.ThighLeft to (0.60f to 0.72f),
)

private const val ASPECT = 0.72f
private const val HEAD_RADIUS = 0.085f
private const val HEAD_Y = 0.085f
private val TOUCH = 44.dp
private val DOT = 30.dp
