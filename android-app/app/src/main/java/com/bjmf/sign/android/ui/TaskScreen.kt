package com.bjmf.sign.android.ui

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bjmf.sign.android.BjmfUiState
import com.bjmf.sign.android.data.tr
import com.bjmf.sign.android.BjmfViewModel
import com.bjmf.sign.android.data.BjmfTask
import com.bjmf.sign.android.data.SIGN_ZONE
import com.bjmf.sign.android.data.TaskForm
import com.bjmf.sign.android.data.computeNextRunMillis
import com.bjmf.sign.android.data.normalizeTimes
import com.bjmf.sign.android.data.parseDateOrNull
import com.bjmf.sign.android.ui.glass.Glass
import com.bjmf.sign.android.ui.glass.GlassButton
import com.bjmf.sign.android.ui.glass.GlassButtonSize
import com.bjmf.sign.android.ui.glass.GlassButtonStyle
import com.bjmf.sign.android.ui.glass.GlassCard
import com.bjmf.sign.android.ui.glass.GlassChip
import com.bjmf.sign.android.ui.glass.GlassIcon
import com.bjmf.sign.android.ui.glass.GlassIconGlyph
import com.bjmf.sign.android.ui.glass.GlassSwitch
import com.bjmf.sign.android.ui.glass.LabeledField
import com.bjmf.sign.android.ui.glass.SectionHeader
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun TaskScreen(state: BjmfUiState, viewModel: BjmfViewModel) {
    TaskListCard(state, viewModel)
    TaskEditorCard(state, viewModel)
}

// region 任务列表

