//
//  MealsWidget.swift
//  OpsonWidgets
//
//  What's logged today, meal by meal: a tick or a dashed ring, the foods by
//  name, and each meal's energy only when the mode shows calories. A row
//  opens the Logger on its meal; the rest opens Today.
//

import SwiftUI
import WidgetKit

struct MealsWidget: Widget {
    static let kind = "OpsonMeals"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: SnapshotProvider()) { entry in
            MealsWidgetView(entry: entry)
        }
        .configurationDisplayName("Meals")
        .description("What you've logged today. Tap a meal to add to it.")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

struct MealsWidgetView: View {
    let entry: SnapshotEntry

    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var colorScheme

    private var theme: WidgetTheme { WidgetTheme(entry.snapshot, colorScheme) }
    private var large: Bool { family == .systemLarge }
    private var rows: [WidgetMealRow] {
        let all = entry.day?.mealRows ?? []
        // Medium has room for the four meals; anything logged without one waits for large.
        return large ? all : Array(all.prefix(4))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: large ? 12 : 6) {
            HStack(alignment: .firstTextBaseline) {
                WidgetTitle(text: entry.labels.meals, theme: theme)
                Spacer(minLength: 8)
                if let progress = entry.day?.progress {
                    Text(progress.label)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(theme.muted)
                        .lineLimit(1)
                }
            }
            if entry.snapshot == nil {
                Spacer(minLength: 0)
                WidgetNote(text: "Open Opson to start.", theme: theme)
                Spacer(minLength: 0)
            } else if rows.isEmpty {
                Spacer(minLength: 0)
                WidgetNote(text: entry.day?.note ?? entry.labels.nothingYet, theme: theme)
                Spacer(minLength: 0)
            } else {
                ForEach(rows) { row in
                    Link(destination: URL(widgetLink: row.link, fallback: WidgetURLs.log)) {
                        MealRowView(row: row, theme: theme, lines: large ? 2 : 1)
                    }
                }
                Spacer(minLength: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .opsonBackground(theme)
        .widgetURL(URL(widgetLink: entry.snapshot?.todayLink, fallback: WidgetURLs.today))
    }
}

private struct MealRowView: View {
    let row: WidgetMealRow
    let theme: WidgetTheme
    let lines: Int

    var body: some View {
        HStack(alignment: .center, spacing: 10) {
            circle
            VStack(alignment: .leading, spacing: 1) {
                Text(row.label)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(theme.ink)
                    .lineLimit(1)
                if !row.foods.isEmpty {
                    Text(row.foods)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(theme.muted)
                        .lineLimit(lines)
                }
            }
            Spacer(minLength: 6)
            if let calories = row.calories {
                Text(calories)
                    .font(.system(size: 12, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(theme.ink)
                    .lineLimit(1)
            }
        }
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var circle: some View {
        let tone = theme.meal(row.slot)
        let content = tone.map { Color(widgetHex: $0.content) } ?? theme.muted
        if row.done {
            Circle()
                .fill(tone.map { Color(widgetHex: $0.container) } ?? theme.track)
                .frame(width: 22, height: 22)
                .overlay(
                    Image(systemName: "checkmark")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(content)
                )
        } else {
            Circle()
                .strokeBorder(content, style: StrokeStyle(lineWidth: 1.5, dash: [3, 3]))
                .frame(width: 22, height: 22)
        }
    }
}

#Preview("Medium", as: .systemMedium) {
    MealsWidget()
} timeline: {
    SnapshotEntry(date: .now, snapshot: .sample(numbers: true))
}

#Preview("Large, no numbers", as: .systemLarge) {
    MealsWidget()
} timeline: {
    SnapshotEntry(date: .now, snapshot: .sample(numbers: false))
}

#Preview("Before the app has run", as: .systemMedium) {
    MealsWidget()
} timeline: {
    SnapshotEntry(date: .now, snapshot: nil)
}
