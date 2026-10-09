import SwiftUI
import UIKit

struct SettingsView: View {
    @Bindable var viewModel: BjmfViewModel

    private static let presets: [Int64] = [
        0xFF3482FF, 0xFF30D158, 0xFFFF375F, 0xFFFF9F0A,
        0xFFAF52DE, 0xFF64D2FF, 0xFFFFD60A, 0xFF8E8E93,
    ]

    var body: some View {
        GlassCard(title: "本机管理") {
            PreferenceRow(title: "任务总数", value: "\(viewModel.tasks.count)")
            RowDivider()
            PreferenceRow(title: "启用任务", value: "\(viewModel.enabledCount)")
            RowDivider()
            PreferenceRow(title: "日志数量", value: "\(viewModel.logs.count)")
            GlassEffectContainer(spacing: 10) {
                HStack(spacing: 10) {
                    Button {
                        viewModel.runAllEnabledNow()
                    } label: {
                        Label("执行全部", systemImage: "play.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)

                    Button {
                        viewModel.rescheduleAll()
                    } label: {
                        Label("重排定时", systemImage: "clock.arrow.circlepath")
                    }
                    .buttonStyle(.glass)
                }
                .controlSize(.large)
                .disabled(viewModel.isBusy)
            }
            .padding(.top, 6)
        }

        GlassCard(title: "界面取色") {
            PreferenceRow(
                title: "当前主题色",
                summary: ColorHex.text(viewModel.accentColor),
                indicator: viewModel.accent
            ) {
                ColorPicker("自定义", selection: Binding(
                    get: { viewModel.accent },
                    set: { viewModel.selectAccentColor(Self.argb(from: $0)) }
                ), supportsOpacity: false)
                .labelsHidden()
            }
            HStack(alignment: .bottom, spacing: 10) {
                LabeledField(label: "HEX 颜色", text: $viewModel.accentColorText)
                Button("应用") { viewModel.applyAccentColor() }
                    .buttonStyle(.glass)
                    .controlSize(.large)
            }
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 4), spacing: 10) {
                ForEach(Self.presets, id: \.self) { color in
                    let isSelected = color == viewModel.accentColor
                    Button {
                        viewModel.selectAccentColor(color)
                    } label: {
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(ColorHex.color(color))
                            .frame(height: 44)
                            .overlay {
                                if isSelected {
                                    Image(systemName: "checkmark")
                                        .font(.headline)
                                        .foregroundStyle(.white)
                                }
                            }
                    }
                    .buttonStyle(.plain)
                    .glassEffect(.clear.interactive(), in: .rect(cornerRadius: 16))
                    .accessibilityLabel(ColorHex.text(color))
                }
            }
        }

        GlassCard(title: "定时说明") {
            PreferenceRow(
                title: "iOS 定时签到",
                summary: "到达执行时间时会推送通知，点击通知或通知上的「立即签到」即可执行；系统也会在后台刷新时自动补签，打开 App 时会补签 30 分钟内错过的任务。请允许通知权限并开启「后台 App 刷新」。"
            )
        }

        GlassCard(title: "隐私数据") {
            PreferenceRow(
                title: "本机私有存储",
                summary: "任务 Cookie、坐标和通知 Key 均保存在本机 App 私有存储中。卸载 App 会清除这些数据。"
            )
        }
    }

    private static func argb(from color: Color) -> Int64 {
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        UIColor(color).getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        func channel(_ value: CGFloat) -> Int64 { Int64((min(max(value, 0), 1) * 255).rounded()) }
        return (0xFF << 24) | (channel(red) << 16) | (channel(green) << 8) | channel(blue)
    }
}
