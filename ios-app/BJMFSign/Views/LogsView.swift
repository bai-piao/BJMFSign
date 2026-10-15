import SwiftUI
import UIKit

private enum LogStatusFilter: String, CaseIterable, Identifiable {
    case all = "全部"
    case success = "成功"
    case issue = "异常"

    var id: String { rawValue }

    func matches(_ status: String) -> Bool {
        switch self {
        case .all: return true
        case .success: return SignStatus.isSuccess(status)
        case .issue: return !SignStatus.isSuccess(status)
        }
    }
}

struct LogsView: View {
    let viewModel: BjmfViewModel
    @State private var statusFilter: LogStatusFilter = .all
    @State private var taskFilter: Int64?
    @State private var visibleCount = 30
    @State private var confirmClear = false

    private var logs: [TaskLog] { viewModel.logs }

    private var filtered: [TaskLog] {
        logs.filter { statusFilter.matches($0.status) && (taskFilter == nil || $0.taskId == taskFilter) }
    }

    private struct TaskName: Identifiable {
        let id: Int64
        let name: String
    }

    private var taskNames: [TaskName] {
        var seen = Set<Int64>()
        var result: [TaskName] = []
        for log in logs where seen.insert(log.taskId).inserted {
            result.append(TaskName(id: log.taskId, name: log.taskName))
        }
        return result
    }

    var body: some View {
        let successCount = logs.filter { SignStatus.isSuccess($0.status) }.count

        GlassCard {
            HStack {
                Text("执行概览")
                    .font(.title3.weight(.semibold))
                Spacer()
                Button("刷新", systemImage: "arrow.clockwise") { viewModel.refreshLocalData() }
                    .labelStyle(.iconOnly)
                    .buttonStyle(.glass)
                Button("清除", systemImage: "trash", role: .destructive) { confirmClear = true }
                    .labelStyle(.iconOnly)
                    .buttonStyle(.glass)
                    .disabled(logs.isEmpty)
            }
            .disabled(viewModel.isBusy)

            HStack(spacing: 10) {
                StatBlock(label: "全部", value: logs.count, color: .primary)
                StatBlock(label: "成功", value: successCount, color: StatusStyle.color("success"))
                StatBlock(label: "异常", value: logs.count - successCount, color: StatusStyle.color("error"))
            }

            Picker("状态", selection: $statusFilter) {
                ForEach(LogStatusFilter.allCases) { Text($0.rawValue).tag($0) }
            }
            .pickerStyle(.segmented)

            if taskNames.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        FilterChip(text: "全部任务", selected: taskFilter == nil) { taskFilter = nil }
                        ForEach(taskNames) { item in
                            FilterChip(text: item.name, selected: taskFilter == item.id) { taskFilter = item.id }
                        }
                    }
                }
            }
        }
        .onChange(of: statusFilter) { _, _ in visibleCount = 30 }
        .onChange(of: taskFilter) { _, _ in visibleCount = 30 }
        .confirmationDialog("清除全部 \(logs.count) 条日志？", isPresented: $confirmClear, titleVisibility: .visible) {
            Button("清除", role: .destructive) { viewModel.clearLogs() }
        }

        if filtered.isEmpty {
            GlassCard {
                Text(logs.isEmpty ? "暂无执行日志" : "没有符合筛选条件的日志")
                    .foregroundStyle(.secondary)
            }
        } else {
            let shown = Array(filtered.prefix(visibleCount))
            let groups = Self.groupByDay(shown)
            LazyVStack(alignment: .leading, spacing: 12) {
                ForEach(groups, id: \.key) { group in
                    Text(group.label)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .padding(.leading, 6)
                        .padding(.top, 4)
                    ForEach(group.logs) { log in
                        LogCard(log: log)
                    }
                }
                if filtered.count > visibleCount {
                    Button("加载更多（剩余 \(filtered.count - visibleCount) 条）") { visibleCount += 30 }
                        .buttonStyle(.glass)
                        .frame(maxWidth: .infinity)
                }
            }
        }
    }

    private struct DayGroup {
        let key: String
        let label: String
        var logs: [TaskLog]
    }

    private static func groupByDay(_ logs: [TaskLog]) -> [DayGroup] {
        var groups: [DayGroup] = []
        for log in logs {
            let date = Date(timeIntervalSince1970: TimeInterval(log.runAt) / 1000)
            let key = NextRunText.dayKey(date)
            if let index = groups.firstIndex(where: { $0.key == key }) {
                groups[index].logs.append(log)
            } else {
                groups.append(DayGroup(key: key, label: NextRunText.dayLabel(date), logs: [log]))
            }
        }
        return groups
    }
}

