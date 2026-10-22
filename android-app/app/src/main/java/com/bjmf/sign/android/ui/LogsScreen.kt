package com.bjmf.sign.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bjmf.sign.android.BjmfUiState
import com.bjmf.sign.android.BjmfViewModel
import com.bjmf.sign.android.data.SIGN_ZONE
import com.bjmf.sign.android.data.TaskLog
import com.bjmf.sign.android.data.parseLogMessage
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
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Text

private const val PAGE_SIZE = 30
private val SUCCESS_STATUSES = setOf("success", "already_signed")

@Composable
internal fun statusColor(status: String): Color {
    val palette = Glass.palette
    return when (status) {
        "success", "already_signed" -> palette.green
        "not_started", "no_sign_in", "skip" -> palette.orange
        else -> palette.red
    }
}

@Composable
internal fun LogsScreen(state: BjmfUiState, viewModel: BjmfViewModel) {
    var statusFilter by rememberSaveable { mutableIntStateOf(0) }
    var taskFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var visibleCount by rememberSaveable { mutableIntStateOf(PAGE_SIZE) }
    var confirmClear by remember { mutableStateOf(false) }
    val palette = Glass.palette

    val logs = state.logs
    val successCount = logs.count { it.status in SUCCESS_STATUSES }
    val issueCount = logs.size - successCount
    val taskNames = remember(logs) {
        logs.distinctBy { it.taskId }.map { it.taskId to it.taskName }
    }
    val filtered = logs.filter { log ->
        val statusOk = when (statusFilter) {
            1 -> log.status in SUCCESS_STATUSES
            2 -> log.status !in SUCCESS_STATUSES
            else -> true
        }
        statusOk && (taskFilter == null || log.taskId == taskFilter)
    }

    GlassCard(
        title = "执行概览",
        action = {
            GlassIconButton(
                icon = GlassIcon.Refresh,
                onClick = viewModel::refreshLocalData,
                size = 36.dp,
                enabled = !state.isBusy,
            )
            GlassIconButton(
                icon = GlassIcon.Trash,
                onClick = { confirmClear = true },
                size = 36.dp,
                style = GlassButtonStyle.Destructive,
                enabled = !state.isBusy && logs.isNotEmpty(),
            )
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBlock("全部", logs.size, palette.label, Modifier.weight(1f))
            StatBlock("成功", successCount, palette.green, Modifier.weight(1f))
            StatBlock("异常", issueCount, palette.red, Modifier.weight(1f))
        }
        SegmentedControl(
            options = listOf("全部", "成功", "异常"),
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
                GlassChip(text = "全部任务", selected = taskFilter == null, onClick = {
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

    if (filtered.isEmpty()) {
        EmptyHint(if (logs.isEmpty()) "暂无执行日志" else "没有符合筛选条件的日志")
    } else {
        val shown = filtered.take(visibleCount)
        shown.groupBy { dayOf(it.runAt) }.forEach { (day, dayLogs) ->
            Text(
                text = dayLabel(day),
                color = palette.secondaryLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp),
            )
            dayLogs.forEach { LogCard(it) }
        }
        if (filtered.size > visibleCount) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GlassButton(
                    text = "加载更多（剩余 ${filtered.size - visibleCount} 条）",
                    onClick = { visibleCount += PAGE_SIZE },
                )
            }
        }
    }

    ConfirmDialog(
        show = confirmClear,
        title = "清除日志",
        summary = "确定清除全部 ${logs.size} 条执行日志？",
        confirmText = "清除",
        destructive = true,
        onDismiss = { confirmClear = false },
        onConfirm = {
            confirmClear = false
            viewModel.clearLogs()
        },
    )
}

@Composable
private fun StatBlock(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text = value.toString(), color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(text = label, color = Glass.palette.secondaryLabel, fontSize = 12.sp)
    }
}

@Composable
private fun LogCard(log: TaskLog) {
    var expanded by rememberSaveable(log.id) { mutableStateOf(false) }
    val parsed = remember(log.message) { parseLogMessage(log.message) }
    val color = statusColor(log.status)
    val palette = Glass.palette
    val accent = Glass.accent
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }

    GlassCard(
        modifier = Modifier
            .animateContentSize()
            .clickable(interactionSource = interaction, indication = null) { expanded = !expanded },
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

        Text(
            text = parsed.summary ?: statusLabel(log.status),
            color = palette.label,
            fontSize = 15.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )

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
                        parsed.notes.dropLast(1).forEach { note ->
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
                text = if (expanded) "收起详情" else "展开详情",
                color = accent,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            if (expanded) {
                GlassButton(
                    text = "复制日志",
                    onClick = { copyToClipboard(context, "${log.taskName} ${formatTime(log.runAt)}\n${log.message}") },
                    icon = GlassIcon.Copy,
                    size = GlassButtonSize.Small,
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("签到日志", text))
    Toast.makeText(context, "已复制日志", Toast.LENGTH_SHORT).show()
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.CHINA)
private val dayFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

private fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(SIGN_ZONE).toLocalDate()

private fun timeOf(millis: Long): String = Instant.ofEpochMilli(millis).atZone(SIGN_ZONE).format(timeFormatter)

private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now(SIGN_ZONE)
    return when (day) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> day.format(dayFormatter)
    }
}
