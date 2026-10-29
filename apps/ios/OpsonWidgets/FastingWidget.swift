//
//  FastingWidget.swift
//  OpsonWidgets
//
//  The fasting timer: how long the fast has run and when it ends, or how
//  long the eating window has left. The app works out the phases ahead of
//  time (FastingPhases in :libraries:widgets) and the timeline steps through
//  them, so the widget moves from fast to window with the app closed, and the
//  running clock is the system's own timer text, which ticks without reloads.
//
//  Only while the timer is on and the mode offers fasting does the snapshot
//  carry it; any other time this says the timer is off and nothing more.
//

import SwiftUI
import WidgetKit

struct FastingWidget: Widget {
    static let kind = "OpsonFasting"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: SnapshotProvider()) { entry in
            FastingWidgetView(entry: entry)
        }
        .configurationDisplayName("Fasting")
        .description("Your fasting timer, when it's on.")
        // No sizes, no place in the gallery: see FastingGate.
        .supportedFamilies(FastingGate.offered ? [.systemSmall, .accessoryRectangular] : [])
    }
}

struct FastingWidgetView: View {
    let entry: SnapshotEntry

    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var colorScheme

    private var theme: WidgetTheme { WidgetTheme(entry.snapshot, colorScheme) }
    private var phase: WidgetFastingPhase? { entry.snapshot?.fasting?.phase(at: entry.date) }
    private var offText: String { entry.snapshot == nil ? "Open Opson to start." : entry.labels.fastingOff }

    var body: some View {
        Group {
            if family == .accessoryRectangular {
                rectangular.accessoryBackground()
            } else {
                small.opsonBackground(theme)
            }
        }
        .widgetURL(URL(widgetLink: entry.snapshot?.todayLink, fallback: WidgetURLs.today))
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 6) {
            WidgetTitle(text: phase?.title ?? entry.labels.fasting, theme: theme)
            Spacer(minLength: 0)
            if let phase {
                if phase.isFasting, let since = phase.since {
                    Text(since, style: .timer)
                        .font(.system(size: 28, weight: .bold))
                        .monospacedDigit()
                        .foregroundStyle(theme.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                }
                (Text(phase.caption + " ") + Text(phase.until, style: .time))
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(phase.isFasting ? theme.muted : theme.ink)
                    .lineLimit(2)
            } else {
                WidgetNote(text: offText, theme: theme)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var rectangular: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(phase?.title ?? entry.labels.fasting).font(.headline).widgetAccentable().lineLimit(1)
            if let phase {
                if phase.isFasting, let since = phase.since {
                    Text(since, style: .timer).monospacedDigit().lineLimit(1)
                }
                (Text(phase.caption + " ") + Text(phase.until, style: .time)).lineLimit(1)
            } else {
                Text(offText).lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview("Fasting", as: .systemSmall) {
    FastingWidget()
} timeline: {
    SnapshotEntry(date: .now, snapshot: .sample(numbers: true, fasting: true))
}

#Preview("Timer off", as: .systemSmall) {
    FastingWidget()
} timeline: {
    SnapshotEntry(date: .now, snapshot: .sample(numbers: true))
}

#Preview("Lock Screen", as: .accessoryRectangular) {
    FastingWidget()
} timeline: {
    SnapshotEntry(date: .now, snapshot: .sample(numbers: true, fasting: true))
}
