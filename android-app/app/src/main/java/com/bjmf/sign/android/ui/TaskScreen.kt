package com.bjmf.sign.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bjmf.sign.android.BjmfUiState
import com.bjmf.sign.android.BjmfViewModel
import com.bjmf.sign.android.data.BjmfTask
import com.bjmf.sign.android.data.SIGN_ZONE
import com.bjmf.sign.android.data.TaskForm
import com.bjmf.sign.android.data.computeNextRunMillis
import com.bjmf.sign.android.data.normalizeTimes
import com.bjmf.sign.android.data.parseDateOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private val ENABLED_GREEN = Color(0xFF24C96B)

@Composable
internal fun TaskScreen(state: BjmfUiState, viewModel: BjmfViewModel) {
    TaskListCard(state, viewModel)
    TaskEditorCard(state, viewModel)
}

// region 任务列表

@Composable
private fun TaskListCard(state: BjmfUiState, viewModel: BjmfViewModel) {
    SectionCard(
        title = "本机任务",
        action = {
            TextButton(text = "新建", onClick = viewModel::startNewTask, enabled = !state.isBusy)
        },
    ) {
        if (state.tasks.isEmpty()) {
            Text(
                text = "还没有任务。扫码登录后会自动填入账号信息，选好位置和时间即可保存。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        } else {
            state.tasks.forEachIndexed { index, task ->
                TaskItem(
                    task = task,
                    selected = task.id == state.selectedTaskId,
                    isBusy = state.isBusy,
                    onSelect = { viewModel.selectTask(task) },
                    onToggle = { viewModel.toggleTask(task) },
                    onRunNow = { viewModel.runTaskNow(task) },
                )
                if (index != state.tasks.lastIndex) ListDivider()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskItem(
    task: BjmfTask,
    selected: Boolean,
    isBusy: Boolean,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
    onRunNow: () -> Unit,
) {
    val summaryColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (selected) Modifier.background(MiuixTheme.colorScheme.tertiaryContainer) else Modifier,
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = if (selected) 12.dp else 0.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(if (task.enabled) ENABLED_GREEN else summaryColor.copy(alpha = 0.5f), CircleShape),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "班级 ${task.classId}",
                    color = summaryColor,
                    fontSize = 13.sp,
                )
            }
            Switch(checked = task.enabled, onCheckedChange = { onToggle() })
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = nextRunLabel(task.times, task.dateStart, task.dateEnd, task.enabled),
            color = if (task.enabled) MiuixTheme.colorScheme.primary else summaryColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )

        if (task.times.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                task.times.forEach { TimeChip(it) }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = buildString {
                append("位置 ${task.lng}, ${task.lat} · 精度 ${task.acc}m")
                dateRangeText(task.dateStart, task.dateEnd)?.let { append("\n生效 ").append(it) }
            },
            color = summaryColor,
            fontSize = 13.sp,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(text = "编辑", onClick = onSelect)
            TextButton(text = "立即签到", onClick = onRunNow, enabled = !isBusy)
        }
    }
}

@Composable
private fun TimeChip(time: String, onRemove: (() -> Unit)? = null) {
    val primary = MiuixTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(primary.copy(alpha = 0.12f))
            .then(if (onRemove != null) Modifier.clickable(onClick = onRemove) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = shortTime(time), color = primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (onRemove != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "×", color = primary.copy(alpha = 0.7f), fontSize = 15.sp)
        }
    }
}

// endregion

