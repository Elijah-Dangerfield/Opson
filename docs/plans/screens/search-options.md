# Search options plan

Cronometer's search options, built for Opson: a sort, a database filter,
category tabs and a source badge on every row, behind `release.searchOptions`
(default off). Off, the Logger is exactly 0.1. Read `logger.md` first; this
only adds to its Search pane.

## Source
- The owner's Cronometer screenshots: a filter button in the search bar opens
  "Food Search Options" (Enable Multi-Add, Category Tabs, a sort, a Database
  filter); tabs under the bar (All, Favorites, Custom, Common, Restaurant,
  Beverages); each row shows the name, a default serving and a right-aligned
  source badge.
- Opson's look, not Cronometer's: `:libraries:ui` components and tokens.

**Multi-Add is not built.** Opson's Logger already picks several foods into one
basket (the + on every row, then "Add to dinner"), which is what Multi-Add
turns on in Cronometer. A toggle for it would switch nothing.

## Flag
`SearchOptionsEnabled`, path `release.searchOptions`, default `false`, in the
QA menu, `apps/admin/config-manifest-registry.json` and the drift test's
`declaredConfigValues`. Read once when the Logger opens (like `release.barcode`).
Off: no filter button, no tabs, no badges, no new flows collected, no options
call to the server. Nothing runs at app start either way.

## Server (additive; 0.1 calls `/v1/foods/search`)

### `foods.category` (V17)
`ALTER TABLE foods ADD COLUMN IF NOT EXISTS category TEXT`, a CHECK added only
when missing, no index (search filters it after the name and full-text
indexes have narrowed the rows). Values:

| category | From |
|---|---|
| `restaurant` | SR Legacy and Foundation `food_category` Fast Foods (21) and Restaurant Foods (25); FNDDS names that say "fast food" or "restaurant" |
| `beverage` | SR Beverages (14) and Alcoholic Beverages (28); SR fruit juices (category 9, name has "juice"); SR fluid milk ("Milk, ..." in 1, not dry, powdered, condensed or evaporated); FNDDS WWEIA 7002 to 7804 (juice, soft drinks, coffee, tea, alcohol, water), milk 1002 to 1208, shakes 1402, plant milk 1902; Branded `branded_food_category` for juice, soda, water, milk, plant milk, tea, coffee, sport and energy drinks, alcohol, beer, powdered drinks and the rest of the "Drinks" family |
| `baby_food` | SR Baby Foods (3); WWEIA 9002 to 9404 (baby food and formula); Branded "Baby/Infant Foods/Beverages", "Infant Formula" |
| `supplement` | WWEIA 9802 (protein and nutritional powders); Branded supplement and vitamin categories |
| null | everything else |

`baby_food` and `supplement` cost one line each and are what most often
crowds a search (formula, protein powder); nothing reads them yet.

**Open Food Facts stays uncategorised.** Mapping `categories_tags` in the
importer is cheap, but filtering the bounded `off_foods` search by category
is not: the caps take the first N matches in scan order, so a filter inside
them needs its own index and a re-run of the 3.3M-row bench, and `off_foods`
isn't loaded on dev to measure. So a category tab (Restaurant, Beverages,
Common) never shows Open Food Facts rows. Common is tiers 1 and 2, which
excludes them by definition anyway.

### Importer and backfill
- `scripts/import_usda_foods.py` writes `category` for every row it writes:
  generic rows from `food_category_id` (SR, Foundation) and the WWEIA number
  (FNDDS), Branded from `branded_food_category` of the record it keeps. One
  pure function per family, `generic_category` and `branded_category`, with
  tests. The committed generic seed is regenerated (pinned releases, so only
  the new column changes). `--psql` refuses to run before V17.
- `scripts/backfill_food_categories.py`: reads the same releases (or
  `--archive` for a downloaded Branded zip), computes the category per
  `(source, source_id)` (generic) or barcode (Branded), and updates only rows
  whose category differs, in batches, `IS DISTINCT FROM` guarded. A second run
  writes nothing. `--dry-run` counts. Run on dev only; the prod command goes in
  the report.

### `GET /v1/foods/search`
Three optional params. With none, the call is the one 0.1 makes and the
response is byte for byte today's (same statement, same DTO; a route test and
a repository test pin it).

| Param | Values | Does |
|---|---|---|
| `category` | `restaurant`, `beverage`, `common` | `foods.category = …`; `common` is tiers 1 and 2. No Open Food Facts rows. |
| `source` | `usda`, `off` | `usda`: USDA sources only, no Open Food Facts. `off`: Open Food Facts only (`off_foods` when the server flag is on, plus `foods` rows cached from barcode lookups). |
| `sort` | `best` (default), `name` | `name` orders the same best matches A to Z. It does not page through every match alphabetically: "Abalone" first for "rice" helps nobody. |

An unknown value is a 400 (`invalid_category`, `invalid_source`,
`invalid_sort`). Filters are SQL fragments chosen from enums, never built
from request text. The search event is recorded as before.

