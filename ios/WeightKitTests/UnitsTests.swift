import XCTest
@testable import WeightKit

/// Mirrors Android UnitsTest / ReverseTest.
@MainActor
final class UnitsTests: XCTestCase {
    func allFields() -> CalcModel {
        let c = CalcModel(units: .metric)
        c.fields = ["t": "0.5", "w": "1250", "pl": "2500", "d": "12,7", "a": "33.3", "s": "17",
                    "od": "60.3", "wt": "3.6", "h": "200", "b": "100", "tw": "5.6", "tf": "8.5"]
        c.lengthText = "6.05"; c.priceText = "2,35"; c.targetText = "123.4"; c.qtyText = "7"
        return c
    }

    func snapshot(_ c: CalcModel) -> [String: String] {
        c.fields.merging(["L": c.lengthText, "P": c.priceText, "T": c.targetText]) { a, _ in a }
    }

    func testMetricImperialMetricKeepsEveryFieldAndResult() {
        let c = allFields()
        let before = snapshot(c)
        let resultsBefore = ShapeKind.allCases.map { s -> Double? in c.shape = s; if case .ok(let r) = c.compute(density: 7850) { return r.totalKg }; return nil }
        c.switchUnits(to: .imperial)
        let resultsImperial = ShapeKind.allCases.map { s -> Double? in c.shape = s; if case .ok(let r) = c.compute(density: 7850) { return r.totalKg }; return nil }
        c.switchUnits(to: .metric)
        let after = snapshot(c)
        for (k, v) in before {
            let a = Fmt.parse(after[k]!)!, b = Fmt.parse(v)!
            XCTAssertLessThanOrEqual(abs(a - b), abs(b) * 1e-4, "\(k): \(v) → \(after[k]!)")
        }
        for (m, i) in zip(resultsBefore, resultsImperial) {
            if let m { XCTAssertEqual(i!, m, accuracy: m * 1e-9) }
        }
    }

    func testRepeatedSwitchingDoesNotDrift() {
        let c = allFields()
        for _ in 0..<50 { c.switchUnits(to: .imperial); c.switchUnits(to: .metric) }
        XCTAssertEqual(Fmt.parse(c.fields["t"]!), 0.5)
        XCTAssertEqual(Fmt.parse(c.fields["d"]!), 12.7)
        XCTAssertEqual(Fmt.parse(c.lengthText), 6.05)
    }

    func testShownImperialValues() {
        let c = allFields()
        c.switchUnits(to: .imperial)
        XCTAssertEqual(Fmt.parse(c.fields["d"]!), 0.5)
        XCTAssertEqual(Fmt.parse(c.lengthText), 19.8491)
        XCTAssertEqual(Fmt.parse(c.priceText)!, 2.35 * 0.45359237, accuracy: 1e-4)
        XCTAssertEqual(Fmt.parse(c.targetText)!, 123.4 / 0.45359237, accuracy: 1e-4)
    }

    func testPricePerKgPerLb() {
        let c = CalcModel(units: .metric); c.priceText = "2"
        XCTAssertEqual(c.pricePerKg!, 2, accuracy: 1e-12)
        c.switchUnits(to: .imperial)
        XCTAssertEqual(c.pricePerKg!, 2, accuracy: 1e-12)
        c.priceText = "1"
        XCTAssertEqual(c.pricePerKg!, 1 / 0.45359237, accuracy: 1e-9)
    }

    func testTargetWeightConverted() {
        let c = CalcModel(units: .metric); c.targetText = "100"
        c.switchUnits(to: .imperial)
        XCTAssertEqual(c.targetKg!, 100, accuracy: 1e-12)
        c.targetText = "100"
        XCTAssertEqual(c.targetKg!, 45.359237, accuracy: 1e-9)
        c.switchUnits(to: .metric)
        XCTAssertEqual(Fmt.parse(c.targetText), 45.3592)
    }

    func testEditedImperialValueIsUsedAsTyped() {
        let c = CalcModel(units: .metric); c.shape = .round; c.fields["d"] = "20"
        c.switchUnits(to: .imperial)
        c.fields["d"] = "1"
        c.switchUnits(to: .metric)
        XCTAssertEqual(Fmt.parse(c.fields["d"]!), 25.4)
    }

