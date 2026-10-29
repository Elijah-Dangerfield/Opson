//
//  WidgetParts.swift
//  OpsonWidgets
//
//  Pieces every widget shares: the colours for the current appearance, the
//  title line, the ring, the neutral line a widget shows when the mode keeps
//  its number in the app, and the sample day for previews and placeholders.
//

import SwiftUI
import WidgetKit

/// The swatch for the system appearance, from the snapshot or Slate before there is one.
struct WidgetTheme {
    let swatch: WidgetSwatch

    init(_ snapshot: WidgetSnapshot?, _ scheme: ColorScheme) {
        let colors = snapshot?.colors ?? .fallback
        swatch = scheme == .dark ? colors.dark : colors.light
    }

    var background: Color { Color(widgetHex: swatch.background) }
    var ink: Color { Color(widgetHex: swatch.ink) }
    var muted: Color { Color(widgetHex: swatch.muted) }
    var track: Color { Color(widgetHex: swatch.track) }
    var bar: Color { Color(widgetHex: swatch.bar) }
    var accent: Color { Color(widgetHex: swatch.accent) }
    var onAccent: Color { Color(widgetHex: swatch.onAccent) }

    /// A macro's or water's colour, `protein`, `carbs`, `fat`, `fiber` or `water`.
    func nutrient(_ key: String) -> Color {
        swatch.nutrients?[key].map { Color(widgetHex: $0.content) } ?? ink
    }

    func meal(_ slot: String) -> WidgetTone? { swatch.meals[slot] }
}

/// The small heavy title every home screen widget opens with.
struct WidgetTitle: View {
    let text: String
    let theme: WidgetTheme

    var body: some View {
        Text(text)
            .font(.system(size: 15, weight: .heavy))
            .foregroundStyle(theme.ink)
            .lineLimit(1)
    }
}

/// The extension's own words for before the app has written a snapshot, from
/// this target's Localizable.xcstrings. Anything the app can say, it writes
/// into the snapshot instead, in the person's language.
enum WidgetText {
    static var openApp: String { String(localized: "Open Opson to start.", comment: "A widget before the app has run, or while nobody is signed in.") }
}

/// What a widget says when there is nothing it may draw: before the app has
/// run, or when the mode keeps this widget's number in the app.
struct WidgetNote: View {
    let text: String
    let theme: WidgetTheme

    var body: some View {
        Text(text)
            .font(.system(size: 14, weight: .medium))
            .foregroundStyle(theme.muted)
            .lineLimit(3)
            .minimumScaleFactor(0.85)
    }
}

/// A ring from the top, clockwise. `range` marks where the range starts, as a
/// faint arc from there to the end, so a full ring reads as the top of it.
struct WidgetRing: View {
    let fraction: Double
    var range: Double?
    let tint: Color
    let track: Color
    var lineWidth: CGFloat = 8