// region 任务编辑

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskEditorCard(state: BjmfUiState, viewModel: BjmfViewModel) {
    val form = state.taskForm
    val editing = state.selectedTaskId != null
    val onFormChange: (TaskForm) -> Unit = viewModel::updateTaskForm
    val times = remember(form.timesText) { normalizeTimes(form.timesText) }
    val summaryColor = MiuixTheme.colorScheme.onSurfaceVariantSummary

    var showTimePicker by remember { mutableStateOf(false) }
    var datePickerTarget by remember { mutableStateOf<String?>(null) }
    var showCookie by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    SectionCard(title = if (editing) "编辑任务" else "新建任务") {
        // 基本信息
        FormSectionTitle("基本信息")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = form.name,
                onValueChange = { onFormChange(form.copy(name = it)) },
                label = "姓名",
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextField(
                value = form.classId,
                onValueChange = { onFormChange(form.copy(classId = it)) },
                label = "班级 ID",
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        // 签到位置
        FormSectionDivider()
        MapCoordinatePicker(
            coord = form.coord,
            favoriteLocations = state.favoriteLocations,
            isBusy = state.isBusy,
            onCoordinateSelected = { lat, lng ->
                onFormChange(form.copy(coord = "%.6f %.6f".format(Locale.US, lng, lat)))
            },
            onUseCurrentLocationAndFavorite = viewModel::useCurrentLocationAndFavorite,
            onFavoriteSelectedLocation = viewModel::favoriteSelectedLocation,
            onUseFavoriteLocation = viewModel::useFavoriteLocation,
            onDeleteFavoriteLocation = viewModel::deleteFavoriteLocation,
            onLocationPermissionDenied = viewModel::locationPermissionDenied,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text("定位精度（米）", fontSize = 14.sp, color = summaryColor)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextField(
                value = form.acc,
                onValueChange = { value -> onFormChange(form.copy(acc = value.filter(Char::isDigit))) },
                label = "精度",
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            listOf("10", "30", "50", "100").forEach { preset ->
                OptionChip(
                    text = preset,
                    selected = form.acc.trim() == preset,
                    onClick = { onFormChange(form.copy(acc = preset)) },
                )
            }
        }

        // 执行时间
        FormSectionDivider()
        FormSectionTitle("执行时间")
        if (times.isEmpty()) {
            Text("尚未添加执行时间", color = summaryColor)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                times.forEach { time ->
                    TimeChip(time) {
                        onFormChange(form.copy(timesText = (times - time).joinToString(",")))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "点按时间可移除",
                color = summaryColor,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(text = "+ 添加时间", onClick = { showTimePicker = true })
        }

        // 生效日期
        FormSectionDivider()
        FormSectionTitle("生效日期")
        DateRow(
            label = "开始日期",
            value = form.dateStart,
            onPick = { datePickerTarget = "start" },
            onClear = { onFormChange(form.copy(dateStart = "")) },
        )
        DateRow(
            label = "结束日期",
            value = form.dateEnd,
            onPick = { datePickerTarget = "end" },
            onClear = { onFormChange(form.copy(dateEnd = "")) },
        )
        Text("留空表示不限制", color = summaryColor, fontSize = 13.sp)

        // 下次执行预览
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = nextRunLabel(times, form.dateStart, form.dateEnd, enabled = true),
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }

        // 登录凭证
        FormSectionDivider()
        FormSectionTitle("登录凭证")
        PreferenceRow(
            title = "Cookie",
            summary = cookieSummary(form.cookie),
            onClick = { showCookie = !showCookie },
            action = {
                TextButton(text = if (showCookie) "收起" else "编辑", onClick = { showCookie = !showCookie })
            },
        )
        if (showCookie || form.cookie.isBlank()) {
            TextField(
                value = form.cookie,
                onValueChange = { onFormChange(form.copy(cookie = it)) },
                label = "Cookie（扫码登录后自动填入）",
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 通知推送
        FormSectionDivider()
        FormSectionTitle("通知推送（可选）")
        TextField(
            value = form.wxKey,
            onValueChange = { onFormChange(form.copy(wxKey = it.trim())) },
            label = "Server酱 Key（微信）",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        TextField(
            value = form.qqKey,
            onValueChange = { onFormChange(form.copy(qqKey = it.trim())) },
            label = "Qmsg酱 Key（QQ）",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // 操作
        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = viewModel::saveTask,
                enabled = !state.isBusy,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(if (editing) "保存修改" else "保存任务")
            }
            TextButton(text = "用扫码账号", onClick = viewModel::newTaskFromAccount, enabled = !state.isBusy)
        }
        if (editing) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = viewModel::runSelectedNow,
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("立即签到")
                }
                TextButton(text = "删除任务", onClick = { confirmDelete = true }, enabled = !state.isBusy)
            }
        }
    }

    TimePickerDialog(
        show = showTimePicker,
        onDismiss = { showTimePicker = false },
        onConfirm = { time ->
            showTimePicker = false
            onFormChange(form.copy(timesText = normalizeTimes((times + time).joinToString(",")).sorted().joinToString(",")))
        },
    )

    DatePickerDialog(
        show = datePickerTarget != null,
        title = if (datePickerTarget == "end") "选择结束日期" else "选择开始日期",
        initial = parseDateOrNull(if (datePickerTarget == "end") form.dateEnd else form.dateStart),
        onDismiss = { datePickerTarget = null },
        onConfirm = { date ->
            val text = date.toString()
            onFormChange(if (datePickerTarget == "end") form.copy(dateEnd = text) else form.copy(dateStart = text))
            datePickerTarget = null
        },
    )

    ConfirmDialog(
        show = confirmDelete,
        title = "删除任务",
        summary = "确定删除「${form.name}」？该任务的定时也会一并取消。",
        confirmText = "删除",
        onDismiss = { confirmDelete = false },
        onConfirm = {
            confirmDelete = false
            viewModel.deleteSelectedTask()
        },
    )
}

@Composable
internal fun FormSectionTitle(text: String) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun FormSectionDivider() {
    Spacer(modifier = Modifier.height(18.dp))
}

@Composable
private fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MiuixTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) primary else primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = if (selected) MiuixTheme.colorScheme.onPrimary else primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DateRow(label: String, value: String, onPick: () -> Unit, onClear: () -> Unit) {
    PreferenceRow(
        title = label,
        value = value.ifBlank { "不限" },
        onClick = onPick,
        action = {
            if (value.isNotBlank()) {
                TextButton(text = "清除", onClick = onClear)
            }
        },
    )
}

// endregion

// region 对话框

@Composable
private fun TimePickerDialog(show: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var hour by remember { mutableIntStateOf(7) }
    var minute by remember { mutableIntStateOf(30) }
    WindowDialog(
        show = show,
        title = "添加执行时间",
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberPicker(
                value = hour,
                onValueChange = { hour = it },
                range = 0..23,
                label = { "%02d".format(it) },
                wrapAround = true,
            )
            Text(":", fontSize = 28.sp, modifier = Modifier.padding(horizontal = 12.dp))
            NumberPicker(
                value = minute,
                onValueChange = { minute = it },
                range = 0..59,
                label = { "%02d".format(it) },
                wrapAround = true,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        DialogButtons(
            confirmText = "添加",
            onDismiss = onDismiss,
            onConfirm = { onConfirm("%02d:%02d:00".format(hour, minute)) },
        )
    }
}

@Composable
private fun DatePickerDialog(
    show: Boolean,
    title: String,
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val today = LocalDate.now(SIGN_ZONE)
    val start = initial ?: today
    var year by remember(show) { mutableIntStateOf(start.year) }
    var month by remember(show) { mutableIntStateOf(start.monthValue) }
    var day by remember(show) { mutableIntStateOf(start.dayOfMonth) }
    val maxDay = YearMonth.of(year, month).lengthOfMonth()

    WindowDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberPicker(
                value = year,
                onValueChange = { year = it },
                range = (today.year - 1)..(today.year + 5),
                label = { "${it}年" },
            )
            NumberPicker(
                value = month,
                onValueChange = { month = it },
                range = 1..12,
                label = { "${it}月" },
                wrapAround = true,
            )
            NumberPicker(
                value = day.coerceAtMost(maxDay),
                onValueChange = { day = it },
                range = 1..maxDay,
                label = { "${it}日" },
                wrapAround = true,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        DialogButtons(
            confirmText = "确定",
            onDismiss = onDismiss,
            onConfirm = { onConfirm(LocalDate.of(year, month, day.coerceAtMost(maxDay))) },
        )
    }
}

@Composable
internal fun ConfirmDialog(
    show: Boolean,
    title: String,
    summary: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismiss,
    ) {
        DialogButtons(confirmText = confirmText, onDismiss = onDismiss, onConfirm = onConfirm)
    }
}

@Composable
private fun DialogButtons(confirmText: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(
            text = "取消",
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            text = confirmText,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

// endregion

// region 文案

private val nextRunFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)
private val timeOnlyFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

internal fun nextRunLabel(times: List<String>, dateStart: String?, dateEnd: String?, enabled: Boolean): String {
    if (!enabled) return "已停用"
    if (times.isEmpty()) return "未设置执行时间"
    val next = computeNextRunMillis(times, dateStart, dateEnd) ?: return "已过生效日期，不再执行"
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(next), SIGN_ZONE)
    val today = LocalDate.now(SIGN_ZONE)
    val day = when (dateTime.toLocalDate()) {
        today -> "今天 " + dateTime.format(timeOnlyFormatter)
        today.plusDays(1) -> "明天 " + dateTime.format(timeOnlyFormatter)
        else -> dateTime.format(nextRunFormatter)
    }
    return "下次执行：$day"
}

private fun shortTime(time: String): String = if (time.endsWith(":00") && time.length == 8) time.dropLast(3) else time

private fun dateRangeText(start: String?, end: String?): String? {
    val s = start?.takeIf { it.isNotBlank() }
    val e = end?.takeIf { it.isNotBlank() }
    return when {
        s == null && e == null -> null
        s != null && e != null -> "$s 至 $e"
        s != null -> "$s 起"
        else -> "至 $e"
    }
}

private fun cookieSummary(cookie: String): String {
    val value = cookie.trim()
    if (value.isEmpty()) return "未填写"
    val head = value.take(18)
    return if (value.length > 18) "$head…（共 ${value.length} 字符）" else head
}

// endregion
