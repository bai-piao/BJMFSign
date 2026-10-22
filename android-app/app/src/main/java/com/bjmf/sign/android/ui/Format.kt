package com.bjmf.sign.android.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun statusLabel(status: String): String {
    return when (status) {
        "success" -> "成功"
        "already_signed" -> "已签到"
        "not_started" -> "未开始"
        "no_sign_in" -> "无签到"
        "skip" -> "跳过"
        "error" -> "失败"
        else -> status.ifBlank { "未知" }
    }
}

internal fun formatTime(millis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(millis))
}

internal fun Long.toHexColorText(): String {
    return "#%06X".format(Locale.US, this and 0xFFFFFF)
}

internal fun String.toImageBitmapOrNull(): ImageBitmap? {
    return runCatching {
        val base64 = substringAfter("base64,", this)
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
