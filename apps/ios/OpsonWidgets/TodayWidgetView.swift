//
//  TodayWidgetView.swift
//  OpsonWidgets
//
//  Laid out as WidgetCard (the Compose lookalike in :libraries:widgets) and
//  the Glance widget. Measurements are WidgetCard's WidgetDimens, by hand.
//

import SwiftUI
import WidgetKit

struct TodayWidgetView: View {
    let entry: TodayEntry

    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var colorScheme

    private var small: Bool { family == .systemSmall }
    private var colors: WidgetColors { entry.snapshot?.colors ?? .fallback }
    private var swatch: WidgetSwatch { colorScheme == .dark ? colors.dark : colors.light }
    private var logURL: URL? { URL(string: entry.snapshot?.logLink ?? "opson://widget/log") }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center) {
                Text(entry.day?.title ?? "Opson")
                    .font(.system(size: 16, weight: .heavy))
                    .foregroundStyle(Color(widgetHex: swatch.ink))
                    .lineLimit(1)
                Spacer(minLength: 8)
                logButton
            }
            Spacer(minLength: 8)
            content
        }
        .containerBackground(for: .widget) { Color(widgetHex: swatch.background) }
        // A small widget is one tap target, so the whole of it is the +.
        .widgetURL(small ? logURL : nil)
    }

    @ViewBuilder
    private var logButton: some View {
        let button = RoundedRectangle(cornerRadius: 10, style: .continuous)
            .fill(Color(widgetHex: swatch.accent))
            .frame(width: 36, height: 36)
            .overlay(
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Color(widgetHex: swatch.onAccent))
            )
            .accessibilityElement()
            .accessibilityLabel(entry.snapshot?.logLabel ?? "Log food")
        if !small, let logURL {
            Link(destination: logURL) { button }
        } else {
            button
        }
    }

    @ViewBuilder
    private var content: some View {
        if let day = entry.day {
            if let energy = day.energy {
                EnergyView(energy: energy, swatch: swatch, small: small)
            } else if !day.meals.isEmpty {
                MealsView(meals: day.meals, swatch: swatch, labels: !small)
            } else if let note = day.note {
                noteText(note)
            }
        } else {
            noteText("Open Opson to start.")
        }
    }

    private func noteText(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 14, weight: .medium))
            .foregroundStyle(Color(widgetHex: swatch.muted))
            .lineLimit(2)
    }
}

private struct EnergyView: View {
    let energy: WidgetEnergy
    let swatch: WidgetSwatch
    let small: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .lastTextBaseline, spacing: 4) {
                Text(energy.amount)
                    .font(.system(size: small ? 22 : 30, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(Color(widgetHex: swatch.ink))
                Text(energy.unit)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Color(widgetHex: swatch.muted))
            }
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(energy.label)
            EnergyBar(energy: energy, swatch: swatch)
        }
    }
}

/// The eaten bar, and the range as a dashed box over it, as Today's hero bar.
private struct EnergyBar: View {
    let energy: WidgetEnergy
    let swatch: WidgetSwatch

    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color(widgetHex: swatch.track))
                    .frame(height: 8)
                Capsule()
                    .fill(Color(widgetHex: swatch.bar))
                    .frame(width: width * energy.fraction, height: 8)
                RoundedRectangle(cornerRadius: 4)
                    .strokeBorder(Color(widgetHex: swatch.accent), style: StrokeStyle(lineWidth: 1.5, dash: [3, 3]))
                    .frame(width: max(0, width * (energy.rangeEnd - energy.rangeStart)), height: 14)
                    .offset(x: width * energy.rangeStart)
            }
            .frame(height: 14)
        }
        .frame(height: 14)
        .accessibilityHidden(true)
    }
}

private struct MealsView: View {
    let meals: [WidgetMeal]
    let swatch: WidgetSwatch
    let labels: Bool

    var body: some View {
        HStack(spacing: 0) {
            ForEach(meals) { meal in
                VStack(spacing: 6) {
                    MealCircle(meal: meal, tone: swatch.meals[meal.slot], fallback: swatch.muted, size: labels ? 40 : 26)
                    if labels {
                        Text(meal.label)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(Color(widgetHex: swatch.ink))
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                }
                .frame(maxWidth: .infinity)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(meal.description)
            }
        }
    }
}

private struct MealCircle: View {
    let meal: WidgetMeal
    let tone: WidgetTone?
    let fallback: String
    let size: CGFloat

