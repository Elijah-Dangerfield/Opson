# Describe it: reading what people say, and fixing it (0.2)

The owner's report from a real iPhone: "I said I ate some toast and eggs. I
would expect the most basic toast and eggs. But it added French toast and 2
hard boiled eggs. And it wasn't easy to increase or decrease amounts on them
individually." This plan is the fix: the server reads a sentence the way a
person means it, and the Describe pane lets each line be corrected in one tap.
Read `logger.md` (Describe) and `voice.md` first; this replaces the "Describe
lines" parts of `logger.md`.

## Why it picked French toast

The parse of "some toast and eggs" was right: two phrases, "toast" and
"eggs". Matching was wrong.

- No USDA name is just "Toast". Search for `toast` finds "French toast, NFS"
  by full text, ranked above "Bread, white, toasted" (shorter name, NFS).
- "eggs" took the first head match, "Egg, whole, raw".
- No quantity meant 1 of the food's "default portion", whatever the label
  happened to be: a school carton of milk, a Big Mac Jr, a medium coffee
  (480 g).

An LLM in front would have produced "toast" and "eggs" too. The fix had to be
in matching and defaults whatever reads the sentence.

## Server: `POST /v1/describe`

### Reading the sentence (`DescribeParser`, `DescribeVocabulary`, `Brands`)
- Sentences split on full stops and newlines, phrases on commas, `and`,
  `with`, `plus`, `also`, `then`. Foods with "and" in the name stay whole
  (`COMPOUNDS`: mac and cheese, PB and J). What follows "with" is an
  addition ("coffee with milk").
- Speech: filler goes ("I had", "um", "for breakfast", "leftover");
  corrections take back what came before ("a coffee, no wait, a latte");
  "no butter", "without cheese" drop that food; known misrecognitions are
  fixed ("big mack", "nuggest", "frys").
- Restaurants: the big chains, by the spellings recognition writes ("chick fil a",
  "chickfila", "chic filet", "mickey d's"), read from "from X", "at X", "X's"
  or a sentence that starts with one, for the whole sentence. Chipotle and
  Sonic count only after a preposition or before a restaurant word.
- Quantity: digits, fractions, words to twenty, a dozen, ranges (the first
  number), a couple, a few. **No quantity, "some" or "a bit of" is null: a
  typical serving.** Units add glass, mug, can, bottle, handful, strip, link,
  patty, bar, scoop, packet, container, fl oz, lb, ml. Sizes add big, regular,
  tall (a Starbucks tall is small), grande, venti.

### Picking the food (`CatalogFoodDescriber`)
1. A named restaurant's own food, when a catalog row carries the restaurant
   and the words said.
2. `CommonFoods`: what a bare everyday word means and its serving, by exact
   USDA name. Toast is a medium slice of white toast, eggs
   one cooked egg, coffee a cup, milk a cup or a fluid ounce after "with".
   Only an exact match uses it, so "French toast" and "scrambled eggs" are
   honoured.
3. Search, generic first as search ranks it, choosing the match that carries
   every word said, is from no other restaurant, and adds the fewest
   preparations nobody said (French, fried, pickled, dried...).
4. Nothing carries all the words: the everyday food the words end in
   ("grilled nuggets" is nuggets), else search on fewer words.

Also: "a Whopper with cheese" stays one line when the catalog has that food
and the first part is not everyday; a run with no "and" ("eggs bacon toast",
how recognition often writes it) splits when every word is an everyday food
and the run is not itself a catalog food.

### How much
Mass unit; a portion named by the unit or size; a household measure (a bowl
is a cup and a half, scaled by size); the photo's weight; then the serving:
`CommonFoods`' or one of the food's typical portion (named for what was said,
one of the thing, medium, counted, cup), times the count said. Bookkeeping
portions (school cartons, "Guideline amount") are never chosen.

### Response (additive; 0.1 ignores the new fields)
`food` (the matched food with portions) and `alternatives` (up to three, the
curated variants first, then search matches carrying at least half the
words). `quantity` counts `portionLabel`; `amountText` reads "2 eggs".

