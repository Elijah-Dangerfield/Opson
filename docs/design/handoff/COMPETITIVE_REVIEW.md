# How we compare to MacroFactor and Cronometer

Based on the seven screenshots you shared, October 2026.

## What they have and how they organize it

**Navigation** (both): 5 slots with a raised center +. MacroFactor uses Dashboard, Food Log, +, Strategy and More. Cronometer uses Discover/Diary, Foods, +, Trends/More. Ours is Today, Trends, +, Foods, More, which matches what people expect.

**Logging** is where they differ most from us.
- MacroFactor's logger is one full-height sheet. Up top: the day's totals against target (`0 / 1869`, P, F, C). Below that: method tabs (Scan, Search, AI, Quick Add, Library), a **Favorites** row of round icons, **time-of-day picks** ("3 PM Picks"), and **Latest**. Every row has a **+** and a compact macro line (`87 kcal 1P 0F 22C • 98 g`). You pick several foods and log them all with one **Log Foods** button. Search sits pinned at the bottom where your thumb is.
- Cronometer: the + opens a menu, and the diary is a flat list with kcal on every row. Weight and watch energy show up inline as diary rows.

**Daily summary**
- Cronometer has three rings (Consumed, Expenditure, Remaining) in a carousel, plus a page of nutrient bars with % of target.
- MacroFactor shows a 7-day grid of macro bars with a Consumed/Remaining toggle.

**Foods hub** (Cronometer): Custom Meals, Custom Recipes (create or import), Custom Foods and Repeat Items. Each is a card with a description and one clear "Create" button.

**Targets**: Cronometer has "Macro & Energy Targets" (Ratios / Fixed / Keto, energy target calculated from goal). MacroFactor has "Strategy" with goal, check-ins and a weekly program.

**Trends**: MacroFactor has Expenditure and Weight Trend with 1W/1M/3M/6M/1Y/All range chips, scale weight vs trend weight, and "Insights & Data" showing 3- and 7-day change.

## Where we were behind

1. **Logging took two steps.** Our + sheet led to a separate Log screen. You couldn't add several foods at once or search inside the sheet, and food rows didn't show macros. This is the most important gap because it happens several times a day.
2. **No targets screen.** You had no place to see or change your energy range or macro split. Every competitor has one.
3. **Foods tab had placeholders** where Create a meal and Add a custom food should have been.
4. **Weight was fixed to 4 weeks.** There were no range chips.
5. **No expenditure trend.** Watch energy showed only today.

## Where we're already ahead (keep these)

- One visibility policy across every surface. Neither app can hide numbers consistently.
- Blind mode shows meal tiles instead of an empty diary.
- Precision tags (Weighed / Estimated / Just the name), "Ate, no details", and copying yesterday's meal.
- No red/green over/under coloring. Cronometer shows 109% in green and 134% in orange.
- Day navigation and the close-day check, which match Cronometer's pattern.

## What changed in Phone.dc.html

- **The + is now a logger like MacroFactor's.** It has a meal picker, a live totals strip (meal checkmarks in modes that hide numbers), search with a scan button, method chips (Photo, Describe, Saved meals, Yesterday's, Ate no details, Weigh-in, Just a number/Add a note), a Favorites row, "Usual at dinner" picks and Recent. Each row has **+ to pick**. A pinned footer adds everything you picked in one tap. Tap a food's name to set the amount, then return to the list.
- **Food rows show compact macros** (`1 medium · 1P 27C 0F`). Each macro goes through the policy, so it drops out wherever that nutrient is hidden.
- **Create a meal**: name, usual meal, foods with remove, and a total. Saved meals appear in the logger.
- **Add a custom food**: name, brand, serving and nutrition fields. Only the fields your mode allows are shown. In Blind there are no fields; you can save by name or photograph the label so it's stored without being shown. New foods show up in search and under "Your foods".
- **Targets** (More → Targets): energy range with Lower/Raise, macro split by Ratios or Grams, and the exercise adjustment. Lower is blocked below your starting target until a dietitian sets `GOAL_FLOOR_KCAL`. Everything is out of view unless calories are shown.
- **Trends**: a new Energy out card (7-day average, shown only when watch energy is visible). Weight has 1W/1M/3M range chips.
- The Foods tab now puts Create first, then Your foods, Saved meals and Recent.

## Round 2 (Cronometer diary, menus, water, fasting)

- **Summary card swipes through 3 pages**, like Cronometer: Energy (Eaten · Expended · Target rings), Macros (bars), and Nutrition scores (Overall, Vitamins, Minerals, Bone, Immune, Energy). Scores are built from vitamins and minerals, which are off by default, so page 3 offers "Turn on". "Watch, out" is renamed **Expended** everywhere.
- **Edge-to-edge cards** on Today.
- **Header**: close-day check · ‹ Today › · + · ⋯. Tap the date to open a **calendar** with dots on days that have something logged.
- **Options are tucked away.** The **+ menu** has Food, Scan, Photo, Describe, Water, Weigh-in, Note and Start a fast. The **⋯ menu** has Close the day, This day's nutrients, Copy previous day, Copy this day to tomorrow, Add a note, Hide/Show water, Fasting, and Clear this day (you tap twice to confirm).
- **Water**: 8 tappable glasses on Today, plus a chip in the logger. Water is a VisibilityPolicy metric. Full numbers shows "3 of 8 glasses · 750 ml". Gentle modes show the glasses with no count.
- **Fasting** (More → Fasting): off by default, and only offered in Full numbers and Sprint. Every other mode explains why it's not there and points to meal reminders. It has 12:12, 14:10 and 16:8 windows and an optional card on Today. Logging food always ends a fast. There are no streaks, no longest-fast records, and nothing over 16 hours. **TODO(clinician):** confirm the list of allowed modes and the 16 h cap.
- Manual **exercise logging** is left out on purpose. Exercise entries that cancel out calories encourage compensating; watch data already covers this.

## What I left out on purpose

- **"Remaining" as its own ring or a Remaining toggle.** A big "what's left" number pushes toward eating less at night. We already show "On target" instead of "0 left".
- **Energy balance with a difference (`2054 − 2389 = −335`).** Framing the day as a deficit is risky. Energy in and energy out stay as separate cards.
- **Weight change readouts** ("−1.6 lbs, Decrease"). I'd want a clinician's view before adding them, even in Full numbers.
- **Weight as a row in the diary.** It's hidden in most of our modes, so it stays in Trends.
- **Recipe import from a URL, Repeat items, and Strategy check-ins.** These are good v1.1 candidates. Repeat items fits Rhythm mode well.
