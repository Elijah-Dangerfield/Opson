# Vitamin and mineral references

What the percent on each vitamin and mineral row is a percent of, and why. The
table lives in `DietaryReferenceIntakes` (`libraries/nutrition/.../MicroReferences.kt`)
and is marked `TODO(dietitian)` until someone qualified has reviewed it.

## The rule

| What the app knows | Reference |
|---|---|
| Age and sex | The RDA for that age band and sex, or the AI where there is no RDA |
| Age, sex not given or "Prefer not to say" | Halfway between the female and male values for that band |
| Sex, no birth year | That sex's 31 to 50 band |
| Neither | The FDA Daily Value for adults |
| Their own number | Theirs, over any of the above |

Age is the birth year Resting energy collects, counted as this year minus that
year, the same way Burned reads it. The app asks for 13 or older, so the
youngest band used is 9 to 13. The bands are the DRI ones: 9 to 13, 14 to 18,
19 to 30, 31 to 50, 51 to 70, 71 and over.

Why each fallback:

- **Halfway for "Prefer not to say".** Resting energy already does this with
  the Mifflin–St Jeor constant, so one answer means the same thing on both
  pages. Where women and men share a value (folate, B12, vitamin D and others)
  halfway is just that value. Iron at 19 to 50 is the widest gap, 18 against
  8, and lands on 13.
- **31 to 50 for sex without age.** It is the longest adult band and the one
  most adults are nearest; it differs from 19 to 30 only in magnesium.
- **Daily Values when nothing is known.** They are what a food label's "%
  Daily Value" uses, so a percent here matches the pack in someone's hand. The
  table before this change mixed three older prototype numbers (vitamin A 700
  µg, vitamin D 15 µg, calcium 1,000 mg) with Daily Values; those three are now
  the Daily Values too (900 µg, 20 µg, 1,300 mg).

## Limits and own numbers

Sodium's reference is the 2019 Chronic Disease Risk Reduction intake (1,800 mg
at 9 to 13, 2,300 mg from 14), a ceiling rather than a target. It draws like
every other row: no red, no "over", and its foods sheet is not framed as foods
to cut.

A number of the person's own is used when it is positive and no more than 100
times the Daily Value. That is a typo guard (15000 for vitamin D's 15 µg), not
advice. Clearing the field goes back to the suggestion.

## Not covered

- Pregnancy and breastfeeding, which change most of these. The app does not ask.
- Upper limits (UL). Nothing here warns about a high intake.
- Conditions that change needs (kidney disease, for one). The settings page
  says a dietitian or doctor can give better numbers.

## Sources

Read 2026-10-27.

- Institute of Medicine. *Dietary Reference Intakes* summary tables for
  vitamins and for elements. National Academies Press. Reproduced, with the
  per-nutrient detail, in the NIH Office of Dietary Supplements fact sheets
  (`ods.od.nih.gov/factsheets/<Nutrient>-HealthProfessional/`).
- National Academies of Sciences, Engineering, and Medicine. *Dietary Reference
  Intakes for Sodium and Potassium* (2019). Potassium AIs and the sodium CDRR.
- Institute of Medicine. *Dietary Reference Intakes for Calcium and Vitamin D*
  (2011).
- U.S. Food and Drug Administration. 21 CFR 101.9(c)(8)(iv) and (c)(9): Daily
  Values for adults and children 4 years and older.
