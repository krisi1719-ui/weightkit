import XCTest
@testable import WeightKit

/// All 13 shapes through the same code path the screen uses (CalcModel.compute). Mirrors Android FormulaTest.
@MainActor
final class FormulaTests: XCTestCase {
    let steel = 7850.0

    func calc(_ shape: ShapeKind, _ v: [String: String] = [:], length: String = "1") -> CalcModel {
        let c = CalcModel(units: .metric)
        c.shape = shape
        c.fields = v
        c.lengthText = length
        return c
    }

    func ok(_ c: CalcModel, file: StaticString = #filePath, line: UInt = #line) -> CalcResult? {
        guard case .ok(let r) = c.compute(density: steel) else { XCTFail("expected ok", file: file, line: line); return nil }
        return r
    }

    func kgM(_ shape: ShapeKind, _ v: [String: String]) -> Double { ok(calc(shape, v))?.perMeterKg ?? .nan }

    func testPlate1000x1000x10Is78_5kg() {
        let r = ok(calc(.plate, ["t": "10", "w": "1000", "pl": "1000"]))!
        XCTAssertEqual(r.pieceKg, 78.5, accuracy: 1e-9)
        XCTAssertEqual(r.volumeM3, 0.01, accuracy: 1e-12)
    }

    func testDefaultScreenIsPlateAndQuantityMultiplies() {
        let c = CalcModel(units: .metric)
        c.qtyText = "3"
        let r = ok(c)!
        XCTAssertEqual(r.pieceKg, 157, accuracy: 1e-9)
        XCTAssertEqual(r.totalKg, 471, accuracy: 1e-9)
    }

    func testRound() { XCTAssertEqual(kgM(.round, ["d": "20"]), 2.466, accuracy: 0.0005) }
    func testSquare() { XCTAssertEqual(kgM(.square, ["a": "10"]), 0.785, accuracy: 1e-9) }
    func testHex() { XCTAssertEqual(kgM(.hex, ["s": "24"]), 3.916, accuracy: 0.0005) }
    func testFlat() { XCTAssertEqual(kgM(.flat, ["w": "50", "t": "5"]), 1.9625, accuracy: 1e-9) }
    func testPipe() { XCTAssertEqual(kgM(.pipe, ["od": "60.3", "wt": "3.6"]), 5.03, accuracy: 0.005) }
    func testRectTube() { XCTAssertEqual(kgM(.rectTube, ["h": "40", "w": "40", "wt": "2"]), 2.386, accuracy: 0.0005) }
    func testAngle() { XCTAssertEqual(kgM(.angle, ["a": "50", "b": "50", "t": "5"]), 475e-6 * steel, accuracy: 1e-9) }
    func testChannel() { XCTAssertEqual(kgM(.channel, ["h": "100", "b": "50", "tw": "6", "tf": "8.5"]), 1348e-6 * steel, accuracy: 1e-9) }
    func testIBeam() { XCTAssertEqual(kgM(.ibeam, ["h": "200", "b": "100", "tw": "5.6", "tf": "8.5"]), 2724.8e-6 * steel, accuracy: 1e-9) }
    func testTee() { XCTAssertEqual(kgM(.tee, ["h": "100", "b": "100", "tw": "10", "tf": "10"]), 1900e-6 * steel, accuracy: 1e-9) }

    func testRebarD12() {
        let c = calc(.rebar); c.rebarIndex = 3
        let r = ok(c)!
        XCTAssertEqual(r.perMeterKg!, 0.888, accuracy: 0.001)
        XCTAssertEqual(r.tableKgM, 0.888)
    }

    func testSectionIPE200UsesTabulatedArea() {
        let c = calc(.section); c.familyID = "ipe"; c.itemIndex = 6
        XCTAssertEqual(c.sectionItem?.name, "IPE 200")
        let r = ok(c)!
        XCTAssertEqual(r.perMeterKg!, 28.5e2 * 1e-6 * steel, accuracy: 1e-9)
        XCTAssertEqual(r.tableKgM, 22.4)
    }

