//
//  FileSharePresenter.swift
//  iosApp
//
//  Presents the system share sheet for a file Kotlin has already written
//  (Router.shareFile), or for a line of text such as a link (Router.shareText).
//  Kotlin keeps the bytes and hands over a path, so nothing here touches
//  KotlinByteArray.
//
import ComposeApp
import UIKit

class IOSFileSharePresenter: FileSharePresenter {

    func present(filePath: String) {
        show(items: [URL(fileURLWithPath: filePath)])
    }

    /// A link goes as a URL, so Messages and Mail show it as one; other text as it is.
    func presentText(text: String) {
        if let url = URL(string: text), url.scheme == "https" || url.scheme == "http" {
            show(items: [url])
        } else {
            show(items: [text])
        }
    }

    private func show(items: [Any]) {
        DispatchQueue.main.async {
            guard let presenter = Self.topViewController() else { return }
            let sheet = UIActivityViewController(activityItems: items, applicationActivities: nil)
            // iPad shows the sheet as a popover, which needs an anchor or UIKit raises.
            if let popover = sheet.popoverPresentationController {
                popover.sourceView = presenter.view
                popover.sourceRect = CGRect(
                    x: presenter.view.bounds.midX,
                    y: presenter.view.bounds.midY,
                    width: 0,
                    height: 0
                )
                popover.permittedArrowDirections = []
            }
            presenter.present(sheet, animated: true)
        }
    }

    /// The controller on top, so the sheet shows over whatever Compose is presenting.
    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = scenes.first(where: { $0.activationState == .foregroundActive }) ?? scenes.first
        let window = scene?.windows.first(where: { $0.isKeyWindow }) ?? scene?.windows.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}
