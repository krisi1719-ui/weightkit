import Foundation
import UIKit

/// One JSON file with everything a user would miss on a new phone: lists, company details, logo,
/// own materials. Same "format" key as Android.
enum Backup {
    struct File: Codable {
        var format = "weightkit-backup"
        var version = 1
        var lists: [CutList]
        var company: String
        var companyAddress: String
        var companyContact: String
        var currency: String
        var customMaterials: [CustomMaterial]
        var logoPng: Data?
    }

    @MainActor
    static func file(lists: ListStore, settings: AppSettings) -> URL {
        let f = File(lists: lists.lists, company: settings.company, companyAddress: settings.companyAddress,
                     companyContact: settings.companyContact, currency: settings.currency,
                     customMaterials: settings.customMaterials, logoPng: settings.logoPNG)
        let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"; df.locale = Locale(identifier: "en_US_POSIX")
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("WeightKit-backup-\(df.string(from: Date())).json")
        try? JSONEncoder().encode(f).write(to: url, options: .atomic)
        return url
    }

    /// Lists are added (a list that is already here is skipped), own materials merged, company
    /// details and logo taken only where they are empty here. nil = not a WeightKit backup.
    @MainActor
    static func restore(_ data: Data, lists: ListStore, settings: AppSettings) -> Int? {
        guard let f = try? JSONDecoder().decode(File.self, from: data), f.format == "weightkit-backup" else { return nil }
        let n = lists.merge(f.lists)
        for m in f.customMaterials where !settings.customMaterials.contains(where: { $0.id == m.id }) {
            settings.customMaterials.append(m)
        }
        if settings.company.isEmpty { settings.company = f.company }
        if settings.companyAddress.isEmpty { settings.companyAddress = f.companyAddress }
        if settings.companyContact.isEmpty { settings.companyContact = f.companyContact }
        if settings.logoPNG == nil, let png = f.logoPng { _ = settings.setLogo(png) }
        return n
    }
}