### No AI, on purpose
The server already calls Anthropic for photos. Describe stays deterministic
for now:
- `legal/privacy.md` lists every outside service the app's data reaches, and
  no AI provider is among them for text (photo estimates add one at the 0.2
  release, `photo.md`). Sending descriptions to Anthropic needs that policy
  and the App Privacy answers updated first, and would reach 0.1 users the
  day a key is set, since 0.1 calls the same route.
- Typed text is read on every 700 ms pause; a model call adds seconds and a
  cost to each.
- The reported failure was matching, which a model in front does not fix.

Where rules still fall short, listed in the report and worth an LLM read
later: long free-form speech with no food words in our lists, unusual
restaurant items the catalog lacks (Chick-fil-A grilled nuggets are generic
nuggets), quantities spread across a sentence ("two of them"), and words
recognition got badly wrong.

## App: the review

### Model
`LoggerChoices.review: DescribeReview(rows, find, dismissed, nextKey)`.
`DescribeRow(key, said, food, unit, quantity, parsed, alternatives, edited,
added)`; the rules are `DescribeRows`, pure. The basket holds exactly the
rows (source Describe), synced after every change.

- A read becomes rows: the server's portion counted from its weight, else
  grams, else one of the first unit. Calories hidden: never grams.
- A new read replaces read rows, keeps rows the person changed (by phrase)
  and rows they added, and skips phrases they removed.
- Blank text or a failed read keeps only added rows.
- A server older than 0.2 sends no food: the line logs as read, with no
  amount controls.

### Steps
Grams 10; one for things nobody halves (egg, nugget, link, strip, wing,
shrimp, nut, olive, an ounce, a teaspoon); a half for everything else
(slices, cups, servings). Never below one step; remove is the X.

### UI (`DescribeReviewViews.kt`)
Under "We read that as", each line: the name (tap: alternatives, then
"Search for something else"), energy, X; under it − amount + and the unit
menu when the food has more than one measure. A line with no food says "Not
in the food list. Tap to find it." Then "Add something it missed", and the
total row ("3 items · Estimate", "About 379 kcal") with "Add to {meal}". The
find pane replaces the lines (back returns to them): a search field and the
catalog's rows; picking adds a line or swaps the one it was opened from.

### Actions (`DescribeReviewAction : LoggerAction`)
| Action | Does |
|---|---|
| `LessTapped(key)` / `MoreTapped(key)` | One step on that line |
| `UnitPicked(key, index)` | Count stays, weight follows; to or from grams converts |
| `AlternativePicked(key, index)` | Swap; same measure keeps the count, else the weight; the old food becomes the first alternative |
| `RemoveTapped(key)` | Line out; its phrase stays out on re-reads |
| `FindTapped(key?)` | Find pane; on a line with no food it starts from its words |
| `FindTyped(text)` / `FindResultsArrived` | Debounced 250 ms catalog search, 15 rows, stale answers dropped |
| `FindPicked(id)` | Adds a line, or swaps the one it was opened for |
| `FindClosed` | Back to the lines, search cancelled |

## Visibility
Line energy, the total and find rows go through the policy on
`Surface.Logger` (bare for rows, unit for the total). Calories hidden: no
grams in the unit menu or as a starting amount. Leak surfaces:
`logger-describe`, `logger-describe-voice`, `logger-describe-edited`,
`logger-describe-find`.

## Flags
Unchanged: Describe ships in 0.1; the mic is `release.voice`. The review
needs no flag of its own; with an older server it degrades to read-only lines.

## Tests
- Server: `DescribeParserTest`, `CatalogFoodDescriberTest` (rules on a fake
  catalog), `DescribeFixturesTest` (phrases grouped by kind, against the USDA
  seed and Branded rows; also checks every `CommonFoods` name and serving
  exists), `FoodRoutesTest` (new fields).
- App: `DescribeRowsTest`, `DescribeReviewViewModelTest` (one per action),
  `LoggerStateBuilderTest`, `FoodCatalogImplTest` (mapping, older server).
