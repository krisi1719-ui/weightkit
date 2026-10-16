import SwiftUI
import PhotosUI
import UserMessagingPlatform

struct SettingsView: View {
    @State private var logoItem: PhotosPickerItem?
    @State private var logoError = false
    @EnvironmentObject var settings: AppSettings
    @EnvironmentObject var pro: ProStore
    @State private var showPaywall = false
    @State private var showAddMaterial = false
    @State private var newName = ""
    @State private var newDensity = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Button { showPaywall = true } label: {
                        HStack {
                            ShapeIcon(shape: .ibeam, color: .accentColor).frame(width: 28, height: 28)
                            VStack(alignment: .leading) {
                                Text(L("pro_title")).font(.headline).foregroundStyle(.primary)
                                Text(pro.purchased ? L("pro_active") :
                                        (pro.inTrial ? L("trial_active", pro.trialEndText) :
                                            (pro.canStartTrial ? L("start_trial") : L("buy_pro_noprice"))))
                                    .font(.subheadline).foregroundStyle(.secondary)
                            }
                        }
                    }
                    .accessibilityIdentifier("settings_pro")
                }
                Section(L("units")) {
                    Picker(L("units"), selection: $settings.units) {
                        Text(L("metric")).tag(UnitSystem.metric)
                        Text(L("imperial")).tag(UnitSystem.imperial)
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                    HStack {
                        Text(L("currency"))
                        Spacer()
                        TextField("€", text: $settings.currency)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 80)
                            .accessibilityLabel(L("currency"))
                            .accessibilityIdentifier("currency")
                    }
                }
                Section(L("company_pdf")) {
                    TextField(L("company_name"), text: $settings.company)
                        .accessibilityIdentifier("company")
                    TextField(L("company_address"), text: $settings.companyAddress, axis: .vertical)
                        .lineLimit(1...2)
                        .accessibilityIdentifier("company_address")
                    TextField(L("company_contact"), text: $settings.companyContact, axis: .vertical)
                        .lineLimit(1...2)
                        .accessibilityIdentifier("company_contact")
                    logoRow
                }
                Section(L("theme")) {
                    Picker(L("theme"), selection: $settings.theme) {
                        Text(L("theme_system")).tag(ThemeChoice.system)
                        Text(L("theme_light")).tag(ThemeChoice.light)
                        Text(L("theme_dark")).tag(ThemeChoice.dark)
                    }
                    .pickerStyle(.segmented)
                    .accessibilityIdentifier("theme_picker")
                }
                Section {
                    ForEach(settings.customMaterials) { m in
                        HStack {
                            Text(m.name)
                            Spacer()
                            Text(Fmt.smart(m.density) + " kg/m³").foregroundStyle(.secondary)
                        }
                        .accessibilityElement(children: .combine)
                        .accessibilityIdentifier("custom_material")
                    }
                    .onDelete { settings.customMaterials.remove(atOffsets: $0) }
                    Button {
                        if pro.hasPro { newName = ""; newDensity = ""; showAddMaterial = true } else { showPaywall = true }
                    } label: {
                        HStack { Label(L("add_material"), systemImage: "plus"); Spacer(); if !pro.purchased { ProBadge() } }
                    }
                    .accessibilityIdentifier("add_material")
                    TrialNotice(id: "trial_notice_materials")
                } header: { Text(L("custom_materials")) }
                Section(L("about")) {
                    Text(L("no_ads_note"))
                    Text(L("data_note")).font(.footnote).foregroundStyle(.secondary)
                    Link(destination: URL(string: "mailto:ggshoplimitedco@gmail.com")!) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(L("contact")).font(.footnote).foregroundStyle(.secondary)
                            Text(verbatim: "ggshoplimitedco@gmail.com")
                        }
                    }
                    .accessibilityIdentifier("contact")
                    Link(L("privacy_policy"), destination: URL(string: "https://ggshop-apps.blogspot.com/p/weightkit-privacy-policy.html")!)
                        .accessibilityIdentifier("privacy_policy")
                    if ConsentInformation.shared.privacyOptionsRequirementStatus == .required {
                        Button(L("privacy_options")) {
                            Task { try? await ConsentForm.presentPrivacyOptionsForm(from: nil) }
                        }
                        .accessibilityIdentifier("privacy_options")
                    }
                    Text("v" + (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"))
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            .navigationTitle(L("tab_settings"))
            .sheet(isPresented: $showPaywall) { PaywallView() }
            .alert(L("add_material"), isPresented: $showAddMaterial) {
                TextField(L("name"), text: $newName).accessibilityIdentifier("material_name_input")
                TextField(L("density_unit_hint"), text: $newDensity).keyboardType(.decimalPad)
                    .accessibilityIdentifier("material_density_input")
                Button(L("save")) {
                    if let d = Fmt.parse(newDensity), d > 0, !newName.isEmpty {
                        settings.customMaterials.append(CustomMaterial(name: newName, density: d))
                    }
                }
                Button(L("cancel"), role: .cancel) {}
            }
        }
    }
}

extension SettingsView {
    /// Logo for the PDF (Pro): the system photo picker, no photo-library permission needed.
    @ViewBuilder var logoRow: some View {
        HStack {
            Text(L("logo"))
            Spacer()
            if let png = settings.logoPNG, let img = UIImage(data: png) {
                Image(uiImage: img).resizable().scaledToFit().frame(width: 72, height: 40)
                    .accessibilityIdentifier("logo_preview")
                Button(role: .destructive) { _ = settings.setLogo(nil) } label: { Image(systemName: "trash") }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(L("logo_remove"))
                    .accessibilityIdentifier("logo_remove")
            } else if pro.hasPro {
                PhotosPicker(selection: $logoItem, matching: .images) { Text(L("logo_pick")) }
                    .buttonStyle(.borderless)
                    .accessibilityIdentifier("logo_pick")
            } else {
                Button { showPaywall = true } label: { HStack(spacing: 6) { Text(L("logo_pick")); ProBadge() } }
                    .buttonStyle(.borderless)
                    .accessibilityIdentifier("logo_pick")
            }
        }
        .onChange(of: logoItem) { item in
            guard let item else { return }
            Task { @MainActor in
                let data = try? await item.loadTransferable(type: Data.self)
                if data == nil || !settings.setLogo(data) { logoError = true }
                logoItem = nil
            }
        }
        .alert(L("logo_bad"), isPresented: $logoError) { Button("OK", role: .cancel) {} }
    }
}
