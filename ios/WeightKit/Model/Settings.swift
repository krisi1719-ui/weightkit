import UIKit
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
    @Published var companyAddress: String { didSet { d.set(companyAddress, forKey: "companyAddress") } }
    @Published var companyContact: String { didSet { d.set(companyContact, forKey: "companyContact") } }
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
        currency = d.string(forKey: "currency") ?? AppSettings.defaultCurrency(Locale.current)
        company = d.string(forKey: "company") ?? ""
        companyAddress = d.string(forKey: "companyAddress") ?? ""
        companyContact = d.string(forKey: "companyContact") ?? ""
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

    /// Currency symbol for a new install. Bulgaria uses the euro since 1 Jan 2026; older locale
    /// data still reports BGN ("лв."), so BGN maps to "€".
    nonisolated static func defaultCurrency(_ locale: Locale) -> String {
        if locale.region?.identifier == "BG" || locale.currency?.identifier == "BGN" { return "€" }
        if locale.region == nil && locale.language.languageCode?.identifier == "bg" { return "€" }
        return locale.currencySymbol ?? "€"
    }

    // MARK: logo for the PDF (Pro)

    nonisolated static var logoURL: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("logo.png")
    }

    @Published private(set) var logoPNG: Data? = try? Data(contentsOf: AppSettings.logoURL)

    /// Saves a picked image as the PDF logo (scaled to at most 600 px). false = not an image.
    func setLogo(_ data: Data?) -> Bool {
        guard let data else {
            try? FileManager.default.removeItem(at: Self.logoURL); logoPNG = nil; return true
        }
        guard let img = UIImage(data: data) else { return false }
        let scale = min(1, 600 / max(img.size.width, img.size.height))
        let size = CGSize(width: max(1, img.size.width * scale), height: max(1, img.size.height * scale))
        let fmt = UIGraphicsImageRendererFormat(); fmt.scale = 1
        let png = UIGraphicsImageRenderer(size: size, format: fmt).pngData { _ in img.draw(in: CGRect(origin: .zero, size: size)) }
        try? png.write(to: Self.logoURL, options: .atomic)
        logoPNG = png
        return true
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
