# Add a packaged food (0.2)

The person photographs a pack's front and its nutrition label. The server
reads both, the person checks what it read, and the food is saved at once as
their own custom food (and logged, when they came from a meal). A copy goes
to Opson's food database as a pending entry; once a reviewer approves it,
everyone's search and scan find it. Behind `release.submitFood`, default off.

Called "Add this food" until 2026-10-28. The new name, and the copy below,
keep it apart from the meal photo estimate: this adds an exact food from its
label, that guesses a plate (docs/decisions.md, 2026-10-28).

## Entry points
`SubmitFoodRoute(dayIso?, slot?, barcode?, entry)` lives in the logger's api
(`features/logger`), so the Foods tab can open it. `entry` (`SubmitEntry`) is
where it was opened, for telemetry and for where the screen goes after.

- **Barcode miss** (`BarcodeScanScreen`): "Add it from the label" comes first,
  above "Save the name only". The route takes the scanner's place; the scanned
  code is shown, not retyped. `entry = BarcodeMiss`.
- **Search with no results** (`FoodSearchScreen`'s `NoResults`): "Add it from
  the label" after Describe it and Save the name. `entry = EmptySearch`.
- **Search with results**: "Can't find it?" closes every result list that has
  a query, with Describe it, Save the name and, on a row of its own, "Add it
  from the label" with one line on where the food goes.
  `entry = SearchResults`.
- **The + sheet**: a "Packaged food" tile beside Scan barcode. The meal
  estimate's tile, "Estimate a meal", comes after it, so the two cameras read
  apart. Hidden on a closed day like every tile that logs. `entry = AddSheet`.
- **Foods tab, Add a food** (`AddFoodScreen`): "Packaged food?" is the first
  card, with "Add it from the label"; "Or type it in" and the hand-typed form
  follow. Opens the route with no day and no meal: the food is saved, not
  logged. `entry = Foods`. Saving closes this screen and Add a food under it,
  back to the Foods tab where the food now is.
- Not when picking for Create a meal: that mode logs nothing, and the flow
  logs.

The meal photo estimate (`release.photo`) is not offered from any of these.
It is the + sheet's "Estimate a meal" tile alone (docs/decisions.md,
2026-10-27).

## Flow
1. **Photos.** Two slots, front (name, brand) and label (the whole panel).
   Each has Take photo (camera, through `CameraPermissions`, as the photo
   estimate does; hidden where there is no camera, the iOS simulator), Choose
   from library, and Retake once taken. The device shrinks each to an upright
   JPEG of at most 1568 px (`MealPhotoPicker`). Read the label is enabled once
   both are there. A picked photo that would not open (an iCloud original
   that never downloaded, say) says "That photo couldn't be opened. Try
   another one." under the slots instead of doing nothing.
2. **Reading.** `POST /v1/foods/labels/parse`. Up to a minute; the app waits
   up to 190 s for the server's two tries.
3. **Check what we read.** The form: name, brand, barcode, serving size and
   g or ml, servings per pack, and per serving: calories, protein, carbs,
   sugars, fiber, fat, saturated fat, sodium, then "More from the label" for
   anything else the label lists. Every field is editable with its keyboard
   (text, digits, decimal). A field the model was unsure of (under 0.7) says
   "Check this" until it is typed. The checks run live as the person types
   (`LabelChecks`, the same rules the server runs): "Calories don't match the
   macros by 46 kcal" with Use 234 kcal and Keep 280 kcal; kJ read as kcal;
   per serving against per 100 (Use 45 g when the serving size is what is
   off); sodium a thousand times off; amounts no food can hold, which have
   to be changed.
