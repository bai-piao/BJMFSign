package com.bjmf.sign.android.data

/** 解析后的日志正文：键值字段 + 结果/说明行。兼容旧版带 ==== 分隔线的日志。 */
data class ParsedLog(
    val fields: List<Pair<String, String>>,
    val notes: List<String>,
) {
    /** 用于折叠状态下的一行摘要：优先取最后一条说明行。 */
    val summary: String? get() = notes.lastOrNull()
}

private val separatorLine = Regex("^=+.*=+$")
private val keyValueLine = Regex("^([^:：]{1,16})[:：]\\s*(.*)$")

fun parseLogMessage(message: String): ParsedLog {
    val fields = mutableListOf<Pair<String, String>>()
    val notes = mutableListOf<String>()
    message.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
        if (separatorLine.matches(line)) return@forEach
        val match = keyValueLine.find(line)
        val key = match?.groupValues?.get(1).orEmpty()
        val isOutcome = "错误" in key || "失败" in key
        if (match != null && match.groupValues[2].isNotBlank() && !isOutcome) {
            fields += match.groupValues[1].trim() to match.groupValues[2].trim()
        } else {
            notes += line
        }
    }
    return ParsedLog(fields, notes)
}
