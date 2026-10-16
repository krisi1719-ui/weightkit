import SwiftUI

struct CalculatorView: View {
    @EnvironmentObject var settings: AppSettings
    @EnvironmentObject var pro: ProStore
    @EnvironmentObject var lists: ListStore
    @EnvironmentObject var calc: CalcModel
    @Environment(\.scenePhase) private var scenePhase

    @FocusState private var focus: String?
    @State private var showPaywall = false
    @State private var showMaterials = false
    @State private var toast: String?

    private var units: UnitSystem { settings.units }
    private var material: Material { settings.material(calc.materialID) }
    private var outcome: CalcOutcome { calc.compute(density: material.density) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    shapePicker
                    inputs
                    materialRow
                    priceRow
                    reverseSection
                    addButton
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .scrollDismissesKeyboard(.interactively)
            .safeAreaInset(edge: .bottom) { ResultCard(outcome: outcome, units: units, cost: { if case .ok(let r) = outcome { return calc.cost(r) } else { return nil } }(),
                                                       currency: settings.currency, qty: calc.quantity, linear: calc.shape.isLinear) }
            .onAppear { calc.proAllowed = pro.hasPro }
            .onReceive(pro.objectWillChange) { DispatchQueue.main.async { calc.proAllowed = pro.hasPro } }
            .onChange(of: scenePhase) { _ in calc.proAllowed = pro.hasPro } // trial may have ended meanwhile
            .navigationTitle(L(calc.shape.titleKey))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Picker(L("units"), selection: $settings.units) {
                        Text("mm · kg").tag(UnitSystem.metric)
                        Text("in · lb").tag(UnitSystem.imperial)
                    }
                    .pickerStyle(.segmented)
                    .frame(width: 150)
                    .accessibilityIdentifier("units_toggle")
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button(L("done")) { focus = nil }
                }
            }
            .sheet(isPresented: $showPaywall) { PaywallView() }
            .sheet(isPresented: $showMaterials) { MaterialPickerView(selected: $calc.materialID) }
            .overlay(alignment: .top) {
                if let toast {
                    Text(toast)
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(.regularMaterial, in: Capsule())
                        .accessibilityIdentifier("toast")
                        .padding(.top, 8)
                        .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
        }
    }

    // MARK: shape picker

    private var shapePicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(ShapeKind.allCases) { s in
                    let selected = s == calc.shape
                    Button {
                        calc.shape = s
                    } label: {
                        VStack(spacing: 6) {
                            ShapeIcon(shape: s, color: selected ? .white : .primary.opacity(0.8))
                                .frame(width: 34, height: 34)
                            Text(L(s.titleKey))
                                .font(.caption2.weight(.medium))
                                .lineLimit(2)
                                .multilineTextAlignment(.center)
                                .frame(width: 74)
                                .foregroundStyle(selected ? .white : .primary)
                                // fixed-width chip: the icon carries the meaning, VoiceOver reads the full name
                                .dynamicTypeSize(...DynamicTypeSize.xxLarge)
                        }
                        .padding(.vertical, 10)
                        .frame(minHeight: 84)
                        .background(selected ? Color.accentColor : Color(.secondarySystemBackground),
                                    in: RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(L(s.titleKey))
                    .accessibilityAddTraits(selected ? .isSelected : [])
                    .accessibilityIdentifier("shape_" + s.rawValue)
                }
            }
        }
        .accessibilityIdentifier("shape_row")
    }

    // MARK: inputs

    @ViewBuilder private var inputs: some View {
        VStack(spacing: 10) {
            if calc.shape == .section { sectionPickers }
            if calc.shape == .rebar { rebarPicker }
            ForEach(calc.shape.fields, id: \.self) { f in
                NumberField(label: L(f.labelKey), symbol: f.symbol, unit: units.dimUnit,
                            text: Binding(get: { calc.text(f.key) }, set: { calc.setText(f.key, $0) }),
                            focus: $focus, focusKey: f.key, id: "field_" + f.key)
            }
            if calc.shape.isLinear {
                NumberField(label: L("dim_length"), symbol: "L", unit: units.lengthUnit,
                            text: $calc.lengthText, focus: $focus, focusKey: "length", id: "field_length")
            }
            NumberField(label: L("quantity"), symbol: "n", unit: L("qty"),
                        text: $calc.qtyText, focus: $focus, focusKey: "qty", id: "field_qty", integer: true)
        }
    }

    private var sectionPickers: some View {
        let fams = Catalog.shared.profileFamilies
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(L("standard")).foregroundStyle(.secondary)
                Spacer()
                Menu {
                    ForEach(Array(fams.enumerated()), id: \.element.id) { i, f in
                        Button {
                            if f.pro && !pro.hasPro { showPaywall = true } else {
                                calc.familyID = f.id
                                calc.itemIndex = 0
                            }
                        } label: {
                            Text(f.name + (f.pro && !pro.purchased ? "  ★ Pro" : ""))
                        }
                        .accessibilityIdentifier("family_picker_item_\(i)")
                    }
                } label: {
                    Text(calc.family?.name ?? "—").fontWeight(.semibold)
                    Image(systemName: "chevron.up.chevron.down").font(.caption)
                }
                .accessibilityIdentifier("family_picker")
                .accessibilityValue(calc.family?.name ?? "")
            }
            if let f = calc.family {
                HStack {
                    Text(L("profile")).foregroundStyle(.secondary)
                    Spacer()
                    Picker(L("profile"), selection: $calc.itemIndex) {
                        ForEach(Array(f.items.enumerated()), id: \.offset) { i, it in
                            Text(it.name).tag(i)
                        }
                    }
                    .pickerStyle(.menu)
                    .accessibilityIdentifier("profile_picker")
                }
                Text(f.source).font(.caption).foregroundStyle(.secondary)
                if f.pro { TrialNotice(id: "trial_notice_profile") }
            }
        }
        .padding(12)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }

    private var rebarPicker: some View {
        HStack {
            Text(L("dim_diameter")).foregroundStyle(.secondary)
            Spacer()
            Picker(L("dim_diameter"), selection: $calc.rebarIndex) {
                ForEach(Array((Catalog.shared.rebar?.items ?? []).enumerated()), id: \.offset) { i, it in
                    Text(it.name + " mm").tag(i)
                }
            }
            .pickerStyle(.menu)
            .accessibilityIdentifier("rebar_picker")
        }
        .padding(12)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }

    // MARK: material & price

    private var materialRow: some View {
        Button { showMaterials = true } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L("material")).font(.caption).foregroundStyle(.secondary)
                    Text(material.name).font(.body.weight(.semibold)).foregroundStyle(.primary)
                        .accessibilityIdentifier("material_name")
                }
                Spacer()
                Text(densityText(material.density)).font(.callout).foregroundStyle(.secondary)
                    .accessibilityIdentifier("material_density")
                Image(systemName: "chevron.right").foregroundStyle(.tertiary)
            }
            .padding(12)
            .frame(minHeight: 56)
            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("material_row")
    }

    private func densityText(_ d: Double) -> String {
        units == .metric ? Fmt.smart(d) + " kg/m³" : Fmt.fixed(d / 27679.9, 4) + " lb/in³"
    }

    private var priceLabel: String {
        switch calc.priceUnit {
        case .kg: return L(units == .metric ? "price_per_kg" : "price_per_lb")
        case .m: return L(units == .metric ? "price_per_m" : "price_per_ft")
        case .pc: return L("price_per_piece")
        }
    }

    /// Price per kg (free) · per metre · per piece (Pro). Sheets have no length, so no per metre there.
    private var priceRow: some View {
        VStack(alignment: .leading, spacing: 8) {
            NumberField(label: priceLabel, symbol: "$",
                        unit: settings.currency, text: $calc.priceText, focus: $focus, focusKey: "price", id: "field_price")
            HStack(spacing: 8) {
                ForEach(PriceUnit.allCases.filter { $0 != .m || calc.shape != .plate }, id: \.self) { u in
                    Button {
                        if u != .kg && !pro.hasPro { showPaywall = true } else { calc.changePriceUnit(u) }
                    } label: {
                        HStack(spacing: 4) {
                            Text(unitLabel(u))
                            if u != .kg && !pro.purchased { ProBadge() }
                        }
                        .font(.subheadline)
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(calc.priceUnit == u ? Color.accentColor.opacity(0.18) : Color(.secondarySystemBackground),
                                    in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(calc.priceUnit == u ? .isSelected : [])
                    .accessibilityIdentifier("price_unit_" + u.rawValue)
                }
            }
        }
        .onChange(of: calc.shape) { s in if s == .plate && calc.priceUnit == .m { calc.changePriceUnit(.kg) } }
    }

    private func unitLabel(_ u: PriceUnit) -> String {
        switch u {
        case .kg: return L(units == .metric ? "price_unit_kg" : "price_unit_lb")
        case .m: return L(units == .metric ? "price_unit_m" : "price_unit_ft")
        case .pc: return L("price_unit_pc")
        }
    }

    // MARK: reverse

    @ViewBuilder private var reverseSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(L("reverse_title")).font(.headline)
                Spacer()
                if !pro.purchased { ProBadge() }
            }
            if pro.hasPro {
                NumberField(label: L("target_weight"), symbol: "m", unit: units.weightUnit,
                            text: $calc.targetText, focus: $focus, focusKey: "target", id: "field_target")
                reverseResults
                TrialNotice(id: "trial_notice_reverse")
            } else {
                Button(L("buy_pro_noprice")) { showPaywall = true }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("reverse_unlock")
            }
        }
        .padding(12)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder private var reverseResults: some View {
        if let targetKg = calc.targetKg, case .ok(let r) = outcome {
            VStack(alignment: .leading, spacing: 6) {
                if let perM = r.perMeterKg {
                    if let lenM = Reverse.neededLengthM(targetKg: targetKg, perMeterKg: perM, qty: calc.quantity) {
                        row(L("needed_length"), Fmt.smart(lenM / units.mPerLength) + " " + units.lengthUnit, id: "reverse_length")
                    }
                } else if calc.shape == .plate, let t = calc.thicknessMM,
                          let need = Reverse.neededThickness(targetKg: targetKg, thickness: t, pieceKg: r.pieceKg, qty: calc.quantity) {
                    row(L("needed_thickness"), Fmt.smart(need / units.mmPerDim) + " " + units.dimUnit, id: "reverse_thickness")
                }
                if r.pieceKg > 0 {
                    row(L("pieces_in_target"), String(Reverse.wholePieces(targetKg: targetKg, pieceKg: r.pieceKg)), id: "reverse_pieces")
                }
            }
        }
    }

    private func row(_ a: String, _ b: String, id: String) -> some View {
        HStack { Text(a).foregroundStyle(.secondary); Spacer(); Text(b).font(.body.weight(.semibold)).monospacedDigit() }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier(id)
    }

    // MARK: add to list

    private var addButton: some View {
        Button {
            guard case .ok(let r) = outcome else { return }
            let item = ListItem(shape: calc.shape == .section ? (calc.family?.name ?? L(calc.shape.titleKey)) : L(calc.shape.titleKey),
                                material: material.name, size: calc.sizeDescription(),
                                qty: calc.quantity, pieceKg: r.pieceKg, pricePerKg: calc.pricePerKg,
                                priceUnit: calc.effectivePriceUnit, pieceM: calc.pieceLengthM)
            let name = lists.add(item)
            focus = nil
            withAnimation { toast = L("added_to", name) }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) { withAnimation { toast = nil } }
        } label: {
            Label(L("add_to_list"), systemImage: "plus.circle.fill")
                .font(.headline)
                .frame(maxWidth: .infinity, minHeight: 50)
        }
        .buttonStyle(.borderedProminent)
        .accessibilityIdentifier("add_to_list")
        .disabled({ if case .ok = outcome { return false } else { return true } }())
        .padding(.bottom, 8)
    }
}

