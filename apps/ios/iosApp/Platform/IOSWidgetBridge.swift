//
//  IOSWidgetBridge.swift
//  iosApp
//
//  Hands the widgets their snapshot. Kotlin builds the snapshot (the
//  visibility policy already applied), settles bursts of writes, and calls
//  write(json:) on the main thread; this stores it where the OpsonWidgets
//  extension reads it and asks WidgetKit to redraw. WidgetKit has no
//  Objective-C API, which is why this half lives in Swift.
//
//  The snapshot is a file in the App Group container, replaced whole
//  (atomically) before the reload is asked for, so the extension reads the
//  new day the moment WidgetKit asks. It used to live in the group's
//  UserDefaults, which a running extension caches: WidgetKit could ask for a
//  timeline before that cache heard of the write, and the widget kept showing
//  the day as it was before the log. The old key is removed on every write
//  so it can never be read in the file's place.
//
//  Called whatever release.widgets says (that flag is only the More row): the
//  extension is in the widget gallery in every build. Both targets need the
//  App Group entitlement; without it there is no container, nothing is
//  shared, and the widget says "Open Opson" for good.
//
import ComposeApp
import Foundation
import UIKit
import WidgetKit

final class IOSWidgetBridge: WidgetBridge {

    /// The same names as `WidgetStore` in apps/ios/OpsonWidgets. Change both together.
    static let appGroup = "group.com.opson.Opson"
    static let fileName = "widget-snapshot.json"
    static let legacyKey = "widgets.snapshot"

    private var foreground: NSObjectProtocol?

    init() {
        // Coming back to the app, redraw from what is stored: cheap while the
        // app is in front (WidgetKit doesn't count it against the budget), and
        // it mends a widget that missed a reload while the app was away.
        foreground = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main
        ) { _ in
            WidgetCenter.shared.reloadAllTimelines()
        }
    }

    deinit {
        if let foreground { NotificationCenter.default.removeObserver(foreground) }
    }

    func write(json: String?) {
        if let url = Self.fileURL {
            do {
                if let json {
                    try Data(json.utf8).write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
                } else if FileManager.default.fileExists(atPath: url.path) {
                    try FileManager.default.removeItem(at: url)
                }
            } catch {
                NSLog("Opson widgets: writing the snapshot failed: %@", error.localizedDescription)
            }
        }
        UserDefaults(suiteName: Self.appGroup)?.removeObject(forKey: Self.legacyKey)
        WidgetCenter.shared.reloadAllTimelines()
    }

    private static var fileURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)?
            .appendingPathComponent(fileName, isDirectory: false)
    }
}
