import Foundation

/// Localized column / row titles for the CSV.
struct CsvLabels {
    let item, shape, material, size, qty, weightPiece, totalWeight, cost, waste, markup, total: String
    var vat = "VAT", totalWithVat = "Total incl. VAT"
}

/// CSV text: UTF-8 BOM (so Excel shows Cyrillic etc.), CRLF, every text quoted ("" escapes "),
/// numbers always with a dot decimal. Byte-identical to Android CsvWriter.
enum CsvWriter {
    static let bom = "\u{FEFF}"

    static func text(_ list: CutList, units u: UnitSystem, currency: String, labels l: CsvLabels) -> String {
        func q(_ s: String) -> String { "\"" + s.replacingOccurrences(of: "\"", with: "\"\"") + "\"" }
        let posix = Locale(identifier: "en_US_POSIX")
        func n(_ v: Double, _ frac: Int = 3) -> String { String(format: "%.\(frac)f", locale: posix, v) }
        var rows: [String] = []
        rows.append([l.item, l.shape, l.material, l.size, l.qty,
                     "\(l.weightPiece) (\(u.weightUnit))", "\(l.totalWeight) (\(u.weightUnit))",
                     "\(l.cost) (\(currency))"].map(q).joined(separator: ","))
        for (i, it) in list.items.enumerated() {
            rows.append([String(i + 1), q(it.shape), q(it.material), q(it.size), String(it.qty),
                         n(it.pieceKg / u.kgPerWeight), n(it.totalKg / u.kgPerWeight),
                         it.cost.map { n($0, 2) } ?? ""].joined(separator: ","))
        }
        rows.append("")
        rows.append(q(l.waste) + "," + n(list.wastePct, 1))
        rows.append(q(l.markup) + "," + n(list.markupPct, 1))
        rows.append(q(l.totalWeight + " (\(u.weightUnit))") + "," + n(list.grossKg / u.kgPerWeight))
        if list.hasCost { rows.append(q(l.total + " (\(currency))") + "," + n(list.totalCost, 2)) }
        if list.hasCost && list.vatPct > 0 {
            rows.append(q(l.vat + " %") + "," + n(list.vatPct, 1))
            rows.append(q(l.totalWithVat + " (\(currency))") + "," + n(list.totalWithVat, 2))
        }
        return bom + rows.joined(separator: "\r\n") + "\r\n"
    }
}
