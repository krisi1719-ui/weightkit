import StoreKitTest
import XCTest

/// The same flows as Android (CalculatorFlowsTest, ListsFlowsTest, ProSettingsAdsTest).
final class FlowTests: UITestBase {

    // Flow 1
    func testF01FirstLaunchMetricPlateNoAdNoPaywall() {
        launch(units: nil, currency: nil)
        ends("result_total", with: "157 kg")                    // default sheet 10 × 1000 × 2000 mm
        XCTAssertEqual(value("field_t"), "10")
        type("field_pl", "1000")
        ends("result_total", with: "78.5 kg")
        sleep(3)                                                 // consent / ATT used to appear here
        XCTAssertFalse(exists("paywall"))
        XCTAssertFalse(exists("banner_ad"))
        XCTAssertFalse(app.alerts.firstMatch.exists)
        XCTAssertFalse(XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.firstMatch.exists, "no ATT prompt")
        XCTAssertTrue(app.buttons["shape_plate"].isSelected)
        shot("f01_first_launch_en_GB")
    }

    func testF01bFirstLaunchUSIsImperial() {
        launch(region: "US", units: nil, currency: nil)
        ends("result_total", with: "326.7 lb")
        XCTAssertEqual(value("field_t"), "0.25")
    }

    // Flow 2
    func testF02EveryShapeChipLiveResultValuesKept() {
        launch()
        // expected kg for L = 2 m (plate: 5 × 50 × 1000 mm) – same numbers as Android
        let pi = Double.pi
        let a: [String: Double] = [
            "round": pi / 4 * 400, "square": 100, "hex": 3.0.squareRoot() / 2 * 576, "flat": 250,
            "pipe": pi / 4 * (60.3 * 60.3 - 53.1 * 53.1), "rectTube": 100 * 50 - 92.8 * 42.8, "angle": 5 * (10 + 50 - 5),
            "channel": 2 * 50 * 8.5 + 83 * 6, "ibeam": 2 * 50 * 8.5 + 83 * 6, "tee": 50 * 8.5 + 91.5 * 6,
            "rebar": pi / 4 * 196, "section": 764,
        ]
        let I = ["h": "100", "b": "50", "tw": "6", "tf": "8.5"]
        let expected: [(String, [String: String], String)] = [
            ("plate", ["t": "5", "w": "50", "pl": "1000"], kg(5 * 50 * 1000 * 1e-9 * 7850)),
            ("round", ["d": "20"], kg(steelKg(areaMM2: a["round"]!, lengthM: 2))),
            ("square", ["a": "10"], kg(steelKg(areaMM2: a["square"]!, lengthM: 2))),
            ("hex", ["s": "24"], kg(steelKg(areaMM2: a["hex"]!, lengthM: 2))),
            ("flat", ["w": "50", "t": "5"], kg(steelKg(areaMM2: a["flat"]!, lengthM: 2))),
            ("pipe", ["od": "60.3", "wt": "3.6"], kg(steelKg(areaMM2: a["pipe"]!, lengthM: 2))),
            ("rectTube", ["h": "100", "w": "50", "wt": "3.6"], kg(steelKg(areaMM2: a["rectTube"]!, lengthM: 2))),
            ("angle", ["a": "10", "b": "50", "t": "5"], kg(steelKg(areaMM2: a["angle"]!, lengthM: 2))),
            ("channel", I, kg(steelKg(areaMM2: a["channel"]!, lengthM: 2))),
            ("ibeam", I, kg(steelKg(areaMM2: a["ibeam"]!, lengthM: 2))),
            ("tee", I, kg(steelKg(areaMM2: a["tee"]!, lengthM: 2))),
            ("rebar", [:], kg(steelKg(areaMM2: a["rebar"]!, lengthM: 2))),
            ("section", [:], kg(steelKg(areaMM2: a["section"]!, lengthM: 2))),
        ]
        for (raw, dims, kg) in expected {
            shape(raw)
            XCTAssertTrue(app.buttons["shape_" + raw].isSelected, raw)
            for (k, v) in dims.sorted(by: { $0.key < $1.key }) where value("field_" + k) != v { type("field_" + k, v) }
            if raw != "plate" { type("field_length", "2") }
            ends("result_total", with: kg)
        }
        shot("f02_section_ipe80")
        shape("round")
        XCTAssertEqual(value("field_d"), "20")
        shape("plate")
        XCTAssertEqual(value("field_t"), "5")
        shape("pipe")
        type("field_wt", "40")
        XCTAssertTrue(exists("result_wall", 2))
        type("field_wt", "")
        XCTAssertTrue(exists("result_missing", 2))
        XCTAssertFalse(app.buttons["add_to_list"].isEnabled)
    }

