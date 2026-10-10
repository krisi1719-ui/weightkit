import UIKit

@MainActor
enum Exporter {
    private static func fileURL(_ name: String, ext: String) -> URL {
        let safe = name.components(separatedBy: CharacterSet(charactersIn: "/\\:?*\"<>|")).joined(separator: "_")
        return FileManager.default.temporaryDirectory.appendingPathComponent((safe.isEmpty ? "list" : safe) + "." + ext)
    }

    private static func w(_ kg: Double, _ u: UnitSystem) -> String { Fmt.fixed(kg / u.kgPerWeight, 2) }

    // MARK: CSV

    static func csv(_ list: CutList, settings: AppSettings) -> URL {
        let labels = CsvLabels(item: L("item"), shape: L("shape"), material: L("material"), size: L("size"),
                               qty: L("qty"), weightPiece: L("weight_piece"), totalWeight: L("total_weight"),
                               cost: L("cost"), waste: L("waste"), markup: L("markup"), total: L("total"))
        let url = fileURL(list.name, ext: "csv")
        try? Data(CsvWriter.text(list, units: settings.units, currency: settings.currency, labels: labels).utf8)
            .write(to: url, options: .atomic)
        return url
    }

    // MARK: PDF (A4)

    static func pdf(_ list: CutList, settings: AppSettings) -> URL {
        let u = settings.units
        let page = CGRect(x: 0, y: 0, width: 595, height: 842)
        let margin: CGFloat = 40
        let url = fileURL(list.name, ext: "pdf")
        let accent = UIColor(red: 0.91, green: 0.45, blue: 0.10, alpha: 1)

        let title = UIFont.systemFont(ofSize: 20, weight: .bold)
        let body = UIFont.systemFont(ofSize: 9.5)
        let bold = UIFont.systemFont(ofSize: 9.5, weight: .semibold)
        let small = UIFont.systemFont(ofSize: 8.5)

        // columns: #, description, material, qty, piece, total, cost
        let cols: [CGFloat] = [22, 190, 110, 32, 56, 56, 49]
        let headers = ["#", L("size"), L("material"), L("qty"),
                       "\(L("weight_piece")) (\(u.weightUnit))", "\(L("weight")) (\(u.weightUnit))",
                       L("cost")]

        let renderer = UIGraphicsPDFRenderer(bounds: page)
        try? renderer.writePDF(to: url) { ctx in
            var y: CGFloat = 0

            func draw(_ s: String, _ font: UIFont, x: CGFloat, width: CGFloat, color: UIColor = .black,
                      align: NSTextAlignment = .left) -> CGFloat {
                let p = NSMutableParagraphStyle()
                p.alignment = align
                p.lineBreakMode = .byWordWrapping
                let attr: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color, .paragraphStyle: p]
                let r = (s as NSString).boundingRect(with: CGSize(width: width, height: 200),
                                                     options: .usesLineFragmentOrigin, attributes: attr, context: nil)
                (s as NSString).draw(with: CGRect(x: x, y: y, width: width, height: ceil(r.height)),
                                     options: .usesLineFragmentOrigin, attributes: attr, context: nil)
                return ceil(r.height)
            }

            func header() {
                var x = margin
                let h: CGFloat = 34 // room for two-line headers such as "Per piece (kg)"
                UIColor(white: 0.93, alpha: 1).setFill()
                UIBezierPath(rect: CGRect(x: margin, y: y, width: page.width - 2 * margin, height: h)).fill()
                y += 4
                for (i, c) in cols.enumerated() {
                    _ = draw(headers[i], bold, x: x + 2, width: c - 4, align: i >= 3 ? .right : .left)
                    x += c
                }
                y += h - 4
            }

            func newPage() {
                ctx.beginPage()
                y = margin
            }

            newPage()
            // title block
            accent.setFill()
            UIBezierPath(rect: CGRect(x: margin, y: y, width: 6, height: 44)).fill()
            _ = draw(L("pdf_title"), title, x: margin + 14, width: 300)
            let company = settings.company
            if !company.isEmpty {
                _ = draw(company, bold, x: page.width - margin - 220, width: 220, align: .right)
            }
            y += 26
            _ = draw(list.name, body, x: margin + 14, width: 300, color: .darkGray)
            _ = draw(L("date") + ": " + Fmt.date(Date()), body, x: page.width - margin - 220, width: 220,
                     color: .darkGray, align: .right)
            y += 34
            header()

            for (i, it) in list.items.enumerated() {
                if y > page.height - margin - 110 {
                    newPage()
                    header()
                }
                let values = [String(i + 1), it.shape + "\n" + it.size, it.material, String(it.qty),
                              w(it.pieceKg, u), w(it.totalKg, u),
                              it.cost.map { Fmt.fixed($0, 2) } ?? "–"]
                var x = margin
                var rowH: CGFloat = 0
                y += 5
                for (c, width) in cols.enumerated() {
                    let h = draw(values[c], body, x: x + 2, width: width - 4, align: c >= 3 ? .right : .left)
                    rowH = max(rowH, h)
                    x += width
                }
                y += rowH + 5
                UIColor(white: 0.85, alpha: 1).setFill()
                UIBezierPath(rect: CGRect(x: margin, y: y, width: page.width - 2 * margin, height: 0.5)).fill()
            }

            // totals
            y += 14
            let labelX = page.width - margin - 260
            func totalRow(_ a: String, _ b: String, _ f: UIFont) {
                _ = draw(a, f, x: labelX, width: 150)
                _ = draw(b, f, x: labelX + 150, width: 110, align: .right)
                y += 16
            }
            totalRow(L("waste"), Fmt.fixed(list.wastePct, 1) + " %", body)
            totalRow(L("total_weight"), w(list.grossKg, u) + " " + u.weightUnit, bold)
            if list.hasCost {
                totalRow(L("subtotal"), Fmt.money(list.netCost, settings.currency), body)
                totalRow(L("markup"), Fmt.fixed(list.markupPct, 1) + " %", body)
                totalRow(L("total"), Fmt.money(list.totalCost, settings.currency), UIFont.systemFont(ofSize: 12, weight: .bold))
            }
            y = page.height - margin - 12
            _ = draw("WeightKit · " + L("data_note"), small, x: margin, width: page.width - 2 * margin, color: .gray)
        }
        return url
    }
}