/// Labeled numeric input with unit suffix. Accepts "," or "." as decimal separator.
struct NumberField: View {
    let label: String
    let symbol: String
    let unit: String
    @Binding var text: String
    var focus: FocusState<String?>.Binding
    let focusKey: String
    let id: String
    var integer = false
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        // Accessibility text sizes: label above the value, so neither is cut off.
        if typeSize.isAccessibilitySize {
            VStack(alignment: .leading, spacing: 4) {
                Text(label).font(.body).foregroundStyle(.secondary)
                HStack { input }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
            .contentShape(Rectangle())
            .onTapGesture { focus.wrappedValue = focusKey }
        } else {
            HStack(spacing: 10) {
                Text(label)
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
                Spacer(minLength: 8)
                input
            }
            .padding(.horizontal, 12)
            .frame(minHeight: 52)
            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
            .contentShape(Rectangle())
            .onTapGesture { focus.wrappedValue = focusKey }
        }
    }

    @ViewBuilder private var input: some View {
            TextField("0", text: $text)
                .keyboardType(integer ? .numberPad : .decimalPad)
                .multilineTextAlignment(.trailing)
                .font(.title3.weight(.semibold).monospacedDigit())
                .focused(focus, equals: focusKey)
                .frame(minWidth: 90)
                .accessibilityLabel(label)
                .accessibilityIdentifier(id)
            Text(unit)
                .font(.callout)
                .foregroundStyle(.secondary)
                .frame(minWidth: 28, alignment: .leading)
    }
}

