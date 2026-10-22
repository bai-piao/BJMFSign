package com.bjmf.sign.android.data

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val SUCCESS_STATUSES = setOf("success", "already_signed")

fun isSuccessStatus(status: String): Boolean = status in SUCCESS_STATUSES

/** 把失败日志翻译成可读的原因和建议。 */
fun readableReason(status: String, message: String): String {
    val lower = message.lowercase(Locale.ROOT)
    return when (status) {
        "success", "already_signed" -> tr("签到成功")
        "skip" -> tr("登录已失效（Cookie 过期），请重新扫码登录并更新任务")
        "no_sign_in" -> tr("当前没有进行中的签到，或不在签到时间内")
        "not_started" -> tr("签到尚未开始，请稍后再试")
        else -> {
            val httpCode = Regex("(?:状态码|HTTP)[:：\\s]*(\\d{3})").find(message)?.groupValues?.get(1)
            when {
                listOf("timeout", "timed out", "unable to resolve host", "unknownhost", "failed to connect",
                    "network", "connection", "socket", "ssl", "offline", "互联网", "网络").any { it in lower } ->
                    tr("网络连接失败，请检查网络后重试")
                httpCode != null && httpCode.startsWith("4") -> tr("请求被服务器拒绝（HTTP {0}），可能是登录失效", httpCode)
                httpCode != null -> tr("服务器暂时不可用（HTTP {0}），请稍后重试", httpCode)
                else -> tr("执行出错，请复制给开发者排查")
            }
        }
    }
}

/** 生成给开发者的诊断信息（隐藏姓名与精确坐标）。 */
fun diagnosticReport(context: Context, log: TaskLog): String {
    val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    val versionName = info?.versionName ?: "?"
    val versionCode = info?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(log.runAt))
    return buildString {
        appendLine("BJMFSign diagnostics")
        appendLine("App: Android $versionName ($versionCode)")
        appendLine("System: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Time: $time")
        appendLine("Status: ${log.status}")
        appendLine("Reason: ${readableReason(log.status, log.message)}")
        appendLine("--- log (name and precise location hidden) ---")
        append(maskLog(log.message))
    }
}

private fun maskLog(message: String): String = message.lines().joinToString("\n") { line ->
    when {
        line.startsWith("用户姓名") -> "用户姓名: ***"
        line.startsWith("纬度") || line.startsWith("经度") -> {
            val value = line.substringAfter(':', "").trim().toDoubleOrNull()
            if (value != null) line.substringBefore(':') + ": " + "%.2f".format(Locale.US, value) + "**" else line
        }
        else -> line
    }
}
