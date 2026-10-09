package com.bjmf.sign.android.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.util.LruCache
import android.util.Base64
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bjmf.sign.android.BjmfScreen
import com.bjmf.sign.android.BjmfUiState
import com.bjmf.sign.android.BjmfViewModel
import com.bjmf.sign.android.data.BjmfTask
import com.bjmf.sign.android.data.FavoriteLocation
import com.bjmf.sign.android.data.TaskForm
import com.bjmf.sign.android.data.TaskLog
import com.bjmf.sign.android.data.UserInfo
import com.bjmf.sign.android.ui.theme.BjmfMiuixTheme
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun BjmfApp(viewModel: BjmfViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val screens = remember { BjmfScreen.entries.toList() }
    val currentScreen by rememberUpdatedState(state.screen)
    val pagerState = rememberPagerState(
        initialPage = screens.indexOf(state.screen).coerceAtLeast(0),
        pageCount = { screens.size },
    )

    LaunchedEffect(state.screen) {
        val target = screens.indexOf(state.screen).coerceAtLeast(0)
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                screens.getOrNull(page)?.takeIf { it != currentScreen }?.let(viewModel::selectScreen)
            }
    }

    BjmfMiuixTheme(keyColor = Color(state.accentColor)) {
        Scaffold { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.background)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) { page ->
                    val screen = screens[page]
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(top = 18.dp, bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        PageHeader(screen)
                        LocalStatusCard(state)

                        when (screen) {
                            BjmfScreen.Login -> LoginScreen(state, viewModel)
                            BjmfScreen.Task -> TaskScreen(state, viewModel)
                            BjmfScreen.Logs -> LogsScreen(state, viewModel)
                            BjmfScreen.Settings -> ManageScreen(
                                state = state,
                                viewModel = viewModel,
                                onAccentTextChange = viewModel::updateAccentColorText,
                                onApplyAccentColor = viewModel::applyAccentColor,
                                onSelectAccentColor = viewModel::selectAccentColor,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                LspBottomNavigationBar(
                    selected = state.screen,
                    onSelect = viewModel::selectScreen,
                    taskBadge = state.tasks.count { it.enabled }.takeIf { it > 0 },
                    logBadge = state.logs.size.takeIf { it > 0 },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun PageHeader(screen: BjmfScreen) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "班级魔方签到",
            style = MiuixTheme.textStyles.title4,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = screenLargeTitle(screen),
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LspBottomNavigationBar(
    selected: BjmfScreen,
    onSelect: (BjmfScreen) -> Unit,
    taskBadge: Int?,
    logBadge: Int?,
    modifier: Modifier = Modifier,
) {
    val screens = remember { BjmfScreen.entries.toList() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.98f))
            .navigationBarsPadding(),
    ) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            screens.forEach { screen ->
                LspBottomItem(
                    screen = screen,
                    selected = screen == selected,
                    badge = when (screen) {
                        BjmfScreen.Task -> taskBadge
                        BjmfScreen.Logs -> logBadge
                        else -> null
                    },
                    onClick = { onSelect(screen) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LspBottomItem(
    screen: BjmfScreen,
    selected: Boolean,
    badge: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
    val indicatorShape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(top = 6.dp, bottom = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.16f), indicatorShape),
                )
            }
            BottomNavIcon(
                screen = screen,
                selected = selected,
                color = contentColor,
                modifier = Modifier.size(24.dp),
            )
            badge?.takeIf { it > 0 }?.let {
                LspNavBadge(
                    count = it,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = screen.title,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LspNavBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(17.dp)
            .widthIn(min = 17.dp)
            .background(MiuixTheme.colorScheme.primary, CircleShape)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = MiuixTheme.colorScheme.onPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun BottomNavIcon(
    screen: BjmfScreen,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round)

        when (screen) {
            BjmfScreen.Login -> {
                val roof = Path().apply {
                    moveTo(w * 0.16f, h * 0.48f)
                    lineTo(w * 0.50f, h * 0.18f)
                    lineTo(w * 0.84f, h * 0.48f)
                }
                drawPath(roof, color = color, style = stroke)
                if (selected) {
                    drawRoundRect(
                        color = color.copy(alpha = 0.22f),
                        topLeft = Offset(w * 0.27f, h * 0.47f),
                        size = Size(w * 0.46f, h * 0.35f),
                        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    )
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.27f, h * 0.45f),
                    size = Size(w * 0.46f, h * 0.38f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    style = stroke,
                )
            }

            BjmfScreen.Task -> {
                if (selected) {
                    drawRoundRect(
                        color = color.copy(alpha = 0.20f),
                        topLeft = Offset(w * 0.20f, h * 0.20f),
                        size = Size(w * 0.60f, h * 0.62f),
                        cornerRadius = CornerRadius(w * 0.11f, w * 0.11f),
                    )
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.20f, h * 0.20f),
                    size = Size(w * 0.60f, h * 0.62f),
                    cornerRadius = CornerRadius(w * 0.11f, w * 0.11f),
                    style = stroke,
                )
                drawLine(color, Offset(w * 0.34f, h * 0.40f), Offset(w * 0.66f, h * 0.40f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.34f, h * 0.58f), Offset(w * 0.58f, h * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }

            BjmfScreen.Logs -> {
                if (selected) {
                    drawRoundRect(
                        color = color.copy(alpha = 0.20f),
                        topLeft = Offset(w * 0.26f, h * 0.15f),
                        size = Size(w * 0.48f, h * 0.70f),
                        cornerRadius = CornerRadius(w * 0.09f, w * 0.09f),
                    )
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.26f, h * 0.15f),
                    size = Size(w * 0.48f, h * 0.70f),
                    cornerRadius = CornerRadius(w * 0.09f, w * 0.09f),
                    style = stroke,
                )
                drawLine(color, Offset(w * 0.38f, h * 0.38f), Offset(w * 0.62f, h * 0.38f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.38f, h * 0.55f), Offset(w * 0.62f, h * 0.55f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.38f, h * 0.72f), Offset(w * 0.55f, h * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }

            BjmfScreen.Settings -> {
                if (selected) {
                    drawCircle(color.copy(alpha = 0.20f), radius = w * 0.34f, center = Offset(w * 0.5f, h * 0.5f))
                }
                repeat(8) { index ->
                    val angle = (PI * 2.0 * index / 8.0).toFloat()
                    val start = Offset(w * (0.5f + cos(angle) * 0.31f), h * (0.5f + sin(angle) * 0.31f))
                    val end = Offset(w * (0.5f + cos(angle) * 0.40f), h * (0.5f + sin(angle) * 0.40f))
                    drawLine(color, start, end, strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
                drawCircle(color = color, radius = w * 0.25f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawCircle(color = color, radius = w * 0.055f, center = Offset(w * 0.5f, h * 0.5f))
            }
        }
    }
}

@Composable
private fun LocalStatusCard(state: BjmfUiState) {
    val enabledCount = state.tasks.count { it.enabled }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(18.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.primary,
            contentColor = MiuixTheme.colorScheme.onPrimary,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (enabledCount > 0) Color.White else MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.45f),
                        CircleShape,
                    ),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "本机任务 $enabledCount/${state.tasks.size}", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "登录、配置、签到、日志和定时任务均在本机完成",
                    color = MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        state.message?.let {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = it,
                color = MiuixTheme.colorScheme.onPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LoginScreen(state: BjmfUiState, viewModel: BjmfViewModel) {
    SectionCard("微信扫码添加账号") {
        Text(
            text = state.qrStatusText,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(16.dp))

        state.qrImageDataUrl?.let {
            QrCodeImage(
                dataUrl = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = viewModel::createQrSession,
                modifier = Modifier.weight(1f),
                enabled = !state.isBusy && !state.isPolling,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(if (state.qrImageDataUrl == null) "获取二维码" else "重新获取")
            }
            TextButton(
                text = "清除",
                onClick = viewModel::clearQrSession,
                enabled = state.qrImageDataUrl != null,
            )
        }
    }

    state.account?.userInfo?.let { UserCard(it) }
}

@Composable
private fun UserCard(user: UserInfo) {
    SectionCard("当前扫码账号") {
        PreferenceRow(title = "姓名", value = user.name)
        ListDivider()
        PreferenceRow(title = "班级", value = user.className)
        ListDivider()
        PreferenceRow(title = "班级 ID", value = user.classId)
        ListDivider()
        PreferenceRow(title = "班级码", value = user.classCode)
    }
}

@Composable
private fun TaskScreen(state: BjmfUiState, viewModel: BjmfViewModel) {
    if (state.tasks.isNotEmpty()) {
        TaskListCard(
            tasks = state.tasks,
            selectedTaskId = state.selectedTaskId,
            onSelect = viewModel::selectTask,
            onToggle = viewModel::toggleTask,
        )
    }

    TaskFormCard(
        form = state.taskForm,
        favoriteLocations = state.favoriteLocations,
        isBusy = state.isBusy,
        hasSelectedTask = state.selectedTaskId != null,
        onFormChange = viewModel::updateTaskForm,
        onUseCurrentLocationAndFavorite = viewModel::useCurrentLocationAndFavorite,
        onFavoriteSelectedLocation = viewModel::favoriteSelectedLocation,
        onUseFavoriteLocation = viewModel::useFavoriteLocation,
        onDeleteFavoriteLocation = viewModel::deleteFavoriteLocation,
        onLocationPermissionDenied = viewModel::locationPermissionDenied,
        onSave = viewModel::saveTask,
        onDelete = viewModel::deleteSelectedTask,
        onRunNow = viewModel::runSelectedNow,
        onNewFromAccount = viewModel::newTaskFromAccount,
    )
}

@Composable
private fun TaskListCard(
    tasks: List<BjmfTask>,
    selectedTaskId: Long?,
    onSelect: (BjmfTask) -> Unit,
    onToggle: (BjmfTask) -> Unit,
) {
    SectionCard("本机任务") {
        tasks.forEachIndexed { index, task ->
            TaskRow(
                task = task,
                selected = task.id == selectedTaskId,
                onSelect = { onSelect(task) },
                onToggle = { onToggle(task) },
            )
            if (index != tasks.lastIndex) {
                ListDivider()
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: BjmfTask,
    selected: Boolean,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
) {
    val dateRange = listOfNotNull(task.dateStart, task.dateEnd)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
    val summary = buildList {
        add(task.times.joinToString(", ").ifBlank { "未设置执行时间" })
        add("${task.lng}, ${task.lat}  acc=${task.acc}")
        if (dateRange.isNotBlank()) add(dateRange)
    }.joinToString("\n")

    PreferenceRow(
        title = "${task.name} / ${task.classId}",
        summary = summary,
        selected = selected,
        indicatorColor = if (task.enabled) Color(0xFF24C96B) else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
        onClick = onSelect,
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(text = "编辑", onClick = onSelect)
                TextButton(text = if (task.enabled) "停用" else "启用", onClick = onToggle)
            }
        },
    )
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Text(title, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun PreferenceRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    selected: Boolean = false,
    indicatorColor: Color? = null,
    onClick: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.background(MiuixTheme.colorScheme.tertiaryContainer, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (selected) 10.dp else 0.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        indicatorColor?.let {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(it, CircleShape),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            summary?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        value?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = it,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        action?.let {
            Spacer(modifier = Modifier.width(8.dp))
            it()
        }
    }
}

@Composable
private fun ListDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 20.dp))
}

@Composable
private fun TaskFormCard(
    form: TaskForm,
    favoriteLocations: List<FavoriteLocation>,
    isBusy: Boolean,
    hasSelectedTask: Boolean,
    onFormChange: (TaskForm) -> Unit,
    onUseCurrentLocationAndFavorite: () -> Unit,
    onFavoriteSelectedLocation: () -> Unit,
    onUseFavoriteLocation: (FavoriteLocation) -> Unit,
    onDeleteFavoriteLocation: (FavoriteLocation) -> Unit,
    onLocationPermissionDenied: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onRunNow: () -> Unit,
    onNewFromAccount: () -> Unit,
) {
    SectionCard(
        title = if (hasSelectedTask) "编辑签到任务" else "新建签到任务",
    ) {
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
        Spacer(modifier = Modifier.height(10.dp))
        MapCoordinatePicker(
            coord = form.coord,
            favoriteLocations = favoriteLocations,
            isBusy = isBusy,
            onCoordinateSelected = { lat, lng ->
                onFormChange(form.copy(coord = "%.6f %.6f".format(Locale.US, lng, lat)))
            },
            onUseCurrentLocationAndFavorite = onUseCurrentLocationAndFavorite,
            onFavoriteSelectedLocation = onFavoriteSelectedLocation,
            onUseFavoriteLocation = onUseFavoriteLocation,
            onDeleteFavoriteLocation = onDeleteFavoriteLocation,
            onLocationPermissionDenied = onLocationPermissionDenied,
        )
        Spacer(modifier = Modifier.height(10.dp))
        TextField(
            value = form.acc,
            onValueChange = { onFormChange(form.copy(acc = it)) },
            label = "定位精度",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        TextField(
            value = form.timesText,
            onValueChange = { onFormChange(form.copy(timesText = it)) },
            label = "执行时间",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = form.dateStart,
                onValueChange = { onFormChange(form.copy(dateStart = it)) },
                label = "开始日期",
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextField(
                value = form.dateEnd,
                onValueChange = { onFormChange(form.copy(dateEnd = it)) },
                label = "结束日期",
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        TextField(
            value = form.cookie,
            onValueChange = { onFormChange(form.copy(cookie = it)) },
            label = "Cookie",
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        TextField(
            value = form.wxKey,
            onValueChange = { onFormChange(form.copy(wxKey = it)) },
            label = "微信通知 Key",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        TextField(
            value = form.qqKey,
            onValueChange = { onFormChange(form.copy(qqKey = it)) },
            label = "Qmsg Key",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onSave,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("保存")
            }
            TextButton(text = "用扫码账号", onClick = onNewFromAccount, enabled = !isBusy)
        }
        if (hasSelectedTask) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRunNow,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("立即签到")
                }
                TextButton(text = "删除任务", onClick = onDelete, enabled = !isBusy)
            }
        }
    }
}

@Composable
private fun MapCoordinatePicker(
    coord: String,
    favoriteLocations: List<FavoriteLocation>,
    isBusy: Boolean,
    onCoordinateSelected: (Double, Double) -> Unit,
    onUseCurrentLocationAndFavorite: () -> Unit,
    onFavoriteSelectedLocation: () -> Unit,
    onUseFavoriteLocation: (FavoriteLocation) -> Unit,
    onDeleteFavoriteLocation: (FavoriteLocation) -> Unit,
    onLocationPermissionDenied: () -> Unit,
) {
    val selected = coord.toLatLngOrNull()
    val initial = selected ?: DEFAULT_MAP_POINT
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) {
            onUseCurrentLocationAndFavorite()
        } else {
            onLocationPermissionDenied()
        }
    }
    val requestCurrentLocation = {
        if (context.hasLocationPermission()) {
            onUseCurrentLocationAndFavorite()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    Text("签到位置", fontWeight = FontWeight.SemiBold)
    Text(
        text = selected?.let { "已选择：经度 ${it.second.toCoordText()}，纬度 ${it.first.toCoordText()}" } ?: "点击地图选择签到点",
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer),
    ) {
        NativeMapPicker(
            initialLat = initial.first,
            initialLng = initial.second,
            hasSelection = selected != null,
            onCoordinateSelected = onCoordinateSelected,
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = requestCurrentLocation,
            enabled = !isBusy,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text("定位并收藏")
        }
        TextButton(
            text = "收藏选点",
            onClick = onFavoriteSelectedLocation,
            enabled = !isBusy && selected != null,
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    if (favoriteLocations.isEmpty()) {
        PreferenceRow(
            title = "位置收藏",
            summary = "可收藏当前位置或地图选点",
        )
    } else {
        Text("位置收藏", fontWeight = FontWeight.SemiBold)
        favoriteLocations.forEachIndexed { index, location ->
            PreferenceRow(
                title = location.name,
                summary = "${location.lng}, ${location.lat}",
                indicatorColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.85f),
                onClick = { onUseFavoriteLocation(location) },
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(text = "选用", onClick = { onUseFavoriteLocation(location) })
                        TextButton(text = "删除", onClick = { onDeleteFavoriteLocation(location) })
                    }
                },
            )
            if (index != favoriteLocations.lastIndex) {
                ListDivider()
            }
        }
    }
}

@Composable
private fun NativeMapPicker(
    initialLat: Double,
    initialLng: Double,
    hasSelection: Boolean,
    onCoordinateSelected: (Double, Double) -> Unit,
) {
    val latestOnSelected by rememberUpdatedState(onCoordinateSelected)

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            AmapTileView(context).apply {
                onPointSelected = { lat, lng -> latestOnSelected(lat, lng) }
                setPoint(initialLat, initialLng, hasSelection)
            }
        },
        update = { mapView ->
            mapView.onPointSelected = { lat, lng -> latestOnSelected(lat, lng) }
            mapView.setPoint(initialLat, initialLng, hasSelection)
        },
    )
}

private class AmapTileView(context: Context) : View(context) {
    var onPointSelected: ((Double, Double) -> Unit)? = null

    private var centerLat = DEFAULT_MAP_POINT.first
    private var centerLng = DEFAULT_MAP_POINT.second
    private var markerLat = DEFAULT_MAP_POINT.first
    private var markerLng = DEFAULT_MAP_POINT.second
    private var hasMarker = false
    private var zoom = 12
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var startCenterX = 0.0
    private var startCenterY = 0.0
    private var scaleAccum = 1f

    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(232, 236, 240)
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(70, 160, 168, 176)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(255, 59, 48)
        style = Paint.Style.FILL
    }
    private val markerInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                scaleAccum = 1f
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleAccum *= detector.scaleFactor
                when {
                    scaleAccum > 1.22f -> {
                        zoom = (zoom + 1).coerceAtMost(MAX_ZOOM)
                        scaleAccum = 1f
                        invalidate()
                    }

                    scaleAccum < 0.82f -> {
                        zoom = (zoom - 1).coerceAtLeast(MIN_ZOOM)
                        scaleAccum = 1f
                        invalidate()
                    }
                }
                return true
            }
        },
    )

    fun setPoint(lat: Double, lng: Double, selected: Boolean) {
        if (abs(centerLat - lat) < 0.000001 && abs(centerLng - lng) < 0.000001 && hasMarker == selected) {
            return
        }
        centerLat = lat.coerceIn(-85.0, 85.0)
        centerLng = lng.coerceIn(-180.0, 180.0)
        markerLat = centerLat
        markerLng = centerLng
        hasMarker = selected
        zoom = if (selected) max(zoom, 16) else zoom
        invalidate()
    }

    override fun onDraw(canvas: AndroidCanvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        drawTiles(canvas)
        drawMarker(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount > 1) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
                val center = project(centerLat, centerLng, zoom)
                startCenterX = center.first
                startCenterY = center.second
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > 4f || abs(dy) > 4f) moved = true
                val next = unproject(startCenterX - dx, startCenterY - dy, zoom)
                centerLat = next.first
                centerLng = next.second
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!moved) {
                    val point = screenToLatLng(event.x, event.y)
                    markerLat = point.first
                    markerLng = point.second
                    hasMarker = true
                    onPointSelected?.invoke(markerLat, markerLng)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    private fun drawTiles(canvas: AndroidCanvas) {
        val center = project(centerLat, centerLng, zoom)
        val topLeftX = center.first - width / 2.0
        val topLeftY = center.second - height / 2.0
        val minX = floor(topLeftX / TILE_SIZE).toInt() - 1
        val maxX = floor((topLeftX + width) / TILE_SIZE).toInt() + 1
        val minY = floor(topLeftY / TILE_SIZE).toInt() - 1
        val maxY = floor((topLeftY + height) / TILE_SIZE).toInt() + 1
        val tileCount = 1 shl zoom

        for (x in minX..maxX) {
            val wrappedX = ((x % tileCount) + tileCount) % tileCount
            for (y in minY..maxY) {
                if (y !in 0 until tileCount) continue
                val left = (x * TILE_SIZE - topLeftX).toFloat()
                val top = (y * TILE_SIZE - topLeftY).toFloat()
                val bitmap = AmapTileCache.get(zoom, wrappedX, y) { postInvalidateOnAnimation() }
                if (bitmap == null) {
                    canvas.drawRect(left, top, left + TILE_SIZE, top + TILE_SIZE, placeholderPaint)
                    canvas.drawRect(left, top, left + TILE_SIZE, top + TILE_SIZE, gridPaint)
                } else {
                    canvas.drawBitmap(bitmap, left, top, tilePaint)
                }
            }
        }
    }

    private fun drawMarker(canvas: AndroidCanvas) {
        if (!hasMarker) return
        val center = project(centerLat, centerLng, zoom)
        val marker = project(markerLat, markerLng, zoom)
        val x = (marker.first - (center.first - width / 2.0)).toFloat()
        val y = (marker.second - (center.second - height / 2.0)).toFloat()
        val radius = 14f
        val path = AndroidPath().apply {
            moveTo(x, y)
            cubicTo(x - radius, y - radius * 0.7f, x - radius, y - radius * 2f, x, y - radius * 2.45f)
            cubicTo(x + radius, y - radius * 2f, x + radius, y - radius * 0.7f, x, y)
            close()
        }
        canvas.drawPath(path, markerPaint)
        canvas.drawOval(RectF(x - 4.8f, y - radius * 1.58f, x + 4.8f, y - radius * 0.9f), markerInnerPaint)
    }

    private fun screenToLatLng(x: Float, y: Float): Pair<Double, Double> {
        val center = project(centerLat, centerLng, zoom)
        return unproject(center.first + x - width / 2.0, center.second + y - height / 2.0, zoom)
    }

    companion object {
        private const val TILE_SIZE = 256
        private const val MIN_ZOOM = 4
        private const val MAX_ZOOM = 18

        fun project(lat: Double, lng: Double, zoom: Int): Pair<Double, Double> {
            val scale = TILE_SIZE * 2.0.pow(zoom)
            val sinLat = sin(lat.coerceIn(-85.0, 85.0) * Math.PI / 180.0)
            val x = (lng + 180.0) / 360.0 * scale
            val y = (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI)) * scale
            return x to y
        }

        fun unproject(x: Double, y: Double, zoom: Int): Pair<Double, Double> {
            val scale = TILE_SIZE * 2.0.pow(zoom)
            val lng = x / scale * 360.0 - 180.0
            val n = Math.PI - 2.0 * Math.PI * y / scale
            val lat = 180.0 / Math.PI * atan(0.5 * (exp(n) - exp(-n)))
            return lat.coerceIn(-85.0, 85.0) to lng.coerceIn(-180.0, 180.0)
        }
    }
}

private object AmapTileCache {
    private const val MAX_CACHE_SIZE = 96
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }
    private val loading = Collections.synchronizedSet(mutableSetOf<String>())
    private val executor = Executors.newFixedThreadPool(4)

    fun get(zoom: Int, x: Int, y: Int, onLoaded: () -> Unit): Bitmap? {
        val key = "$zoom/$x/$y"
        cache.get(key)?.let { return it }
        if (loading.add(key)) {
            executor.execute {
                runCatching {
                    val url = tileUrl(zoom, x, y)
                    val connection = URL(url).openConnection()
                    connection.connectTimeout = 6000
                    connection.readTimeout = 8000
                    connection.setRequestProperty("User-Agent", "BJMFSign/1.0 Android")
                    connection.getInputStream().use { stream ->
                        BitmapFactory.decodeStream(stream)?.let { bitmap -> cache.put(key, bitmap) }
                    }
                }
                loading.remove(key)
                onLoaded()
            }
        }
        return null
    }

    private fun tileUrl(zoom: Int, x: Int, y: Int): String {
        val subdomain = (abs(x + y) % 4) + 1
        return "https://webrd0$subdomain.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x=$x&y=$y&z=$zoom"
    }
}

@Composable
private fun LogsScreen(state: BjmfUiState, viewModel: BjmfViewModel) {
    SectionCard("日志") {
        PreferenceRow(
            title = "本机执行日志",
            summary = "正文完整显示，可长按选择复制",
            value = "${state.logs.size} 条",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        text = "刷新",
                        onClick = viewModel::refreshLocalData,
                        enabled = !state.isBusy,
                    )
                    TextButton(
                        text = "清除",
                        onClick = viewModel::clearLogs,
                        enabled = !state.isBusy && state.logs.isNotEmpty(),
                    )
                }
            },
        )
    }

    if (state.logs.isEmpty()) {
        EmptyHint("暂无执行日志")
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.logs.forEach { LogCard(it) }
        }
    }
}