private struct StatBlock: View {
    let label: String
    let value: Int
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("\(value)")
                .font(.title2.weight(.semibold).monospacedDigit())
                .foregroundStyle(color)
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(color.opacity(0.12), in: .rect(cornerRadius: 14))
    }
}

private struct FilterChip: View {
    let text: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(.subheadline)
                .lineLimit(1)
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .foregroundStyle(selected ? AnyShapeStyle(.white) : AnyShapeStyle(.tint))
                .background(selected ? AnyShapeStyle(.tint) : AnyShapeStyle(.tint.opacity(0.14)), in: .capsule)
        }
        .buttonStyle(.plain)
    }
}

private struct LogCard: View {
    let log: TaskLog
    @State private var expanded = false
    @State private var copied = false

    var body: some View {
        let parsed = ParsedLog(message: log.message)
        let color = StatusStyle.color(log.status)
        let runAt = Date(timeIntervalSince1970: TimeInterval(log.runAt) / 1000)

        GlassCard {
            HStack(spacing: 10) {
                Text(SignStatus.label(log.status))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(color)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 3)
                    .background(color.opacity(0.15), in: .capsule)
                Text(log.taskName)
                    .font(.body.weight(.semibold))
                    .lineLimit(1)
                Spacer()
                Text(NextRunText.timeOnly(runAt))
                    .font(.footnote.monospacedDigit())
                    .foregroundStyle(.secondary)
            }

            Text(parsed.summary ?? SignStatus.description(log.status))
                .font(.subheadline)
                .lineLimit(expanded ? nil : 2)

            if expanded {
                VStack(alignment: .leading, spacing: 6) {
                    Grid(alignment: .leading, horizontalSpacing: 12, verticalSpacing: 6) {
                        ForEach(Array(parsed.fields.enumerated()), id: \.offset) { _, field in
                            GridRow {
                                Text(field.key)
                                    .foregroundStyle(.secondary)
                                Text(field.value)
                                    .font(.system(.footnote, design: .monospaced))
                            }
                            .font(.footnote)
                        }
                    }
                    ForEach(Array(parsed.notes.dropLast().enumerated()), id: \.offset) { _, note in
                        Text(note)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(.fill.tertiary, in: .rect(cornerRadius: 14))
                .transition(.opacity.combined(with: .move(edge: .top)))
            }

            HStack {
                Button {
                    withAnimation(.smooth) { expanded.toggle() }
                } label: {
                    Label(expanded ? "收起详情" : "展开详情", systemImage: expanded ? "chevron.up" : "chevron.down")
                        .font(.footnote)
                }
                .buttonStyle(.plain)
                .foregroundStyle(.tint)
                Spacer()
                if expanded {
                    Button {
                        UIPasteboard.general.string = "\(log.taskName) \(DateText.format(millis: log.runAt))\n\(log.message)"
                        copied = true
                    } label: {
                        Label(copied ? "已复制" : "复制日志", systemImage: copied ? "checkmark" : "doc.on.doc")
                            .font(.footnote)
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { withAnimation(.smooth) { expanded.toggle() } }
    }
}
