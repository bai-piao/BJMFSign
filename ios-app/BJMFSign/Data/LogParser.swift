import Foundation

/// 解析后的日志正文：键值字段 + 结果/说明行。兼容旧版带 ==== 分隔线的日志。
struct ParsedLog {
    var fields: [(key: String, value: String)]
    var notes: [String]

    /// 折叠状态下的一行摘要：优先取最后一条说明行。
    var summary: String? { notes.last }

    init(message: String) {
        var fields: [(key: String, value: String)] = []
        var notes: [String] = []
        for raw in message.components(separatedBy: .newlines) {
            let line = raw.trimmingCharacters(in: .whitespaces)
            if line.isEmpty { continue }
            if line.hasPrefix("=") && line.hasSuffix("=") { continue }
            if let match = Regexes.allGroups("^([^:：]{1,16})[:：]\\s*(.*)$", in: line).first {
                let key = match[1].trimmingCharacters(in: .whitespaces)
                let value = match[2].trimmingCharacters(in: .whitespaces)
                let isOutcome = key.contains("错误") || key.contains("失败")
                if !value.isEmpty && !isOutcome {
                    fields.append((key, value))
                    continue
                }
            }
            notes.append(line)
        }
        self.fields = fields
        self.notes = notes
    }
}
