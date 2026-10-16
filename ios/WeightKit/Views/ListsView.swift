import SwiftUI
import UniformTypeIdentifiers

struct ListsView: View {
    @EnvironmentObject var lists: ListStore
    @EnvironmentObject var pro: ProStore
    @EnvironmentObject var settings: AppSettings
    @State private var showNew = false
    @State private var newName = ""
    @State private var showPaywall = false
    @State private var path: [UUID] = []
    @State private var share: ShareItem?
    @State private var importing = false
    @State private var message: String?

    var body: some View {
        NavigationStack(path: $path) {
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
            .navigationDestination(for: UUID.self) { id in ListDetailView(listID: id) { path.append($0) } }
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Menu {
                        Button { backup() } label: { Label(L("backup_export") + (pro.purchased ? "" : "  ★ Pro"), systemImage: "square.and.arrow.up") }
                            .disabled(lists.lists.isEmpty)
                            .accessibilityIdentifier("backup_export")
                        Button { if pro.hasPro { importing = true } else { showPaywall = true } } label: {
                            Label(L("backup_import") + (pro.purchased ? "" : "  ★ Pro"), systemImage: "square.and.arrow.down")
                        }
                        .accessibilityIdentifier("backup_import")
                    } label: { Label(L("more"), systemImage: "ellipsis.circle") }
                    .accessibilityIdentifier("lists_menu")
                }
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
            .sheet(item: $share) { s in ShareSheet(items: [s.url]) }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .plainText, .data]) { result in
                guard case .success(let url) = result else { return }
                let ok = url.startAccessingSecurityScopedResource()
                defer { if ok { url.stopAccessingSecurityScopedResource() } }
                if let data = try? Data(contentsOf: url), let n = Backup.restore(data, lists: lists, settings: settings) {
                    message = L("backup_done", String(n))
                } else {
                    message = L("backup_bad")
                }
            }
            .alert(message ?? "", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
                Button("OK", role: .cancel) {}
            }
        }
    }

    private func backup() {
        guard pro.hasPro else { showPaywall = true; return }
        share = ShareItem(url: Backup.file(lists: lists, settings: settings))
    }
}

struct ListDetailView: View {
    @EnvironmentObject var lists: ListStore
    @EnvironmentObject var pro: ProStore
    @EnvironmentObject var settings: AppSettings
    let listID: UUID
    var onOpen: (UUID) -> Void = { _ in }
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
                        // job / customer names: no autocorrect and no inline prediction that "Done" would accept
                        .autocorrectionDisabled()
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
                        let vatOn = list.vatPct > 0 && pro.hasPro
                        total(L(vatOn ? "total_ex_vat" : "total"), Fmt.money(list.totalCost, settings.currency), id: "total_cost")
                        if vatOn {
                            total(L("vat") + " " + Fmt.edit(list.vatPct) + " %", Fmt.money(list.vat, settings.currency), id: "total_vat")
                            total(L("total_with_vat"), Fmt.money(list.totalWithVat, settings.currency), id: "total_with_vat")
                        }
                    }
                }
                quoteSection(i)
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
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button {
                            if pro.hasPro { if let id = lists.duplicate(listID) { onOpen(id) } } else { showPaywall = true }
                        } label: { Label(L("duplicate") + (pro.purchased ? "" : "  ★ Pro"), systemImage: "plus.square.on.square") }
                        .accessibilityIdentifier("list_duplicate")
                    } label: { Label(L("more"), systemImage: "ellipsis.circle") }
                    .accessibilityIdentifier("list_menu")
                }
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

    /// Client, quote number, VAT, validity and notes for the PDF quote (Pro). Without Pro: one row that opens the paywall.
    @ViewBuilder private func quoteSection(_ i: Int) -> some View {
        Section {
            if pro.hasPro {
                TextField(L("client"), text: $lists.lists[i].client)
                    .autocorrectionDisabled()
                    .accessibilityIdentifier("quote_client")
                TextField(L("quote_no"), text: $lists.lists[i].quoteNo)
                    .autocorrectionDisabled()
                    .accessibilityIdentifier("quote_no")
                pctRow(L("vat_pct"), value: $lists.lists[i].vatPct, id: "quote_vat")
                HStack {
                    Text(L("valid_days"))
                    Spacer()
                    TextField("0", value: $lists.lists[i].validDays, format: .number)
                        .keyboardType(.numberPad)
                        .focused($focused)
                        .multilineTextAlignment(.trailing)
                        .frame(width: 80)
                        .accessibilityLabel(L("valid_days"))
                        .accessibilityIdentifier("quote_valid")
                }
                TextField(L("notes"), text: $lists.lists[i].notes, axis: .vertical)
                    .lineLimit(2...6)
                    .accessibilityIdentifier("quote_notes")
            } else {
                Button { showPaywall = true } label: {
                    HStack { Text(L("quote")); Spacer(); ProBadge() }.frame(minHeight: 44)
                }
                .accessibilityIdentifier("quote_locked")
            }
        } header: {
            if pro.hasPro { Text(L("quote")) }
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
