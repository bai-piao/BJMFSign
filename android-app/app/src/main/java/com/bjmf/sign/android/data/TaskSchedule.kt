package com.bjmf.sign.android.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

val SIGN_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

/** 把 "07:30,12:00:00，18:00" 等输入规范为去重后的 HH:mm:ss 列表（保持输入顺序）。 */
fun normalizeTimes(raw: String): List<String> {
    return raw
        .split(Regex("[,，\\n\\s]+"))
        .mapNotNull { item ->
            val parts = item.trim().split(":")
            if (parts.size !in 2..3) return@mapNotNull null
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            val second = parts.getOrNull(2)?.toIntOrNull() ?: 0
            if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return@mapNotNull null
            "%02d:%02d:%02d".format(hour, minute, second)
        }
        .distinct()
}

fun parseDateOrNull(raw: String?): LocalDate? =
    raw?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/**
 * 计算下一次执行时间（北京时间）：从 max(今天, 开始日期) 起向后最多 370 天，超过结束日期即返回 null。
 */
fun computeNextRunMillis(
    times: List<String>,
    dateStart: String?,
    dateEnd: String?,
    nowMillis: Long = System.currentTimeMillis(),
): Long? {
    val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), SIGN_ZONE)
    val startDate = parseDateOrNull(dateStart)
    val endDate = parseDateOrNull(dateEnd)
    val parsedTimes = times
        .mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }
        .sorted()
    if (parsedTimes.isEmpty()) return null

    val firstDate = listOfNotNull(now.toLocalDate(), startDate).maxOrNull() ?: now.toLocalDate()
    for (offset in 0..370) {
        val date = firstDate.plusDays(offset.toLong())
        if (endDate != null && date.isAfter(endDate)) return null
        for (time in parsedTimes) {
            val candidate = LocalDateTime.of(date, time)
            if (candidate.isAfter(now)) {
                return candidate.atZone(SIGN_ZONE).toInstant().toEpochMilli()
            }
        }
    }
    return null
}

fun BjmfTask.nextRunMillis(nowMillis: Long = System.currentTimeMillis()): Long? =
    if (enabled) computeNextRunMillis(times, dateStart, dateEnd, nowMillis) else null
