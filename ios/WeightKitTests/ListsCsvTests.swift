import XCTest
@testable import WeightKit

/// Mirrors Android ListsCsvTest (expects byte-identical CSV).
@MainActor
final class ListsCsvTests: XCTestCase {
    let list = CutList(name: "Оферта \"Иванов\", 2026", wastePct: 10, markupPct: 20, items: [
        ListItem(shape: "Ламарина", material: "Стомана S235", size: "10 × 1000 × 2000 mm", qty: 3, pieceKg: 157, pricePerKg: 2),
        ListItem(shape: "IPE", material: "Steel, \"S355\"", size: "IPE 200 × 6 m", qty: 2, pieceKg: 134.4, pricePerKg: nil),
    ])

    func testTotalsWasteMarkupItemsWithoutPrice() {
        XCTAssertEqual(list.netKg, 739.8, accuracy: 1e-9)
        XCTAssertEqual(list.grossKg, 739.8 * 1.1, accuracy: 1e-9)
        XCTAssertTrue(list.hasCost)
        XCTAssertEqual(list.netCost, 942, accuracy: 1e-9)
        XCTAssertEqual(list.totalCost, 942 * 1.1 * 1.2, accuracy: 1e-9)
        XCTAssertNil(list.items[1].cost)
        var noPrice = list; noPrice.items.removeFirst()
        XCTAssertFalse(noPrice.hasCost)
        XCTAssertEqual(noPrice.totalCost, 0)
        XCTAssertEqual(CutList(name: "x").grossKg, 0)
    }

    func testPersistenceRoundTrip() {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".json")
        defer { try? FileManager.default.removeItem(at: url) }
        let a = ListStore(url: url)
        a.lists = [list]
        a.newList(name: "Second")
        a.add(ListItem(shape: "Round", material: "Steel", size: "Ø20", qty: 1, pieceKg: 2.47, pricePerKg: nil))
        let b = ListStore(url: url)
        XCTAssertEqual(b.lists, a.lists)
        XCTAssertEqual(b.lists.map(\.name), ["Second", list.name])
        XCTAssertEqual(b.lists[0].items.count, 1)
        try? Data("not json".utf8).write(to: url)
        XCTAssertEqual(ListStore(url: url).lists, [])
    }

    let labels = CsvLabels(item: "Поз.", shape: "Форма", material: "Материал", size: "Размер", qty: "бр.",
                           weightPiece: "Тегло/бр.", totalWeight: "Общо тегло", cost: "Цена",
                           waste: "Отпадък", markup: "Надценка", total: "Общо")

    func testCsvFormat() {
        let csv = CsvWriter.text(list, units: .metric, currency: "лв", labels: labels)
        XCTAssertEqual(Array(Data(csv.utf8).prefix(3)), [0xEF, 0xBB, 0xBF])
        let rows = String(csv.dropFirst()).components(separatedBy: "\r\n")
        XCTAssertEqual(rows[0], "\"Поз.\",\"Форма\",\"Материал\",\"Размер\",\"бр.\",\"Тегло/бр. (kg)\",\"Общо тегло (kg)\",\"Цена (лв)\"")
        XCTAssertEqual(rows[1], "1,\"Ламарина\",\"Стомана S235\",\"10 × 1000 × 2000 mm\",3,157.000,471.000,942.00")
        XCTAssertEqual(rows[2], "2,\"IPE\",\"Steel, \"\"S355\"\"\",\"IPE 200 × 6 m\",2,134.400,268.800,")
        XCTAssertEqual(rows[3], "")
        XCTAssertEqual(rows[4], "\"Отпадък\",10.0")
        XCTAssertEqual(rows[5], "\"Надценка\",20.0")
        XCTAssertEqual(rows[6], "\"Общо тегло (kg)\",813.780")
        XCTAssertEqual(rows[7], "\"Общо (лв)\",1243.44")
        XCTAssertEqual(rows[8], "")
    }

    func testCsvImperial() {
        let csv = CsvWriter.text(list, units: .imperial, currency: "$", labels: labels)
        XCTAssertTrue(csv.contains("(lb)"))
        XCTAssertTrue(csv.contains(",346.126,1038.377,"))
    }

    func testCsvFileWrittenWithBom() throws {
        let s = AppSettings()
        let url = Exporter.csv(list, settings: s)
        let data = try Data(contentsOf: url)
        XCTAssertEqual(Array(data.prefix(3)), [0xEF, 0xBB, 0xBF])
        XCTAssertTrue(url.lastPathComponent.hasSuffix(".csv"))
        XCTAssertFalse(url.lastPathComponent.contains("\""))
    }

    /// Same list as the Android UI export test; files go to test-artifacts/exports for the content check.
    func testExportArtifacts() throws {
        let s = AppSettings()
        let (oldCompany, oldCurrency) = (s.company, s.currency)
        defer { s.company = oldCompany; s.currency = oldCurrency }
        s.company = "Метал ООД"; s.currency = "лв"
        let l = CutList(name: "Оферта \"Иванов\", 2026", wastePct: 10, markupPct: 20, items: [
            ListItem(shape: "Sheet / plate", material: "Carbon steel (S235/S355, A36)", size: "10 × 1000 × 2000 mm", qty: 1, pieceKg: 157, pricePerKg: 2),
            ListItem(shape: "Round bar", material: "Carbon steel (S235/S355, A36)", size: "Ø20 mm × 6 m", qty: 1, pieceKg: .pi / 4 * 400 * 1e-6 * 7850 * 6, pricePerKg: nil),
            ListItem(shape: "IPE", material: "Carbon steel (S235/S355, A36)", size: "IPE 80 × 6 m", qty: 1, pieceKg: 764 * 1e-6 * 7850 * 6, pricePerKg: nil),
        ])
        let out = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().appendingPathComponent("test-artifacts/exports")
        try? FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)
        for (src, name) in [(Exporter.pdf(l, settings: s), "ios_list.pdf"), (Exporter.csv(l, settings: s), "ios_list.csv")] {
            let dst = out.appendingPathComponent(name)
            try? FileManager.default.removeItem(at: dst)
            try FileManager.default.copyItem(at: src, to: dst)
        }
        let csv = try String(contentsOf: out.appendingPathComponent("ios_list.csv"), encoding: .utf8)
        XCTAssertTrue(csv.contains("414.48"), csv)
    }

    func testPdfFileHasContent() throws {
        let url = Exporter.pdf(list, settings: AppSettings())
        let data = try Data(contentsOf: url)
        XCTAssertTrue(data.starts(with: Data("%PDF".utf8)))
        XCTAssertGreaterThan(data.count, 2000)
    }
}
