import SwiftUI

/// 页面背景：以主题色生成的柔和网格渐变，让液态玻璃卡片有可折射的内容。
struct GlassBackground: View {
    let accent: Color
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let dark = colorScheme == .dark
        let base: Color = dark ? .black : Color(.systemGroupedBackground)
        // 深色模式下降低渐变强度，避免背景过亮。
        let k = dark ? 0.6 : 1.0
        MeshGradient(
            width: 3,
            height: 3,
            points: [
                [0, 0], [0.5, 0], [1, 0],
                [0, 0.5], [0.6, 0.45], [1, 0.5],
                [0, 1], [0.5, 1], [1, 1],
            ],
            colors: [
                accent.opacity(0.55 * k), accent.opacity(0.25 * k), .purple.opacity(0.25 * k),
                base, accent.opacity(0.18 * k), .cyan.opacity(0.2 * k),
                base, base, accent.opacity(0.22 * k),
            ]
        )
        .background(base)
        .ignoresSafeArea()
    }
}

/// 液态玻璃卡片。
struct GlassCard<Content: View>: View {
    var title: String?
    var tint: Color?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let title {
                Text(title)
                    .font(.title3.weight(.semibold))
            }
            content
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassEffect(glass, in: .rect(cornerRadius: 26))
    }

    private var glass: Glass {
        if let tint { return .regular.tint(tint) }
        return .regular
    }
}

/// 对应 Android 端的 PreferenceRow。
struct PreferenceRow<Action: View>: View {
    let title: String
    var summary: String?
    var value: String?
    var selected = false
    var indicator: Color?
    var onTap: (() -> Void)?
    @ViewBuilder var action: Action

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            if let indicator {
                Circle()
                    .fill(indicator)
                    .frame(width: 9, height: 9)
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.body.weight(.semibold))
                if let summary, !summary.isEmpty {
                    Text(summary)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(3)
                }
            }
            Spacer(minLength: 8)
            if let value, !value.isEmpty {
                Text(value)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .multilineTextAlignment(.trailing)
            }
            action
        }
        .padding(.vertical, 10)
        .padding(.horizontal, selected ? 12 : 0)
        .background {
            if selected {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(.tint.opacity(0.16))
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { onTap?() }
    }
}

extension PreferenceRow where Action == EmptyView {
    init(title: String, summary: String? = nil, value: String? = nil, selected: Bool = false, indicator: Color? = nil, onTap: (() -> Void)? = nil) {
        self.init(title: title, summary: summary, value: value, selected: selected, indicator: indicator, onTap: onTap) {
            EmptyView()
        }
    }
}

/// 带标题的输入框，内部使用轻量填充而不是叠加玻璃。
struct LabeledField: View {
    let label: String
    @Binding var text: String
    var placeholder: String = ""
    var axis: Axis = .horizontal
    var keyboard: UIKeyboardType = .default

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(label)
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)
            TextField(placeholder.isEmpty ? label : placeholder, text: $text, axis: axis)
                .lineLimit(axis == .vertical ? 1...4 : 1...1)
                .keyboardType(keyboard)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(.fill.tertiary, in: .rect(cornerRadius: 14))
        }
    }
}

struct RowDivider: View {
    var body: some View {
        Divider().padding(.leading, 20)
    }
}

enum StatusStyle {
    static func color(_ status: String) -> Color {
        switch status {
        case "success", "already_signed": return Color(red: 0.14, green: 0.79, blue: 0.42)
        case "not_started", "no_sign_in", "skip": return Color(red: 0.85, green: 0.55, blue: 0.0)
        default: return Color(red: 0.90, green: 0.28, blue: 0.30)
        }
    }

    static let enabledColor = Color(red: 0.14, green: 0.79, blue: 0.42)
}

enum DateText {
    private static let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter
    }()

    static func format(millis: Int64) -> String {
        formatter.string(from: Date(timeIntervalSince1970: TimeInterval(millis) / 1000))
    }
}

/// 表单分组标题。
struct SectionHeader: View {
    let text: String

    init(_ text: String) {
        self.text = text
    }

    var body: some View {
        Text(text)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.tint)
    }
}

struct SectionSpacer: View {
    var body: some View {
        Spacer().frame(height: 8)
    }
}

/// 自动换行的横向排布（用于时间标签等）。
struct FlowLayout: Layout {
    var spacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var usedWidth: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > maxWidth {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
            usedWidth = max(usedWidth, x - spacing)
        }
        return CGSize(width: proposal.width ?? usedWidth, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

/// 下次执行时间、日期范围等展示文案（北京时间）。
enum NextRunText {
    private static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = BjmfScheduler.zone
        return calendar
    }

    private static func formatter(_ format: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.timeZone = BjmfScheduler.zone
        formatter.dateFormat = format
        return formatter
    }

    static func label(times: [String], dateStart: String?, dateEnd: String?, enabled: Bool) -> String {
        if !enabled { return "已停用" }
        if times.isEmpty { return "未设置执行时间" }
        guard let next = BjmfScheduler.nextRun(times: times, dateStart: dateStart, dateEnd: dateEnd) else {
            return "已过生效日期，不再执行"
        }
        let time = formatter("HH:mm").string(from: next)
        let day: String
        if calendar.isDateInToday(next) {
            day = "今天 \(time)"
        } else if calendar.isDateInTomorrow(next) {
            day = "明天 \(time)"
        } else {
            day = formatter("MM-dd HH:mm").string(from: next)
        }
        return "下次执行：\(day)"
    }

    static func shortTime(_ time: String) -> String {
        time.count == 8 && time.hasSuffix(":00") ? String(time.prefix(5)) : time
    }

    static func dateRange(start: String?, end: String?) -> String? {
        let s = start?.trimmed.nilIfEmpty
        let e = end?.trimmed.nilIfEmpty
        switch (s, e) {
        case (nil, nil): return nil
        case let (s?, e?): return "\(s) 至 \(e)"
        case let (s?, nil): return "\(s) 起"
        case let (nil, e?): return "至 \(e)"
        }
    }

    static func dateString(_ date: Date) -> String {
        formatter("yyyy-MM-dd").string(from: date)
    }

    static func date(from text: String) -> Date? {
        formatter("yyyy-MM-dd").date(from: text.trimmed)
    }

    static func dayLabel(_ date: Date) -> String {
        if calendar.isDateInToday(date) { return "今天" }
        if calendar.isDateInYesterday(date) { return "昨天" }
        return formatter("M月d日 EEEE").string(from: date)
    }

    static func dayKey(_ date: Date) -> String {
        formatter("yyyy-MM-dd").string(from: date)
    }

    static func timeOnly(_ date: Date) -> String {
        formatter("HH:mm:ss").string(from: date)
    }
}
