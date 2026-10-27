package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.opson.libraries.diary.SearchDatabase
import com.opson.libraries.diary.SearchSort
import com.opson.libraries.diary.ui.FoodRowsCard
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.radio.RadioRow
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.search_options_database
import opson.libraries.resources.generated.resources.search_options_db_all
import opson.libraries.resources.generated.resources.search_options_db_mine
import opson.libraries.resources.generated.resources.search_options_db_mine_sub
import opson.libraries.resources.generated.resources.search_options_db_off
import opson.libraries.resources.generated.resources.search_options_db_usda
import opson.libraries.resources.generated.resources.search_options_done
import opson.libraries.resources.generated.resources.search_options_sort
import opson.libraries.resources.generated.resources.search_options_sort_az
import opson.libraries.resources.generated.resources.search_options_sort_best
import opson.libraries.resources.generated.resources.search_options_sort_recent
import opson.libraries.resources.generated.resources.search_options_sort_recent_sub
import opson.libraries.resources.generated.resources.search_options_tabs
import opson.libraries.resources.generated.resources.search_options_tabs_sub
import opson.libraries.resources.generated.resources.search_options_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The search options: how results are sorted, which database the All tab
 * searches, and whether the category tabs show. Each choice saves as it is
 * made. Stateless; the entry point puts it in a `BottomSheet` with
 * `scrollableContent`.
 */
@Composable
fun SearchOptionsSheetContent(
    state: SearchOptionsState,
    onAction: (SearchOptionsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(
            title = stringResource(Res.string.search_options_title),
            onClose = { onAction(SearchOptionsAction.DoneTapped) },
        )
        Group(stringResource(Res.string.search_options_sort)) {
            SearchSort.entries.forEachIndexed { index, sort ->
                RadioRow(
                    title = stringResource(sort.label),
                    subtitle = if (sort == SearchSort.MostRecent) stringResource(Res.string.search_options_sort_recent_sub) else null,
                    selected = sort == state.sort,
                    onClick = { onAction(SearchOptionsAction.SortPicked(sort)) },
                    showDivider = index < SearchSort.entries.lastIndex,
                )
            }
        }
        Group(stringResource(Res.string.search_options_database)) {
            state.databases.forEachIndexed { index, database ->
                RadioRow(
                    title = stringResource(database.label),
                    subtitle = if (database == SearchDatabase.MyFoods) stringResource(Res.string.search_options_db_mine_sub) else null,
                    selected = database == state.database,
                    onClick = { onAction(SearchOptionsAction.DatabasePicked(database)) },
                    showDivider = index < state.databases.lastIndex,
                )
            }
        }
        FoodRowsCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.section),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(Res.string.search_options_tabs), typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
                    Text(
                        text = stringResource(Res.string.search_options_tabs_sub),
                        typography = AppTheme.type.secondarySmall,
                        color = AppTheme.colors.muted,
                    )
                }
                AppSwitch(checked = state.tabs, onCheckedChange = { onAction(SearchOptionsAction.TabsToggled(it)) })
            }
        }
        ButtonPrimary(onClick = { onAction(SearchOptionsAction.DoneTapped) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.search_options_done))
        }
    }
}

@Composable
private fun Group(title: String, rows: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        SectionLabel(title)
        FoodRowsCard { rows() }
    }
}

private val SearchSort.label: StringResource
    get() = when (this) {
        SearchSort.BestMatch -> Res.string.search_options_sort_best
        SearchSort.MostRecent -> Res.string.search_options_sort_recent
        SearchSort.AToZ -> Res.string.search_options_sort_az
    }

private val SearchDatabase.label: StringResource
    get() = when (this) {
        SearchDatabase.All -> Res.string.search_options_db_all
        SearchDatabase.Usda -> Res.string.search_options_db_usda
        SearchDatabase.OpenFoodFacts -> Res.string.search_options_db_off
        SearchDatabase.MyFoods -> Res.string.search_options_db_mine
    }
