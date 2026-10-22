import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct SettingsView: View {
    @Bindable var viewModel: BjmfViewModel

    private static let presets: [Int64] = [
        0xFF3482FF, 0xFF30D158, 0xFFFF375F, 0xFFFF9F0A,
        0xFFAF52DE, 0xFF64D2FF, 0xFFFFD60A, 0xFF8E8E93,
    ]

    var body: some View {
        GlassCard(title: tr("本机管理")) {
            PreferenceRow(title: tr("任务总数"), value: "\(viewModel.tasks.count)")
            RowDivider()
            PreferenceRow(title: tr("启用任务"), value: "\(viewModel.enabledCount)")
            RowDivider()
            PreferenceRow(title: tr("日志数量"), value: "\(viewModel.logs.count)")
            GlassEffectContainer(spacing: 10) {
                HStack(spacing: 10) {
                    Button {
                        viewModel.runAllEnabledNow()
                    } label: {
                        Label(tr("执行全部"), systemImage: "play.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)

                    Button {
                        viewModel.rescheduleAll()
                    } label: {
                        Label(tr("重排定时"), systemImage: "clock.arrow.circlepath")
                    }
                    .buttonStyle(.glass)
                }
                .controlSize(.large)
                .disabled(viewModel.isBusy)
            }
            .padding(.top, 6)
        }

        GlassCard(title: tr("外观")) {
            Text(tr("深色模式可跟随系统，或固定为浅色 / 深色"))
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Picker(tr("外观"), selection: Binding(
                get: { viewModel.themeMode },
                set: { viewModel.setThemeMode($0) }
            )) {
                Text(tr("跟随系统")).tag("system")
                Text(tr("浅色")).tag("light")
                Text(tr("深色")).tag("dark")
            }
            .pickerStyle(.segmented)
        }

        GlassCard(title: tr("语言")) {
            Text(tr("界面语言，跟随系统时按系统语言显示"))
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Picker(tr("语言"), selection: Binding(
                get: { viewModel.language },
                set: { viewModel.setLanguage($0) }
            )) {
                Text(tr("跟随系统")).tag(I18n.system)
                Text("简体").tag(I18n.zhHans)
                Text("繁體").tag(I18n.zhHant)
                Text("English").tag(I18n.en)
            }
            .pickerStyle(.segmented)
        }

        GlassCard(title: tr("界面取色")) {
            PreferenceRow(
                title: tr("当前主题色"),
                summary: ColorHex.text(viewModel.accentColor),
                indicator: viewModel.accent
            ) {
                ColorPicker(tr("自定义"), selection: Binding(
                    get: { viewModel.accent },
                    set: { viewModel.selectAccentColor(Self.argb(from: $0)) }
                ), supportsOpacity: false)
                .labelsHidden()
            }
            HStack(alignment: .bottom, spacing: 10) {
                LabeledField(label: tr("HEX 颜色"), text: $viewModel.accentColorText)
                Button(tr("应用")) { viewModel.applyAccentColor() }
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
                    .accessibilityLabel(tr("主题色 {0}", ColorHex.text(color)))
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
        }

        BackupSection(viewModel: viewModel)

        GlassCard(title: tr("使用引导")) {
            PreferenceRow(
                title: tr("重新查看引导"),
                summary: tr("再次完成扫码登录、通知权限和后台刷新设置")
            ) {
                Button(tr("打开")) { viewModel.reopenOnboarding() }
                    .buttonStyle(.glass)
            }
        }

        GlassCard(title: tr("定时说明")) {
            PreferenceRow(
                title: tr("iOS 定时签到"),
                summary: tr("到达执行时间时会推送通知，点击通知或通知上的「立即签到」即可执行；系统也会在后台刷新时自动补签，打开 App 时会补签 30 分钟内错过的任务。请允许通知权限并开启「后台 App 刷新」。")
            )
        }

        GlassCard(title: tr("隐私数据")) {
            PreferenceRow(
                title: tr("本机私有存储"),
                summary: tr("任务 Cookie、坐标和通知 Key 均保存在本机 App 私有存储中。卸载 App 会清除这些数据。")
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

// MARK: - 备份与迁移

/// 加密备份文件（内容已是加密后的 JSON）。
struct BackupDocument: FileDocument {
    static let fileType = UTType(filenameExtension: "bjmf") ?? .data
    static var readableContentTypes: [UTType] { [fileType, .data, .json] }

    var data: Data

    init(data: Data) {
        self.data = data
    }

    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

private struct BackupSection: View {
    let viewModel: BjmfViewModel

    private enum Prompt: Identifiable {
        case export
        case importFile(URL)

        var id: String {
            switch self {
            case .export: return "export"
            case .importFile(let url): return url.absoluteString
            }
        }
    }

    @State private var prompt: Prompt?
    @State private var showImporter = false
    @State private var exportDocument: BackupDocument?
    @State private var showExporter = false

    private var fileName: String {
        "bjmfsign-backup-\(NextRunText.dayKey(Date()).replacingOccurrences(of: "-", with: "")).bjmf"
    }

    var body: some View {
        GlassCard(title: tr("备份与迁移")) {
            Text(tr("把任务、位置收藏和界面设置导出为加密文件，换手机时直接导入。Android 与 iOS 通用。"))
                .font(.subheadline)
                .foregroundStyle(.secondary)
            GlassEffectContainer(spacing: 10) {
                HStack(spacing: 10) {
                    Button {
                        prompt = .export
                    } label: {
                        Label(tr("导出备份"), systemImage: "square.and.arrow.up")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)

                    Button {
                        showImporter = true
                    } label: {
                        Label(tr("导入备份"), systemImage: "square.and.arrow.down")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glass)
                }
                .controlSize(.large)
                .disabled(viewModel.isBusy)
            }
        }
        .sheet(item: $prompt) { prompt in
            switch prompt {
            case .export:
                PasswordSheet(
                    title: tr("设置备份密码"),
                    summary: tr("导入时需要输入此密码，请牢记。至少 6 位。"),
                    requireConfirm: true
                ) { password in
                    self.prompt = nil
                    Task {
                        do {
                            exportDocument = BackupDocument(data: try await viewModel.exportBackupData(password: password))
                            showExporter = true
                        } catch {
                            viewModel.message = tr("导出失败：{0}", error.localizedDescription)
                        }
                    }
                }
            case .importFile(let url):
                PasswordSheet(
                    title: tr("输入备份密码"),
                    summary: tr("导入会替换本机现有的任务和位置收藏。"),
                    requireConfirm: false
                ) { password in
                    self.prompt = nil
                    viewModel.importBackup(from: url, password: password)
                }
            }
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.item]) { result in
            switch result {
            case .success(let url): prompt = .importFile(url)
            case .failure(let error): viewModel.message = tr("导入失败：{0}", error.localizedDescription)
            }
        }
        .fileExporter(
            isPresented: $showExporter,
            document: exportDocument,
            contentType: BackupDocument.fileType,
            defaultFilename: fileName
        ) { result in
            viewModel.backupExported(result)
            exportDocument = nil
        }
    }
}

private struct PasswordSheet: View {
    let title: String
    let summary: String
    let requireConfirm: Bool
    let onConfirm: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var password = ""
    @State private var confirm = ""
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    SecureField(tr("密码"), text: $password)
                        .textContentType(.newPassword)
                    if requireConfirm {
                        SecureField(tr("再次输入密码"), text: $confirm)
                            .textContentType(.newPassword)
                    }
                } footer: {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(summary)
                        if let error {
                            Text(error).foregroundStyle(.red)
                        }
                    }
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(tr("取消"), role: .cancel) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(tr("确定"), systemImage: "checkmark") { submit() }
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func submit() {
        if requireConfirm && password.count < 6 {
            error = tr("密码至少 6 位")
        } else if requireConfirm && password != confirm {
            error = tr("两次输入的密码不一致")
        } else if password.isEmpty {
            error = tr("请输入密码")
        } else {
            onConfirm(password)
        }
    }
}
