# <Screen name> plan

Write this before writing code. Keep it short and specific: it is the thing a
reviewer checks the code against.

## Source
- Handoff section: `docs/design/handoff/README.md` §x.y
- Prototype markup: `docs/design/handoff/Phone.dc.html`, the `<screen>` block
- Logic: `docs/design/handoff/phone-logic.js`, functions …

## Route
`XRoute(args)`, `screen<>` or `bottomSheet<>`, which feature module.

## State
```kotlin
data class XState(...)
```
Every field, and which `Display` values it carries. No raw nutrient numbers
in state that the screen could format: carry `Display`, or carry the number
and pass it through `NutrientValue`.

## Actions
Each one named for what the user did or what arrived. One line each on what
it does.

## Events
Navigation and toasts.

## Data
Which repository calls, which flows are observed, what happens offline.

## Components
Which `:libraries:ui` and `:libraries:nutrition:ui` components, and any new
component this needs (it goes in the library, not here).

## Visibility surfaces
Every number on the screen: metric, surface, and what it shows at each level
(Show / Soften / Delay / Hide). Plus how the screen changes with
`ModePreset.today`.

## Copy
Every string, from the handoff where it exists.

## Edge cases
Empty day, future day, past day, offline, meal groups off, units, large text.

## Tests
ViewModel tests by action, domain tests needed, the `SURFACES` entry for the
leak test, previews to add.
