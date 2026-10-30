//
//  IOSLabelModelBridge.swift
//  iosApp
//
//  The Kotlin LabelModelBridge over LabelModelExtractor (Foundation Models).
//  Only the label's OCR text comes in; nothing from it is logged.
//
import ComposeApp
import Foundation

final class IOSLabelModelBridge: LabelModelBridge {
    func extract(labelText: String, callback: LabelModelCallback) {
        Task.detached(priority: .userInitiated) {
            switch await LabelModelExtractor.extract(labelText) {
            case let .read(json, model):
                callback.finished(json: json, model: model, error: nil)
            case .unavailable:
                callback.finished(json: nil, model: nil, error: "unavailable")
            case .failed:
                callback.finished(json: nil, model: nil, error: "failed")
            }
        }
    }
}