@Composable
private fun LogCard(log: TaskLog) {
    val statusColor = when (log.status) {
        "success", "already_signed" -> Color(0xFF24C96B)
        "not_started", "no_sign_in", "skip" -> Color(0xFFDA8B00)
        else -> Color(0xFFE5484D)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = statusLabel(log.status),
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatTime(log.runAt),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = log.taskName,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(10.dp))
        SelectionContainer {
            Text(text = log.message)
        }
    }
}

@Composable
private fun ManageScreen(
    state: BjmfUiState,
    viewModel: BjmfViewModel,
    onAccentTextChange: (String) -> Unit,
    onApplyAccentColor: () -> Unit,
    onSelectAccentColor: (Long) -> Unit,
) {
    SectionCard("本机管理") {
        PreferenceRow(title = "任务总数", value = state.tasks.size.toString())
        ListDivider()
        PreferenceRow(title = "启用任务", value = state.tasks.count { it.enabled }.toString())
        ListDivider()
        PreferenceRow(title = "日志数量", value = state.logs.size.toString())
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = viewModel::runAllEnabledNow,
                enabled = !state.isBusy,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("执行全部")
            }
            TextButton(
                text = "重排定时",
                onClick = viewModel::rescheduleAll,
                enabled = !state.isBusy,
            )
        }
    }

    ThemeColorSection(
        accentColor = state.accentColor,
        accentColorText = state.accentColorText,
        onAccentTextChange = onAccentTextChange,
        onApplyAccentColor = onApplyAccentColor,
        onSelectAccentColor = onSelectAccentColor,
    )

    SectionCard("隐私数据") {
        PreferenceRow(
            title = "本机私有存储",
            summary = "任务 Cookie、坐标和通知 Key 均保存在本机 App 私有存储中。卸载 App 会清除这些数据。",
        )
    }
}

