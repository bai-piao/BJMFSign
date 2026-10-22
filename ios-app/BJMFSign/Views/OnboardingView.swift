import SwiftUI
import UIKit
import UserNotifications

/// 首次引导：扫码登录 → 通知权限 → 后台刷新，顶部显示进度。
struct OnboardingView: View {
    @Bindable var viewModel: BjmfViewModel
    @State private var step = 0
    @State private var notificationStatus: UNAuthorizationStatus = .notDetermined
    @State private var backgroundStatus: UIBackgroundRefreshStatus = UIApplication.shared.backgroundRefreshStatus
    @Environment(\.scenePhase) private var scenePhase

    private let stepCount = 3

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    StepProgress(step: step, count: stepCount)
                    stepContent
                        .id(step)
                        .transition(.asymmetric(
                            insertion: .move(edge: .trailing).combined(with: .opacity),
                            removal: .move(edge: .leading).combined(with: .opacity)
                        ))
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .background { GlassBackground(accent: viewModel.accent) }
            .navigationTitle(tr("使用引导"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(tr("跳过")) { viewModel.completeOnboarding() }
                }
            }
            .safeAreaInset(edge: .bottom) { footer }
        }
        .task { await refreshStatus() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await refreshStatus() } }
        }
    }

    @ViewBuilder
    private var stepContent: some View {
        switch step {
        case 0:
            StepHeader(
                icon: "qrcode.viewfinder",
                title: tr("扫码登录"),
                summary: tr("用微信扫描二维码登录班级魔方，App 会自动获取账号和班级信息。也可以稍后在「登录」页完成。")
            )
            LoginView(viewModel: viewModel)
        case 1:
            StepHeader(
                icon: "bell.badge",
                title: tr("通知权限"),
                summary: tr("iOS 依靠通知提醒你签到：到点推送通知，点按即可签到；后台补签后也会告诉你结果。")
            )
            GlassCard {
                PreferenceRow(
                    title: tr("允许通知"),
                    summary: notificationStatus == .authorized || notificationStatus == .provisional ? tr("已允许通知") : tr("尚未允许通知"),
                    indicator: notificationStatus == .authorized ? StatusStyle.enabledColor : .orange
                ) {
                    Button(notificationStatus == .notDetermined ? tr("允许") : tr("去设置")) {
                        Task {
                            if notificationStatus == .notDetermined {
                                _ = await BjmfScheduler.requestNotificationPermission()
                            } else {
                                openSettings()
                            }
                            await refreshStatus()
                        }
                    }
                    .buttonStyle(.glass)
                    .disabled(notificationStatus == .authorized)
                }
            }
        default:
            StepHeader(
                icon: "arrow.clockwise.circle",
                title: tr("后台 App 刷新"),
                summary: tr("开启「后台 App 刷新」后，系统会在后台自动补签；打开 App 时也会补签 30 分钟内错过的任务。")
            )
            GlassCard {
                PreferenceRow(
                    title: tr("后台 App 刷新"),
                    summary: backgroundStatus == .available ? tr("已开启") : tr("未开启，请在系统设置中打开"),
                    indicator: backgroundStatus == .available ? StatusStyle.enabledColor : .orange
                ) {
                    Button(tr("去设置")) { openSettings() }
                        .buttonStyle(.glass)
                        .disabled(backgroundStatus == .available)
                }
                RowDivider()
                PreferenceRow(
                    title: tr("低电量模式"),
                    summary: tr("低电量模式会暂停后台刷新，请留意")
                )
            }
            GlassCard {
                Label(tr("设置完成，开始使用吧"), systemImage: "checkmark.seal.fill")
                    .font(.headline)
                    .foregroundStyle(.tint)
            }
        }
    }

    private var footer: some View {
        GlassEffectContainer(spacing: 10) {
            HStack(spacing: 10) {
                if step > 0 {
                    Button {
                        withAnimation(.smooth) { step -= 1 }
                    } label: {
                        Label(tr("上一步"), systemImage: "chevron.left")
                    }
                    .buttonStyle(.glass)
                }
                Button {
                    if step < stepCount - 1 {
                        withAnimation(.smooth) { step += 1 }
                    } else {
                        viewModel.completeOnboarding()
                    }
                } label: {
                    Text(step < stepCount - 1 ? tr("下一步") : tr("完成"))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.glassProminent)
            }
            .controlSize(.large)
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }

    private func refreshStatus() async {
        notificationStatus = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        backgroundStatus = UIApplication.shared.backgroundRefreshStatus
    }

    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }
}

/// 分段进度条：已完成的步骤高亮。
private struct StepProgress: View {
    let step: Int
    let count: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                ForEach(0..<count, id: \.self) { index in
                    Capsule()
                        .fill(index <= step ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary.opacity(0.25)))
                        .frame(height: 6)
                }
            }
            .animation(.smooth, value: step)
            Text(tr("第 {0} 步，共 {1} 步", step + 1, count))
                .font(.footnote.weight(.medium))
                .foregroundStyle(.secondary)
        }
        .padding(.top, 8)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(tr("第 {0} 步，共 {1} 步", step + 1, count))
        .accessibilityValue(Text("\(Int(Double(step + 1) / Double(count) * 100))%"))
    }
}

private struct StepHeader: View {
    let icon: String
    let title: String
    let summary: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: icon)
                .font(.largeTitle)
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            Text(title)
                .font(.title2.weight(.bold))
                .accessibilityAddTraits(.isHeader)
            Text(summary)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 4)
    }
}