### `GET /v1/foods/search/options` (new)
`{"sources": ["usda", "off"], "categories": ["restaurant", "beverage", "common"]}`,
`off` only when `FOOD_SEARCH_OFF_ENABLED` is on. Lets the app offer the Open
Food Facts filter only when the server has it. Auth required, no user data.

## Client

### Settings (new top-level keys, `:libraries:diary`)
```kotlin
val searchSort: SearchSort = SearchSort.BestMatch        // "best_match" | "most_recent" | "a_to_z"
val searchDatabase: SearchDatabase = SearchDatabase.All  // "all" | "usda" | "open_food_facts" | "my_foods"
val searchTabs: Boolean = true                           // Category tabs on
```
Top level because the server merges settings shallowly. Unknown values coerce
to the default (`SettingsJson.coerceInputValues`). The tab picked is not a
setting: the sheet opens on All every time.

### `:libraries:foods`
- `FoodSearchFilter(category: SearchCategory?, source: SearchSource?, order: SearchOrder)`
  and `FoodCatalog.search(query, limit, filter = FoodSearchFilter.None)`; the
  default sends no params. Offline, the cache answers source and Common
  filters from each cached food's source and tier; Restaurant and Beverages
  have no offline answer (the cache has no category), so they come back
  empty with `fromCache`.
- `FoodCatalog.searchOptions(): Catching<SearchOptions>` (`openFoodFacts`).

### Logger
`LoggerChoices` gains `searchOptions: Boolean` (the flag), `tab: SearchTab`
and `openFoodFacts: Boolean` (from the options call). `LoggerInputs` gains
`favorites: List<LogItem>` (`frequentFoods(20)`, what the Logger already calls
Favorites: the foods logged most over 30 days), `history: List<LogItem>`
(`recentFoods(30)`) and `known: Map<String, Food>` (`FoodCatalog.observeRecent`,
for badges on rows that came from the diary). All three stay empty and
uncollected with the flag off.

State additions:
```kotlin
data class LoggerState(..., val options: SearchOptionsBar? = null)  // null with the flag off
data class SearchOptionsBar(val tabs: List<SearchTab>?, val tab: SearchTab, val active: Boolean)
data class FoodRowUi(..., val badge: SourceBadge?, val verified: Boolean)
enum class SourceBadge { Usda, UsdaBranded, OpenFoodFacts, YourFood }
LoggerBody.Results(..., openFoodFacts: Boolean)   // the ODbL line under results
LoggerBody.Listed(rows)                           // Favorites, Custom or My foods before typing
LoggerBody.TabHint(tab)                           // Common, Restaurant, Beverages before typing
LoggerBody.TabEmpty(tab)                          // Favorites or Custom with nothing in it
```
`tabs` is null when the Category tabs setting is off. `active` lights the
filter button when the sort or database is not the default, so a narrowed
list never looks like a short one.

What the body holds (pure, in `LoggerStateBuilder`):

| Tab | Empty query | Query |
|---|---|---|
| All | Usual and Recent as today; Most recent: Recent from `history`; My foods: your foods, then `history` | Database All: your matching foods, then the catalog (today). USDA / Open Food Facts: the catalog with `source`. My foods: your foods and `history` that match, no server call. |
| Favorites | `favorites` | `favorites` whose name matches |
| Custom | custom foods | custom foods that match |
| Common / Restaurant / Beverages | `TabHint` | the catalog with `category` |

The database filter applies to All; every other tab already names where its
foods come from, so a tab can't contradict it into an empty list. Open Food
Facts as a database falls back to All if the server stops offering it.

The method chips show on All only: on another tab the person is looking for
a food, not a way to log. With the Category tabs setting off, a tab picked
earlier no longer applies and the body is All's.

Then the sort, on every list: Best match keeps the order it came in; Most
recent puts foods in `history` first, newest first, the rest after in their
order; A to Z by name (the server gets `sort=name` too). Rows are deduped by
key as today.

Badges: catalog foods by source (`usda_foundation`, `usda_sr`, `usda_fndds`
USDA; `usda_branded` USDA branded; `off` Open Food Facts; Nutritionix and
unknown none), custom foods Your food. Diary rows look their food up in
`known`; a food not cached gets none. `verified` is the food's flag (tiers 1
to 3); custom foods are never marked.

Actions (all named for what happened):

| Action | Does |
|---|---|
| `SearchOptionsTapped` | Event `OpenSearchOptions(openFoodFacts)`. |
| `TabPicked(tab)` | Tab, re-search if typing, telemetry. |
| `SearchOptionsArrived(openFoodFacts)` | Rebuild. |
| (settings change) | `InputsArrived` already carries settings; a changed sort or database re-runs the search. |

Search results are dropped unless both the query and the filter they were
asked with are still current. The view model searches again only when the
query or the filter changes (`searchIfStale`), so a basket change or any
other settings write doesn't re-run it.

### Options sheet
`SearchOptionsRoute(openFoodFacts: Boolean)`, `bottomSheet<>` over the
Logger, `SearchOptionsViewModel` (settings, telemetry).

