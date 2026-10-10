import Foundation

struct ListItem: Codable, Identifiable, Hashable {
    var id = UUID()
    var shape: String
    var material: String
    var size: String
    var qty: Int
    var pieceKg: Double
    /// Price per kg at the time the item was added (nil = no price).
    var pricePerKg: Double?

    var totalKg: Double { pieceKg * Double(qty) }
    var cost: Double? { pricePerKg.map { $0 * totalKg } }
}

struct CutList: Codable, Identifiable, Hashable {
    var id = UUID()
    var name: String
    var created = Date()
    var wastePct: Double = 0
    var markupPct: Double = 0
    var items: [ListItem] = []

    var netKg: Double { items.reduce(0) { $0 + $1.totalKg } }
    var grossKg: Double { netKg * (1 + wastePct / 100) }
    var hasCost: Bool { items.contains { $0.pricePerKg != nil } }
    var netCost: Double { items.reduce(0) { $0 + ($1.cost ?? 0) } }
    /// Cost incl. waste and markup.
    var totalCost: Double { netCost * (1 + wastePct / 100) * (1 + markupPct / 100) }
}

@MainActor
final class ListStore: ObservableObject {
    @Published var lists: [CutList] = [] { didSet { save() } }

    private let url: URL

    nonisolated static var defaultURL: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("lists.json")
    }

    nonisolated static func deleteSavedLists() { try? FileManager.default.removeItem(at: defaultURL) }

    init(url: URL = ListStore.defaultURL) {
        self.url = url
        if let data = try? Data(contentsOf: url),
           let l = try? JSONDecoder().decode([CutList].self, from: data) {
            lists = l
        }
    }

    private func save() {
        if let data = try? JSONEncoder().encode(lists) {
            try? data.write(to: url, options: .atomic)
        }
    }

    /// Adds an item to the most recent list (creating one if needed). Returns the list name.
    @discardableResult
    func add(_ item: ListItem, toListID: UUID? = nil) -> String {
        if let id = toListID, let i = lists.firstIndex(where: { $0.id == id }) {
            lists[i].items.append(item)
            return lists[i].name
        }
        if lists.isEmpty {
            lists.append(CutList(name: L("default_list")))
        }
        lists[0].items.append(item)
        return lists[0].name
    }

    func newList(name: String) {
        lists.insert(CutList(name: name.isEmpty ? L("default_list") : name), at: 0)
    }
}