    var body: some View {
        ZStack {
            Circle().stroke(track, lineWidth: lineWidth)
            if let range, range < 1 {
                Circle()
                    .trim(from: max(0, range), to: 1)
                    .stroke(tint.opacity(0.25), style: StrokeStyle(lineWidth: lineWidth, lineCap: .butt))
                    .rotationEffect(.degrees(-90))
            }
            Circle()
                .trim(from: 0, to: max(0, min(1, fraction)))
                .stroke(tint, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .accessibilityHidden(true)
    }
}

/// Glasses as Today's water card draws them: a row of segments, the drunk ones filled.
struct WaterSegments: View {
    let filled: Int
    let total: Int
    let tint: Color
    let track: Color

    var body: some View {
        HStack(spacing: 3) {
            ForEach(0..<max(total, 1), id: \.self) { index in
                Capsule()
                    .fill(index < filled ? tint : track)
                    .frame(height: 8)
            }
        }
        .accessibilityHidden(true)
    }
}

extension View {
    /// The widget's background in either appearance, from the app's theme.
    func opsonBackground(_ theme: WidgetTheme) -> some View {
        containerBackground(for: .widget) { theme.background }
    }

    /// Accessory widgets draw on the system's own Lock Screen material.
    func accessoryBackground() -> some View {
        containerBackground(for: .widget) { Color.clear }
    }
}

extension URL {
    /// A widget link from the snapshot, or the built-in one for a snapshot that predates it.
    init(widgetLink: String?, fallback: String) {
        self = URL(string: widgetLink ?? fallback) ?? URL(string: fallback)!
    }
}

enum WidgetURLs {
    static let today = "opson://widget/today"
    static let log = "opson://widget/log"
    static let water = "opson://widget/water/add"
}

extension WidgetColors {
    /// Slate, for the moment before the app has written a snapshot: the
    /// placeholder, a widget added from the gallery before sign-in, and after
    /// sign-out. Every colour after that comes from the snapshot, which reads
    /// the app's theme (`WidgetPalette` in :libraries:widgets).
    static let fallback = WidgetColors(
        light: WidgetSwatch(
            background: "#EEF1F6", ink: "#18202E", muted: "#5A6478", track: "#E0E5ED",
            bar: "#18202E", accent: "#A3500A", onAccent: "#FFFFFF", meals: [:], nutrients: nil
        ),
        dark: WidgetSwatch(
            background: "#161B27", ink: "#E6EAF2", muted: "#98A2B4", track: "#323B4D",
            bar: "#E6EAF2", accent: "#FA9E5C", onAccent: "#1F120A", meals: [:], nutrients: nil
        )
    )
}

// MARK: - Sample day
//
// What the app's builder writes for the design's day (v3 widgets.png):
// breakfast, lunch and a snack, 1,245 kcal against 1,900 to 2,300. For the
// Xcode canvas and WidgetKit's redacted placeholder only; a widget on a home
// screen or in the gallery always draws what the app wrote.

extension WidgetSnapshot {
    static func sample(numbers: Bool, fasting: Bool = false, now: Date = .now) -> WidgetSnapshot {
        let today = WidgetDates.iso(now)
        let tones: [String: WidgetTone] = [
            "breakfast": WidgetTone(content: "#C4920A", container: "#FBF0D0"),
            "lunch": WidgetTone(content: "#3D7FD9", container: "#DFEAFA"),
            "snack": WidgetTone(content: "#8E6E3A", container: "#F1E8DC"),
            "dinner": WidgetTone(content: "#D1477A", container: "#FAE3EC"),
        ]
        let nutrients: [String: WidgetTone] = [
            "protein": tones["dinner"]!, "carbs": tones["breakfast"]!, "fat": tones["lunch"]!,
            "fiber": tones["snack"]!, "water": tones["lunch"]!,
        ]
        let meals = [
            WidgetMeal(slot: "breakfast", label: "Breakfast", done: true, description: "Breakfast, done"),
            WidgetMeal(slot: "lunch", label: "Lunch", done: true, description: "Lunch, done"),
            WidgetMeal(slot: "snack", label: "Snack", done: true, description: "Snack, done"),
            WidgetMeal(slot: "dinner", label: "Dinner", done: false, description: "Dinner, not yet"),
        ]
        let rows = [
            WidgetMealRow(slot: "breakfast", label: "Breakfast", done: true, foods: "Oat porridge with milk, banana, coffee with milk",
                          calories: numbers ? "445 kcal" : nil, link: "opson://widget/log?slot=breakfast"),
            WidgetMealRow(slot: "lunch", label: "Lunch", done: true, foods: "Chicken adobo, jasmine rice, cooked",
                          calories: numbers ? "515 kcal" : nil, link: "opson://widget/log?slot=lunch"),
            WidgetMealRow(slot: "snack", label: "Snack", done: true, foods: "Apple, peanut butter",
                          calories: numbers ? "285 kcal" : nil, link: "opson://widget/log?slot=snack"),
            WidgetMealRow(slot: "dinner", label: "Dinner", done: false, foods: "Nothing yet", calories: nil, link: "opson://widget/log?slot=dinner"),
        ]
        let energy = WidgetEnergy(
            amount: "1,245", unit: "kcal", label: "1,245 kcal",
            fraction: 1245.0 / 2760, rangeStart: 1900.0 / 2760, rangeEnd: 2300.0 / 2760,
            ring: 1245.0 / 2300, ringRangeStart: 1900.0 / 2300
        )
        let macros = [
            WidgetMacro(key: "protein", label: "Protein", amount: "84", unit: "g", text: "84 / 120 g", fraction: 0.7),
            WidgetMacro(key: "carbs", label: "Carbs", amount: "210", unit: "g", text: "210 / 250 g", fraction: 0.84),
            WidgetMacro(key: "fat", label: "Fat", amount: "56", unit: "g", text: "56 / 70 g", fraction: 0.8),
            WidgetMacro(key: "fiber", label: "Fiber", amount: "21", unit: "g", text: "21 / 30 g", fraction: 0.7),
        ]
        let water = WidgetWater(text: numbers ? "1,250 of 2,000 ml" : nil, filled: 5, total: 8,
                                label: numbers ? "1,250 of 2,000 ml" : "5 of 8 glasses", glass: "250 ml")
        let slate = WidgetColors.fallback
        let light = WidgetSwatch(
            background: slate.light.background, ink: slate.light.ink, muted: slate.light.muted,
            track: slate.light.track, bar: slate.light.bar, accent: slate.light.accent,
            onAccent: slate.light.onAccent, meals: tones, nutrients: nutrients
        )
        let start = Calendar.current.startOfDay(for: now)
        let fastingTimer = WidgetFasting(plan: "14:10", phases: [
            WidgetFastingPhase(fromMs: 0, kind: "fasting", title: "Fasting", caption: "Ends at",
                               untilMs: Int64(start.addingTimeInterval(9 * 3600).timeIntervalSince1970 * 1000),
                               sinceMs: Int64(start.addingTimeInterval(-5 * 3600).timeIntervalSince1970 * 1000)),
        ])
        return WidgetSnapshot(
            version: WidgetSnapshot.supportedVersion,
            days: [
                WidgetDay(
                    day: today, title: "Today", energy: numbers ? energy : nil, meals: numbers ? [] : meals, note: nil,
                    left: numbers ? WidgetFigure(amount: "655", unit: "to range", label: "655 left") : nil,
                    progress: WidgetProgress(done: 3, total: 4, label: "3 of 4 meals"),
                    macros: numbers ? macros : [], water: water, mealRows: rows
                ),
            ],
            logLabel: "Log food",
            logLink: WidgetURLs.log,
            colors: WidgetColors(light: light, dark: slate.dark),
            todayLink: WidgetURLs.today,
            waterLink: WidgetURLs.water,
            fasting: fasting ? fastingTimer : nil,
            labels: .fallback
        )
    }
}
