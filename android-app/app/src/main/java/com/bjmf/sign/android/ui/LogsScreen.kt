package com.bjmf.sign.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bjmf.sign.android.BjmfUiState
import com.bjmf.sign.android.BjmfViewModel
import com.bjmf.sign.android.data.I18n
import com.bjmf.sign.android.data.SIGN_ZONE
import com.bjmf.sign.android.data.TaskLog
import com.bjmf.sign.android.data.diagnosticReport
import com.bjmf.sign.android.data.isSuccessStatus
import com.bjmf.sign.android.data.parseLogMessage
import com.bjmf.sign.android.data.readableReason
import com.bjmf.sign.android.data.tr
import com.bjmf.sign.android.ui.glass.Glass
import com.bjmf.sign.android.ui.glass.GlassButton
import com.bjmf.sign.android.ui.glass.GlassButtonSize
import com.bjmf.sign.android.ui.glass.GlassButtonStyle
import com.bjmf.sign.android.ui.glass.GlassCard
import com.bjmf.sign.android.ui.glass.GlassChip
import com.bjmf.sign.android.ui.glass.GlassIcon
import com.bjmf.sign.android.ui.glass.GlassIconButton
import com.bjmf.sign.android.ui.glass.GlassIconGlyph
import com.bjmf.sign.android.ui.glass.SegmentedControl
import com.bjmf.sign.android.ui.glass.StatusPill
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import top.yukonga.miuix.kmp.basic.Text

private const val PAGE_SIZE = 30

@Composable
internal fun statusColor(status: String): Color {
    val palette = Glass.palette
    return when (status) {
        "success", "already_signed" -> palette.green
        "not_started", "no_sign_in", "skip" -> palette.orange
        else -> palette.red
    }
}

/** 某一天的执行统计。 */
private data class DayStat(val success: Int, val failed: Int) {
    val total: Int get() = success + failed
}

@Composable
internal fun LogsScreen(state: BjmfUiState, viewModel: BjmfViewModel) {
    var statusFilter by rememberSaveable { mutableIntStateOf(0) }
    var taskFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var visibleCount by rememberSaveable { mutableIntStateOf(PAGE_SIZE) }
    var selectedDayText by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val palette = Glass.palette
    val selectedDay = selectedDayText?.let(LocalDate::parse)

    val logs = state.logs
    val successCount = logs.count { isSuccessStatus(it.status) }
    val issueCount = logs.size - successCount
    val taskNames = remember(logs) {
        logs.distinctBy { it.taskId }.map { it.taskId to it.taskName }
    }
    val filtered = logs.filter { log ->
        val statusOk = when (statusFilter) {
            1 -> isSuccessStatus(log.status)
            2 -> !isSuccessStatus(log.status)
            else -> true
        }
        statusOk && (taskFilter == null || log.taskId == taskFilter)
    }
    val dayStats = remember(filtered) {
        filtered.groupBy { dayOf(it.runAt) }.mapValues { (_, items) ->
            DayStat(success = items.count { isSuccessStatus(it.status) }, failed = items.count { !isSuccessStatus(it.status) })
        }
    }

    GlassCard(
        title = tr("执行概览"),
        action = {
            GlassIconButton(
                icon = GlassIcon.Refresh,
                onClick = viewModel::refreshLocalData,
                contentDescription = tr("刷新"),
                size = 36.dp,
                enabled = !state.isBusy,
            )
            GlassIconButton(
                icon = GlassIcon.Trash,
                onClick = { confirmClear = true },
                contentDescription = tr("清除日志"),
                size = 36.dp,
                style = GlassButtonStyle.Destructive,
                enabled = !state.isBusy && logs.isNotEmpty(),
            )
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBlock(tr("全部"), logs.size, palette.label, Modifier.weight(1f))
            StatBlock(tr("成功"), successCount, palette.green, Modifier.weight(1f))
            StatBlock(tr("异常"), issueCount, palette.red, Modifier.weight(1f))
        }
        SegmentedControl(
            options = listOf(tr("全部"), tr("成功"), tr("异常")),
            selectedIndex = statusFilter,
            onSelect = {
                statusFilter = it
                visibleCount = PAGE_SIZE
            },
        )
        if (taskNames.size > 1) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GlassChip(text = tr("全部任务"), selected = taskFilter == null, onClick = {
                    taskFilter = null
                    visibleCount = PAGE_SIZE
                })
                taskNames.forEach { (id, name) ->
                    GlassChip(text = name, selected = taskFilter == id, onClick = {
                        taskFilter = id
                        visibleCount = PAGE_SIZE
                    })
                }
            }
        }
    }

    CalendarHeatmapCard(
        stats = dayStats,
        selectedDay = selectedDay,
        onSelectDay = { day -> selectedDayText = if (day == selectedDay) null else day.toString() },
    )

    if (selectedDay != null) {
        val dayLogs = filtered.filter { dayOf(it.runAt) == selectedDay }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tr("{0} · {1} 条", dayLabel(selectedDay), dayLogs.size),
                color = palette.secondaryLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
            )
            GlassButton(text = tr("显示全部"), onClick = { selectedDayText = null }, size = GlassButtonSize.Small)
        }
        if (dayLogs.isEmpty()) {
            EmptyHint(tr("这一天没有执行记录"))
        } else {
            dayLogs.forEach { LogCard(it, initiallyExpanded = true) }
        }
    } else if (filtered.isEmpty()) {
        EmptyHint(if (logs.isEmpty()) tr("暂无执行日志") else tr("没有符合筛选条件的日志"))
    } else {
        val shown = filtered.take(visibleCount)
        shown.groupBy { dayOf(it.runAt) }.forEach { (day, items) ->
            Text(
                text = dayLabel(day),
                color = palette.secondaryLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp),
            )
            items.forEach { LogCard(it) }
        }
        if (filtered.size > visibleCount) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GlassButton(
                    text = tr("加载更多（剩余 {0} 条）", filtered.size - visibleCount),
                    onClick = { visibleCount += PAGE_SIZE },
                )
            }
        }
    }

    ConfirmDialog(
        show = confirmClear,
        title = tr("清除日志"),
        summary = tr("确定清除全部 {0} 条执行日志？", logs.size),
        confirmText = tr("清除"),
        destructive = true,
        onDismiss = { confirmClear = false },
        onConfirm = {
            confirmClear = false
            selectedDayText = null
            viewModel.clearLogs()
        },
    )
}

