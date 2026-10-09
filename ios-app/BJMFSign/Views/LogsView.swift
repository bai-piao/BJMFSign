import SwiftUI

struct LogsView: View {
    let viewModel: BjmfViewModel
    @State private var confirmClear = false

    var body: some View {
        GlassCard(title: "日志") {
            PreferenceRow(
                title: "本机执行日志",
                summary: "正文完整显示，可长按选择复制",
                value: "\(viewModel.logs.count) 条"
            )
            GlassEffectContainer(spacing: 10) {
                HStack(spacing: 10) {
                    Button {
                        viewModel.refreshLocalData()
                    } label: {
                        Label("刷新", systemImage: "arrow.clockwise")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glass)

                    Button(role: .destructive) {
                        confirmClear = true
                    } label: {
                        Label("清除", systemImage: "trash")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glass)
                    .disabled(viewModel.logs.isEmpty)
                }
                .disabled(viewModel.isBusy)
            }
        }
        .confirmationDialog("清除全部日志？", isPresented: $confirmClear, titleVisibility: .visible) {
            Button("清除", role: .destructive) { viewModel.clearLogs() }
        }

        if viewModel.logs.isEmpty {
            GlassCard {
                Text("暂无执行日志")
                    .foregroundStyle(.secondary)
            }
        } else {
            LazyVStack(spacing: 12) {
                ForEach(viewModel.logs) { log in
                    LogCard(log: log)
                }
            }
        }
    }
}

private struct LogCard: View {
    let log: TaskLog

    var body: some View {
        GlassCard {
            HStack {
                Label(SignStatus.label(log.status), systemImage: SignStatus.isSuccess(log.status) ? "checkmark.seal.fill" : "exclamationmark.triangle.fill")
                    .font(.headline)
                    .foregroundStyle(StatusStyle.color(log.status))
                Spacer()
                Text(DateText.format(millis: log.runAt))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Text(log.taskName)
                .foregroundStyle(.secondary)
            Text(log.message)
                .font(.system(.footnote, design: .monospaced))
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