4. **Add to {meal}**, or **Save food** from the Foods tab. Required: a name
   and a serving size always; calories, protein, carbs and fat where the mode
   shows them; and no impossible amount where it can be seen. The food is
   saved as a custom food with the rest of the label per serving
   (`SubmitFoodForm.customFood`), logged when there is a meal
   (`PickSource.Label`, `logger.method_picked method=label`), then sent
   (`POST /v1/foods/submissions`, with both photos, the server's own reading
   by `parseId`, and `customFoodId`, the person's copy). Once the server has
   it, `SubmittedFoods.sent` marks that custom food pending. The screen
   closes: "{food} added to {meal}. We'll check it for Opson's food
   database." or "{food} saved to your foods. We'll check it for Opson's food
   database."

A closed day (Close the day, maybe on another phone) refuses the log, as it
refuses every write (`DayWrite.DayClosed`). The food is still saved to the
person's foods and still sent for review, then the closed-day sheet takes this
screen's place; once the day is reopened the food is one search away. Search,
the + sheet and the scanner offer no way in on a closed day in the first
place. From the Foods tab there is no day, so a closed one changes nothing.

Calm ends: no label in the second photo (Retake the label, Type it in), too
many reads (Type it in, Try again), read failed or offline (Try again, Type it
in), camera off (Allow or Settings, Choose from library), the server cannot
read labels (the form opens empty, to fill in from the photos), and the review
copy failed after saving ("Added to your foods": Try again with the same
submission id, or Done). Each says "logged" or "saved" as fits the entry.

## Verified and unverified
A food in Opson's food database is pending until a reviewer approves it, and
only its submitter sees it until then (search filters on `review_status`;
`GET /v1/foods/{id}` is 404 to anyone else). Where the submitter meets it:

- **Their own copy** (the custom food, in Your foods, search, Recent, the
  Foods tab and the food detail) reads **Unverified** while the review is
  pending: a small clock and the word, in place of the source badge on a
  search row, under the name on the Foods tab, and on the detail "Sent to
  Opson's food database. Unverified until someone checks it." Once approved,
  the row mark goes and the detail says "Checked and added to Opson's food
  database." A rejected one goes back to being just their food.
- **The catalog's pending row** (their own second scan finds it) reads
  Unverified on its row, and on the detail "From Opson's food database" with
  "Unverified. Only you see it until someone checks it against your photos."
- **An approved food** is Opson's for everyone: "From Opson's food database"
  on the detail, and with the search options on an "Opson" badge with the
  verified check. Every verified catalog food's detail now has the check
  before its source.

The marks are words, so every mode shows them. The person's copy is linked to
its submission by `customFoodId` (V27); `SubmittedFoods` keeps the statuses
on the phone, marks a food the moment it is sent, refreshes from
`GET /v1/foods/submissions` on every account edge (`UserScopedSyncer`), and is
wiped on a user change. A server without the route (404) leaves the marks as
the phone recorded them. The catalog row says where it stands itself:
`reviewStatus` on the food response, sent only when it is not approved.

## Numbers and the mode
The label's numbers draw, editable, only when calories, protein, carbs and fat
all show on the logger (and the label's other nutrients only when micros show
too). In any mode that keeps calories out of sight, the person can still add
the food, and the screen says it plainly:

- the form keeps name, brand, barcode and serving, which are amounts of food
  every mode shows;
- in place of the numbers, one sentence: the label's numbers are saved with
  the food and the mode keeps them out of sight; or something on the label
  doesn't add up and someone will check it against the photos; or some
  numbers couldn't be read and someone will fill them in;
- nothing is required that cannot be seen, and no check offers a figure;
- what the model read is submitted as read, with its checks, so review sees
  exactly what needs a look.

A Pause-before-showing-more reveal was considered and left out: copying a
label is not a reason to raise the mode, and review catches what the person
could not check.

## Privacy
The photos go to the server and Anthropic to read the label, and are stored
with the submission only when the person submits. Only the submitter and the
reviewer see them; they are deleted 30 days after the review decision, and
with the account. Approved food data (not the photos) is shared with all
users. `legal/privacy.md` says the same.

## Telemetry
`food_add.started` and `food_add.submitted` carry `origin` (`barcode` or
`search`: whether a code was scanned, the value the server stores) and
`entry` (`barcode_miss`, `empty_search`, `search_results`, `add_sheet`,
`foods`). `docs/practices/app-events.md` has the rest.

## Not built
- A "my submissions" screen. The person's own copy is a custom food, marked
  while its review is pending; the server's list feeds the marks only.
- Editing a submission after it is sent.
- Merging the person's copy into the approved catalog food. After approval
  they have both: their copy, unmarked, and Opson's, verified.

## Tests
`SubmitFoodStateBuilderTest`, `SubmitFoodViewModelTest` (every entry, the
save-only mode, the marks, a photo that would not open), `LabelChecksTest`
(app and server), `LabelOutputReaderTest` and `AnthropicLabelParserTest` on
recorded-shape answers, `SubmittedFoodsStoreTest`, `FoodSearchStateBuilderTest`
and `FoodDetailTest` (Unverified and Verified), `LoggerViewModelTest`
(Packaged food tile), `AddFoodTest` and `FoodsTabTest`, the repositories
against Postgres, the routes with fakes, `FoodSubmissionJourneyTest` end to
end, and the leak test's `submit-food-*`, `search-unverified`,
`food-detail-in-review`, `foods-add-food-pack` and `foods-unverified`
surfaces.
