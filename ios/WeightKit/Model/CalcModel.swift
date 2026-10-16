import Foundation

struct CalcResult {
    let pieceKg: Double
    /// Weight per metre (linear shapes only).
    let perMeterKg: Double?
    let totalKg: Double
    let volumeM3: Double
    let surfaceM2: Double?
    let density: Double
    let areaMM2: Double?
    /// Tabulated steel mass for standard profiles (kg/m), for cross-checking.
    let tableKgM: Double?
    let tableLbFt: Double?
}

enum CalcOutcome {
    case ok(CalcResult)
    case missing
    case wall
}

/// State of the calculator screen. Text fields hold values in the CURRENT display units.
@MainActor
final class CalcModel: ObservableObject {
    @Published var shape: ShapeKind = .plate
    @Published var materialID: String = "steel"
    @Published var fields: [String: String] = [:]
    @Published var lengthText = "6"
    @Published var qtyText = "1"
    /// Price per kg/lb, per m/ft or per piece (see `priceUnit`), as typed.
    @Published var priceText = ""
    /// What `priceText` refers to. Per metre / per piece need Pro (see `proAllowed`).
    @Published var priceUnit: PriceUnit = .kg
    @Published var targetText = ""
    @Published var familyID: String = "ipe"
    @Published var itemIndex: Int = 0
    @Published var rebarIndex: Int = 4
    private(set) var units: UnitSystem
    static let maxQty = 1_000_000

    /// Exact base value (mm, m, price per kg, kg) behind a text that we produced by a unit switch.
    /// As long as the user does not edit that text, calculations use the exact value, so switching
    /// units back and forth never loses precision and both systems give the same result.
    private var exact: [String: (text: String, value: Double)] = [:]

    init(units: UnitSystem) {
        self.units = units
        if units == .imperial {
            lengthText = "20"
            fields = ["t": "0.25", "w": "48", "pl": "96"]
        } else {
            fields = ["t": "10", "w": "1000", "pl": "2000"]
        }
    }

    /// What the user typed, so it survives the app being terminated in the background.
    struct Snapshot: Codable {
        var units: UnitSystem, shape: ShapeKind, material: String, fields: [String: String]
        var length: String, qty: String, price: String, target: String
        var family: String, item: Int, rebar: Int
        var priceUnit: PriceUnit? = nil
    }

    var snapshot: Snapshot {
        Snapshot(units: units, shape: shape, material: materialID, fields: fields, length: lengthText, qty: qtyText,
                 price: priceText, target: targetText, family: familyID, item: itemIndex, rebar: rebarIndex,
                 priceUnit: priceUnit)
    }

    /// Restores a snapshot; ignored if it was taken in another unit system.
    func restore(_ s: Snapshot?) {
        guard let s, s.units == units else { return }
        shape = s.shape; materialID = s.material; fields = s.fields
        lengthText = s.length; qtyText = s.qty; priceText = s.price; targetText = s.target
        familyID = s.family; itemIndex = s.item; rebarIndex = s.rebar
        priceUnit = s.priceUnit ?? .kg
    }

    static let snapshotKey = "calc"

    func save() { UserDefaults.standard.set(try? JSONEncoder().encode(snapshot), forKey: Self.snapshotKey) }

    func restoreSaved() {
        guard let data = UserDefaults.standard.data(forKey: Self.snapshotKey) else { return }
        restore(try? JSONDecoder().decode(Snapshot.self, from: data))
    }

    func text(_ key: String) -> String { fields[key] ?? "" }
    func setText(_ key: String, _ v: String) { fields[key] = v }

    private func base(_ key: String, _ text: String, _ factor: Double) -> Double? {
        if let e = exact[key], e.text == text { return e.value }
        return Fmt.parse(text).map { $0 * factor }
    }

    private func mm(_ key: String) -> Double? { base("f:" + key, text(key), units.mmPerDim) }
    private var lengthM: Double? { base("length", lengthText, units.mPerLength) }

    /// Convert every typed value when the unit system changes, so nothing is lost.
    func switchUnits(to new: UnitSystem) {
        guard new != units else { return }
        let old = units
        func convert(_ key: String, _ text: String, _ oldF: Double, _ newF: Double) -> String? {
            guard let b = base(key, text, oldF) else { return nil }
            let t = Fmt.edit(round4(b / newF))
            exact[key] = (t, b)
            return t
        }
        for (k, v) in fields {
            if let t = convert("f:" + k, v, old.mmPerDim, new.mmPerDim) { fields[k] = t }
        }
        if let t = convert("length", lengthText, old.mPerLength, new.mPerLength) { lengthText = t }
        switch priceUnit {
        case .kg: // price per kg <-> price per lb
            if let t = convert("price", priceText, 1 / old.kgPerWeight, 1 / new.kgPerWeight) { priceText = t }
        case .m:  // price per m <-> price per ft
            if let t = convert("price_m", priceText, 1 / old.mPerLength, 1 / new.mPerLength) { priceText = t }
        case .pc: break
        }
        if let t = convert("target", targetText, old.kgPerWeight, new.kgPerWeight) { targetText = t }
        units = new
    }

    private func round4(_ x: Double) -> Double { (x * 10000).rounded() / 10000 }

    /// Set by the screen from ProStore.hasPro. Without Pro a Pro table (e.g. picked during the trial) is not used.
    @Published var proAllowed = true

    var family: SectionFamily? {
        Catalog.shared.profileFamilies.first { $0.id == familyID && (!$0.pro || proAllowed) }
            ?? Catalog.shared.profileFamilies.first { !$0.pro }
    }

    var sectionItem: SectionItem? {
        guard let f = family, !f.items.isEmpty else { return nil }
        return f.items[min(itemIndex, f.items.count - 1)]
    }

