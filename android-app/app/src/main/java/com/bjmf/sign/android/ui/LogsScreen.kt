package com.bjmf.sign.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val PAGE_SIZE = 30
private val SUCCESS_STATUSES = setOf("success", "already_signed")

internal fun statusColor(status: String): Color = when (status) {
    "success", "already_signed" -> Color(0xFF24C96B)
    "not_started", "no_sign_in", "skip" -> Color(0xFFDA8B00)
    else -> Color(0xFFE5484D)
}

@Composable
internal fun LogsScreen(state: BjmfUiState, viewModel: BjmfViewModel) {
    var statusFilter by rememberSaveable { mutableIntStateOf(0) }
    var taskFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var visibleCount by rememberSaveable { mutableIntStateOf(PAGE_SIZE) }
    var confirmClear by remember { mutableStateOf(false) }

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

    SectionCard(
        title = "执行概览",
        action = {
            Row {
                TextButton(text = "刷新", onClick = viewModel::refreshLocalData, enabled = !state.isBusy)
                TextButton(
                    text = "清除",
                    onClick = { confirmClear = true },
                    enabled = !state.isBusy && logs.isNotEmpty(),
                )
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBlock("全部", logs.size, MiuixTheme.colorScheme.onSurface, Modifier.weight(1f))
            StatBlock("成功", successCount, statusColor("success"), Modifier.weight(1f))
            StatBlock("异常", issueCount, statusColor("error"), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        TabRowWithContour(
            tabs = listOf("全部", "成功", "异常"),
            selectedTabIndex = statusFilter,
            onTabSelected = {
                statusFilter = it
                visibleCount = PAGE_SIZE
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (taskNames.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip("全部任务", taskFilter == null) {
                    taskFilter = null
                    visibleCount = PAGE_SIZE
                }
                taskNames.forEach { (id, name) ->
                    FilterChip(name, taskFilter == id) {
                        taskFilter = id
                        visibleCount = PAGE_SIZE
                    }
                }
            }
        }
    }

    if (filtered.isEmpty()) {
        EmptyHint(if (logs.isEmpty()) "暂无执行日志" else "没有符合筛选条件的日志")
    } else {
        val shown = filtered.take(visibleCount)
        val groups = shown.groupBy { dayOf(it.runAt) }
        groups.forEach { (day, dayLogs) ->
            Text(
                text = dayLabel(day),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                dayLogs.forEach { LogCard(it) }
            }
        }
        if (filtered.size > visibleCount) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(
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
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text = value.toString(), color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(text = label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MiuixTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) primary else primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = if (selected) MiuixTheme.colorScheme.onPrimary else primary,
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun LogCard(log: TaskLog) {
    var expanded by rememberSaveable(log.id) { mutableStateOf(false) }
    val parsed = remember(log.message) { parseLogMessage(log.message) }
    val color = statusColor(log.status)
    val summaryColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(modifier = Modifier.clickable { expanded = !expanded }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(color.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(text = statusLabel(log.status), color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = log.taskName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(text = timeOf(log.runAt), color = summaryColor, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = parsed.summary ?: statusLabel(log.status),
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (expanded) "收起详情 ▴" else "展开详情 ▾",
                color = MiuixTheme.colorScheme.primary,
                fontSize = 13.sp,
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        parsed.fields.forEach { (key, value) ->
                            Row {
                                Text(
                                    text = key,
                                    color = summaryColor,
                                    fontSize = 13.sp,
                                    modifier = Modifier.width(96.dp),
                                )
                                Text(text = value, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        parsed.notes.dropLast(1).forEach { note ->
                            Text(text = note, fontSize = 13.sp, color = summaryColor)
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    text = "复制日志",
                    onClick = { copyToClipboard(context, "${log.taskName} ${formatTime(log.runAt)}\n${log.message}") },
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
