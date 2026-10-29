//
//  IOSTextRecognitionBridge.swift
//  iosApp
//
//  Reads the text on a nutrition label photo with Vision, on the device
//  (IosTextRecognizer in Kotlin). Kotlin writes the JPEG and hands over a
//  path, so nothing here touches KotlinByteArray. Nothing is logged from the
//  image or the text.
//
import ComposeApp
import UIKit
import Vision

final class IOSTextRecognitionBridge: TextRecognitionBridge {

    /// Languages a label in the shops we see is likely to be printed in. Only
    /// the ones this iOS version supports are asked for.
    private static let wantedLanguages = ["en-US", "fr-FR", "de-DE", "es-ES", "it-IT", "pt-BR", "nl-NL"]

    private let queue = DispatchQueue(label: "com.opson.text-recognition", qos: .userInitiated)

    // Not RecognizeDocumentsRequest (iOS 26) yet: its table structure would help, but plain
    // lines feed the same Kotlin parser on every iOS version the app supports.
    func recognize(imagePath: String, callback: TextRecognitionCallback) {
        queue.async {
            guard let cgImage = UIImage(contentsOfFile: imagePath)?.cgImage else {
                callback.finished(imageWidth: 0, imageHeight: 0, error: "failed")
                return
            }
            let width = Int32(cgImage.width)
            let height = Int32(cgImage.height)

            let request = VNRecognizeTextRequest()
            request.recognitionLevel = .accurate
            // Numbers matter more than words here, and correction "fixes" 0 into O.
            request.usesLanguageCorrection = false
            // Labels are dense: a row of a nutrition table can be a sixtieth of the photo.
            request.minimumTextHeight = 0.01
            guard let supported = try? request.supportedRecognitionLanguages() else {
                callback.finished(imageWidth: width, imageHeight: height, error: "unavailable")
                return
            }
            let languages = Self.wantedLanguages.filter { supported.contains($0) }
            if !languages.isEmpty {
                request.recognitionLanguages = languages
            }

            let handler = VNImageRequestHandler(cgImage: cgImage, orientation: .up, options: [:])
            do {
                try handler.perform([request])
            } catch {
                callback.finished(imageWidth: width, imageHeight: height, error: "failed")
                return
            }

            for observation in request.results ?? [] {
                guard let candidate = observation.topCandidates(1).first else { continue }
                let box = observation.boundingBox
                // Vision's box is normalised with its origin bottom-left; Kotlin's is top-left.
                callback.line(
                    text: candidate.string,
                    confidence: Double(candidate.confidence),
                    left: Double(box.minX),
                    top: Double(1 - box.maxY),
                    width: Double(box.width),
                    height: Double(box.height)
                )
            }
            callback.finished(imageWidth: width, imageHeight: height, error: nil)
        }
    }
}