@Composable
private fun TaskListCard(state: BjmfUiState, viewModel: BjmfViewModel) {
    GlassCard(
        title = tr("本机任务"),
        action = {
            GlassButton(
                text = tr("新建"),
                onClick = viewModel::startNewTask,
                icon = GlassIcon.Plus,
                size = GlassButtonSize.Small,
                enabled = !state.isBusy,
            )
        },
    ) {
        if (state.tasks.isEmpty()) {
            Text(
                text = tr("还没有任务。扫码登录后会自动填入账号信息，选好位置和时间即可保存。"),
                color = Glass.palette.secondaryLabel,
                fontSize = 15.sp,
            )
        } else {
            Column {
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
    val palette = Glass.palette
    val accent = Glass.accent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (selected) Modifier.background(accent.copy(alpha = 0.14f)) else Modifier)
            .clickable(onClick = onSelect)
            .padding(horizontal = if (selected) 12.dp else 0.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(if (task.enabled) palette.green else palette.secondaryLabel.copy(alpha = 0.5f), CircleShape),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.name,
                    color = palette.label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = tr("班级 {0}", task.classId), color = palette.secondaryLabel, fontSize = 12.sp)
            }
            GlassSwitch(checked = task.enabled, onCheckedChange = { onToggle() }, contentDescription = tr("启用任务"))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconGlyph(
                icon = if (task.enabled) GlassIcon.Clock else GlassIcon.Pause,
                color = if (task.enabled) accent else palette.secondaryLabel,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = nextRunLabel(task.times, task.dateStart, task.dateEnd, task.enabled),
                color = if (task.enabled) accent else palette.secondaryLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        if (task.times.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                task.times.forEach { GlassChip(text = shortTime(it)) }
            }
        }

        Text(
            text = buildString {
                append(tr("位置 {0}, {1} · 精度 {2}m", task.lng, task.lat, task.acc))
                dateRangeText(task.dateStart, task.dateEnd)?.let { append("\n").append(tr("生效 {0}", it)) }
            },
            color = palette.secondaryLabel,
            fontSize = 13.sp,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            GlassButton(text = tr("编辑"), onClick = onSelect, size = GlassButtonSize.Small)
            GlassButton(
                text = tr("立即签到"),
                onClick = onRunNow,
                icon = GlassIcon.Location,
                size = GlassButtonSize.Small,
                enabled = !isBusy,
            )
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
    val palette = Glass.palette
    val accent = Glass.accent

    var showTimePicker by remember { mutableStateOf(false) }
    var datePickerTarget by remember { mutableStateOf<String?>(null) }
    var showCookie by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    GlassCard(
        title = if (editing) tr("编辑任务") else tr("新建任务"),
        modifier = Modifier.animateContentSize(),
    ) {
        // 基本信息
        SectionHeader(tr("基本信息"))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledField(
                label = tr("姓名"),
                value = form.name,
                onValueChange = { onFormChange(form.copy(name = it)) },
                modifier = Modifier.weight(1f),
            )
            LabeledField(
                label = tr("班级 ID"),
                value = form.classId,
                onValueChange = { onFormChange(form.copy(classId = it)) },
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }

        // 签到位置
        FormSectionGap()
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
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LabeledField(
                label = tr("定位精度（米）"),
                value = form.acc,
                onValueChange = { value -> onFormChange(form.copy(acc = value.filter(Char::isDigit))) },
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            listOf("10", "30", "50", "100").forEach { preset ->
                GlassChip(
                    text = preset,
                    selected = form.acc.trim() == preset,
                    onClick = { onFormChange(form.copy(acc = preset)) },
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }

        // 执行时间
        FormSectionGap()
        SectionHeader(tr("执行时间"))
        if (times.isEmpty()) {
            Text(tr("尚未添加执行时间"), color = palette.secondaryLabel, fontSize = 15.sp)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                times.forEach { time ->
                    GlassChip(
                        text = shortTime(time),
                        removable = true,
                        onClick = { onFormChange(form.copy(timesText = (times - time).joinToString(","))) },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tr("点按时间可移除"),
                color = palette.secondaryLabel,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                text = tr("添加时间"),
                onClick = { showTimePicker = true },
                icon = GlassIcon.Plus,
                size = GlassButtonSize.Small,
            )
        }

        // 生效日期
        FormSectionGap()
        SectionHeader(tr("生效日期"))
        Column {
            DateRow(
                label = tr("开始日期"),
                value = form.dateStart,
                onPick = { datePickerTarget = "start" },
                onClear = { onFormChange(form.copy(dateStart = "")) },
            )
            ListDivider()
            DateRow(
                label = tr("结束日期"),
                value = form.dateEnd,
                onPick = { datePickerTarget = "end" },
                onClear = { onFormChange(form.copy(dateEnd = "")) },
            )
        }
        Text(tr("点按选择日期，留空表示不限制"), color = palette.secondaryLabel, fontSize = 13.sp)

        // 下次执行预览
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.12f))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconGlyph(GlassIcon.Clock, color = accent, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = nextRunLabel(times, form.dateStart, form.dateEnd, enabled = true),
                color = accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        // 登录凭证
        FormSectionGap()
        SectionHeader(tr("登录凭证"))
        PreferenceRow(
            title = "Cookie",
            summary = cookieSummary(form.cookie),
            onClick = { showCookie = !showCookie },
            action = {
                GlassIconGlyph(
                    icon = if (showCookie) GlassIcon.ChevronUp else GlassIcon.ChevronDown,
                    color = palette.secondaryLabel,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
        if (showCookie || form.cookie.isBlank()) {
            LabeledField(
                label = tr("Cookie（扫码登录后自动填入）"),
                value = form.cookie,
                onValueChange = { onFormChange(form.copy(cookie = it)) },
                singleLine = false,
            )
        }

        // 通知推送
        FormSectionGap()
        SectionHeader(tr("通知推送（可选）"))
        LabeledField(
            label = tr("Server酱 Key（微信）"),
            value = form.wxKey,
            onValueChange = { onFormChange(form.copy(wxKey = it.trim())) },
        )
        LabeledField(
            label = tr("Qmsg酱 Key（QQ）"),
            value = form.qqKey,
            onValueChange = { onFormChange(form.copy(qqKey = it.trim())) },
        )

        // 操作
        FormSectionGap()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                text = if (editing) tr("保存修改") else tr("保存任务"),
                onClick = viewModel::saveTask,
                style = GlassButtonStyle.Prominent,
                size = GlassButtonSize.Large,
                icon = GlassIcon.Check,
                enabled = !state.isBusy,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                text = tr("用扫码账号"),
                onClick = viewModel::newTaskFromAccount,
                size = GlassButtonSize.Large,
                enabled = !state.isBusy,
            )
        }
        if (editing) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(
                    text = tr("立即签到"),
                    onClick = viewModel::runSelectedNow,
                    size = GlassButtonSize.Large,
                    icon = GlassIcon.Location,
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    text = tr("删除任务"),
                    onClick = { confirmDelete = true },
                    style = GlassButtonStyle.Destructive,
                    size = GlassButtonSize.Large,
                    enabled = !state.isBusy,
                )
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
        title = if (datePickerTarget == "end") tr("选择结束日期") else tr("选择开始日期"),
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
        title = tr("删除任务"),
        summary = tr("确定删除「{0}」？该任务的定时也会一并取消。", form.name),
        confirmText = tr("删除"),
        destructive = true,
        onDismiss = { confirmDelete = false },
        onConfirm = {
            confirmDelete = false
            viewModel.deleteSelectedTask()
        },
    )
}

@Composable
private fun FormSectionGap() {
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun DateRow(label: String, value: String, onPick: () -> Unit, onClear: () -> Unit) {
    PreferenceRow(
        title = label,
        value = value.ifBlank { tr("不限") },
        onClick = onPick,
        action = {
            if (value.isNotBlank()) {
                GlassButton(
                    text = tr("清除"),
                    onClick = onClear,
                    style = GlassButtonStyle.Destructive,
                    size = GlassButtonSize.Small,
                )
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
        title = tr("添加执行时间"),
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
            confirmText = tr("添加"),
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
                label = { tr("{0}年", it) },
            )
            NumberPicker(
                value = month,
                onValueChange = { month = it },
                range = 1..12,
                label = { tr("{0}月", it) },
                wrapAround = true,
            )
            NumberPicker(
                value = day.coerceAtMost(maxDay),
                onValueChange = { day = it },
                range = 1..maxDay,
                label = { tr("{0}日", it) },
                wrapAround = true,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        DialogButtons(
            confirmText = tr("确定"),
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
    destructive: Boolean = false,
) {
    WindowDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismiss,
    ) {
        DialogButtons(confirmText = confirmText, onDismiss = onDismiss, onConfirm = onConfirm, destructive = destructive)
    }
}

@Composable
internal fun DialogButtons(
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassButton(
            text = tr("取消"),
            onClick = onDismiss,
            size = GlassButtonSize.Large,
            modifier = Modifier.weight(1f),
        )
        GlassButton(
            text = confirmText,
            onClick = onConfirm,
            style = if (destructive) GlassButtonStyle.Destructive else GlassButtonStyle.Prominent,
            size = GlassButtonSize.Large,
            modifier = Modifier.weight(1f),
        )
    }
}

// endregion

// region 文案

private val nextRunFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)
private val timeOnlyFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

internal fun nextRunLabel(times: List<String>, dateStart: String?, dateEnd: String?, enabled: Boolean): String {
    if (!enabled) return tr("已停用")
    if (times.isEmpty()) return tr("未设置执行时间")
    val next = computeNextRunMillis(times, dateStart, dateEnd) ?: return tr("已过生效日期，不再执行")
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(next), SIGN_ZONE)
    val today = LocalDate.now(SIGN_ZONE)
    val day = when (dateTime.toLocalDate()) {
        today -> tr("今天 {0}", dateTime.format(timeOnlyFormatter))
        today.plusDays(1) -> tr("明天 {0}", dateTime.format(timeOnlyFormatter))
        else -> dateTime.format(nextRunFormatter)
    }
    return tr("下次执行：{0}", day)
}

private fun shortTime(time: String): String = if (time.endsWith(":00") && time.length == 8) time.dropLast(3) else time

private fun dateRangeText(start: String?, end: String?): String? {
    val s = start?.takeIf { it.isNotBlank() }
    val e = end?.takeIf { it.isNotBlank() }
    return when {
        s == null && e == null -> null
        s != null && e != null -> tr("{0} 至 {1}", s, e)
        s != null -> tr("{0} 起", s)
        else -> tr("至 {0}", e)
    }
}

private fun cookieSummary(cookie: String): String {
    val value = cookie.trim()
    if (value.isEmpty()) return tr("未填写")
    val head = value.take(18)
    return if (value.length > 18) tr("{0}…（共 {1} 字符）", head, value.length) else head
}

// endregion
