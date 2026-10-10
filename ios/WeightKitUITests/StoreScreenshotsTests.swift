import XCTest

/// Not a functional test: produces clean App Store screenshots per store language (same scenes as
/// Android StoreScreenshotsTest). Run on an iPhone 6.9"/6.3" simulator, e.g. in CI:
///   xcodebuild test -only-testing:WeightKitUITests/StoreScreenshotsTests ...
/// Screens are XCTest attachments named "store_<lang>_<NN_name>" (keepAlways); export them with
///   xcrun xcresulttool export attachments --path <result.xcresult> --output-path <dir>
final class StoreScreenshotsTests: UITestBase {

    private struct L {
        let lang: String, region: String, imperial: Bool, currency: String, listName: String
        let plate: [String], plPrice: String
        let family: String, profile: String, profLen: String, profQty: String
        let pipe: [String], pipeLen: String, pipeQty: String
        let roundD: String, roundLen: String, target: String
        let done: String
    }

    private static func metric(_ lang: String, _ region: String, _ cur: String, _ name: String, _ done: String) -> L {
        L(lang: lang, region: region, imperial: false, currency: cur, listName: name,
          plate: ["10", "1500", "3000"], plPrice: "1,2",
          family: "HEB", profile: "HEB 200", profLen: "6", profQty: "4",
          pipe: ["60,3", "3,6"], pipeLen: "6", pipeQty: "10", roundD: "20", roundLen: "6", target: "300", done: done)
    }

    private static let langs: [String: L] = [
        "en": L(lang: "en", region: "US", imperial: true, currency: "$", listName: "Warehouse frame",
                plate: ["0.25", "48", "96"], plPrice: "0.85",
                family: "W", profile: "W8x24", profLen: "20", profQty: "4",
                pipe: ["2.375", "0.154"], pipeLen: "20", pipeQty: "10", roundD: "0.75", roundLen: "20", target: "500",
                done: "Done"),
        "de": metric("de", "DE", "€", "Hallenrahmen", "Fertig"),
        "fr": metric("fr", "FR", "€", "Charpente entrepôt", "OK"),
        "es": metric("es", "ES", "€", "Estructura nave", "OK"),
        "tr": metric("tr", "TR", "₺", "Depo çatı", "Bitti"),
        "ru": metric("ru", "RU", "₽", "Каркас склада", "Готово"),
        "bg": metric("bg", "BG", "€", "Склад – конструкция", "Готово"),
    ]

    private var current = "en"

    private func store(_ name: String) {
        sleep(1)
        let a = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        a.name = "store_\(current)_\(name)"
        a.lifetime = .keepAlways
        add(a)
    }

    private func start(_ l: L, reset: Bool, theme: String = "light") {
        current = l.lang
        launch(lang: l.lang, region: l.region, reset: reset, theme: theme,
               units: l.imperial ? "imperial" : "metric", currency: l.currency,
               extra: ["-wkOwned", "-company", "Northline Metalworks"])
        doneLabel = l.done
    }

    /// Relaunch keeping the state: no focused field, cursor or keyboard toolbar on the picture.
    private func clean(_ l: L, theme: String = "light") {
        app.terminate()
        start(l, reset: false, theme: theme)
    }

    private func addItem() {
        tap("add_to_list")
        sleep(4) // the toast disappears
    }

    private func run(_ lang: String) {
        let l = Self.langs[lang]!
        start(l, reset: true)

        // 1 – sheet / plate with price
        shape("plate")
        type("field_t", l.plate[0]); type("field_w", l.plate[1]); type("field_pl", l.plate[2])
        type("field_qty", "1"); type("field_price", l.plPrice)
        addItem()
        clean(l); shape("plate")
        store("01_plate")

        // 2 – standard profile (HEB 200 / W8x24)
        shape("section")
        tap("family_picker"); menuItem(l.family).tap()
        tap("profile_picker"); menuItem(l.profile).tap()
        type("field_length", l.profLen); type("field_qty", l.profQty)
        addItem()
        clean(l); shape("section")
        store("02_profiles")

        // 3 – pipe
        shape("pipe")
        type("field_od", l.pipe[0]); type("field_wt", l.pipe[1])
        type("field_length", l.pipeLen); type("field_qty", l.pipeQty)
        addItem()
        clean(l); shape("pipe")
        store("03_pipe")

        // 4 – materials
        tap("material_row")
        _ = app.searchFields.firstMatch.waitForExistence(timeout: 3)
        store("04_materials")
        app.buttons["material_steel"].tap() // closes the sheet, steel stays selected
        sleep(1)

        // 5 – reverse: how long for a target weight
        shape("round")
        type("field_d", l.roundD); type("field_length", l.roundLen); type("field_qty", "1")
        type("field_target", l.target)
        clean(l); shape("round")
        scrollTo(field("field_target"))
        store("05_reverse")

        // 6 – cut list with waste and markup
        tab(1); tap("list_row")
        type("list_name", l.listName); type("field_waste", "5"); type("field_markup", "15")
        clean(l); tab(1); tap("list_row")
        store("06_list")

        // 8 – dark mode calculator
        clean(l, theme: "dark"); tab(0); shape("plate")
        store("08_dark")
    }

    /// In-app purchase review screenshot: the Pro screen of a new user (trial not started yet).
    func test_paywall() {
        current = "review"
        app = XCUIApplication()
        app.launchArguments = ["-wkUITest", "-wkReset", "-AppleLanguages", "(en)", "-AppleLocale", "en_US",
                               "-theme", "light", "-units", "imperial", "-currency", "$"]
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 20))
        tab(2)
        tap("settings_pro")
        XCTAssertTrue(exists("paywall", 5))
        _ = el("start_trial").waitForExistence(timeout: 8)
        sleep(2)
        store("paywall")
    }

    func test_en() { run("en") }
    func test_de() { run("de") }
    func test_fr() { run("fr") }
    func test_es() { run("es") }
    func test_tr() { run("tr") }
    func test_ru() { run("ru") }
    func test_bg() { run("bg") }
}