    func testParseRules() {
        XCTAssertEqual(Fmt.parse("12,5"), 12.5)
        XCTAssertEqual(Fmt.parse(" 12.5 "), 12.5)
        XCTAssertEqual(Fmt.parse("1 234,5"), 1234.5)
        XCTAssertEqual(Fmt.parse("1\u{00A0}234,5"), 1234.5)
        XCTAssertEqual(Fmt.parse(".5"), 0.5)
        XCTAssertEqual(Fmt.parse("-3"), -3)
        for bad in ["", "abc", "1e3", "12f", "12d", "0x10", "NaN", "nan", "inf", "Infinity", "1.2.3", "1,234.5", ","] {
            XCTAssertNil(Fmt.parse(bad), bad)
        }
    }

    func testImperialDefaults() {
        guard case .ok(let r) = CalcModel(units: .imperial).compute(density: 7850) else { return XCTFail() }
        XCTAssertEqual(r.totalKg / 0.45359237, 326.7, accuracy: 0.1)
    }

    // MARK: reverse

    func testNeededLength() {
        XCTAssertEqual(Reverse.neededLengthM(targetKg: 100, perMeterKg: 2.4661, qty: 4)!, 100 / (2.4661 * 4), accuracy: 1e-9)
        XCTAssertNil(Reverse.neededLengthM(targetKg: 0, perMeterKg: 2.4, qty: 1))
        XCTAssertNil(Reverse.neededLengthM(targetKg: 10, perMeterKg: 0, qty: 1))
    }

    func testNeededPlateThickness() {
        XCTAssertEqual(Reverse.neededThickness(targetKg: 300, thickness: 10, pieceKg: 157, qty: 1)!, 10 * 300 / 157, accuracy: 1e-9)
        XCTAssertEqual(Reverse.neededThickness(targetKg: 300, thickness: 10, pieceKg: 157, qty: 2)!, 10 * 300 / 314, accuracy: 1e-9)
        XCTAssertNil(Reverse.neededThickness(targetKg: 300, thickness: 0, pieceKg: 157, qty: 1))
    }

    func testWholePieces() {
        XCTAssertEqual(Reverse.wholePieces(targetKg: 235.5, pieceKg: 78.5), 3)
        XCTAssertEqual(Reverse.wholePieces(targetKg: 0.3, pieceKg: 0.1), 3) // 2.9999999999999996 in floating point
        XCTAssertEqual(Reverse.wholePieces(targetKg: 235.4, pieceKg: 78.5), 2)
        XCTAssertEqual(Reverse.wholePieces(targetKg: 50, pieceKg: 78.5), 0)
        XCTAssertEqual(Reverse.wholePieces(targetKg: 50, pieceKg: 0), 0)
    }

    func testReverseThroughModelImperial() {
        let c = CalcModel(units: .metric)                 // plate 10 × 1000 × 2000 = 157 kg
        c.targetText = "300"
        c.switchUnits(to: .imperial)
        guard case .ok(let r) = c.compute(density: 7850) else { return XCTFail() }
        let t = Reverse.neededThickness(targetKg: c.targetKg!, thickness: c.thicknessMM!, pieceKg: r.pieceKg, qty: c.quantity)!
        XCTAssertEqual(t, 10 * 300 / 157, accuracy: 1e-9)  // mm, independent of display units
    }
}

@MainActor
final class CalcSnapshotTests: XCTestCase {
    func testSnapshotRoundTripSurvivesTermination() throws {
        let a = CalcModel(units: .metric)
        a.shape = .pipe; a.fields = ["od": "60,3", "wt": "3.6"]; a.lengthText = "4"; a.qtyText = "7"
        a.priceText = "2.5"; a.targetText = "100"; a.materialID = "ss304"; a.familyID = "heb"; a.itemIndex = 3; a.rebarIndex = 2
        let enc = JSONEncoder(); enc.outputFormatting = .sortedKeys
        let data = try enc.encode(a.snapshot)
        let b = CalcModel(units: .metric)
        b.restore(try JSONDecoder().decode(CalcModel.Snapshot.self, from: data))
        XCTAssertEqual(try enc.encode(b.snapshot), data)
    }

    func testSnapshotFromOtherUnitSystemIsIgnored() {
        let a = CalcModel(units: .metric); a.lengthText = "4"
        let b = CalcModel(units: .imperial)
        b.restore(a.snapshot)
        b.restore(nil)
        XCTAssertEqual(b.lengthText, "20")
    }
}