/// Result card pinned to the bottom of the calculator; updates live.
struct ResultCard: View {
    let outcome: CalcOutcome
    let units: UnitSystem
    let cost: Double?
    let currency: String
    let qty: Int
    let linear: Bool

    var body: some View {
        VStack(spacing: 8) {
            switch outcome {
            case .missing:
                Text(L("invalid_input")).font(.callout).foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, minHeight: 60)
                    .accessibilityIdentifier("result_missing")
            case .wall:
                Label(L("invalid_wall"), systemImage: "exclamationmark.triangle.fill")
                    .font(.callout.weight(.semibold)).foregroundStyle(.orange)
                    .frame(maxWidth: .infinity, minHeight: 60)
                    .accessibilityIdentifier("result_wall")
            case .ok(let r):
                HStack(alignment: .firstTextBaseline) {
                    Text(L("total_weight")).font(.subheadline).foregroundStyle(.secondary)
                    Spacer()
                    Text(Fmt.weight(r.totalKg, units))
                        .font(.system(size: 34, weight: .bold, design: .rounded).monospacedDigit())
                        .minimumScaleFactor(0.5)
                        .lineLimit(1)
                }
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("result_total")
                HStack(spacing: 16) {
                    if qty > 1 { metric(L("weight_piece"), Fmt.weight(r.pieceKg, units), "result_piece") }
                    if let pm = r.perMeterKg {
                        if units == .metric {
                            metric(L("weight_per_m"), Fmt.smart(pm) + " kg/m", "result_per_length")
                        } else {
                            metric(L("weight_per_ft"), Fmt.smart(pm * 0.3048 / 0.45359237) + " lb/ft", "result_per_length")
                        }
                    }
                    if let c = cost {
                        metric(L("cost"), Fmt.money(c, currency), "result_cost")
                    }
                    Spacer(minLength: 0)
                }
                HStack(spacing: 16) {
                    metric(L("volume"), volumeText(r.volumeM3 * Double(qty)), "result_volume")
                    if let s = r.surfaceM2 { metric(L("surface"), surfaceText(s * Double(qty)), "result_surface") }
                    if let t = r.tableKgM {
                        let txt = units == .metric ? Fmt.smart(t) + " kg/m"
                            : Fmt.smart(r.tableLbFt ?? t * 0.3048 / 0.45359237) + " lb/ft"
                        metric(L("table_mass", "").trimmingCharacters(in: .whitespaces), txt, "result_table")
                    }
                    Spacer(minLength: 0)
                }
            }
        }
        .padding(16)
        .background(.regularMaterial)
        .overlay(alignment: .top) { Divider() }
        // pinned over the inputs: keep it from covering most of the screen at the largest sizes
        .dynamicTypeSize(...DynamicTypeSize.accessibility1)
    }

    private func metric(_ label: String, _ value: String, _ id: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(label).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
            Text(value).font(.subheadline.weight(.semibold).monospacedDigit()).lineLimit(1).minimumScaleFactor(0.7)
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier(id)
    }

    private func volumeText(_ m3: Double) -> String {
        if units == .imperial {
            let in3 = m3 / 1.6387064e-5
            return in3 < 1728 ? Fmt.smart(in3) + " in³" : Fmt.smart(in3 / 1728) + " ft³"
        }
        return m3 < 0.001 ? Fmt.smart(m3 * 1e6) + " cm³" : Fmt.smart(m3 * 1000) + " dm³"
    }

    private func surfaceText(_ m2: Double) -> String {
        units == .metric ? Fmt.smart(m2) + " m²" : Fmt.smart(m2 / 0.09290304) + " ft²"
    }
}
