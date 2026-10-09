package com.bjmf.sign.android.data

data class LoginQr(
    val imageDataUrl: String,
    val expiresInSeconds: Int = 120,
)

data class UserInfo(
    val name: String,
    val classId: String,
    val className: String,
    val classCode: String,
)

data class BjmfTask(
    val id: Long,
    val name: String,
    val classId: String,
    val cookie: String,
    val lat: String,
    val lng: String,
    val acc: String = "30",
    val wxKey: String = "",
    val qqKey: String = "",
    val times: List<String> = emptyList(),
    val dateStart: String? = null,
    val dateEnd: String? = null,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class TaskLog(
    val id: Long,
    val taskId: Long,
    val taskName: String,
    val runAt: Long,
    val status: String,
    val message: String,
)

data class SignResult(
    val taskId: Long,
    val taskName: String,
    val status: String,
    val message: String,
)

data class LoginAccount(
    val cookie: String,
    val userInfo: UserInfo,
)

data class FavoriteLocation(
    val id: Long,
    val name: String,
    val lat: String,
    val lng: String,
    val createdAt: Long = System.currentTimeMillis(),
)

data class TaskForm(
    val selectedTaskId: Long? = null,
    val name: String = "",
    val classId: String = "",
    val cookie: String = "",
    val coord: String = "",
    val acc: String = "30",
    val timesText: String = "07:30:00,12:00:00,18:00:00",
    val wxKey: String = "",
    val qqKey: String = "",
    val dateStart: String = "",
    val dateEnd: String = "",
)
