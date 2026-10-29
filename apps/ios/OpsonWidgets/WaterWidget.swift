//
//  WaterWidget.swift
//  OpsonWidgets
//
//  Today's water as the glasses Today's card draws, with the amount only
//  when the mode shows it. On the home screen a tap adds a glass: it opens
//  the app with opson://widget/water/add, which writes one glass of the
//  person's size to today and lands on Today, where the card shows it and a
//  tap on the last glass takes it off. The write happens in the app, never
//  here: the extension has no diary, no outbox and no idea who is signed in.
//  On the Lock Screen a tap only opens Today, because a phone in a pocket
//  shouldn't add water.
//

import SwiftUI
import WidgetKit

struct WaterWidget: Widget {
    static let kind = "OpsonWater"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: SnapshotProvider()) { entry in
            WaterWidgetView(entry: entry)
        }
        .configurationDisplayName("Water")
        .description("Today's glasses. Tap to add one.")
        .supportedFamilies([.systemSmall, .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

struct WaterWidgetView: View {
    let entry: SnapshotEntry

    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var colorScheme

    private var theme: WidgetTheme { WidgetTheme(entry.snapshot, colorScheme) }
    private var water: WidgetWater? { entry.day?.water }
    private var today: URL { URL(widgetLink: entry.snapshot?.todayLink, fallback: WidgetURLs.today) }
    private var add: URL { URL(widgetLink: entry.snapshot?.waterLink, fallback: WidgetURLs.water) }

    /// Why there is nothing to draw, when there isn't.
    private var emptyText: String? {
        guard let snapshot = entry.snapshot else { return WidgetText.openApp }
        if water != nil { return nil }
        if entry.day?.mealRows?.isEmpty == true, let note = entry.day?.note { return note }
        return snapshot.text.waterHidden
    }

    var body: some View {
        switch family {
        case .accessoryCircular: circular.accessoryBackground().widgetURL(today)
        case .accessoryRectangular: rectangular.accessoryBackground().widgetURL(today)
        case .accessoryInline: inline.accessoryBackground().widgetURL(today)
        default:
            // Adding needs a snapshot to add against; without one the tap only opens the app.
            small.opsonBackground(theme).widgetURL(water == nil ? today : add)
        }
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 8) {
            WidgetTitle(text: entry.labels.water, theme: theme)
            if let water {
                Spacer(minLength: 0)
                if let text = water.text {
                    Text(text)
                        .font(.system(size: 15, weight: .bold))
                        .monospacedDigit()
                        .foregroundStyle(theme.ink)
                        .lineLimit(2)
                        .minimumScaleFactor(0.7)
                }
                WaterSegments(filled: water.filled, total: water.total, tint: theme.nutrient("water"), track: theme.track)
                    .accessibilityElement()
                    .accessibilityLabel(water.label)
                Spacer(minLength: 0)
                HStack(spacing: 4) {
                    Image(systemName: "plus")
                        .font(.system(size: 12, weight: .heavy))
                    Text(entry.labels.addGlass)
                        .font(.system(size: 13, weight: .bold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                .foregroundStyle(theme.onAccent)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Capsule().fill(theme.accent))
                .accessibilityElement(children: .combine)
                .accessibilityLabel(water.addLabel ?? "\(entry.labels.addGlass), \(water.glass)")
            } else if let emptyText {
                Spacer(minLength: 0)
                WidgetNote(text: emptyText, theme: theme)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    @ViewBuilder
    private var circular: some View {
        if let water {
            Gauge(value: water.fraction) {
                Image(systemName: "drop.fill")
            } currentValueLabel: {
                Image(systemName: "drop.fill")
            }
            .gaugeStyle(.accessoryCircularCapacity)
            .accessibilityLabel(water.label)
        } else {
            ZStack {
                AccessoryWidgetBackground()
                Image(systemName: "drop")
            }
        }
    }

    private var rectangular: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(entry.labels.water).font(.headline).widgetAccentable()
            if let water {
                if let text = water.text {
                    Text(text).monospacedDigit().lineLimit(1).minimumScaleFactor(0.8).privacySensitive()
                }
                Gauge(value: water.fraction) { EmptyView() }
                    .gaugeStyle(.accessoryLinearCapacity)
                    .accessibilityLabel(water.label)
            } else if let emptyText {
                Text(emptyText).lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var inline: some View {
        Label(water?.text ?? entry.labels.water, systemImage: "drop.fill").privacySensitive()
    }
}

#Preview("Water", as: .systemSmall) {
    WaterWidget()
} timeline: {
    SnapshotEntry(date: .now, snapshot: .sample(numbers: true))
}

#Preview("Glasses only", as: .systemSmall) {
    WaterWidget()
} timeline: {
    SnapshotEntry(date: .now, snapshot: .sample(numbers: false))
}

#Preview("Before the app has run", as: .systemSmall) {
    WaterWidget()
} timeline: {
    SnapshotEntry(date: .now, snapshot: nil)
}

#Preview("Lock Screen", as: .accessoryRectangular) {
    WaterWidget()
} timeline: {
    SnapshotEntry(date: .now, snapshot: .sample(numbers: true))
}

#Preview("Lock Screen, circular", as: .accessoryCircular) {
    WaterWidget()
} timeline: {
    SnapshotEntry(date: .now, snapshot: .sample(numbers: true))
}
