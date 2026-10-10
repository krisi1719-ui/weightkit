import StoreKitTest
import XCTest

/// Shared helpers. Each test launches a clean app (-wkReset ≈ new install) unless told otherwise.
class UITestBase: XCTestCase {
    var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        // StoreKit test purchases live outside the app (a reset does not remove them): start every test unpaid
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("Pro.storekit")
        if let s = try? SKTestSession(contentsOf: url) {
            s.resetToDefaultState()
            s.askToBuyEnabled = false
            s.clearTransactions()
        }
    }

    /// - days: debug clock offset (0 = trial, 20 = trial over)
    func launch(days: Double = 0, lang: String = "en", region: String = "GB", reset: Bool = true,
                theme: String = "light", units: String? = "metric", currency: String? = "€", extra: [String] = []) {
        app = XCUIApplication()
        if days > 0 && reset {
            // install at day 0 (first launch is stamped with the debug clock), then come back `days` later
            launch(days: 0, lang: lang, region: region, reset: true, theme: theme, units: units, currency: currency, extra: extra)
            app.terminate()
            launch(days: days, lang: lang, region: region, reset: false, theme: theme, units: units, currency: currency, extra: extra)
            return
        }
        var args = ["-wkUITest", "-wkAutoTrial", "-wkClockOffsetHours", String(days * 24),
                    "-AppleLanguages", "(\(lang))", "-AppleLocale", "\(lang)_\(region)", "-theme", theme]
        if reset { args.append("-wkReset") }
        if let units { args += ["-units", units] }
        if let currency { args += ["-currency", currency] }
        app.launchArguments = args + extra
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 20))
        if days >= 14 { answerConsent() }
    }

    /// After the trial the app (correctly) asks for ad consent: choose the privacy-preserving answers.
    func answerConsent() {
        let no = app.buttons["Do not consent"].firstMatch
        if no.waitForExistence(timeout: 12) { no.tap() }
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let att = springboard.buttons["Ask App Not to Track"]
        if att.waitForExistence(timeout: 4) { att.tap() }
    }

    /// Debug-only label with the entitlement state (see DebugHooks.stateLine).
    var state: String { label("debug_state") }

    func el(_ id: String) -> XCUIElement { app.descendants(matching: .any)[id].firstMatch }
    func field(_ id: String) -> XCUIElement { app.textFields[id].firstMatch }
    func value(_ id: String) -> String { field(id).value as? String ?? "" }
    func label(_ id: String) -> String { el(id).label }
    func exists(_ id: String, _ t: TimeInterval = 0) -> Bool { t > 0 ? el(id).waitForExistence(timeout: t) : el(id).exists }

    func tab(_ i: Int) {
        dismissKeyboard()
        app.tabBars.buttons.element(boundBy: i).tap()
    }

    /// On screen (an off-screen element has an invalid activation point, isHittable would throw).
    func onScreen(_ e: XCUIElement) -> Bool {
        guard e.exists else { return false }
        let f = e.frame, w = app.windows.firstMatch.frame
        let kb = app.keyboards.firstMatch
        var bottom = kb.exists ? kb.frame.minY : w.maxY - 90 // above the tab bar
        // the calculator's result card is pinned over the bottom of the scroll view
        for id in ["result_total", "result_missing", "result_wall"] {
            let r = app.descendants(matching: .any)[id].firstMatch
            if r.exists && r.frame.minY > w.midY { bottom = min(bottom, r.frame.minY - 24) }
        }
        return !f.isEmpty && f.minX >= w.minX && f.maxX <= w.maxX && f.minY >= w.minY + 90 && f.maxY <= bottom
    }

    /// Label of the keyboard toolbar "Done" button (localized by the app).
    var doneLabel = "Done"

    func tap(_ id: String, file: StaticString = #filePath, line: UInt = #line) {
        let e = el(id)
        XCTAssertTrue(e.waitForExistence(timeout: 5), "missing \(id)", file: file, line: line)
        scrollTo(e)
        e.tap()
    }

    func scrollTo(_ e: XCUIElement) {
        guard e.exists, !onScreen(e) else { return }
        let main = app.scrollViews.firstMatch.exists ? app.scrollViews.firstMatch : app.collectionViews.firstMatch
        var n = 0
        while !onScreen(e) && n < 8 { (e.frame.minY > app.windows.firstMatch.frame.midY ? main.swipeUp(velocity: .slow) : main.swipeDown(velocity: .slow)); n += 1 }
    }

    /// Replace the text of a field.
    func type(_ id: String, _ text: String) {
        let f = field(id)
        XCTAssertTrue(f.waitForExistence(timeout: 5), "missing field \(id)")
        scrollTo(f)
        // tap at the far right so the cursor lands after the text (number fields are right-aligned)
        f.coordinate(withNormalizedOffset: CGVector(dx: 0.97, dy: 0.5)).tap()
        if !(f.value(forKey: "hasKeyboardFocus") as? Bool ?? false) { f.tap() }
        // the value may equal the placeholder ("€"), so always delete
        let old = f.value as? String ?? ""
        f.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: old.count + 2))
        if !text.isEmpty { f.typeText(text) }
        dismissKeyboard()
        let now = f.value as? String ?? ""
        XCTAssertEqual(now == f.placeholderValue ? "" : now, text, "typed into \(id)")
    }

    func dismissKeyboard() {
        guard app.keyboards.firstMatch.exists else { return }
        let done = app.toolbars.buttons[doneLabel]
        if done.exists { done.tap() } else if app.buttons[doneLabel].exists { app.buttons[doneLabel].firstMatch.tap() }
        else if app.keyboards.buttons.count > 0 { app.keyboards.buttons.element(boundBy: app.keyboards.buttons.count - 1).tap() }
        else { app.typeText("\n") } // return key (text fields without a toolbar)
        _ = app.keyboards.firstMatch.waitForNonExistence(timeout: 2)
    }

    func shape(_ raw: String) {
        dismissKeyboard()
        let row = app.scrollViews["shape_row"]
        var n = 0
        while !onScreen(row) && n < 6 { app.scrollViews.firstMatch.swipeDown(velocity: .fast); n += 1 }
        let chip = app.buttons["shape_" + raw]
        let all = ["plate", "round", "square", "hex", "flat", "pipe", "rectTube", "angle", "channel", "ibeam", "tee", "rebar", "section"]
        _ = all
        n = 0
        while !onScreen(chip) && n < 12 {
            let w = app.windows.firstMatch.frame
            let toLeft = chip.frame.minX > w.midX // chip is right of the screen: drag content to the left
            let a = row.coordinate(withNormalizedOffset: CGVector(dx: toLeft ? 0.85 : 0.15, dy: 0.5))
            let b = row.coordinate(withNormalizedOffset: CGVector(dx: toLeft ? 0.35 : 0.65, dy: 0.5))
            a.press(forDuration: 0.05, thenDragTo: b)
            n += 1
        }
        chip.tap()
    }

    /// Text of a combined element ("Label, value").
    func ends(_ id: String, with s: String, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(exists(id, 3), "missing \(id)", file: file, line: line)
        XCTAssertTrue(label(id).hasSuffix(s), "\(id): '\(label(id))' should end with '\(s)'", file: file, line: line)
    }

    func shot(_ name: String) {
        let a = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        a.name = name
        a.lifetime = .keepAlways
        add(a)
    }

    /// Same formatting as the app's Fmt.weight (en_GB).
    func kg(_ v: Double, unit: String = "kg") -> String {
        let f = NumberFormatter()
        f.locale = Locale(identifier: "en_GB")
        f.numberStyle = .decimal
        f.maximumFractionDigits = abs(v) < 1 ? 3 : (abs(v) < 100 ? 2 : 1)
        return (f.string(from: NSNumber(value: v)) ?? "") + " " + unit
    }

    /// kg of a linear shape from its area in mm², steel, length in m – the app's formula.
    func steelKg(areaMM2: Double, lengthM: Double) -> Double { areaMM2 * 1e-6 * 7850 * lengthM }

    func menuItem(_ prefix: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", prefix)).firstMatch
    }
}
