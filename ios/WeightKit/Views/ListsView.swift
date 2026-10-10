import SwiftUI

struct ListsView: View {
    @EnvironmentObject var lists: ListStore
    @EnvironmentObject var pro: ProStore
    @EnvironmentObject var settings: AppSettings
    @State private var showNew = false
    @State private var newName = ""
    @State private var showPaywall = false

    var body: some View {
        NavigationStack {
            Group {
                if lists.lists.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "list.bullet.rectangle").font(.system(size: 44)).foregroundStyle(.secondary)
                        Text(L("lists_empty")).multilineTextAlignment(.center).foregroundStyle(.secondary)
                            .accessibilityIdentifier("lists_empty")
                    }
                    .padding(32)
                } else {
                    List {
                        ForEach(lists.lists) { l in
                            NavigationLink(value: l.id) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(l.name).font(.headline)
                                    Text("\(l.items.count) \(L("items").lowercased()) · \(Fmt.weight(l.grossKg, settings.units))")
                                        .font(.subheadline).foregroundStyle(.secondary)
                                }
                                .padding(.vertical, 4)
                            }
                            .accessibilityIdentifier("list_row")
                        }
                        .onDelete { lists.lists.remove(atOffsets: $0) }
                    }
                }
            }
            .safeAreaInset(edge: .bottom) { BannerSlot() }
            .navigationTitle(L("tab_lists"))
            .navigationDestination(for: UUID.self) { id in ListDetailView(listID: id) }
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        // Free: one list. More lists = Pro.
                        if lists.lists.count >= 1 && !pro.hasPro { showPaywall = true } else { newName = ""; showNew = true }
                    } label: { Label(L("new_list"), systemImage: "plus") }
                    .accessibilityIdentifier("new_list")
                }
            }
            .alert(L("new_list"), isPresented: $showNew) {
                TextField(L("list_name"), text: $newName).accessibilityIdentifier("new_list_name")
                Button(L("save")) { lists.newList(name: newName) }.accessibilityIdentifier("dialog_save")
                Button(L("cancel"), role: .cancel) {}
            }
            .sheet(isPresented: $showPaywall) { PaywallView() }
        }
    }
}

struct ListDetailView: View {
    @EnvironmentObject var lists: ListStore
    @EnvironmentObject var pro: ProStore
    @EnvironmentObject var settings: AppSettings
    let listID: UUID
    @State private var share: ShareItem?
    @FocusState private var focused: Bool
    @State private var showPaywall = false

    private var index: Int? { lists.lists.firstIndex { $0.id == listID } }

    var body: some View {
        if let i = index {
            let list = lists.lists[i]
            List {
                Section {
                    TextField(L("list_name"), text: $lists.lists[i].name)
                        .font(.headline)
                        .accessibilityIdentifier("list_name")
                }
                Section(L("items")) {
                    ForEach(list.items) { it in
                        VStack(alignment: .leading, spacing: 3) {
                            HStack {
                                Text("\(it.qty) × \(it.shape)").font(.body.weight(.semibold))
                                Spacer()
                                Text(Fmt.weight(it.totalKg, settings.units)).font(.body.weight(.semibold)).monospacedDigit()
                            }
                            Text(it.size).font(.subheadline)
                            HStack {
                                Text(it.material).font(.caption).foregroundStyle(.secondary)
                                Spacer()
                                if let c = it.cost { Text(Fmt.money(c, settings.currency)).font(.caption).foregroundStyle(.secondary) }
                            }
                        }
                        .padding(.vertical, 2)
                        .accessibilityElement(children: .combine)
                        .accessibilityIdentifier("list_item")
                    }
                    .onDelete { lists.lists[i].items.remove(atOffsets: $0) }
                }
                Section {
                    pctRow(L("waste"), value: $lists.lists[i].wastePct, id: "field_waste")
                    pctRow(L("markup"), value: $lists.lists[i].markupPct, id: "field_markup")
                    total(L("total_weight"), Fmt.weight(list.grossKg, settings.units), id: "total_weight")
                    if list.hasCost {
                        total(L("subtotal"), Fmt.money(list.netCost, settings.currency), id: "subtotal")
                        total(L("total"), Fmt.money(list.totalCost, settings.currency), id: "total_cost")
                    }
                }
                Section {
                    Button { export(pdf: true, list) } label: { exportLabel(L("export_pdf"), "doc.richtext") }
                        .accessibilityIdentifier("export_pdf")
                    Button { export(pdf: false, list) } label: { exportLabel(L("export_csv"), "tablecells") }
                        .accessibilityIdentifier("export_csv")
                    TrialNotice(id: "trial_notice_export")
                }
            }
            .navigationTitle(list.name)
            .navigationBarTitleDisplayMode(.inline)
            .scrollDismissesKeyboard(.interactively)
            .toolbar {
                // decimal pads have no return key: same "Done" as on the calculator
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button(L("done")) { focused = false }
                }
            }
            .sheet(item: $share) { s in ShareSheet(items: [s.url]) }
            .sheet(isPresented: $showPaywall) { PaywallView() }
        } else {
            EmptyView()
        }
    }

    private func exportLabel(_ t: String, _ icon: String) -> some View {
        HStack {
            Label(t, systemImage: icon)
            Spacer()
            if !pro.purchased { ProBadge() }
        }
        .frame(minHeight: 44)
    }

    private func pctRow(_ label: String, value: Binding<Double>, id: String) -> some View {
        HStack {
            Text(label)
            Spacer()
            TextField("0", value: value, format: .number)
                .keyboardType(.decimalPad)
                .focused($focused)
                .multilineTextAlignment(.trailing)
                .frame(width: 80)
                .accessibilityLabel(label)
                .accessibilityIdentifier(id)
            Text("%").foregroundStyle(.secondary)
        }
    }

    private func total(_ a: String, _ b: String, id: String) -> some View {
        HStack { Text(a).font(.headline); Spacer(); Text(b).font(.headline).monospacedDigit() }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier(id)
    }

    private func export(pdf: Bool, _ list: CutList) {
        guard pro.hasPro else { showPaywall = true; return }
        let url = pdf ? Exporter.pdf(list, settings: settings) : Exporter.csv(list, settings: settings)
        share = ShareItem(url: url)
    }
}

struct ShareItem: Identifiable {
    let id = UUID()
    let url: URL
}

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}
