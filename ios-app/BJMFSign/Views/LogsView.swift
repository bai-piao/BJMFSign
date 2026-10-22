import SwiftUI
import UIKit

private enum LogStatusFilter: String, CaseIterable, Identifiable {
    case all = "全部"
    case success = "成功"
    case issue = "异常"

    var id: String { rawValue }
    var title: String { tr(rawValue) }

    func matches(_ status: String) -> Bool {
        switch self {
        case .all: return true
        case .success: return SignStatus.isSuccess(status)
        case .issue: return !SignStatus.isSuccess(status)
        }
    }
}

/// 某一天的执行统计。
struct DayStat: Equatable {
    var success = 0
    var failed = 0
    var total: Int { success + failed }
}

struct LogsView: View {
    let viewModel: BjmfViewModel
    @State private var statusFilter: LogStatusFilter = .all
    @State private var taskFilter: Int64?
    @State private var visibleCount = 30
    @State private var confirmClear = false
    @State private var selectedDay: String?

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
        let filtered = self.filtered

        GlassCard {
            HStack {
                Text(tr("执行概览"))
                    .font(.title3.weight(.semibold))
                    .accessibilityAddTraits(.isHeader)
                Spacer()
                Button(tr("刷新"), systemImage: "arrow.clockwise") { viewModel.refreshLocalData() }
                    .labelStyle(.iconOnly)
                    .buttonStyle(.glass)
                Button(tr("清除日志"), systemImage: "trash", role: .destructive) { confirmClear = true }
                    .labelStyle(.iconOnly)
                    .buttonStyle(.glass)
                    .disabled(logs.isEmpty)
            }
            .disabled(viewModel.isBusy)

            HStack(spacing: 10) {
                StatBlock(label: tr("全部"), value: logs.count, color: .primary)
                StatBlock(label: tr("成功"), value: successCount, color: StatusStyle.color("success"))
                StatBlock(label: tr("异常"), value: logs.count - successCount, color: StatusStyle.color("error"))
            }

            Picker(tr("状态"), selection: $statusFilter) {
                ForEach(LogStatusFilter.allCases) { Text($0.title).tag($0) }
            }
            .pickerStyle(.segmented)

            if taskNames.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        FilterChip(text: tr("全部任务"), selected: taskFilter == nil) { taskFilter = nil }
                        ForEach(taskNames) { item in
                            FilterChip(text: item.name, selected: taskFilter == item.id) { taskFilter = item.id }
                        }
                    }
                }
            }
        }
        .onChange(of: statusFilter) { _, _ in visibleCount = 30 }
        .onChange(of: taskFilter) { _, _ in visibleCount = 30 }
        .confirmationDialog(tr("确定清除全部 {0} 条执行日志？", logs.count), isPresented: $confirmClear, titleVisibility: .visible) {
            Button(tr("清除"), role: .destructive) {
                selectedDay = nil
                viewModel.clearLogs()
            }
        }

        CalendarHeatmapCard(stats: Self.dayStats(filtered), selectedDay: $selectedDay)

        if let selectedDay {
            let dayLogs = filtered.filter { Self.dayKey($0.runAt) == selectedDay }
            HStack {
                Text(tr("{0} · {1} 条", Self.dayLabel(selectedDay), dayLogs.count))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .accessibilityAddTraits(.isHeader)
                Spacer()
                Button(tr("显示全部")) { withAnimation(.smooth) { self.selectedDay = nil } }
                    .buttonStyle(.glass)
                    .controlSize(.small)
            }
            .padding(.horizontal, 6)
            if dayLogs.isEmpty {
                GlassCard {
                    Text(tr("这一天没有执行记录"))
                        .foregroundStyle(.secondary)
                }
            } else {
                LazyVStack(spacing: 12) {
                    ForEach(dayLogs) { LogCard(log: $0, initiallyExpanded: true) }
                }
            }
        } else if filtered.isEmpty {
            GlassCard {
                Text(logs.isEmpty ? tr("暂无执行日志") : tr("没有符合筛选条件的日志"))
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
                        .accessibilityAddTraits(.isHeader)
                    ForEach(group.logs) { log in
                        LogCard(log: log)
                    }
                }
                if filtered.count > visibleCount {
                    Button(tr("加载更多（剩余 {0} 条）", filtered.count - visibleCount)) { visibleCount += 30 }
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

    static func dayKey(_ millis: Int64) -> String {
        NextRunText.dayKey(Date(timeIntervalSince1970: TimeInterval(millis) / 1000))
    }

    static func dayLabel(_ key: String) -> String {
        NextRunText.date(from: key).map(NextRunText.dayLabel) ?? key
    }

    private static func dayStats(_ logs: [TaskLog]) -> [String: DayStat] {
        var stats: [String: DayStat] = [:]
        for log in logs {
            let key = dayKey(log.runAt)
            if SignStatus.isSuccess(log.status) {
                stats[key, default: DayStat()].success += 1
            } else {
                stats[key, default: DayStat()].failed += 1
            }
        }
        return stats
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

// MARK: - 日历热力图

private struct CalendarHeatmapCard: View {
    let stats: [String: DayStat]
    @Binding var selectedDay: String?
    @State private var monthOffset = 0

    private static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = BjmfScheduler.zone
        calendar.firstWeekday = 2
        return calendar
    }

    private var month: Date {
        let calendar = Self.calendar
        let start = calendar.date(from: calendar.dateComponents([.year, .month], from: Date()))!
        return calendar.date(byAdding: .month, value: monthOffset, to: start)!
    }

    /// 以周一开始的 6×7 网格，nil 表示占位。
    private var cells: [Date?] {
        let calendar = Self.calendar
        let leading = (calendar.component(.weekday, from: month) + 5) % 7
        let days = calendar.range(of: .day, in: .month, for: month)?.count ?? 30
        var result: [Date?] = Array(repeating: nil, count: leading)
        for day in 0..<days {
            result.append(calendar.date(byAdding: .day, value: day, to: month))
        }
        while result.count % 7 != 0 { result.append(nil) }
        return result
    }

    private var monthTitle: String {
        let formatter = DateFormatter()
        formatter.locale = I18n.locale
        formatter.timeZone = BjmfScheduler.zone
        formatter.setLocalizedDateFormatFromTemplate("yyyyMMMM")
        return formatter.string(from: month)
    }

    private static let weekdays = ["一", "二", "三", "四", "五", "六", "日"]

    var body: some View {
        let maxCount = max(1, stats.values.map(\.total).max() ?? 1)
        let monthKeys = cells.compactMap { $0.map(NextRunText.dayKey) }
        let monthStats = monthKeys.compactMap { stats[$0] }
        let success = monthStats.reduce(0) { $0 + $1.success }
        let failed = monthStats.reduce(0) { $0 + $1.failed }

        GlassCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(monthTitle)
                        .font(.title3.weight(.semibold))
                        .accessibilityAddTraits(.isHeader)
                    Text(tr("{0}：成功 {1}，失败 {2}", monthTitle, success, failed))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button(tr("上个月"), systemImage: "chevron.left") { withAnimation(.smooth) { monthOffset -= 1 } }
                    .labelStyle(.iconOnly)
                    .buttonStyle(.glass)
                Button(tr("下个月"), systemImage: "chevron.right") { withAnimation(.smooth) { monthOffset += 1 } }
                    .labelStyle(.iconOnly)
                    .buttonStyle(.glass)
                    .disabled(monthOffset >= 0)
            }

            let columns = Array(repeating: GridItem(.flexible(), spacing: 6), count: 7)
            LazyVGrid(columns: columns, spacing: 6) {
                ForEach(Self.weekdays, id: \.self) { day in
                    Text(tr(day))
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                        .accessibilityHidden(true)
                }
                ForEach(Array(cells.enumerated()), id: \.offset) { _, date in
                    if let date {
                        let key = NextRunText.dayKey(date)
                        HeatCell(
                            date: date,
                            stat: stats[key],
                            maxCount: maxCount,
                            selected: selectedDay == key
                        ) {
                            withAnimation(.smooth) { selectedDay = selectedDay == key ? nil : key }
                        }
                    } else {
                        Color.clear.aspectRatio(1, contentMode: .fit)
                            .accessibilityHidden(true)
                    }
                }
            }

            HStack(spacing: 12) {
                LegendDot(color: StatusStyle.color("success"), text: tr("成功"))
                LegendDot(color: StatusStyle.color("skip"), text: tr("部分失败"))
                LegendDot(color: StatusStyle.color("error"), text: tr("失败"))
                Spacer()
            }
            Text(tr("点按某一天查看当天的执行详情"))
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }
}

private struct HeatCell: View {
    let date: Date
    let stat: DayStat?
    let maxCount: Int
    let selected: Bool
    let action: () -> Void

    var body: some View {
        let day = Calendar(identifier: .gregorian).dateComponents(in: BjmfScheduler.zone, from: date).day ?? 0
        let isToday = NextRunText.dayKey(date) == NextRunText.dayKey(Date())
        let base: Color? = {
            guard let stat, stat.total > 0 else { return nil }
            if stat.failed == 0 { return StatusStyle.color("success") }
            if stat.success == 0 { return StatusStyle.color("error") }
            return StatusStyle.color("skip")
        }()
        let intensity = stat.map { 0.35 + 0.65 * Double($0.total) / Double(maxCount) } ?? 0

        Button(action: action) {
            Text("\(day)")
                .font(.footnote.weight(isToday ? .bold : .regular).monospacedDigit())
                .minimumScaleFactor(0.6)
                .lineLimit(1)
                .foregroundStyle(base != nil && intensity > 0.6 ? AnyShapeStyle(.white) : AnyShapeStyle(.primary))
                .frame(maxWidth: .infinity)
                .aspectRatio(1, contentMode: .fit)
                .background(
                    (base?.opacity(intensity) ?? Color.secondary.opacity(0.1)),
                    in: .rect(cornerRadius: 8)
                )
                .overlay {
                    if selected || isToday {
                        RoundedRectangle(cornerRadius: 8)
                            .strokeBorder(selected ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary), lineWidth: selected ? 2 : 1)
                    }
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibilityText)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private var accessibilityText: String {
        let label = NextRunText.dayLabel(date)
        guard let stat, stat.total > 0 else { return tr("{0}：无记录", label) }
        return tr("{0}：成功 {1}，失败 {2}", label, stat.success, stat.failed)
    }
}

private struct LegendDot: View {
    let color: Color
    let text: String

    var body: some View {
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 3)
                .fill(color)
                .frame(width: 10, height: 10)
            Text(text)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - 统计与卡片

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
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(label) \(value)")
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
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct LogCard: View {
    let log: TaskLog
    @State private var expanded: Bool
    @State private var copied: String?

    init(log: TaskLog, initiallyExpanded: Bool = false) {
        self.log = log
        _expanded = State(initialValue: initiallyExpanded)
    }

    var body: some View {
        let parsed = ParsedLog(message: log.message)
        let color = StatusStyle.color(log.status)
        let runAt = Date(timeIntervalSince1970: TimeInterval(log.runAt) / 1000)
        let success = SignStatus.isSuccess(log.status)

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
            .accessibilityElement(children: .combine)

            if !success {
                Label(Diagnostics.readableReason(status: log.status, message: log.message), systemImage: "exclamationmark.triangle.fill")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(color.opacity(0.12), in: .rect(cornerRadius: 14))
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

            HStack(spacing: 8) {
                Button {
                    withAnimation(.smooth) { expanded.toggle() }
                } label: {
                    Label(expanded ? tr("收起详情") : tr("展开详情"), systemImage: expanded ? "chevron.up" : "chevron.down")
                        .font(.footnote)
                }
                .buttonStyle(.plain)
                .foregroundStyle(.tint)
                Spacer()
                if !success {
                    Button {
                        UIPasteboard.general.string = Diagnostics.report(for: log)
                        copied = tr("已复制诊断信息")
                    } label: {
                        Label(tr("复制给开发者"), systemImage: "ladybug")
                            .font(.footnote)
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                } else if expanded {
                    Button {
                        UIPasteboard.general.string = "\(log.taskName) \(DateText.format(millis: log.runAt))\n\(log.message)"
                        copied = tr("已复制日志")
                    } label: {
                        Label(tr("复制日志"), systemImage: "doc.on.doc")
                            .font(.footnote)
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                }
            }
            if let copied {
                Label(copied, systemImage: "checkmark")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .transition(.opacity)
            }
        }
        .animation(.smooth, value: copied)
    }
}
