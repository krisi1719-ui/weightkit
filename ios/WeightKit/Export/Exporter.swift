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
                               cost: L("cost"), waste: L("waste"), markup: L("markup"), total: L("total"),
                               vat: L("vat"), totalWithVat: L("total_with_vat"))
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
            let top = y
            // logo (Pro) at the top left instead of the accent bar
            var tx = margin + 14
            if let png = settings.logoPNG, let logo = UIImage(data: png), logo.size.height > 0 {
                let w = min(140, logo.size.width * 48 / logo.size.height)
                let h = logo.size.height * w / logo.size.width
                logo.draw(in: CGRect(x: margin, y: top, width: w, height: h))
                tx = margin + w + 12
            } else {
                accent.setFill()
                UIBezierPath(rect: CGRect(x: margin, y: top, width: 6, height: 44)).fill()
            }
            let heading = list.isQuote
                ? L("quote_title") + (list.quoteNo.isEmpty ? "" : " № " + list.quoteNo.trimmingCharacters(in: .whitespaces))
                : L("pdf_title")
            _ = draw(heading, title, x: tx, width: 300)
            y = top + 26
            y += draw(list.name, body, x: tx, width: 300, color: .darkGray)
            let client = list.client.trimmingCharacters(in: .whitespacesAndNewlines)
            if !client.isEmpty { y += draw(L("client") + ": " + client, bold, x: tx, width: 300) }
            let leftEnd = y
            // right column: company name, address, contact, then the date
            let rx = page.width - margin - 220
            y = top
            let company = settings.company.trimmingCharacters(in: .whitespacesAndNewlines)
            if !company.isEmpty { y += draw(company, bold, x: rx, width: 220, align: .right) }
            for line in [settings.companyAddress, settings.companyContact] {
                let t = line.trimmingCharacters(in: .whitespacesAndNewlines)
                if !t.isEmpty { y += draw(t, body, x: rx, width: 220, color: .darkGray, align: .right) }
            }
            y = max(y + 2, top + 26)
            let today = Date()
            y += draw(L("date") + ": " + Fmt.date(today), body, x: rx, width: 220, color: .darkGray, align: .right)
            if list.validDays > 0 {
                let until = today.addingTimeInterval(TimeInterval(list.validDays) * 86400)
                y += draw(L("valid_until") + ": " + Fmt.date(until), body, x: rx, width: 220, color: .darkGray, align: .right)
            }
            y = max(y, top + 44, leftEnd) + 16
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
                if list.vatPct > 0 {
                    totalRow(L("total_ex_vat"), Fmt.money(list.totalCost, settings.currency), bold)
                    totalRow(L("vat") + " " + Fmt.fixed(list.vatPct, 1) + " %", Fmt.money(list.vat, settings.currency), body)
                    totalRow(L("total_with_vat"), Fmt.money(list.totalWithVat, settings.currency), UIFont.systemFont(ofSize: 12, weight: .bold))
                } else {
                    totalRow(L("total"), Fmt.money(list.totalCost, settings.currency), UIFont.systemFont(ofSize: 12, weight: .bold))
                }
            }
            let notes = list.notes.trimmingCharacters(in: .whitespacesAndNewlines)
            if !notes.isEmpty {
                y += 14
                if y > page.height - margin - 80 { newPage() }
                _ = draw(L("notes"), bold, x: margin, width: page.width - 2 * margin)
                y += 14
                y += draw(notes, body, x: margin, width: page.width - 2 * margin)
            }
            y = page.height - margin - 12
            _ = draw("WeightKit · " + L("data_note"), small, x: margin, width: page.width - 2 * margin, color: .gray)
        }
        return url
    }
}
