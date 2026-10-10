import SwiftUI

struct PaywallView: View {
    @EnvironmentObject var pro: ProStore
    @EnvironmentObject var ads: AdsManager
    @Environment(\.dismiss) private var dismiss
    @State private var showRewardAfterDismiss = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                ShapeIcon(shape: .ibeam, color: .accentColor)
                    .frame(width: 72, height: 72)
                    .padding(.top, 24)
                Text(L("pro_title")).font(.largeTitle.weight(.bold))
                    .accessibilityIdentifier("paywall")
                Text(L("pro_desc"))
                    .font(.body)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal)
                Label(L("no_ads_note"), systemImage: "checkmark.seal.fill")
                    .font(.callout.weight(.semibold))
                Spacer()
                if pro.purchased {
                    Label(L("pro_active"), systemImage: "checkmark.circle.fill")
                        .font(.headline).foregroundStyle(.green)
                        .accessibilityIdentifier("pro_active")
                } else {
                    if pro.canStartTrial {
                        // guideline 3.1.1: duration, what locks afterwards and the price are shown before the trial starts
                        Text(pro.product.map { L("trial_terms", $0.displayPrice) } ?? L("trial_terms_noprice"))
                            .font(.footnote)
                            .multilineTextAlignment(.center)
                            .foregroundStyle(.secondary)
                            .accessibilityIdentifier("trial_terms")
                        Button {
                            Task { await pro.startTrial(); if pro.inTrial { dismiss() } }
                        } label: {
                            Text(L("start_trial"))
                                .font(.headline)
                                .frame(maxWidth: .infinity, minHeight: 52)
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(pro.busy)
                        .accessibilityIdentifier("start_trial")
                    } else if !pro.inTrial && !pro.hasReward {
                        Text(L("trial_over")).font(.footnote).foregroundStyle(.secondary)
                            .accessibilityIdentifier("trial_over")
                    }
                    Button {
                        Task { await pro.buy(); if pro.purchased { dismiss() } }
                    } label: {
                        Group {
                            if let p = pro.product {
                                Text(L("buy_pro", p.displayPrice))
                            } else {
                                Text(L("buy_pro_noprice"))
                            }
                        }
                        .font(.headline)
                        .frame(maxWidth: .infinity, minHeight: 52)
                    }
                    .buttonStyle(BuyButtonStyle(prominent: !pro.canStartTrial))
                    .disabled(pro.busy)
                    .accessibilityIdentifier("buy_pro")
                    Button(L("restore")) { Task { await pro.restore() } }
                        .disabled(pro.busy)
                        .accessibilityIdentifier("restore")
                    if let e = pro.errorMessage { Text(e).font(.footnote).foregroundStyle(.red) }
                    if ads.rewardedOffer && !pro.hasPro {
                        Button {
                            // present only after the sheet is gone (see onDisappear): presenting while
                            // it is still animating fails silently
                            showRewardAfterDismiss = true
                            dismiss()
                        } label: {
                            Label(L("reward_offer"), systemImage: "play.rectangle")
                        }
                        .buttonStyle(.bordered)
                        .accessibilityIdentifier("reward_offer")
                    }
                    if pro.hasReward {
                        Text(L("reward_active")).font(.footnote).foregroundStyle(.secondary)
                    }
                }
                if pro.inTrial {
                    Text(L("trial_active", pro.trialEndText)).font(.footnote).foregroundStyle(.secondary)
                }
            }
            .padding(20)
            .onDisappear {
                if showRewardAfterDismiss { ads.showRewarded() }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L("done")) { dismiss() }.accessibilityIdentifier("paywall_done")
                }
            }
        }
    }
}

/// Bordered-prominent when buying is the main action, bordered while the free trial is offered first.
private struct BuyButtonStyle: PrimitiveButtonStyle {
    let prominent: Bool
    func makeBody(configuration: Configuration) -> some View {
        if prominent {
            Button(role: configuration.role, action: configuration.trigger) { configuration.label }
                .buttonStyle(.borderedProminent)
        } else {
            Button(role: configuration.role, action: configuration.trigger) { configuration.label }
                .buttonStyle(.bordered)
        }
    }
}