    var rebarItem: SectionItem? {
        guard let f = Catalog.shared.rebar, !f.items.isEmpty else { return nil }
        return f.items[min(rebarIndex, f.items.count - 1)]
    }

    /// 1 … maxQty; empty / invalid input counts as 1. (Int(Double) would trap on huge input.)
    var quantity: Int { Int(min(max(Fmt.parse(qtyText) ?? 1, 1), Double(Self.maxQty))) }

    /// Price unit in effect: per metre / per piece fall back to per kg without Pro.
    var effectivePriceUnit: PriceUnit { proAllowed || priceUnit == .kg ? priceUnit : .kg }

    /// Internal price (per kg, per metre or per piece – see `effectivePriceUnit`), nil if not entered.
    var pricePerKg: Double? {
        let p: Double?
        switch effectivePriceUnit {
        case .kg: p = base("price", priceText, 1 / units.kgPerWeight)
        case .m: p = base("price_m", priceText, 1 / units.mPerLength)
        case .pc: p = Fmt.parse(priceText)
        }
        guard let p, p > 0 else { return nil }
        return p
    }

    /// Length of one piece in metres; nil for sheets (they have no length).
    var pieceLengthM: Double? {
        guard shape != .plate, let l = lengthM, l > 0 else { return nil }
        return l
    }

    /// Cost of the calculated quantity in the chosen price unit; nil without a price.
    func cost(_ r: CalcResult) -> Double? {
        guard let p = pricePerKg else { return nil }
        switch effectivePriceUnit {
        case .kg: return p * r.totalKg
        case .m: return pieceLengthM.map { p * $0 * Double(quantity) }
        case .pc: return p * Double(quantity)
        }
    }

    func changePriceUnit(_ u: PriceUnit) {
        guard u != priceUnit else { return }
        priceUnit = u
        exact["price"] = nil; exact["price_m"] = nil
    }

    /// Target weight in kg, nil if not entered.
    var targetKg: Double? {
        guard let t = base("target", targetText, units.kgPerWeight), t > 0 else { return nil }
        return t
    }

    /// Plate thickness in mm (for the reverse calculation).
    var thicknessMM: Double? { mm("t") }

    func compute(density: Double) -> CalcOutcome {
        var mm: [String: Double] = [:]
        for k in fields.keys { if let x = self.mm(k) { mm[k] = x } }
        let qty = Double(quantity)

        if shape == .plate {
            // plate: V = t·W·L ; surface = 2·W·L + 2·t·(W+L)
            guard let t = mm["t"], let w = mm["w"], let l = mm["pl"], t > 0, w > 0, l > 0 else { return .missing }
            let vol = t * w * l * 1e-9
            let piece = vol * density
            let surf = (2 * w * l + 2 * t * (w + l)) * 1e-6
            return .ok(CalcResult(pieceKg: piece, perMeterKg: nil, totalKg: piece * qty, volumeM3: vol,
                                  surfaceM2: surf, density: density, areaMM2: nil, tableKgM: nil, tableLbFt: nil))
        }

        guard let lengthM, lengthM > 0 else { return .missing }

        var area: Double
        var perim: Double?
        var tableKgM: Double?
        var tableLbFt: Double?
        switch shape {
        case .section:
            guard let f = family, let it = sectionItem, let a = it.areaMM2(shape: f.shape) else { return .missing }
            area = a
            perim = it.perimeterMM(shape: f.shape)
            tableKgM = it.steel_kg_m
            tableLbFt = it.nominal_lb_ft
        case .rebar:
            guard let it = rebarItem, let a = it.areaMM2(shape: "rebar") else { return .missing }
            area = a
            perim = it.perimeterMM(shape: "rebar")
            tableKgM = it.steel_kg_m
        default:
            do {
                let cs = try Geometry.section(shape, mm)
                area = cs.areaMM2
                perim = cs.perimeterMM
            } catch GeoError.wall {
                return .wall
            } catch {
                return .missing
            }
        }
        // mass per metre = A[mm²]·1e-6 [m²] · ρ [kg/m³]
        let perM = area * 1e-6 * density
        let piece = perM * lengthM
        return .ok(CalcResult(pieceKg: piece, perMeterKg: perM, totalKg: piece * qty,
                              volumeM3: area * 1e-6 * lengthM, surfaceM2: perim.map { $0 * 1e-3 * lengthM },
                              density: density, areaMM2: area, tableKgM: tableKgM, tableLbFt: tableLbFt))
    }

    /// Human-readable size for lists / PDF, in current units.
    func sizeDescription() -> String {
        let du = units.dimUnit
        func v(_ k: String) -> String { text(k).isEmpty ? "?" : text(k) }
        let len = shape.isLinear ? " × " + (lengthText.isEmpty ? "?" : lengthText) + " " + units.lengthUnit : ""
        switch shape {
        case .plate: return "\(v("t")) × \(v("w")) × \(v("pl")) \(du)"
        case .round: return "Ø\(v("d")) \(du)" + len
        case .square: return "\(v("a")) × \(v("a")) \(du)" + len
        case .hex: return "SW \(v("s")) \(du)" + len
        case .flat: return "\(v("w")) × \(v("t")) \(du)" + len
        case .pipe: return "Ø\(v("od")) × \(v("wt")) \(du)" + len
        case .rectTube: return "\(v("h")) × \(v("w")) × \(v("wt")) \(du)" + len
        case .angle: return "L \(v("a")) × \(v("b")) × \(v("t")) \(du)" + len
        case .channel, .ibeam, .tee: return "\(v("h")) × \(v("b")) × \(v("tw")) / \(v("tf")) \(du)" + len
        case .rebar: return (rebarItem?.name ?? "") + len
        case .section: return (sectionItem?.name ?? "") + len
        }
    }
}
