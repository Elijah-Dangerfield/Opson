# Add this food (0.2)

When a scan or a search finds nothing, the person photographs the pack's
front and its nutrition label. The server reads both, the person checks what
it read, and the food is logged straight away as their own custom food. A
copy goes to a review queue; once a reviewer approves it, everyone's search
and scan find it. Behind `release.submitFood`, default off.

## Entry points
- **Barcode miss** (`BarcodeScanScreen`): Add this food comes first, above
  "Save the name only". `SubmitFoodRoute(dayIso, slot, barcode)` takes the
  scanner's place; the scanned code is shown, not retyped.
- **Search with no results** (`FoodSearchScreen`'s `NoResults`): Add this
  food after Describe it and Save the name. `SubmitFoodRoute(..., fromSearch = true)`
  opens over search; the barcode field is optional, typed if the pack has one.
- Not when picking for Create a meal: that mode logs nothing.

The meal photo estimate (`release.photo`) is not offered from either. It is
the + sheet's Photo tile alone (docs/decisions.md, 2026-10-27).

## Flow
1. **Photos.** Two slots, front (name, brand) and label (the whole panel).
   Each has Take photo (camera, through `CameraPermissions`, as the photo
   estimate does), Choose from library, and Retake once taken. The device
   shrinks each to a JPEG of at most 1568 px (`MealPhotoPicker`). Read the
   label is enabled once both are there.
2. **Reading.** `POST /v1/foods/labels/parse`. Up to a minute.
3. **Check what we read.** The form: name, brand, barcode, serving size and
   g or ml, servings per pack, and per serving: calories, protein, carbs,
   sugars, fiber, fat, saturated fat, sodium, then "More from the label" for
   anything else the label lists. Every field is editable with its keyboard
   (text, digits, decimal). A field the model was unsure of (under 0.7) says
   "Check this" until it is typed. The checks run live as the person types
   (`LabelChecks`, the same rules the server runs): "Calories don't match the
   macros by 36 kcal" with Use 234 kcal and Keep 270 kcal; kJ read as kcal;
   per serving against per 100 (Use 45 g when the serving size is what is
   off); sodium a thousand times off; amounts no food can hold, which have
   to be changed.
4. **Add to {meal}.** Required: a name and a serving size always; calories,
   protein, carbs and fat where the mode shows them; and no impossible amount
   where it can be seen. The food is saved as a custom food and logged
   (`PickSource.Label`, `logger.method_picked method=label`), then sent
   (`POST /v1/foods/submissions`, with both photos and the server's own
   reading by `parseId`). The screen closes: "{food} added to {meal}. We'll
   check it for everyone."

Calm ends: no label in the second photo (Retake the label, Type it in), too
many reads (Type it in, Try again), read failed or offline (Try again, Type it
in), camera off (Allow or Settings, Choose from library), the server cannot
read labels (the form opens empty, to fill in from the photos), and the review
copy failed after logging ("Added to your foods": Try again with the same
submission id, or Done).

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

## Not built
- A "my submissions" screen. The person's own copy is a custom food; whether
  review approved it changes nothing for them.
- Editing a submission after it is sent.
- A closed day blocking the add: another change makes a closed day block all
  adding, this screen included.

## Tests
`SubmitFoodStateBuilderTest`, `SubmitFoodViewModelTest`, `LabelChecksTest`
(app and server), `LabelOutputReaderTest` and `AnthropicLabelParserTest` on
recorded-shape answers, the repositories against Postgres, the routes with
fakes, `FoodSubmissionJourneyTest` end to end, and the leak test's
`submit-food-*` surfaces.
