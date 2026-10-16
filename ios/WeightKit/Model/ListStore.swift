import Foundation

/// What a typed price refers to. Per kg is free; per metre and per piece are Pro.
enum PriceUnit: String, Codable, CaseIterable { case kg, m, pc }

struct ListItem: Codable, Identifiable, Hashable {
    var id = UUID()
    var shape: String
    var material: String
    var size: String
    var qty: Int
    var pieceKg: Double
    /// Price when the item was added, per `priceUnit` (per kg / per metre / per piece); nil = no price.
    var pricePerKg: Double?
    var priceUnit: PriceUnit = .kg
    /// Length of one piece in metres (bars, tubes, profiles); nil for sheets.
    var pieceM: Double? = nil

    var totalKg: Double { pieceKg * Double(qty) }
    var cost: Double? {
        guard let p = pricePerKg else { return nil }
        switch priceUnit {
        case .kg: return p * totalKg
        case .m: return pieceM.map { p * $0 * Double(qty) }
        case .pc: return p * Double(qty)
        }
    }

    init(id: UUID = UUID(), shape: String, material: String, size: String, qty: Int, pieceKg: Double,
         pricePerKg: Double?, priceUnit: PriceUnit = .kg, pieceM: Double? = nil) {
        self.id = id; self.shape = shape; self.material = material; self.size = size; self.qty = qty
        self.pieceKg = pieceKg; self.pricePerKg = pricePerKg; self.priceUnit = priceUnit; self.pieceM = pieceM
    }

    // lists saved by 1.0 have no priceUnit / pieceM
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        id = try c.decode(UUID.self, forKey: .id); shape = try c.decode(String.self, forKey: .shape)
        material = try c.decode(String.self, forKey: .material); size = try c.decode(String.self, forKey: .size)
        qty = try c.decode(Int.self, forKey: .qty); pieceKg = try c.decode(Double.self, forKey: .pieceKg)
        pricePerKg = try c.decodeIfPresent(Double.self, forKey: .pricePerKg)
        priceUnit = (try? c.decodeIfPresent(PriceUnit.self, forKey: .priceUnit)) ?? .kg
        pieceM = try c.decodeIfPresent(Double.self, forKey: .pieceM)
    }
}

struct CutList: Codable, Identifiable, Hashable {
    var id = UUID()
    var name: String
    var created = Date()
    var wastePct: Double = 0
    var markupPct: Double = 0
    var items: [ListItem] = []
    // quote details for the PDF (Pro)
    var client = ""
    var quoteNo = ""
    var vatPct: Double = 0
    /// Offer valid for N days from the PDF date; 0 = not shown.
    var validDays = 0
    var notes = ""

    init(id: UUID = UUID(), name: String, created: Date = Date(), wastePct: Double = 0, markupPct: Double = 0,
         items: [ListItem] = []) {
        self.id = id; self.name = name; self.created = created; self.wastePct = wastePct
        self.markupPct = markupPct; self.items = items
    }

    // lists saved by 1.0 have no quote fields
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        id = try c.decode(UUID.self, forKey: .id); name = try c.decode(String.self, forKey: .name)
        created = try c.decode(Date.self, forKey: .created)
        wastePct = try c.decode(Double.self, forKey: .wastePct); markupPct = try c.decode(Double.self, forKey: .markupPct)
        items = try c.decode([ListItem].self, forKey: .items)
        client = try c.decodeIfPresent(String.self, forKey: .client) ?? ""
        quoteNo = try c.decodeIfPresent(String.self, forKey: .quoteNo) ?? ""
        vatPct = try c.decodeIfPresent(Double.self, forKey: .vatPct) ?? 0
        validDays = try c.decodeIfPresent(Int.self, forKey: .validDays) ?? 0
        notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
    }

    var netKg: Double { items.reduce(0) { $0 + $1.totalKg } }
    var grossKg: Double { netKg * (1 + wastePct / 100) }
    var hasCost: Bool { items.contains { $0.pricePerKg != nil } }
    var netCost: Double { items.reduce(0) { $0 + ($1.cost ?? 0) } }
    /// Cost incl. waste and markup.
    var totalCost: Double { netCost * (1 + wastePct / 100) * (1 + markupPct / 100) }
    var vat: Double { totalCost * vatPct / 100 }
    var totalWithVat: Double { totalCost + vat }
    var isQuote: Bool { !client.isEmpty || !quoteNo.isEmpty || validDays > 0 || vatPct > 0 }
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

    /// Copy of a list (new ids) at the top; returns the copy's id.
    @discardableResult
    func duplicate(_ id: UUID) -> UUID? {
        guard let src = lists.first(where: { $0.id == id }) else { return nil }
        var c = src
        c.id = UUID(); c.created = Date(); c.name = src.name + " (" + L("copy_suffix") + ")"
        c.items = src.items.map { var x = $0; x.id = UUID(); return x }
        lists.insert(c, at: 0)
        return c.id
    }

    /// Lists not yet here are added at the top. Returns how many were added.
    func merge(_ incoming: [CutList]) -> Int {
        let have = Set(lists.map(\.id))
        let add = incoming.filter { !have.contains($0.id) }
        lists.insert(contentsOf: add, at: 0)
        return add.count
    }
}
