package com.bjmf.sign.android.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class BjmfStore(context: Context) {
    private val prefs = context.getSharedPreferences("bjmf_local_store", Context.MODE_PRIVATE)

    fun loadTasks(): List<BjmfTask> {
        val raw = prefs.getString(KEY_TASKS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.toTaskOrNull()?.let(::add)
            }
        }.sortedByDescending { it.updatedAt }
    }

    fun saveTask(task: BjmfTask): BjmfTask {
        val now = System.currentTimeMillis()
        val stored = loadTasks().toMutableList()
        val next = if (task.id == 0L) {
            task.copy(id = now, createdAt = now, updatedAt = now)
        } else {
            task.copy(updatedAt = now)
        }
        val index = stored.indexOfFirst { it.id == next.id }
        if (index >= 0) {
            stored[index] = next
        } else {
            stored.add(next)
        }
        saveTasks(stored)
        return next
    }

    fun deleteTask(taskId: Long) {
        saveTasks(loadTasks().filterNot { it.id == taskId })
    }

    fun findTask(taskId: Long): BjmfTask? = loadTasks().firstOrNull { it.id == taskId }

    fun loadFavoriteLocations(): List<FavoriteLocation> {
        val raw = prefs.getString(KEY_FAVORITE_LOCATIONS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.toFavoriteLocationOrNull()?.let(::add)
            }
        }.sortedByDescending { it.createdAt }
    }

    fun saveFavoriteLocation(location: FavoriteLocation): FavoriteLocation {
        val now = System.currentTimeMillis()
        val next = if (location.id == 0L) location.copy(id = now, createdAt = now) else location
        val stored = loadFavoriteLocations().toMutableList()
        val index = stored.indexOfFirst { it.id == next.id }
        if (index >= 0) {
            stored[index] = next
        } else {
            stored.add(next)
        }
        saveFavoriteLocations(stored)
        return next
    }

    fun deleteFavoriteLocation(locationId: Long) {
        saveFavoriteLocations(loadFavoriteLocations().filterNot { it.id == locationId })
    }

    fun loadAccentColor(): Long = prefs.getLong(KEY_ACCENT_COLOR, DEFAULT_ACCENT_COLOR)

    fun saveAccentColor(color: Long) {
        prefs.edit().putLong(KEY_ACCENT_COLOR, color).apply()
    }

    fun loadThemeMode(): String = prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM

    fun saveThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun loadLanguage(): String = prefs.getString(KEY_LANGUAGE, I18n.SYSTEM) ?: I18n.SYSTEM

    fun saveLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun loadDynamicColor(): Boolean = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)

    fun saveDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    fun isOnboardingDone(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    /** 备份明文：任务、位置收藏与界面设置（不含日志）。字段与 iOS 端一致。 */
    fun buildBackupJson(): String {
        val tasks = JSONArray().apply { loadTasks().forEach { put(it.toJson()) } }
        val locations = JSONArray().apply { loadFavoriteLocations().forEach { put(it.toJson()) } }
        val settings = JSONObject()
            .put("accentColor", loadAccentColor())
            .put("themeMode", loadThemeMode())
            .put("language", loadLanguage())
            .put("dynamicColor", loadDynamicColor())
        return JSONObject()
            .put("format", BackupCrypto.FORMAT)
            .put("version", BackupCrypto.VERSION)
            .put("platform", "android")
            .put("exportedAt", System.currentTimeMillis())
            .put("tasks", tasks)
            .put("favoriteLocations", locations)
            .put("settings", settings)
            .toString()
    }

    data class RestoreSummary(val tasks: Int, val locations: Int)

    /** 用备份替换本机任务与位置收藏，并恢复界面设置。 */
    fun restoreBackupJson(json: String): RestoreSummary {
        val root = JSONObject(json)
        if (root.optString("format") != BackupCrypto.FORMAT) throw BackupCrypto.InvalidFileException()
        val tasks = root.optJSONArray("tasks").let { array ->
            buildList { for (i in 0 until (array?.length() ?: 0)) array?.optJSONObject(i)?.toTaskOrNull()?.let(::add) }
        }
        val locations = root.optJSONArray("favoriteLocations").let { array ->
            buildList { for (i in 0 until (array?.length() ?: 0)) array?.optJSONObject(i)?.toFavoriteLocationOrNull()?.let(::add) }
        }
        saveTasks(tasks)
        saveFavoriteLocations(locations)
        root.optJSONObject("settings")?.let { settings ->
            if (settings.has("accentColor")) saveAccentColor(settings.optLong("accentColor", DEFAULT_ACCENT_COLOR))
            settings.optString("themeMode").takeIf { it.isNotBlank() }?.let(::saveThemeMode)
            settings.optString("language").takeIf { it.isNotBlank() }?.let(::saveLanguage)
            if (settings.has("dynamicColor")) saveDynamicColor(settings.optBoolean("dynamicColor"))
        }
        return RestoreSummary(tasks.size, locations.size)
    }

    fun appendLog(log: TaskLog) {
        val logs = loadLogs(limit = Int.MAX_VALUE).toMutableList()
        logs.add(0, log)
        val recent = logs
            .sortedByDescending { it.runAt }
            .take(MAX_LOGS)
        saveLogs(recent)
    }

    fun clearLogs() {
        prefs.edit().putString(KEY_LOGS, "[]").apply()
    }

    fun loadLogs(taskId: Long? = null, limit: Int = 100): List<TaskLog> {
        val raw = prefs.getString(KEY_LOGS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.toLogOrNull()?.let(::add)
            }
        }
            .filter { taskId == null || it.taskId == taskId }
            .sortedByDescending { it.runAt }
            .take(limit)
    }

    private fun saveTasks(tasks: List<BjmfTask>) {
        val array = JSONArray()
        tasks.sortedByDescending { it.updatedAt }.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_TASKS, array.toString()).apply()
    }

    private fun saveLogs(logs: List<TaskLog>) {
        val array = JSONArray()
        logs.sortedByDescending { it.runAt }.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_LOGS, array.toString()).apply()
    }

    private fun saveFavoriteLocations(locations: List<FavoriteLocation>) {
        val array = JSONArray()
        locations.sortedByDescending { it.createdAt }.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_FAVORITE_LOCATIONS, array.toString()).apply()
    }

    private fun BjmfTask.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("classId", classId)
        .put("cookie", cookie)
        .put("lat", lat)
        .put("lng", lng)
        .put("acc", acc)
        .put("wxKey", wxKey)
        .put("qqKey", qqKey)
        .put("times", JSONArray(times))
        .put("dateStart", dateStart ?: JSONObject.NULL)
        .put("dateEnd", dateEnd ?: JSONObject.NULL)
        .put("enabled", enabled)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

    private fun TaskLog.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("taskId", taskId)
        .put("taskName", taskName)
        .put("runAt", runAt)
        .put("status", status)
        .put("message", message)

    private fun FavoriteLocation.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("lat", lat)
        .put("lng", lng)
        .put("createdAt", createdAt)

    private fun JSONObject.toTaskOrNull(): BjmfTask? = runCatching {
        BjmfTask(
            id = optLong("id"),
            name = optString("name"),
            classId = optString("classId"),
            cookie = optString("cookie"),
            lat = optString("lat"),
            lng = optString("lng"),
            acc = optString("acc", "30"),
            wxKey = optString("wxKey"),
            qqKey = optString("qqKey"),
            times = optJSONArray("times").toStringList(),
            dateStart = nullableString("dateStart"),
            dateEnd = nullableString("dateEnd"),
            enabled = optBoolean("enabled", true),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
            updatedAt = optLong("updatedAt", System.currentTimeMillis()),
        )
    }.getOrNull()

    private fun JSONObject.toLogOrNull(): TaskLog? = runCatching {
        TaskLog(
            id = optLong("id"),
            taskId = optLong("taskId"),
            taskName = optString("taskName"),
            runAt = optLong("runAt"),
            status = optString("status"),
            message = optString("message"),
        )
    }.getOrNull()

    private fun JSONObject.toFavoriteLocationOrNull(): FavoriteLocation? = runCatching {
        FavoriteLocation(
            id = optLong("id"),
            name = optString("name"),
            lat = optString("lat"),
            lng = optString("lng"),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
        )
    }.getOrNull()

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    companion object {
        const val DEFAULT_ACCENT_COLOR: Long = 0xFF3482FF
        private const val KEY_TASKS = "tasks"
        private const val KEY_LOGS = "logs"
        private const val KEY_FAVORITE_LOCATIONS = "favorite_locations"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        private const val MAX_LOGS = 300
    }
}
