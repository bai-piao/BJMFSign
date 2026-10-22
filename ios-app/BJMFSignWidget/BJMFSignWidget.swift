import SwiftUI
import WidgetKit

/// 主屏小组件：显示上一次签到的执行结果与下次执行时间。
struct LastResultEntry: TimelineEntry {
    let date: Date
    let result: SharedStore.LastResult?
}

struct LastResultProvider: TimelineProvider {
    func placeholder(in context: Context) -> LastResultEntry {
        LastResultEntry(date: Date(), result: SharedStore.LastResult(
            taskName: tr("签到任务"),
            status: "success",
            reason: tr("签到成功"),
            runAt: Int64(Date().timeIntervalSince1970 * 1000),
            succeeded: 1,
            total: 1,
            nextRunAt: nil
        ))
    }

    func getSnapshot(in context: Context, completion: @escaping (LastResultEntry) -> Void) {
        completion(context.isPreview ? placeholder(in: context) : LastResultEntry(date: Date(), result: SharedStore.loadLastResult()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<LastResultEntry>) -> Void) {
        let entry = LastResultEntry(date: Date(), result: SharedStore.loadLastResult())
        // App 每次执行后都会主动刷新；这里每小时刷新一次相对时间。
        completion(Timeline(entries: [entry], policy: .after(Date().addingTimeInterval(3600))))
    }
}

struct LastResultWidgetView: View {
    let entry: LastResultEntry
    @Environment(\.widgetFamily) private var family

    private var accent: Color {
        let argb = SharedStore.accentColor
        return Color(
            .sRGB,
            red: Double((argb >> 16) & 0xFF) / 255,
            green: Double((argb >> 8) & 0xFF) / 255,
            blue: Double(argb & 0xFF) / 255
        )
    }

    var body: some View {
        Group {
            if let result = entry.result {
                content(result)
            } else {
                VStack(alignment: .leading, spacing: 6) {
                    Image(systemName: "checklist")
                        .font(.title2)
                        .foregroundStyle(accent)
                    Spacer(minLength: 0)
                    Text(tr("班级魔方签到"))
                        .font(.headline)
                    Text(tr("还没有执行记录"))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .containerBackground(for: .widget) {
            LinearGradient(colors: [accent.opacity(0.25), accent.opacity(0.05)], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
    }

    @ViewBuilder
    private func content(_ result: SharedStore.LastResult) -> some View {
        let success = SharedStore.isSuccess(result.status)
        let color: Color = success ? .green : (result.status == "error" ? .red : .orange)
        let runAt = Date(timeIntervalSince1970: TimeInterval(result.runAt) / 1000)

        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Image(systemName: success ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                    .font(.title3)
                    .foregroundStyle(color)
                    .widgetAccentable()
                Text(success ? tr("签到成功") : tr("签到未完成"))
                    .font(.headline)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            Text(result.taskName)
                .font(.subheadline.weight(.semibold))
                .lineLimit(1)
            if family != .systemSmall || !success {
                Text(result.reason)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(family == .systemSmall ? 2 : 3)
            }
            Spacer(minLength: 0)
            if result.total > 1 {
                Text(tr("成功 {0}/{1}", result.succeeded, result.total))
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(.secondary)
            }
            HStack(spacing: 4) {
                Image(systemName: "clock")
                Text(runAt, style: .relative)
            }
            .font(.caption2)
            .foregroundStyle(.secondary)
            if family != .systemSmall, let next = result.nextRunAt {
                let nextDate = Date(timeIntervalSince1970: TimeInterval(next) / 1000)
                Text(tr("下次执行：{0}", nextDate.formatted(.dateTime.locale(I18n.locale).month().day().hour().minute())))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

struct LastResultWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: SharedStore.widgetKind, provider: LastResultProvider()) { entry in
            LastResultWidgetView(entry: entry)
        }
        .configurationDisplayName(tr("上次签到结果"))
        .description(tr("显示上一次执行的结果和下次执行时间"))
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

@main
struct BJMFSignWidgetBundle: WidgetBundle {
    var body: some Widget {
        LastResultWidget()
    }
}
