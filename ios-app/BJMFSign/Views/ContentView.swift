import SwiftUI

struct ContentView: View {
    @Bindable var viewModel: BjmfViewModel

    var body: some View {
        TabView(selection: Binding(get: { viewModel.screen }, set: { viewModel.selectScreen($0) })) {
            Tab(BjmfScreen.login.title, systemImage: BjmfScreen.login.systemImage, value: BjmfScreen.login) {
                page(.login) { LoginView(viewModel: viewModel) }
            }
            Tab(BjmfScreen.task.title, systemImage: BjmfScreen.task.systemImage, value: BjmfScreen.task) {
                page(.task) { TaskView(viewModel: viewModel) }
            }
            .badge(viewModel.enabledCount)
            Tab(BjmfScreen.logs.title, systemImage: BjmfScreen.logs.systemImage, value: BjmfScreen.logs) {
                page(.logs) { LogsView(viewModel: viewModel) }
            }
            .badge(viewModel.logs.count)
            Tab(BjmfScreen.settings.title, systemImage: BjmfScreen.settings.systemImage, value: BjmfScreen.settings) {
                page(.settings) { SettingsView(viewModel: viewModel) }
            }
        }
        .tabBarMinimizeBehavior(.onScrollDown)
        // 切换界面语言后重建视图树，让所有文案立即刷新。
        .id(viewModel.language)
        .tint(viewModel.accent)
        .preferredColorScheme(viewModel.preferredColorScheme)
        .fullScreenCover(isPresented: $viewModel.showOnboarding) {
            OnboardingView(viewModel: viewModel)
                .tint(viewModel.accent)
                .preferredColorScheme(viewModel.preferredColorScheme)
                .interactiveDismissDisabled()
        }
    }

    private func page<Content: View>(_ screen: BjmfScreen, @ViewBuilder content: () -> Content) -> some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    StatusCard(viewModel: viewModel)
                    content()
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .scrollDismissesKeyboard(.interactively)
            .background { GlassBackground(accent: viewModel.accent) }
            .navigationTitle(screen.largeTitle)
            .navigationSubtitle(tr("班级魔方签到"))
            .toolbar {
                if viewModel.isBusy || viewModel.isPolling {
                    ToolbarItem(placement: .topBarTrailing) {
                        ProgressView()
                            .accessibilityLabel(tr("正在处理"))
                    }
                }
            }
        }
    }
}

/// 顶部状态卡：本机任务数量与最近一条提示信息（对应 Android 端 LocalStatusCard）。
struct StatusCard: View {
    let viewModel: BjmfViewModel

    var body: some View {
        GlassCard(tint: viewModel.accent.opacity(0.75)) {
            HStack(spacing: 12) {
                Circle()
                    .fill(viewModel.enabledCount > 0 ? Color.white : Color.white.opacity(0.45))
                    .frame(width: 10, height: 10)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(tr("本机任务 {0}/{1}", viewModel.enabledCount, viewModel.tasks.count))
                        .font(.headline)
                    Text(tr("登录、配置、签到、日志和定时提醒均在本机完成"))
                        .font(.subheadline)
                        .opacity(0.8)
                        .lineLimit(2)
                }
            }
            if let message = viewModel.message {
                Text(message)
                    .font(.subheadline)
                    .lineLimit(3)
                    .transition(.opacity)
            }
        }
        .foregroundStyle(.white)
        .animation(.smooth, value: viewModel.message)
        .accessibilityElement(children: .combine)
    }
}
