import SwiftUI

struct CustomMaterial: Codable, Identifiable, Hashable {
    var id = UUID().uuidString
    var name: String
    var density: Double
}

enum ThemeChoice: String, CaseIterable, Identifiable {
    case system, light, dark
    var id: String { rawValue }
    var scheme: ColorScheme? { self == .light ? .light : (self == .dark ? .dark : nil) }
}

@MainActor
final class AppSettings: ObservableObject {
    private let d = UserDefaults.standard

    @Published var units: UnitSystem { didSet { d.set(units.rawValue, forKey: "units") } }
    @Published var currency: String { didSet { d.set(currency, forKey: "currency") } }
    @Published var company: String { didSet { d.set(company, forKey: "company") } }
    @Published var theme: ThemeChoice { didSet { d.set(theme.rawValue, forKey: "theme") } }
    @Published var customMaterials: [CustomMaterial] {
        didSet { d.set(try? JSONEncoder().encode(customMaterials), forKey: "customMaterials") }
    }
    let firstLaunch: Date

    init() {
        if let raw = d.string(forKey: "units"), let u = UnitSystem(rawValue: raw) {
            units = u
        } else {
            // US, Liberia, Myanmar default to imperial.
            units = Locale.current.measurementSystem == .us ? .imperial : .metric
        }
        currency = d.string(forKey: "currency") ?? (Locale.current.currencySymbol ?? "€")
        company = d.string(forKey: "company") ?? ""
        theme = ThemeChoice(rawValue: d.string(forKey: "theme") ?? "") ?? .system
        if let data = d.data(forKey: "customMaterials"),
           let list = try? JSONDecoder().decode([CustomMaterial].self, from: data) {
            customMaterials = list
        } else {
            customMaterials = []
        }
        if let first = d.object(forKey: "firstLaunch") as? Date {
            firstLaunch = first
        } else {
            firstLaunch = AppClock.current.wall // = Date() in release
            d.set(firstLaunch, forKey: "firstLaunch")
        }
    }

    /// All materials: built-in + user-defined.
    var allMaterials: [Material] {
        Catalog.shared.materials + customMaterials.map {
            Material(id: "custom_" + $0.id, group: "custom", name: $0.name, density: $0.density)
        }
    }

    func material(_ id: String) -> Material {
        allMaterials.first { $0.id == id } ?? Catalog.shared.materials[0]
    }
}
