import Foundation

/// Localized string helper.
func L(_ key: String) -> String { NSLocalizedString(key, comment: "") }
func L(_ key: String, _ arg: String) -> String { String(format: NSLocalizedString(key, comment: ""), arg) }

enum UnitSystem: String, CaseIterable, Codable, Identifiable {
    case metric, imperial
    var id: String { rawValue }

    /// Unit for cross-section dimensions (and plate width/length).
    var dimUnit: String { self == .metric ? "mm" : "in" }
    /// Unit for bar length.
    var lengthUnit: String { self == .metric ? "m" : "ft" }
    var weightUnit: String { self == .metric ? "kg" : "lb" }

    /// mm per display unit of dimensions
    var mmPerDim: Double { self == .metric ? 1 : 25.4 }
    /// metres per display unit of length
    var mPerLength: Double { self == .metric ? 1 : 0.3048 }
    /// kg per display unit of weight
    var kgPerWeight: Double { self == .metric ? 1 : 0.45359237 }
}

enum Fmt {
    private static func formatter(_ frac: Int) -> NumberFormatter {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.maximumFractionDigits = frac
        f.minimumFractionDigits = 0
        f.usesGroupingSeparator = true
        return f
    }

    /// Number with sensible precision for weights/lengths.
    static func smart(_ v: Double) -> String {
        let a = abs(v)
        let frac = a < 1 ? 3 : (a < 100 ? 2 : 1)
        return formatter(frac).string(from: NSNumber(value: v)) ?? String(v)
    }

    static func fixed(_ v: Double, _ frac: Int) -> String {
        let f = formatter(frac)
        f.minimumFractionDigits = frac
        return f.string(from: NSNumber(value: v)) ?? String(v)
    }

    /// Plain value for putting back into a text field (no grouping).
    static func edit(_ v: Double) -> String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.usesGroupingSeparator = false
        f.maximumFractionDigits = 4
        return f.string(from: NSNumber(value: v)) ?? String(v)
    }

    /// Accepts "12,5" and "12.5" (spaces / no-break spaces ignored). Plain decimals only, the same
    /// on Android: no exponents, hex, "nan" or "inf".
    static func parse(_ s: String) -> Double? {
        let t = String(s.unicodeScalars.filter { !CharacterSet.whitespacesAndNewlines.contains($0) })
            .replacingOccurrences(of: ",", with: ".")
        guard t.range(of: #"^[+-]?(\d+(\.\d*)?|\.\d+)$"#, options: .regularExpression) != nil,
              let v = Double(t), v.isFinite else { return nil }
        return v
    }

    static func weight(_ kg: Double, _ u: UnitSystem) -> String {
        smart(kg / u.kgPerWeight) + " " + u.weightUnit
    }

    static func money(_ v: Double, _ symbol: String) -> String {
        let s = fixed(v, 2)
        return symbol.isEmpty ? s : s + " " + symbol
    }

    static func date(_ d: Date) -> String {
        let f = DateFormatter()
        f.dateStyle = .medium
        f.timeStyle = .none
        return f.string(from: d)
    }
}

/// Reverse calculation (Pro): what is needed for a target weight. Same on Android.
enum Reverse {
    /// Bar length per piece [m] so that `qty` pieces weigh `targetKg`.
    static func neededLengthM(targetKg: Double, perMeterKg: Double, qty: Int) -> Double? {
        targetKg > 0 && perMeterKg > 0 && qty > 0 ? targetKg / (perMeterKg * Double(qty)) : nil
    }

    /// Plate thickness (same unit as `thickness`); weight is proportional to thickness.
    static func neededThickness(targetKg: Double, thickness: Double, pieceKg: Double, qty: Int) -> Double? {
        targetKg > 0 && thickness > 0 && pieceKg > 0 && qty > 0 ? thickness * targetKg / (pieceKg * Double(qty)) : nil
    }

    /// Whole pieces that fit in `targetKg`. The epsilon keeps e.g. 0.3 / 0.1 from becoming 2.
    static func wholePieces(targetKg: Double, pieceKg: Double) -> Int {
        targetKg > 0 && pieceKg > 0 ? Int((targetKg / pieceKg * (1 + 1e-9)).rounded(.down)) : 0
    }
}