    // Flow 3
    func testF03ProfilesTrialAndAfterTrial() {
        launch()
        shape("section")
        XCTAssertTrue(label("family_picker").contains("IPE"))
        XCTAssertFalse(exists("trial_notice_profile"))
        tap("family_picker"); menuItem("HEB").tap()
        XCTAssertTrue(label("family_picker").contains("HEB"))
        XCTAssertTrue(exists("trial_notice_profile", 2))
        ends("result_table", with: "20.4 kg/m")
        XCTAssertFalse(exists("paywall"))
        shot("f03_heb_trial")
        // trial ends while HEB is selected (calculator state is restored): the Pro table is not used
        XCUIDevice.shared.press(.home); sleep(2)
        launch(days: 20, reset: false)
        shape("section")
        XCTAssertTrue(label("family_picker").contains("IPE"), label("family_picker"))

        launch(days: 20)
        shape("section")
        tap("family_picker"); menuItem("UPN").tap()
        XCTAssertTrue(label("family_picker").contains("UPN"))
        tap("family_picker"); menuItem("HEB").tap()
        XCTAssertTrue(exists("paywall", 3))
        tap("paywall_done")
        XCTAssertTrue(label("family_picker").contains("UPN"), "selection unchanged")
    }

    // Flow 4
    func testF04RebarMaterialCustomMaterial() {
        launch()
        shape("rebar")
        tap("rebar_picker"); menuItem("Ø12").tap()
        ends("result_table", with: "0.888 kg/m")
        tap("material_row")
        let search = app.searchFields.firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 3))
        search.tap(); search.typeText("304")
        XCTAssertTrue(app.buttons["material_ss304"].waitForExistence(timeout: 2))
        XCTAssertFalse(app.buttons["material_steel"].exists)
        shot("f04_material_search")
        app.buttons["material_ss304"].tap()
        XCTAssertEqual(label("material_name"), "Stainless 304 / 1.4301")
        XCTAssertEqual(label("material_density"), "7,930 kg/m³")

        tab(2)
        tap("add_material")
        let alert = app.alerts.firstMatch
        XCTAssertTrue(alert.waitForExistence(timeout: 3))
        alert.textFields.element(boundBy: 0).tap(); alert.textFields.element(boundBy: 0).typeText("Test alloy")
        alert.textFields.element(boundBy: 1).tap(); alert.textFields.element(boundBy: 1).typeText("8000,5")
        alert.buttons["Save"].tap()
        XCTAssertTrue(exists("custom_material", 5))
        XCTAssertEqual(app.descendants(matching: .any).matching(identifier: "custom_material").count, 1)
        tab(0)
        tap("material_row")
        app.searchFields.firstMatch.tap(); app.searchFields.firstMatch.typeText("alloy")
        app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'material_custom_'")).firstMatch.tap()
        XCTAssertEqual(label("material_name"), "Test alloy")
        XCTAssertEqual(label("material_density"), "8,000.5 kg/m³")
        tab(2)
        let row = el("custom_material")
        row.swipeLeft()
        app.buttons["Delete"].tap()
        XCTAssertFalse(exists("custom_material"))
        tab(0)
        XCTAssertEqual(label("material_name"), "Carbon steel (S235/S355, A36)")

        launch(days: 20)
        tab(2)
        tap("add_material")
        XCTAssertTrue(exists("paywall", 3))
    }

    // Flow 5
    func testF05UnitsToggleToolbarAndSettings() {
        launch()
        type("field_t", "12.7")
        ends("result_total", with: "199.4 kg")
        app.segmentedControls["units_toggle"].buttons.element(boundBy: 1).tap()
        XCTAssertEqual(value("field_t"), "0.5")
        XCTAssertEqual(value("field_w"), "39.3701")
        ends("result_total", with: "439.6 lb")
        app.segmentedControls["units_toggle"].buttons.element(boundBy: 0).tap()
        XCTAssertEqual(value("field_t"), "12.7")
        XCTAssertEqual(value("field_w"), "1000")
        XCTAssertEqual(value("field_pl"), "2000")
        ends("result_total", with: "199.4 kg")
        tab(2)
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Imperial")).firstMatch.tap()
        tab(0)
        XCTAssertEqual(value("field_t"), "0.5")
        tab(2)
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Metric")).firstMatch.tap()
        tab(0)
        XCTAssertEqual(value("field_t"), "12.7")
        ends("result_total", with: "199.4 kg")
    }

    // Flow 6
    func testF06PriceCostAndReverse() {
        launch()
        XCTAssertFalse(exists("result_cost"))
        type("field_price", "2,5")
        ends("result_cost", with: "392.50 €")
        type("field_qty", "2")
        ends("result_cost", with: "785.00 €")
        ends("result_piece", with: "157 kg")
        type("field_qty", "1")
        type("field_target", "300")
        ends("reverse_thickness", with: "19.11 mm")
        ends("reverse_pieces", with: "1")
        shape("round")
        type("field_d", "20"); type("field_length", "6")
        ends("reverse_length", with: "121.6 m")
        ends("reverse_pieces", with: "20")
        XCTAssertTrue(exists("trial_notice_reverse"))
        shot("f06_reverse")
        app.segmentedControls["units_toggle"].buttons.element(boundBy: 1).tap()
        XCTAssertEqual(value("field_price"), "1.134")
        ends("reverse_length", with: "399.1 ft")

        launch(days: 20)
        XCTAssertFalse(field("field_target").exists)
        shot("dbg_f06_before_unlock")
        tap("reverse_unlock")
        shot("dbg_f06_after_unlock")
        XCTAssertTrue(exists("paywall", 3))
    }

    private func addThreeItems() {
        type("field_price", "2")
        tap("add_to_list")
        XCTAssertTrue(app.staticTexts["toast"].waitForExistence(timeout: 2), "toast")
        type("field_price", "")
        shape("round"); type("field_d", "20"); type("field_length", "6")
        tap("add_to_list")
        shape("section")
        tap("add_to_list")
    }

    // Flow 7
    func testF07AddItemsEditListTotalsDelete() {
        launch()
        addThreeItems()
        tab(1)
        XCTAssertEqual(app.buttons.matching(identifier: "list_row").count, 1)
        XCTAssertTrue(app.buttons["list_row"].label.contains("3 items"))
        app.buttons["list_row"].tap()
        XCTAssertEqual(app.descendants(matching: .any).matching(identifier: "list_item").count, 3)
        type("list_name", "Оферта №1")
        type("field_waste", "10")
        type("field_markup", "20")
        let round6 = steelKg(areaMM2: .pi / 4 * 400, lengthM: 6), ipe6 = steelKg(areaMM2: 764, lengthM: 6)
        ends("total_weight", with: kg((157 + round6 + ipe6) * 1.1))
        ends("subtotal", with: "314.00 €")
        ends("total_cost", with: "414.48 €")
        shot("f07_list_detail")
        let item = app.descendants(matching: .any).matching(identifier: "list_item").element(boundBy: 1)
        item.swipeLeft(); app.buttons["Delete"].tap()
        XCTAssertEqual(app.descendants(matching: .any).matching(identifier: "list_item").count, 2)
        ends("total_weight", with: kg((157 + ipe6) * 1.1))
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.buttons["list_row"].label.contains("Оферта №1"))
        app.buttons["list_row"].tap()
        XCTAssertEqual(value("field_waste"), "10")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["list_row"].swipeLeft(); app.buttons["Delete"].tap()
        XCTAssertTrue(exists("lists_empty", 3))
    }

    // Flow 8
    func testF08SecondListTrialVsFree() {
        launch()
        tap("add_to_list")
        tab(1)
        tap("new_list")
        XCTAssertTrue(app.alerts.textFields.firstMatch.waitForExistence(timeout: 3))
        app.alerts.textFields.firstMatch.tap()
        app.alerts.textFields.firstMatch.typeText("Second")
        shot("dbg_f08_alert")
        app.alerts.buttons["Save"].tap()
        sleep(1)
        shot("dbg_f08_after_save")
        XCTAssertEqual(app.buttons.matching(identifier: "list_row").count, 2)
        launch(reset: false)                                     // lists survive a restart
        tab(1)
        XCTAssertEqual(app.buttons.matching(identifier: "list_row").count, 2)

        launch(days: 20)
        tap("add_to_list")
        tab(1)
        tap("new_list")
        XCTAssertTrue(exists("paywall", 3))
        XCTAssertFalse(app.alerts.firstMatch.exists)
        shot("f08_paywall_second_list")
    }

    // Flow 9
    func testF09ExportPdfCsvShareSheet() {
        launch(currency: "лв", extra: ["-company", "Метал ООД"])
        addThreeItems()
        tab(1)
        app.buttons["list_row"].tap()
        type("list_name", "Оферта Иванов")
        type("field_waste", "10")
        type("field_markup", "20")
        for id in ["export_csv", "export_pdf"] {
            tap(id)
            let sheet = app.otherElements["ActivityListView"]
            XCTAssertTrue(sheet.waitForExistence(timeout: 8) || app.navigationBars["UIActivityContentView"].waitForExistence(timeout: 2),
                          "share sheet for \(id)")
            shot("f09_share_\(id)")
            // file name is shown in the sheet header
            XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'Оферта Иванов'")).count > 0)
            let close = app.buttons["Close"].firstMatch
            if close.exists { close.tap() } else { app.swipeDown(velocity: .fast) }
            sleep(1)
        }
        // file contents are checked on the host (test-artifacts/exports) by the runner script

        launch(days: 20)
        tap("add_to_list")
        tab(1)
        app.buttons["list_row"].tap()
        tap("export_pdf")
        XCTAssertTrue(exists("paywall", 3))
    }

    // Flow 10
    func testF10SettingsCurrencyCompanyTheme() {
        launch(theme: "system")
        tab(2)
        type("currency", "лв")
        type("company", "Метал ООД")
        app.segmentedControls["theme_picker"].buttons.element(boundBy: 2).tap(); shot("f10_settings_dark")
        app.segmentedControls["theme_picker"].buttons.element(boundBy: 1).tap(); shot("f10_settings_light")
        app.segmentedControls["theme_picker"].buttons.element(boundBy: 0).tap()
        tab(0)
        type("field_price", "1")
        ends("result_cost", with: "157.00 лв")
        app.terminate()
        app.launchArguments = ["-wkUITest"]
        app.launch()
        tab(2)
        XCTAssertEqual(value("currency"), "лв")
        XCTAssertEqual(value("company"), "Метал ООД")
        XCTAssertTrue(app.segmentedControls["theme_picker"].buttons.element(boundBy: 0).isSelected)
    }

    // Flow 11 – StoreKit test session on the Pro.storekit configuration (purchase, restore, refund)
    func testF11PaywallPriceBuyRestoreRefund() throws {
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("Pro.storekit")
        let session = try SKTestSession(contentsOf: url)
        session.resetToDefaultState()
        session.disableDialogs = true
        session.clearTransactions()

        launch(days: 20)
        tab(1)
        tab(2)
        tap("settings_pro")
        XCTAssertTrue(exists("paywall", 3))
        let buy = app.buttons["buy_pro"]
        expectation(for: NSPredicate(format: "label CONTAINS '6.99'"), evaluatedWith: buy)
        waitForExpectations(timeout: 20)
        shot("f11_paywall_free")
        buy.tap()
        // StoreKit test purchase: the sheet closes on success (or shows "Pro unlocked")
        let done = NSPredicate { _, _ in !self.el("paywall").exists || self.el("pro_active").exists }
        expectation(for: done, evaluatedWith: NSObject())
        waitForExpectations(timeout: 45)
        XCTAssertTrue(state.contains("purchased=true"), state)
        sleep(1)
        if exists("paywall_done") { tap("paywall_done") }
        tab(1)
        XCTAssertFalse(exists("banner_ad"))
        tab(0); shape("section")
        tap("family_picker"); menuItem("HEB").tap()
        XCTAssertTrue(label("family_picker").contains("HEB"))
        XCTAssertFalse(exists("trial_notice_profile"))

        // "new install" (app data wiped): StoreKit still reports the purchase → Pro without tapping Restore;
        // Restore (AppStore.sync) is only shown while not owned
        launch(days: 20)
        tab(2); tap("settings_pro")
        XCTAssertTrue(exists("pro_active", 10))
        XCTAssertFalse(exists("restore"))
        tap("paywall_done")

        // refund → Pro removed on the next launch
        let tx = try XCTUnwrap(session.allTransactions().first)
        try session.refundTransaction(identifier: tx.identifier)
        launch(days: 20, reset: false)
        tab(2); tap("settings_pro")
        sleep(3)
        XCTAssertFalse(exists("pro_active"))
        XCTAssertTrue(app.buttons["buy_pro"].exists)
        session.clearTransactions()
    }

    // Flow 15
    func testF15BackgroundAndTerminationKeepState() {
        launch()
        shape("round"); type("field_d", "33"); type("field_length", "2,5"); type("field_price", "3")
        tap("add_to_list")
        XCUIDevice.shared.press(.home)
        sleep(2)
        app.activate()
        XCTAssertEqual(value("field_d"), "33")
        XCUIDevice.shared.press(.home)                         // background → state saved
        sleep(2)
        app.terminate()                                          // the system kills the app
        launch(reset: false)
        XCTAssertTrue(app.buttons["shape_round"].isSelected)
        XCTAssertEqual(value("field_d"), "33")
        XCTAssertEqual(value("field_length"), "2,5")
        XCTAssertEqual(value("field_price"), "3")
        tab(1)
        XCTAssertEqual(app.buttons.matching(identifier: "list_row").count, 1)
    }
}
