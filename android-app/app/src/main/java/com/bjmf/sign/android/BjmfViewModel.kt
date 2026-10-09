package com.bjmf.sign.android

import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bjmf.sign.android.data.BjmfNativeService
import com.bjmf.sign.android.data.BjmfScheduler
import com.bjmf.sign.android.data.BjmfStore
import com.bjmf.sign.android.data.BjmfTask
import com.bjmf.sign.android.data.FavoriteLocation
import com.bjmf.sign.android.data.LoginAccount
import com.bjmf.sign.android.data.TaskForm
import com.bjmf.sign.android.data.TaskLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class BjmfScreen(val title: String) {
    Login("登录"),
    Task("任务"),
    Logs("日志"),
    Settings("管理"),
}

data class BjmfUiState(
    val screen: BjmfScreen = BjmfScreen.Login,
    val isBusy: Boolean = false,
    val isPolling: Boolean = false,
    val qrImageDataUrl: String? = null,
    val qrStatusText: String = "尚未创建扫码会话",
    val account: LoginAccount? = null,
    val tasks: List<BjmfTask> = emptyList(),
    val logs: List<TaskLog> = emptyList(),
    val favoriteLocations: List<FavoriteLocation> = emptyList(),
    val taskForm: TaskForm = TaskForm(),
    val selectedTaskId: Long? = null,
    val accentColor: Long = BjmfStore.DEFAULT_ACCENT_COLOR,
    val accentColorText: String = BjmfStore.DEFAULT_ACCENT_COLOR.toHexColor(),
    val message: String? = null,
)

class BjmfViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val store = BjmfStore(appContext)
    private val service = BjmfNativeService()
    private val scheduler = BjmfScheduler(appContext)
    private var qrSession: BjmfNativeService.QrSession? = null
    private var pollingJob: Job? = null

    private val _uiState = MutableStateFlow(
        BjmfUiState(
            tasks = store.loadTasks(),
            logs = store.loadLogs(limit = Int.MAX_VALUE),
            favoriteLocations = store.loadFavoriteLocations(),
            accentColor = store.loadAccentColor(),
            accentColorText = store.loadAccentColor().toHexColor(),
        ),
    )
    val uiState: StateFlow<BjmfUiState> = _uiState.asStateFlow()

    init {
        scheduler.scheduleAll(_uiState.value.tasks)
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }

    fun selectScreen(screen: BjmfScreen) {
        _uiState.update { it.copy(screen = screen, message = null) }
    }

    fun updateTaskForm(form: TaskForm) {
        _uiState.update { it.copy(taskForm = form, selectedTaskId = form.selectedTaskId, message = null) }
    }

    fun createQrSession() {
        viewModelScope.launch {
            pollingJob?.cancel()
            _uiState.update {
                it.copy(
                    isBusy = true,
                    isPolling = false,
                    qrStatusText = "正在获取微信扫码二维码...",
                    qrImageDataUrl = null,
                    message = null,
                )
            }
            runCatching { service.createQrSession() }
                .onSuccess { session ->
                    qrSession = session
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            isPolling = true,
                            qrImageDataUrl = session.qr.imageDataUrl,
                            qrStatusText = "请使用微信扫码并确认登录",
                            account = null,
                        )
                    }
                    beginPolling(session)
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            isPolling = false,
                            qrStatusText = "二维码创建失败",
                            message = throwable.cleanMessage("二维码创建失败"),
                        )
                    }
                }
        }
    }

    fun clearQrSession() {
        pollingJob?.cancel()
        qrSession = null
        _uiState.update {
            it.copy(
                isPolling = false,
                qrImageDataUrl = null,
                qrStatusText = "已清除扫码会话",
                message = null,
            )
        }
    }

    fun selectTask(task: BjmfTask) {
        _uiState.update {
            it.copy(
                selectedTaskId = task.id,
                taskForm = task.toForm(),
                screen = BjmfScreen.Task,
                message = "已载入 ${task.name} 的任务",
            )
        }
    }

    fun newTaskFromAccount() {
        val account = uiState.value.account
        val form = if (account == null) {
            TaskForm()
        } else {
            TaskForm(
                name = account.userInfo.name,
                classId = account.userInfo.classId,
                cookie = account.cookie,
            )
        }
        _uiState.update {
            it.copy(
                selectedTaskId = null,
                taskForm = form,
                screen = BjmfScreen.Task,
                message = if (account == null) "请先扫码登录，或手动填入 Cookie" else "已用当前登录账号填充任务",
            )
        }
    }

    fun saveTask() {
        viewModelScope.launch {
            val state = uiState.value
            val form = state.taskForm
            val coord = parseCoord(form.coord)
            val times = normalizeTimes(form.timesText)

            if (form.name.isBlank() || form.classId.isBlank() || form.cookie.isBlank()) {
                _uiState.update { it.copy(message = "姓名、班级 ID 和 Cookie 不能为空") }
                return@launch
            }
            if (coord == null || times.isEmpty()) {
                _uiState.update { it.copy(message = "坐标和执行时间格式不正确") }
                return@launch
            }

            val old = form.selectedTaskId?.let(store::findTask)
            val task = BjmfTask(
                id = old?.id ?: 0L,
                name = form.name.trim(),
                classId = form.classId.trim(),
                cookie = form.cookie.trim(),
                lat = coord.first,
                lng = coord.second,
                acc = form.acc.trim().ifBlank { "30" },
                wxKey = form.wxKey.trim(),
                qqKey = form.qqKey.trim(),
                times = times,
                dateStart = form.dateStart.trim().ifBlank { null },
                dateEnd = form.dateEnd.trim().ifBlank { null },
                enabled = old?.enabled ?: true,
                createdAt = old?.createdAt ?: System.currentTimeMillis(),
            )

            val saved = store.saveTask(task)
            scheduler.schedule(saved)
            reloadLocalData(selectedTaskId = saved.id)
            _uiState.update {
                it.copy(
                    screen = BjmfScreen.Task,
                    taskForm = saved.toForm(),
                    message = "任务已保存，并已安排下一次自动签到",
                )
            }
        }
    }

    fun deleteSelectedTask() {
        val taskId = uiState.value.selectedTaskId ?: return
        scheduler.cancel(taskId)
        store.deleteTask(taskId)
        reloadLocalData(selectedTaskId = null)
        _uiState.update {
            it.copy(taskForm = accountFormOrEmpty(it.account), message = "任务已删除")
        }
    }

    fun toggleTask(task: BjmfTask) {
        val saved = store.saveTask(task.copy(enabled = !task.enabled))
        if (saved.enabled) {
            scheduler.schedule(saved)
        } else {
            scheduler.cancel(saved.id)
        }
        reloadLocalData(selectedTaskId = saved.id)
        _uiState.update { it.copy(message = if (saved.enabled) "任务已启用" else "任务已停用") }
    }

    fun runSelectedNow() {
        val taskId = uiState.value.selectedTaskId
        val task = taskId?.let(store::findTask)
        if (task == null) {
            _uiState.update { it.copy(message = "请先选择一个任务") }
            return
        }
        runTasksNow(listOf(task), summary = false)
    }

    fun runAllEnabledNow() {
        val tasks = store.loadTasks().filter { it.enabled }
        if (tasks.isEmpty()) {
            _uiState.update { it.copy(message = "没有已启用的任务") }
            return
        }
        runTasksNow(tasks, summary = true)
    }

    fun rescheduleAll() {
        val tasks = store.loadTasks()
        scheduler.scheduleAll(tasks)
        _uiState.update { it.copy(message = "已重新安排所有已启用任务") }
    }

    fun refreshLocalData() {
        reloadLocalData(uiState.value.selectedTaskId)
        _uiState.update { it.copy(message = "已刷新本地数据") }
    }

    fun clearLogs() {
        store.clearLogs()
        reloadLocalData(uiState.value.selectedTaskId)
        _uiState.update { it.copy(message = "日志已清除") }
    }

    fun favoriteSelectedLocation() {
        val coord = parseCoord(uiState.value.taskForm.coord)
        if (coord == null) {
            _uiState.update { it.copy(message = "请先在地图上选择位置") }
            return
        }
        val saved = store.saveFavoriteLocation(
            FavoriteLocation(
                id = 0L,
                name = "地图选点 ${shortTime()}",
                lat = coord.first,
                lng = coord.second,
            ),
        )
        reloadLocalData(uiState.value.selectedTaskId)
        _uiState.update {
            it.copy(
                taskForm = it.taskForm.copy(coord = "${saved.lng} ${saved.lat}"),
                message = "已收藏选取位置",
            )
        }
    }

    fun useFavoriteLocation(location: FavoriteLocation) {
        _uiState.update {
            it.copy(
                taskForm = it.taskForm.copy(coord = "${location.lng} ${location.lat}"),
                message = "已选用 ${location.name}",
            )
        }
    }

    fun deleteFavoriteLocation(location: FavoriteLocation) {
        store.deleteFavoriteLocation(location.id)
        reloadLocalData(uiState.value.selectedTaskId)
        _uiState.update { it.copy(message = "已删除位置收藏") }
    }

    fun locationPermissionDenied() {
        _uiState.update { it.copy(message = "需要定位权限才能收藏当前位置") }
    }

    fun useCurrentLocationAndFavorite() {
        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).filter { provider -> runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false) }

        if (providers.isEmpty()) {
            _uiState.update { it.copy(message = "定位服务未开启") }
            return
        }

        val lastLocation = providers
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }

        if (lastLocation != null) {
            saveCurrentLocationFavorite(lastLocation)
            return
        }

        var handled = false
        val provider = providers.first()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (handled) return
                handled = true
                locationManager.removeUpdates(this)
                saveCurrentLocationFavorite(location)
            }
        }

        runCatching {
            _uiState.update { it.copy(message = "正在获取当前位置...") }
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    if (!handled) {
                        handled = true
                        locationManager.removeUpdates(listener)
                        _uiState.update { it.copy(message = "暂时无法获取当前位置，请稍后重试") }
                    }
                },
                15000L,
            )
        }.onFailure { throwable ->
            runCatching { locationManager.removeUpdates(listener) }
            _uiState.update { it.copy(message = throwable.cleanMessage("无法获取当前位置")) }
        }
    }

    fun updateAccentColorText(value: String) {
        _uiState.update { it.copy(accentColorText = value, message = null) }
    }

    fun selectAccentColor(color: Long) {
        store.saveAccentColor(color)
        _uiState.update {
            it.copy(
                accentColor = color,
                accentColorText = color.toHexColor(),
                message = "主题色已更新",
            )
        }
    }

    fun applyAccentColor() {
        val parsed = parseHexColor(uiState.value.accentColorText)
        if (parsed == null) {
            _uiState.update { it.copy(message = "颜色格式应为 #RRGGBB 或 #AARRGGBB") }
            return
        }
        selectAccentColor(parsed)
    }

    private fun runTasksNow(tasks: List<BjmfTask>, summary: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = "正在执行签到...") }
            val results = tasks.map { task ->
                val result = service.runSign(task)
                store.appendLog(
                    TaskLog(
                        id = System.currentTimeMillis() + task.id,
                        taskId = result.taskId,
                        taskName = result.taskName,
                        runAt = System.currentTimeMillis(),
                        status = result.status,
                        message = result.message,
                    ),
                )
                result
            }
            if (summary) {
                tasks.firstOrNull { it.wxKey.isNotBlank() }?.let { task ->
                    runCatching { service.sendSummary(task.wxKey, results) }
                }
            }
            scheduler.scheduleAll(store.loadTasks())
            reloadLocalData(uiState.value.selectedTaskId)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    screen = BjmfScreen.Logs,
                    message = "签到执行完成：${results.joinToString { result -> result.status }}",
                )
            }
        }
    }

    private fun saveCurrentLocationFavorite(location: Location) {
        val mapped = wgs84ToGcj02IfNeeded(location.latitude, location.longitude)
        val saved = store.saveFavoriteLocation(
            FavoriteLocation(
                id = 0L,
                name = "当前位置 ${shortTime()}",
                lat = mapped.first.toCoordText(),
                lng = mapped.second.toCoordText(),
            ),
        )
        reloadLocalData(uiState.value.selectedTaskId)
        _uiState.update {
            it.copy(
                taskForm = it.taskForm.copy(coord = "${saved.lng} ${saved.lat}"),
                message = "已获取并收藏当前位置",
            )
        }
    }

    private fun beginPolling(session: BjmfNativeService.QrSession) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            repeat(80) {
                if (!isActive) return@launch
                delay(1500)
                runCatching { service.pollQrLogin(session) }
                    .onSuccess { account ->
                        if (account == null) {
                            _uiState.update { it.copy(qrStatusText = "等待扫码确认...", isPolling = true) }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isPolling = false,
                                    qrStatusText = "登录成功",
                                    account = account,
                                    taskForm = accountFormOrEmpty(account),
                                    selectedTaskId = null,
                                    screen = BjmfScreen.Task,
                                    message = "${account.userInfo.name} 已登录，可保存为本机任务",
                                )
                            }
                            return@launch
                        }
                    }
                    .onFailure { throwable ->
                        _uiState.update {
                            it.copy(
                                isPolling = false,
                                qrStatusText = "登录轮询失败",
                                message = throwable.cleanMessage("登录轮询失败"),
                            )
                        }
                        return@launch
                    }
            }
            _uiState.update {
                it.copy(isPolling = false, qrStatusText = "二维码已过期", message = "请重新获取二维码")
            }
        }
    }

    private fun reloadLocalData(selectedTaskId: Long?) {
        val tasks = store.loadTasks()
        val selected = selectedTaskId?.let { id -> tasks.firstOrNull { it.id == id } }
        _uiState.update {
            it.copy(
                tasks = tasks,
                logs = store.loadLogs(limit = Int.MAX_VALUE),
                favoriteLocations = store.loadFavoriteLocations(),
                selectedTaskId = selected?.id,
                taskForm = selected?.toForm() ?: it.taskForm.copy(selectedTaskId = null),
            )
        }
    }

    private fun accountFormOrEmpty(account: LoginAccount?): TaskForm {
        return account?.let {
            TaskForm(
                name = it.userInfo.name,
                classId = it.userInfo.classId,
                cookie = it.cookie,
            )
        } ?: TaskForm()
    }

    private fun parseCoord(raw: String): Pair<String, String>? {
        val parts = raw
            .replace("，", " ")
            .replace(",", " ")
            .replace("|", " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val first = parts[0].toDoubleOrNull() ?: return null
        val second = parts[1].toDoubleOrNull() ?: return null
        val (lat, lng) = normalizeCoordPair(first, second) ?: return null
        return lat.toCoordText() to lng.toCoordText()
    }

    private fun normalizeTimes(raw: String): List<String> {
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

    private fun BjmfTask.toForm(): TaskForm {
        return TaskForm(
            selectedTaskId = id,
            name = name,
            classId = classId,
            cookie = cookie,
            coord = listOf(lng, lat).filter { it.isNotBlank() }.joinToString(" "),
            acc = acc,
            timesText = times.joinToString(","),
            wxKey = wxKey,
            qqKey = qqKey,
            dateStart = dateStart.orEmpty(),
            dateEnd = dateEnd.orEmpty(),
        )
    }

    private fun Throwable.cleanMessage(fallback: String): String {
        return localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun shortTime(): String {
        return java.text.SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(java.util.Date())
    }
}

private fun parseHexColor(raw: String): Long? {
    val clean = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    val argb = when (clean.length) {
        6 -> "FF$clean"
        8 -> clean
        else -> return null
    }
    return argb.toLongOrNull(16)?.takeIf { it in 0x00000000..0xFFFFFFFF }
}

private fun Long.toHexColor(): String {
    return "#%06X".format(Locale.US, this and 0xFFFFFF)
}

private fun Double.toCoordText(): String = "%.6f".format(Locale.US, this)

private fun normalizeCoordPair(first: Double, second: Double): Pair<Double, Double>? {
    return when {
        first in -180.0..180.0 && second in -90.0..90.0 && first !in -90.0..90.0 -> second to first
        first in -90.0..90.0 && second in -180.0..180.0 && second !in -90.0..90.0 -> first to second
        first in -180.0..180.0 && second in -90.0..90.0 -> second to first
        first in -90.0..90.0 && second in -180.0..180.0 -> first to second
        else -> null
    }
}

private fun wgs84ToGcj02IfNeeded(lat: Double, lng: Double): Pair<Double, Double> {
    if (outOfChina(lat, lng)) return lat to lng
    var dLat = transformLat(lng - 105.0, lat - 35.0)
    var dLng = transformLng(lng - 105.0, lat - 35.0)
    val radLat = lat / 180.0 * Math.PI
    var magic = sin(radLat)
    magic = 1 - EE * magic * magic
    val sqrtMagic = sqrt(magic)
    dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI)
    dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * Math.PI)
    return (lat + dLat) to (lng + dLng)
}

private fun outOfChina(lat: Double, lng: Double): Boolean {
    return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

private fun transformLat(x: Double, y: Double): Double {
    var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
    ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
    ret += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
    ret += (160.0 * sin(y / 12.0 * Math.PI) + 320 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
    return ret
}

private fun transformLng(x: Double, y: Double): Double {
    var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
    ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
    ret += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
    ret += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
    return ret
}

private const val A = 6378245.0
private const val EE = 0.00669342162296594323
