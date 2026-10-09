package com.bjmf.sign.android.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class BjmfScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAll(tasks: List<BjmfTask>) {
        tasks.forEach { task ->
            if (task.enabled) {
                schedule(task)
            } else {
                cancel(task.id)
            }
        }
    }

    fun schedule(task: BjmfTask) {
        cancel(task.id)
        if (!task.enabled) return
        val nextRunAt = nextRunMillis(task) ?: return
        val pendingIntent = taskPendingIntent(task.id, PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRunAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRunAt, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRunAt, pendingIntent)
        }
    }

    fun cancel(taskId: Long) {
        taskPendingIntentOrNull(taskId)?.let(alarmManager::cancel)
    }

    private fun taskPendingIntent(taskId: Long, extraFlag: Int): PendingIntent {
        val intent = Intent(context, SignAlarmReceiver::class.java)
            .setAction(ACTION_SIGN_TASK)
            .putExtra(EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            taskId.toRequestCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or extraFlag,
        )
    }

    private fun taskPendingIntentOrNull(taskId: Long): PendingIntent? {
        val intent = Intent(context, SignAlarmReceiver::class.java)
            .setAction(ACTION_SIGN_TASK)
            .putExtra(EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            taskId.toRequestCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
    }

    private fun nextRunMillis(task: BjmfTask, nowMillis: Long = System.currentTimeMillis()): Long? {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        val startDate = task.dateStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val endDate = task.dateEnd?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val times = task.times
            .mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }
            .sorted()
        if (times.isEmpty()) return null

        val firstDate = listOfNotNull(now.toLocalDate(), startDate).maxOrNull() ?: now.toLocalDate()
        for (offset in 0..370) {
            val date = firstDate.plusDays(offset.toLong())
            if (endDate != null && date.isAfter(endDate)) return null
            for (time in times) {
                val candidate = LocalDateTime.of(date, time)
                if (candidate.isAfter(now)) {
                    return candidate.atZone(zone).toInstant().toEpochMilli()
                }
            }
        }
        return null
    }

    private fun Long.toRequestCode(): Int = (this xor (this ushr 32)).toInt()

    companion object {
        const val ACTION_SIGN_TASK = "com.bjmf.sign.android.action.SIGN_TASK"
        const val EXTRA_TASK_ID = "task_id"
    }
}