    var body: some View {
        let content = Color(widgetHex: tone?.content ?? fallback)
        if meal.done {
            Circle()
                .fill(Color(widgetHex: tone?.container ?? fallback))
                .frame(width: size, height: size)
                .overlay(
                    Image(systemName: "checkmark")
                        .font(.system(size: size * 0.4, weight: .bold))
                        .foregroundStyle(content)
                )
        } else {
            Circle()
                .strokeBorder(content, style: StrokeStyle(lineWidth: 1.5, dash: [3, 3]))
                .frame(width: size, height: size)
        }
    }
}

extension WidgetColors {
    /// Pine, for the moment before the app has written a snapshot. Every
    /// colour after that comes from the snapshot, which reads the app's theme.
    static let fallback = WidgetColors(
        light: WidgetSwatch(
            background: "#F2F4F3", ink: "#16201E", muted: "#5A6461", track: "#E6EAE8",
            bar: "#16201E", accent: "#1D7A6B", onAccent: "#FFFFFF", meals: [:]
        ),
        dark: WidgetSwatch(
            background: "#0E1312", ink: "#EAF1EF", muted: "#9EA8A6", track: "#252C2B",
            bar: "#EAF1EF", accent: "#5CC2AE", onAccent: "#0E1312", meals: [:]
        )
    )
}

// MARK: - Previews
//
// What the app's builder writes for the design's day (v3 widgets.png):
// breakfast, lunch and a snack, 1,245 kcal against 1,900 to 2,300. Sample
// data for the canvas only; the real widget never builds a number itself.

extension WidgetSnapshot {
    static func preview(numbers: Bool) -> WidgetSnapshot {
        let today = WidgetDates.iso(.now)
        let tones: [String: WidgetTone] = [
            "breakfast": WidgetTone(content: "#E0892B", container: "#FCEBD8"),
            "lunch": WidgetTone(content: "#3D7FD9", container: "#DFEAFA"),
            "snack": WidgetTone(content: "#8E6E3A", container: "#F1E8DC"),
            "dinner": WidgetTone(content: "#D1477A", container: "#FAE3EC"),
        ]
        let meals = [
            WidgetMeal(slot: "breakfast", label: "Breakfast", done: true, description: "Breakfast, done"),
            WidgetMeal(slot: "lunch", label: "Lunch", done: true, description: "Lunch, done"),
            WidgetMeal(slot: "snack", label: "Snack", done: true, description: "Snack, done"),
            WidgetMeal(slot: "dinner", label: "Dinner", done: false, description: "Dinner, not yet"),
        ]
        let energy = WidgetEnergy(
            amount: "1,245", unit: "kcal", label: "1,245 kcal",
            fraction: 1245.0 / 2760, rangeStart: 1900.0 / 2760, rangeEnd: 2300.0 / 2760
        )
        let light = WidgetSwatch(
            background: "#F2F4F3", ink: "#16201E", muted: "#5A6461", track: "#E6EAE8",
            bar: "#16201E", accent: "#1D7A6B", onAccent: "#FFFFFF", meals: tones
        )
        return WidgetSnapshot(
            version: WidgetSnapshot.supportedVersion,
            days: [
                WidgetDay(day: today, title: "Today", energy: numbers ? energy : nil, meals: numbers ? [] : meals, note: nil),
            ],
            logLabel: "Log food",
            logLink: "opson://widget/log",
            colors: WidgetColors(light: light, dark: WidgetColors.fallback.dark)
        )
    }
}

#Preview("Full numbers", as: .systemMedium) {
    TodayWidget()
} timeline: {
    TodayEntry(date: .now, snapshot: .preview(numbers: true))
}

#Preview("Blind and gentle modes", as: .systemMedium) {
    TodayWidget()
} timeline: {
    TodayEntry(date: .now, snapshot: .preview(numbers: false))
}

#Preview("Small, full numbers", as: .systemSmall) {
    TodayWidget()
} timeline: {
    TodayEntry(date: .now, snapshot: .preview(numbers: true))
}

#Preview("Small, blind", as: .systemSmall) {
    TodayWidget()
} timeline: {
    TodayEntry(date: .now, snapshot: .preview(numbers: false))
}

#Preview("Before the app has run", as: .systemMedium) {
    TodayWidget()
} timeline: {
    TodayEntry(date: .now, snapshot: nil)
}
