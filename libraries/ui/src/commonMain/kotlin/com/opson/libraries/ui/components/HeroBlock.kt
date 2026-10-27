package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.LocalContentColor
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.color.ProvideContentColor
import com.opson.system.dropShadow
import com.opson.system.thenIf
import opson.libraries.resources.generated.resources.ds_next
import opson.libraries.resources.generated.resources.ds_page_of
import opson.libraries.resources.generated.resources.ds_previous
import opson.libraries.resources.generated.resources.Res as SharedRes
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.opson.libraries.ui.system.minTouchTarget

/**
 * The design v3 colored top block: hero background, bottom corners of 24, the hero shadow, white
 * content. It bleeds past the page padding like [EdgeCard] and, by default, paints behind the
 * status bar and pads its content below it.
 *
 * Today stopped using it in design sync 2; only the design system gallery shows it now.
 */
@Composable
fun HeroBlock(
    modifier: Modifier = Modifier,
    edgeToEdge: Boolean = true,
    drawBehindStatusBar: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = AppTheme.shapes.heroBlock
    Column(
        modifier = modifier
            .fillMaxWidth()
            .thenIf(edgeToEdge) { bleedHorizontal(AppTheme.spacing.page) }
            .dropShadow(AppTheme.shadows.heroBlock, shape)
            .clip(shape)
            .background(AppTheme.colors.hero)
            .thenIf(drawBehindStatusBar) { windowInsetsPadding(WindowInsets.statusBars) }
            .padding(bottom = 2.dp),
    ) {
        ProvideContentColor(AppTheme.colors.onHero) {
            Column(content = content)
        }
    }
}

/**
 * The dots row under the summary pager: ‹, one dot per page, ›, 22dp tall. Colors default to
 * the on-hero set since that is where it lives.
 */
@Composable
fun PagerDots(
    pageCount: Int,
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: ColorResource = AppTheme.colors.onHero,
    unselectedColor: ColorResource = AppTheme.colors.onHero.withAlpha(UNSELECTED_DOT_ALPHA),
    chevronColor: ColorResource = AppTheme.colors.onHeroMuted,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(DotsRowHeight),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PagerChevron(
            icon = AppIcons.ChevronLeft.described(stringResource(SharedRes.string.ds_previous)),
            color = chevronColor,
            onClick = { onPageSelected((currentPage - 1).coerceAtLeast(0)) },
        )
        repeat(pageCount) { page ->
            val description = stringResource(SharedRes.string.ds_page_of, page + 1, pageCount)
            Box(
                modifier = Modifier
                    .minTouchTarget()
                    .size(DotsRowHeight)
                    .selectable(selected = page == currentPage, role = Role.Tab, onClick = { onPageSelected(page) })
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(if (page == currentPage) selectedColor else unselectedColor, CircleShape)
                )
            }
        }
        PagerChevron(
            icon = AppIcons.ChevronRight.described(stringResource(SharedRes.string.ds_next)),
            color = chevronColor,
            onClick = { onPageSelected((currentPage + 1).coerceAtMost(pageCount - 1)) },
        )
    }
}

@Composable
private fun PagerChevron(
    icon: IconResource,
    color: ColorResource,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .minTouchTarget()
            .width(40.dp)
            .height(DotsRowHeight)
            .bounceClick(scaleDown = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon = icon, size = 22.dp, color = color)
    }
}

private val DotsRowHeight = 22.dp
private const val UNSELECTED_DOT_ALPHA = 0.3f

@Preview(widthDp = 390)
@Composable
private fun HeroBlockPreview() {
    PreviewLightDark {
        HeroBlock(drawBehindStatusBar = false) {
            Text(
                text = "Today",
                typography = AppTheme.type.dayLabel,
                color = LocalContentColor.current,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
            )
            Text(
                text = "ENERGY SUMMARY",
                typography = AppTheme.type.overline,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Box(Modifier.fillMaxWidth().height(70.dp))
            PagerDots(pageCount = 3, currentPage = 0, onPageSelected = {})
        }
    }
}
