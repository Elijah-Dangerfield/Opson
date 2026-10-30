//
//  LabelModelExtractor.swift
//  iosApp
//
//  Asks Apple's on-device Foundation Models (iOS 26, Apple Intelligence) to
//  copy a nutrition panel's numbers out of the label's OCR text. A prototype
//  behind labels.onDeviceModel: Kotlin only keeps numbers that are printed in
//  that text (LabelModelMerge), so the model can fill a gap the parser left
//  or cast doubt on a number, never invent one. Free, offline, and nothing
//  leaves the phone. Kept apart from the Kotlin bridge so it typechecks on
//  its own.
//
import Foundation
#if canImport(FoundationModels)
import FoundationModels
#endif

enum LabelModelAnswer {
    case read(json: String, model: String)
    case unavailable
    case failed
}

enum LabelModelExtractor {
    static func extract(_ labelText: String) async -> LabelModelAnswer {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            return await FoundationLabelReader.read(labelText)
        }
        #endif
        return .unavailable
    }
}

#if canImport(FoundationModels)

@available(iOS 26.0, *)
@Generable
struct LabelColumn {
    @Guide(description: "Calories (kcal) as printed. Empty when only kJ is printed.")
    var energyKcal: Double?
    @Guide(description: "Protein in grams as printed.")
    var proteinGrams: Double?
    @Guide(description: "Total carbohydrate in grams as printed.")
    var carbohydrateGrams: Double?
    @Guide(description: "Total fat in grams as printed.")
    var fatGrams: Double?
    @Guide(description: "Dietary fiber in grams as printed.")
    var fiberGrams: Double?
    @Guide(description: "Total sugars in grams as printed.")
    var sugarsGrams: Double?
    @Guide(description: "Saturated fat in grams as printed.")
    var saturatedFatGrams: Double?
    @Guide(description: "Sodium in milligrams as printed. Empty when only salt is printed.")
    var sodiumMilligrams: Double?
}

@available(iOS 26.0, *)
@Generable
struct LabelReading {
    @Guide(description: "The serving size in grams or millilitres, the number in brackets after a household measure, e.g. 55 for '2/3 cup (55g)'. Empty when none is printed.")
    var servingSize: Double?
    @Guide(description: "The unit of servingSize: g or ml.")
    var servingUnit: String?
    @Guide(description: "Servings per container as printed.")
    var servingsPerContainer: Double?
    @Guide(description: "The per serving column. Empty when the label has none.")
    var perServing: LabelColumn?
    @Guide(description: "The per 100 g or per 100 ml column. Empty when the label has none.")
    var per100: LabelColumn?
}

@available(iOS 26.0, *)
enum FoundationLabelReader {
    static let modelName = "apple-foundation-models"

    static let instructions = """
        You copy numbers from the OCR text of one food nutrition label. \
        Copy only numbers that appear in the text, in the units printed. \
        Never estimate, calculate, round or convert. \
        Ignore percent daily value numbers. \
        Leave a field empty when its number is not in the text.
        """

    static func read(_ labelText: String) async -> LabelModelAnswer {
        let model = SystemLanguageModel.default
        guard case .available = model.availability else { return .unavailable }
        do {
            let session = LanguageModelSession(model: model, instructions: instructions)
            let response = try await session.respond(
                to: "Label text:\n\(labelText)",
                generating: LabelReading.self,
                options: GenerationOptions(samplingMode: .greedy)
            )
            guard let json = encode(response.content) else { return .failed }
            return .read(json: json, model: modelName)
        } catch {
            return .failed
        }
    }

    /// The bridge's JSON: nutrients keyed by the wire names Kotlin's LabelNutrient uses.
    static func encode(_ reading: LabelReading) -> String? {
        var root: [String: Any] = [:]
        root["servingSize"] = reading.servingSize
        root["servingUnit"] = reading.servingUnit?.lowercased()
        root["servingsPerContainer"] = reading.servingsPerContainer
        root["perServing"] = column(reading.perServing)
        root["per100"] = column(reading.per100)
        guard let data = try? JSONSerialization.data(withJSONObject: root.compactMapValues { $0 }) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private static func column(_ column: LabelColumn?) -> [String: Double] {
        guard let column else { return [:] }
        let pairs: [(String, Double?)] = [
            ("energy_kcal", column.energyKcal),
            ("protein_g", column.proteinGrams),
            ("carbs_g", column.carbohydrateGrams),
            ("fat_g", column.fatGrams),
            ("fiber_g", column.fiberGrams),
            ("sugars_g", column.sugarsGrams),
            ("saturated_fat_g", column.saturatedFatGrams),
            ("sodium_mg", column.sodiumMilligrams),
        ]
        var out: [String: Double] = [:]
        for (key, value) in pairs {
            if let value { out[key] = value }
        }
        return out
    }
}

#endif