    func testProTablePickedInTrialNotUsedWithoutPro() {
        let c = calc(.section); c.familyID = "heb"; c.itemIndex = 0
        XCTAssertEqual(c.family?.id, "heb")
        c.proAllowed = false
        XCTAssertEqual(c.family?.id, "ipe")
        XCTAssertEqual(ok(c)!.perMeterKg!, 764 * 1e-6 * steel, accuracy: 1e-9)
        c.proAllowed = true
        XCTAssertEqual(c.family?.id, "heb")
    }

    func testCommaDecimal() {
        XCTAssertEqual(kgM(.flat, ["w": "12,5", "t": "5"]), kgM(.flat, ["w": "12.5", "t": "5"]))
    }

    func testZeroEmptyNegativeAreMissing() {
        for v in ["0", "", "-5", "abc", "1e3", "12f", "0x10", " "] {
            guard case .missing = calc(.round, ["d": v]).compute(density: steel) else { return XCTFail("'\(v)'") }
        }
        guard case .missing = calc(.round, ["d": "20"], length: "0").compute(density: steel) else { return XCTFail() }
        guard case .missing = calc(.round, ["d": "20"], length: "").compute(density: steel) else { return XCTFail() }
        guard case .missing = calc(.plate, ["t": "10", "w": "0", "pl": "5"]).compute(density: steel) else { return XCTFail() }
    }

    func testWallTooThick() {
        func wall(_ s: ShapeKind, _ v: [String: String]) {
            guard case .wall = calc(s, v).compute(density: steel) else { return XCTFail("\(s) \(v)") }
        }
        wall(.pipe, ["od": "10", "wt": "6"])
        wall(.rectTube, ["h": "40", "w": "20", "wt": "11"])
        wall(.angle, ["a": "50", "b": "30", "t": "30"])
        wall(.channel, ["h": "20", "b": "50", "tw": "5", "tf": "10"])
        wall(.ibeam, ["h": "100", "b": "50", "tw": "60", "tf": "5"])
        wall(.tee, ["h": "10", "b": "50", "tw": "5", "tf": "10"])
        XCTAssertEqual(kgM(.pipe, ["od": "10", "wt": "5"]), kgM(.round, ["d": "10"]), accuracy: 1e-9)
    }

    func testQuantityIsClamped() {
        let c = calc(.round, ["d": "20"])
        for (txt, q) in [("", 1), ("0", 1), ("-4", 1), ("2,7", 2), ("99999999999999999999", CalcModel.maxQty)] {
            c.qtyText = txt
            XCTAssertEqual(c.quantity, q, txt) // used to crash: Int(1e20) traps
        }
    }
}

final class CatalogTests: XCTestCase {
    let cat = Catalog.shared

    func testAllFamiliesLoaded() {
        XCTAssertEqual(cat.families.map(\.id), ["ipe", "hea", "heb", "upn", "w", "ismb", "ismc", "pipe40", "pipe80", "rebar"])
        XCTAssertEqual(cat.families.reduce(0) { $0 + $1.items.count }, 169)
        XCTAssertEqual(cat.materials.count, 30)
    }

    func testEveryItemAreaTimes7850Within2PctOfTable() {
        var n = 0
        for f in cat.families { for it in f.items {
            guard let table = it.steel_kg_m else { continue }
            let calc = it.areaMM2(shape: f.shape)! * 1e-6 * 7850
            XCTAssertLessThanOrEqual(abs(calc - table) / table, 0.02, "\(it.name): \(calc) vs \(table)")
            n += 1
        }}
        XCTAssertEqual(n, 169 - 28, "pipes have no tabulated kg/m")
    }

    func testImperialWMatchesNominalLbFt() {
        let w = cat.families.first { $0.id == "w" }!
        XCTAssertEqual(w.items.count, 38)
        for it in w.items {
            let lbFt = it.areaMM2(shape: w.shape)! * 1e-6 * 7850 * 0.3048 / 0.45359237
            XCTAssertEqual(it.nominal_lb_ft, Double(it.name.split(separator: "x")[1]))
            XCTAssertLessThanOrEqual(abs(lbFt - it.nominal_lb_ft!) / it.nominal_lb_ft!, 0.02, it.name)
        }
    }

    func testProSplitMatchesMonetizationDoc() {
        XCTAssertEqual(Set(cat.families.filter(\.pro).map(\.id)), ["hea", "heb", "w", "ismb", "ismc", "pipe80"])
    }
}
