//
//  FileSharePresenter.swift
//  iosApp
//
//  Presents the system share sheet for a file Kotlin has already written
//  (Router.shareFile). Kotlin keeps the bytes and hands over a path, so
//  nothing here touches KotlinByteArray.
//
import ComposeApp
import UIKit

class IOSFileSharePresenter: FileSharePresenter {

    func present(filePath: String) {
        let url = URL(fileURLWithPath: filePath)
        DispatchQueue.main.async {
            guard let presenter = Self.topViewController() else { return }
            let sheet = UIActivityViewController(activityItems: [url], applicationActivities: nil)
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
