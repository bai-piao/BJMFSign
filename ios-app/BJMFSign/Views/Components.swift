import SwiftUI

/// 页面背景：以主题色生成的柔和网格渐变，让液态玻璃卡片有可折射的内容。
struct GlassBackground: View {
    let accent: Color
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let base: Color = colorScheme == .dark ? .black : Color(.systemGroupedBackground)
        MeshGradient(
            width: 3,
            height: 3,
            points: [
                [0, 0], [0.5, 0], [1, 0],
                [0, 0.5], [0.6, 0.45], [1, 0.5],
                [0, 1], [0.5, 1], [1, 1],
            ],
            colors: [
                accent.opacity(0.55), accent.opacity(0.25), .purple.opacity(0.25),
                base, accent.opacity(0.18), .cyan.opacity(0.2),
                base, base, accent.opacity(0.22),
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