```kotlin
data class SearchOptionsState(val sort: SearchSort, val database: SearchDatabase,
    val databases: List<SearchDatabase>, val tabs: Boolean)
```
Actions `SettingsArrived`, `SortPicked`, `DatabasePicked`, `TabsToggled(on)`,
`DoneTapped`; each pick writes the setting at once (`SettingsRepository.update`, no numbers
stamp). Layout: `SheetHeader("Search options", onClose)`, "Sort by" with three
`RadioRow`s, "Database" with `RadioRow`s, a "Category tabs" row with
`AppSwitch` and a caption. No numbers anywhere.

### Components
- `SearchField(action = …)`: a trailing slot that stays while typing, for the
  filter button (`:libraries:ui`).
- Tabs: a horizontally scrolling row of compact `PillChip`s, role Tab.
- `AppIcons.Tune` for the filter button, if it isn't there.

## Visibility surfaces
Badges, tabs, the sheet and the hints carry no numbers. Rows keep going
through the policy exactly as before: energy and macros in a bare context on
`Surface.Logger`. Sorting by name or recency reads no nutrient. Leak test:
`logger-search-tabs` (tabs, badges, filters lit, showsCaloriesInFull) and
`logger-search-options` (the sheet, none).

## Licence (Open Food Facts, ODbL)
`docs/food-data-sources.md` asks for "Contains data from Open Food Facts,
available under the Open Database License" on a credits screen and wherever
its foods appear. Each Open Food Facts row now names its source, and when any
are in the results a line under them says so in full. The Foods tab keeps its
credit line. The Credits screen with a link isn't built yet. With the server
flag on and this flag off, Logger rows from Open Food Facts carry no
attribution, so flip `release.searchOptions` with or before
`FOOD_SEARCH_OFF_ENABLED`.

## Copy (`strings_search_options.xml`)

| Key | String |
|---|---|
| `search_options_cd` | Search options |
| `search_options_title` | Search options |
| `search_options_sort` | Sort by |
| `search_options_sort_best` / `_recent` / `_az` | Best match / Most recent / A to Z |
| `search_options_sort_recent_sub` | Foods you've logged come first |
| `search_options_database` | Database |
| `search_options_db_all` / `_usda` / `_off` / `_mine` | All / USDA / Open Food Facts / My foods |
| `search_options_db_mine_sub` | Foods you made and foods you've logged |
| `search_options_tabs` | Category tabs |
| `search_options_tabs_sub` | Show All, Favorites, Custom and more under the search bar |
| `search_options_done` | Done |
| `search_tab_all` / `_favorites` / `_custom` / `_common` / `_restaurant` / `_beverages` | All / Favorites / Custom / Common / Restaurant / Beverages |
| `search_tab_hint_common` / `_restaurant` / `_beverages` | Search everyday foods / Search restaurant and fast food / Search drinks |
| `search_tab_empty_favorites` | Foods you log often show up here. |
| `search_tab_empty_custom` | Foods you make show up here. |
| `search_badge_usda` / `_branded` / `_off` / `_yours` | USDA / USDA branded / Open Food Facts / Your food |
| `search_badge_verified_cd` | Verified |
| `search_off_credit` | Contains data from Open Food Facts, available under the Open Database License. |

## Telemetry
`search.sort_picked` (`sort`), `search.database_picked` (`database`),
`search.tabs_toggled` (`on`), `search.tab_picked` (`tab`). Values only, never
a query. `SearchOptionsTelemetry` takes the enums.

## Edge cases
- Flag off: everything above absent; `FoodCatalog.search` sends no params.
- Tabs setting off: no tab row, tab stays All.
- Offline: Restaurant and Beverages come back empty with the offline caption;
  Favorites, Custom and My foods never need the server.
- Picking for a meal: tabs and options work the same.
- Open Food Facts picked, then the server turns it off: treated as All, and
  the sheet stops offering it.

## Tests
- Server: `FoodCategoryTest`-style checks in the repository test (category,
  source and common filters, `sort=name`, no params equals today), route
  tests (params parsed, 400s, the default call unchanged, options endpoint),
  `DatabaseSchemaTest` (column, check, V17 twice), `SearchRelevanceTest`
  unchanged.
- Scripts: `generic_category`, `branded_category` unit tests.
- Client: `LoggerStateBuilderTest` (tabs, database, sorts, badges, hints,
  Blind rows), `LoggerViewModelTest` (tab picks, settings change re-searches,
  stale filter dropped, flag off sends nothing new), `SearchOptionsViewModelTest`,
  `FoodCatalogImplTest` (params, offline filters), `SettingsTest` (new keys
  round-trip, unknown values coerce).
- Previews: `LoggerSearchTabsPreview`, `…DarkPreview`, `…BlindPreview`,
  `LoggerSearchOptionsPreview`, `…DarkPreview`, and the existing
  `LoggerSearchPreview` is the flag-off render.