@Composable
private fun ThemeColorSection(
    accentColor: Long,
    accentColorText: String,
    onAccentTextChange: (String) -> Unit,
    onApplyAccentColor: () -> Unit,
    onSelectAccentColor: (Long) -> Unit,
) {
    val presets = remember {
        listOf(
            0xFF3482FF,
            0xFF30D158,
            0xFFFF375F,
            0xFFFF9F0A,
            0xFFAF52DE,
            0xFF64D2FF,
            0xFFFFD60A,
            0xFF8E8E93,
        )
    }

    SectionCard("界面取色") {
        PreferenceRow(
            title = "当前主题色",
            summary = accentColor.toHexColorText(),
            indicatorColor = Color(accentColor),
            action = {
                TextButton(text = "应用", onClick = onApplyAccentColor)
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = accentColorText,
            onValueChange = onAccentTextChange,
            label = "HEX 颜色",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        presets.chunked(4).forEachIndexed { rowIndex, rowColors ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowColors.forEach { color ->
                    ColorSwatch(
                        color = color,
                        selected = color == accentColor,
                        onClick = { onSelectAccentColor(color) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (rowIndex != presets.chunked(4).lastIndex) {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Long,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(Color(color), shape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MiuixTheme.colorScheme.primary else Color.White.copy(alpha = 0.55f),
                shape = shape,
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(18.dp),
    ) {
        Text(
            text = text,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(82.dp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QrCodeImage(dataUrl: String, modifier: Modifier = Modifier) {
    val bitmap = remember(dataUrl) { dataUrl.toImageBitmapOrNull() }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            Text("二维码解析失败")
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                    .padding(18.dp),
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "微信登录二维码",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun screenLargeTitle(screen: BjmfScreen): String {
    return when (screen) {
        BjmfScreen.Login -> "扫码登录"
        BjmfScreen.Task -> "签到任务"
        BjmfScreen.Logs -> "执行日志"
        BjmfScreen.Settings -> "本机管理"
    }
}

private fun statusLabel(status: String): String {
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

private fun formatTime(millis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(millis))
}

private val DEFAULT_MAP_POINT = 39.904200 to 116.407400

private fun String.toLatLngOrNull(): Pair<Double, Double>? {
    val parts = replace("，", " ")
        .replace(",", " ")
        .replace("|", " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    if (parts.size < 2) return null
    val first = parts[0].toDoubleOrNull() ?: return null
    val second = parts[1].toDoubleOrNull() ?: return null
    val (lat, lng) = normalizeCoordPair(first, second) ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return lat to lng
}

private fun Double.toCoordText(): String = "%.6f".format(Locale.US, this)

private fun android.content.Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

private fun normalizeCoordPair(first: Double, second: Double): Pair<Double, Double>? {
    return when {
        first in -180.0..180.0 && second in -90.0..90.0 && first !in -90.0..90.0 -> second to first
        first in -90.0..90.0 && second in -180.0..180.0 && second !in -90.0..90.0 -> first to second
        first in -180.0..180.0 && second in -90.0..90.0 -> second to first
        first in -90.0..90.0 && second in -180.0..180.0 -> first to second
        else -> null
    }
}

private fun Long.toHexColorText(): String {
    return "#%06X".format(Locale.US, this and 0xFFFFFF)
}

private fun String.toImageBitmapOrNull(): ImageBitmap? {
    return runCatching {
        val base64 = substringAfter("base64,", this)
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