// region 日历热力图

@Composable
private fun CalendarHeatmapCard(
    stats: Map<LocalDate, DayStat>,
    selectedDay: LocalDate?,
    onSelectDay: (LocalDate) -> Unit,
) {
    val today = LocalDate.now(SIGN_ZONE)
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    val palette = Glass.palette
    val accent = Glass.accent
    val maxCount = stats.values.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1

    GlassCard(
        title = monthTitle(month),
        action = {
            GlassIconButton(
                icon = GlassIcon.ChevronLeft,
                onClick = { monthText = month.minusMonths(1).toString() },
                contentDescription = tr("上个月"),
                size = 34.dp,
            )
            GlassIconButton(
                icon = GlassIcon.ChevronRight,
                onClick = { monthText = month.plusMonths(1).toString() },
                contentDescription = tr("下个月"),
                size = 34.dp,
                enabled = month < YearMonth.from(today),
            )
        },
    ) {
        // 星期标题（周一开始）
        Row {
            weekdayLabels().forEach { label ->
                Text(
                    text = label,
                    color = palette.secondaryLabel,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val first = month.atDay(1)
        val start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = (0 until 42).map { start.plusDays(it.toLong()) }
            .chunked(7)
            .filter { week -> week.any { it.month == first.month } }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        val inMonth = day.month == first.month
                        val stat = stats[day]
                        HeatCell(
                            day = day,
                            stat = if (inMonth) stat else null,
                            inMonth = inMonth,
                            isToday = day == today,
                            selected = day == selectedDay,
                            maxCount = maxCount,
                            onClick = { if (inMonth && !day.isAfter(today)) onSelectDay(day) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        // 图例
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem(palette.green, tr("成功"))
            LegendItem(palette.orange, tr("部分失败"))
            LegendItem(palette.red, tr("失败"))
            LegendItem(palette.fill, tr("无记录"))
        }
        Text(tr("点按某一天查看当天的执行详情"), color = palette.secondaryLabel, fontSize = 13.sp)
        if (selectedDay != null) {
            Text(
                text = tr("已选中 {0}", dayLabel(selectedDay)),
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun HeatCell(
    day: LocalDate,
    stat: DayStat?,
    inMonth: Boolean,
    isToday: Boolean,
    selected: Boolean,
    maxCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Glass.palette
    val base = when {
        stat == null || stat.total == 0 -> null
        stat.failed == 0 -> palette.green
        stat.success == 0 -> palette.red
        else -> palette.orange
    }
    val intensity = if (stat == null) 0f else 0.35f + 0.65f * (stat.total.toFloat() / maxCount)
    val background = base?.copy(alpha = intensity) ?: palette.fill
    val shape = RoundedCornerShape(8.dp)
    val description = when {
        stat == null || stat.total == 0 -> tr("{0}：无记录", dayLabel(day))
        else -> tr("{0}：成功 {1}，失败 {2}", dayLabel(day), stat.success, stat.failed)
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (inMonth) background else Color.Transparent)
            .then(if (selected) Modifier.border(2.dp, Glass.accent, shape) else Modifier)
            .then(if (isToday && !selected) Modifier.border(1.dp, palette.secondaryLabel, shape) else Modifier)
            .clickable(enabled = inMonth, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (inMonth) {
            Text(
                text = day.dayOfMonth.toString(),
                color = if (base != null && intensity > 0.6f) Color.White else palette.label,
                fontSize = 13.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, color = Glass.palette.secondaryLabel, fontSize = 12.sp)
    }
}

// endregion

@Composable
private fun StatBlock(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(text = value.toString(), color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(text = label, color = Glass.palette.secondaryLabel, fontSize = 12.sp)
    }
}

@Composable
private fun LogCard(log: TaskLog, initiallyExpanded: Boolean = false) {
    var expanded by rememberSaveable(log.id) { mutableStateOf(initiallyExpanded) }
    val parsed = remember(log.message) { parseLogMessage(log.message) }
    val success = isSuccessStatus(log.status)
    val color = statusColor(log.status)
    val palette = Glass.palette
    val accent = Glass.accent
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val reason = readableReason(log.status, log.message)

    GlassCard(
        modifier = Modifier
            .animateContentSize()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = if (expanded) tr("收起详情") else tr("展开详情"),
            ) { expanded = !expanded },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(text = statusLabel(log.status), color = color)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = log.taskName,
                color = palette.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = timeOf(log.runAt), color = palette.secondaryLabel, fontSize = 13.sp)
        }

        if (success) {
            Text(
                text = parsed.summary ?: statusLabel(log.status),
                color = palette.label,
                fontSize = 15.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            // 失败：给出可读原因
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                GlassIconGlyph(GlassIcon.Bell, color = color, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = reason, color = palette.label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.fill)
                    .padding(12.dp),
            ) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        parsed.fields.forEach { (key, value) ->
                            Row {
                                Text(
                                    text = key,
                                    color = palette.secondaryLabel,
                                    fontSize = 13.sp,
                                    modifier = Modifier.width(96.dp),
                                )
                                Text(text = value, color = palette.label, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        val notes = if (success) parsed.notes.dropLast(1) else parsed.notes
                        notes.forEach { note ->
                            Text(text = note, fontSize = 13.sp, color = palette.secondaryLabel)
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconGlyph(
                icon = if (expanded) GlassIcon.ChevronUp else GlassIcon.ChevronDown,
                color = accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (expanded) tr("收起详情") else tr("展开详情"),
                color = accent,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            if (!success) {
                GlassButton(
                    text = tr("复制给开发者"),
                    onClick = { copyToClipboard(context, diagnosticReport(context, log), tr("已复制诊断信息")) },
                    icon = GlassIcon.Copy,
                    size = GlassButtonSize.Small,
                )
            } else if (expanded) {
                GlassButton(
                    text = tr("复制日志"),
                    onClick = { copyToClipboard(context, "${log.taskName} ${formatTime(log.runAt)}\n${log.message}", tr("已复制日志")) },
                    icon = GlassIcon.Copy,
                    size = GlassButtonSize.Small,
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String, toast: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("BJMFSign", text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(SIGN_ZONE).toLocalDate()

private fun timeOf(millis: Long): String = Instant.ofEpochMilli(millis).atZone(SIGN_ZONE).format(timeFormatter)

private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now(SIGN_ZONE)
    return when (day) {
        today -> tr("今天")
        today.minusDays(1) -> tr("昨天")
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(I18n.locale()))
    }
}

private fun monthTitle(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern(tr("yyyy年M月"), I18n.locale()))

private fun weekdayLabels(): List<String> =
    listOf(tr("一"), tr("二"), tr("三"), tr("四"), tr("五"), tr("六"), tr("日"))
