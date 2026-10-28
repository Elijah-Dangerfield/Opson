# The food detail and Edit a copy, next to Cronometer

Research for the food detail redesign (2026-10-27). Cronometer's help centre and
community forum, plus the screens the owner showed from the app. What we took is
first; how Cronometer does it is underneath.

## What we took from it

- **A short list of rows, one job each.** Cronometer's add dialog is Amount,
  Serving size, Timestamp and Group, read top to bottom. Opson's detail had the
  meal and the time hidden in ⋮ and a big segmented How sure in the middle. It
  is now one card of rows: Amount (the number and the unit side by side), Meal,
  Time and How sure.
- **The weight said once.** Cronometer's serving list carries each weight in
  the menu, not in every label. Opson's chips read "2 Tbsp (31g) · 31 g". Chips
  are now plain amounts, the unit menu gives each unit's weight on the right, and
  one line under the amount says what it weighs in all.
- **Grams always, and the other weights.** Cronometer offers "g" for every food.
  Opson offers grams, ounces and pounds for any food whose weight is known
  (ounces first for someone who weighs themselves in pounds), and ml and fl oz
  when a portion states a volume.
- **Where the energy comes from.** Cronometer's Energy summary is a ring with
  each macro's share of the calories. Opson draws a split bar and a line per
  macro with its grams and its share, through the policy, so a mode that hides
  calories still shows the shares of the macros it shows, and Macros only shows
  none for a portion.
- **This food against today.** Cronometer's Macronutrient targets card shows
  the food's part of energy and protein against the day's targets. Opson's
  Today with this does the same for energy, protein, carbs and fat: the day so
  far and this food in one bar, the total, and the food's share of the target.
  Macros only gets words and bars, No numbers gets nothing.
- **The source and how much it lists.** Cronometer names the data source and
  links to the listed nutrients. Opson's bottom line names the source and how
  many nutrients it gives, beside All nutrients.
- **⋮ for the rest.** Add to favorites, Remove from recents, Report issue and
  Edit a copy, as Cronometer has them.
- **Edit a copy is the whole label.** Cronometer's copy is the full custom food
  editor: name, serving sizes with weights, which serving the numbers are per,
  and every label line with %DV. That is what fixes a food listed only by
  volume, which was the owner's creamer. Opson's editor now does the same and
  saves a food of the person's own that syncs.

## What we left out

- **Label type** (Cronometer's US, Canadian and other label formats). Opson
  shows one order, a US label's, and the FDA daily values the app already uses
  for vitamins and minerals. Nobody asked for another.
- **%DV on fat, carbs and protein.** The app only has daily references for
  vitamins and minerals, and a target for the macros is the person's own, which
  Today with this already shows.
- **A ring.** A split bar says the same in less height and matches Today's card.

## How Cronometer does it

**Adding a food.** The web dialog has the serving size, quantity and diary
group at the bottom, and a circle graph of the macronutrient breakdown you can
hover for each macro's energy. On mobile, tapping a food opens the Edit Serving
dialog with the same fields and a ⋮ menu.

**Edit a copy.** On the web, a food opened in the Food Editor has an icon in
the top right with Edit a Copy; on mobile it is in the Edit Serving dialog's ⋮.
The copy lands in your custom foods. Staff advise it for exactly the creamer
case: weigh a set volume once, then add serving sizes with their weights to a
copy, because weight is the most accurate way to log.

**Custom food editor.** The first serving is the label's, and the nutrition
facts are typed for it. More serving sizes can be added once the food has a
weight, each a name and a weight ("2 cookies, 28 g"); every later serving is
worked out from grams. A full nutrient breakdown sits below the label for
anything the label leaves out.

**Volume without weight.** Staff say every food needs a weight before another
serving can be added. A forum thread from 2024 reports copies of volume-only
foods showing broken servings ("24 16 oz") until a gram weight was added to
each one. Opson avoids that class of bug by converting everything through a
serving's weight and saying plainly when there is none yet.

**Reporting.** Report an issue sends a food to the curation team, with advice
to check the serving size and rounding first.

## Sources

- [Add a Food](https://support.cronometer.com/hc/en-us/articles/360018193011-Add-a-Food), Cronometer help centre
- [Create a Custom Food](https://support.cronometer.com/hc/en-us/articles/360018240312-Create-a-Custom-Food) and [Mobile: Create a Custom Food](https://support.cronometer.com/hc/en-us/articles/360019866351-Mobile-Create-a-Custom-Food)
- [Macronutrient Breakdown](https://support.cronometer.com/hc/en-us/articles/31119712060308-Macronutrient-Breakdown)
- [Mobile: Report an Issue](https://support.cronometer.com/hc/en-us/articles/360021237511-Mobile-Report-an-Issue)
- Forum: [Serving size screwed up when cloning custom foods](https://forums.cronometer.com/discussion/6431/serving-size-screwed-up-when-cloning-copying-custom-foods), [Editing saved information](https://forums.cronometer.com/discussion/3673/editing-saved-information), [liquid measurements](https://forums.cronometer.com/discussion/3324/liquid-measurements)
