import SwiftUI

struct MaterialPickerView: View {
    @EnvironmentObject var settings: AppSettings
    @Environment(\.dismiss) private var dismiss
    @Binding var selected: String
    @State private var query = ""

    private var groups: [String] { Catalog.groups + (settings.customMaterials.isEmpty ? [] : ["custom"]) }

    var body: some View {
        NavigationStack {
            List {
                ForEach(groups, id: \.self) { g in
                    let mats = settings.allMaterials.filter {
                        $0.group == g && (query.isEmpty || $0.name.localizedCaseInsensitiveContains(query))
                    }
                    if !mats.isEmpty {
                        Section(L("group_" + g)) {
                            ForEach(mats) { m in
                                Button {
                                    selected = m.id
                                    dismiss()
                                } label: {
                                    HStack {
                                        Text(m.name).foregroundStyle(.primary)
                                        Spacer()
                                        Text(density(m.density)).foregroundStyle(.secondary).monospacedDigit()
                                        if m.id == selected {
                                            Image(systemName: "checkmark").foregroundStyle(Color.accentColor)
                                        }
                                    }
                                    .frame(minHeight: 44)
                                }
                                .accessibilityIdentifier("material_" + m.id)
                            }
                        }
                    }
                }
            }
            .searchable(text: $query, prompt: L("search"))
            .navigationTitle(L("material"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("cancel")) { dismiss() } }
            }
        }
    }

    private func density(_ d: Double) -> String {
        settings.units == .metric ? Fmt.smart(d) + " kg/m³" : Fmt.fixed(d / 27679.9, 4) + " lb/in³"
    }
}
