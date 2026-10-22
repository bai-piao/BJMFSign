package com.bjmf.sign.android.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bjmf.sign.android.BjmfScreen
import com.bjmf.sign.android.BjmfUiState
import com.bjmf.sign.android.BjmfViewModel
import com.bjmf.sign.android.data.BjmfStore
import com.bjmf.sign.android.data.FavoriteLocation
import com.bjmf.sign.android.data.UserInfo
import com.bjmf.sign.android.ui.glass.Glass
import com.bjmf.sign.android.ui.glass.GlassBackground
import com.bjmf.sign.android.ui.glass.GlassButton
import com.bjmf.sign.android.ui.glass.GlassButtonSize
import com.bjmf.sign.android.ui.glass.GlassButtonStyle
import com.bjmf.sign.android.ui.glass.GlassCard
import com.bjmf.sign.android.ui.glass.GlassDivider
import com.bjmf.sign.android.ui.glass.GlassIcon
import com.bjmf.sign.android.ui.glass.GlassIconButton
import com.bjmf.sign.android.ui.glass.GlassIconGlyph
import com.bjmf.sign.android.ui.glass.GlassSpinner
import com.bjmf.sign.android.ui.glass.GlassTabBar
import com.bjmf.sign.android.ui.glass.GlassTabItem
import com.bjmf.sign.android.ui.glass.GlassTheme
import com.bjmf.sign.android.ui.glass.LabeledField
import com.bjmf.sign.android.ui.glass.SectionHeader
import com.bjmf.sign.android.ui.glass.SegmentedControl
import com.bjmf.sign.android.ui.theme.BjmfMiuixTheme
import com.bjmf.sign.android.ui.theme.isAppInDarkTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.Text

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

    val dark = isAppInDarkTheme(state.themeMode)
    BjmfMiuixTheme(keyColor = Color(state.accentColor), themeMode = state.themeMode) {
        GlassTheme(accent = Color(state.accentColor), dark = dark) {
            val hazeState = rememberHazeState()
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState),
                ) {
                    GlassBackground()
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val screen = screens[page]
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp)
                                .padding(top = 12.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            PageHeader(screen, busy = state.isBusy || state.isPolling)
                            StatusCard(state)

                            when (screen) {
                                BjmfScreen.Login -> LoginScreen(state, viewModel)
                                BjmfScreen.Task -> TaskScreen(state, viewModel)
                                BjmfScreen.Logs -> LogsScreen(state, viewModel)
                                BjmfScreen.Settings -> ManageScreen(state, viewModel)
                            }
                        }
                    }
                }

                GlassTabBar(
                    items = screens.map { screen ->
                        GlassTabItem(
                            title = screen.title,
                            icon = screen.icon(),
                            badge = when (screen) {
                                BjmfScreen.Task -> state.tasks.count { it.enabled }
                                BjmfScreen.Logs -> state.logs.size
                                else -> null
                            },
                        )
                    },
                    selectedIndex = screens.indexOf(state.screen),
                    onSelect = { index -> viewModel.selectScreen(screens[index]) },
                    hazeState = hazeState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

private fun BjmfScreen.icon(): GlassIcon = when (this) {
    BjmfScreen.Login -> GlassIcon.Qr
    BjmfScreen.Task -> GlassIcon.Checklist
    BjmfScreen.Logs -> GlassIcon.Doc
    BjmfScreen.Settings -> GlassIcon.Gear
}

private fun BjmfScreen.largeTitle(): String = when (this) {
    BjmfScreen.Login -> "扫码登录"
    BjmfScreen.Task -> "签到任务"
    BjmfScreen.Logs -> "执行日志"
    BjmfScreen.Settings -> "本机管理"
}

/** 大标题 + 副标题（对应 iOS 的 navigationTitle / navigationSubtitle）。 */
@Composable
private fun PageHeader(screen: BjmfScreen, busy: Boolean) {
    val palette = Glass.palette
    Row(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = screen.largeTitle(),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = palette.label,
            )
            Text(text = "班级魔方签到", fontSize = 15.sp, color = palette.secondaryLabel)
        }
        if (busy) GlassSpinner()
    }
}

/** 顶部状态卡：本机任务数量与最近一条提示（主题色着色玻璃）。 */
@Composable
private fun StatusCard(state: BjmfUiState) {
    val enabledCount = state.tasks.count { it.enabled }
    GlassCard(
        tint = Glass.accent.copy(alpha = 0.75f),
        modifier = Modifier.animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(if (enabledCount > 0) Color.White else Color.White.copy(alpha = 0.45f), CircleShape),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "本机任务 $enabledCount/${state.tasks.size}",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "登录、配置、签到、日志和定时任务均在本机完成",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        state.message?.let {
            Text(text = it, color = Color.White, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

// region 登录

@Composable
private fun LoginScreen(state: BjmfUiState, viewModel: BjmfViewModel) {
    val palette = Glass.palette
    GlassCard(title = "微信扫码添加账号", modifier = Modifier.animateContentSize()) {
        Text(text = state.qrStatusText, color = palette.secondaryLabel, fontSize = 15.sp)

        state.qrImageDataUrl?.let { dataUrl ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                QrCodeImage(
                    dataUrl = dataUrl,
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
            }
            Text(
                text = "可截图后在微信「扫一扫 → 相册」中识别，或用另一台设备的微信扫描",
                color = palette.secondaryLabel,
                fontSize = 13.sp,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                text = if (state.qrImageDataUrl == null) "获取二维码" else "重新获取",
                onClick = viewModel::createQrSession,
                style = GlassButtonStyle.Prominent,
                size = GlassButtonSize.Large,
                icon = GlassIcon.Qr,
                enabled = !state.isBusy && !state.isPolling,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                text = "清除",
                onClick = viewModel::clearQrSession,
                style = GlassButtonStyle.Destructive,
                size = GlassButtonSize.Large,
                enabled = state.qrImageDataUrl != null,
            )
        }
    }

    state.account?.userInfo?.let { UserCard(it) }
}

@Composable
private fun UserCard(user: UserInfo) {
    GlassCard(title = "当前扫码账号") {
        Column {
            PreferenceRow(title = "姓名", value = user.name)
            ListDivider()
            PreferenceRow(title = "班级", value = user.className)
            ListDivider()
            PreferenceRow(title = "班级 ID", value = user.classId)
            ListDivider()
            PreferenceRow(title = "班级码", value = user.classCode)
        }
    }
}

@Composable
private fun QrCodeImage(dataUrl: String, modifier: Modifier = Modifier) {
    val bitmap = remember(dataUrl) { dataUrl.toImageBitmapOrNull() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            Text("二维码解析失败", color = Color.Black)
        } else {
            Image(
                bitmap = bitmap,
                contentDescription = "微信登录二维码",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// endregion

// region 通用行

/** 列表行（对应 iOS 的 PreferenceRow）。 */
@Composable
internal fun PreferenceRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    selected: Boolean = false,
    indicatorColor: Color? = null,
    onClick: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    val palette = Glass.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (selected) Modifier.background(Glass.accent.copy(alpha = 0.14f)) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (selected) 12.dp else 0.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        indicatorColor?.let {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(it, CircleShape),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = palette.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            summary?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = it,
                    color = palette.secondaryLabel,
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        value?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = it,
                color = palette.secondaryLabel,
                fontSize = 15.sp,
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
internal fun ListDivider() {
    GlassDivider()
}

@Composable
internal fun EmptyHint(text: String) {
    GlassCard {
        Text(text = text, color = Glass.palette.secondaryLabel, fontSize = 15.sp)
    }
}

// endregion

// region 地图选点

@Composable
internal fun MapCoordinatePicker(
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
    val palette = Glass.palette
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("签到位置")
        Text(
            text = selected?.let { "已选择：经度 ${it.second.toCoordText()}，纬度 ${it.first.toCoordText()}" } ?: "点击地图选择签到点",
            color = palette.secondaryLabel,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, palette.separator, RoundedCornerShape(20.dp))
                .background(palette.fill),
        ) {
            NativeMapPicker(
                initialLat = initial.first,
                initialLng = initial.second,
                hasSelection = selected != null,
                onCoordinateSelected = onCoordinateSelected,
            )
            GlassIconButton(
                icon = GlassIcon.Location,
                onClick = requestCurrentLocation,
                enabled = !isBusy,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                text = "定位并收藏",
                onClick = requestCurrentLocation,
                style = GlassButtonStyle.Prominent,
                icon = GlassIcon.Location,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                text = "收藏选点",
                onClick = onFavoriteSelectedLocation,
                icon = GlassIcon.Star,
                enabled = !isBusy && selected != null,
            )
        }

        if (favoriteLocations.isEmpty()) {
            PreferenceRow(title = "位置收藏", summary = "可收藏当前位置或地图选点")
        } else {
            Text(text = "位置收藏", color = palette.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Column {
                favoriteLocations.forEachIndexed { index, location ->
                    PreferenceRow(
                        title = location.name,
                        summary = "${location.lng}, ${location.lat}",
                        indicatorColor = Glass.accent.copy(alpha = 0.85f),
                        onClick = { onUseFavoriteLocation(location) },
                        action = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                GlassButton(
                                    text = "选用",
                                    onClick = { onUseFavoriteLocation(location) },
                                    size = GlassButtonSize.Small,
                                )
                                GlassButton(
                                    text = "删除",
                                    onClick = { onDeleteFavoriteLocation(location) },
                                    style = GlassButtonStyle.Destructive,
                                    size = GlassButtonSize.Small,
                                )
                            }
                        },
                    )
                    if (index != favoriteLocations.lastIndex) ListDivider()
                }
            }
        }
    }
}

// endregion

// region 管理

@Composable
private fun ManageScreen(state: BjmfUiState, viewModel: BjmfViewModel) {
    GlassCard(title = "本机管理") {
        Column {
            PreferenceRow(title = "任务总数", value = state.tasks.size.toString())
            ListDivider()
            PreferenceRow(title = "启用任务", value = state.tasks.count { it.enabled }.toString())
            ListDivider()
            PreferenceRow(title = "日志数量", value = state.logs.size.toString())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                text = "执行全部",
                onClick = viewModel::runAllEnabledNow,
                style = GlassButtonStyle.Prominent,
                size = GlassButtonSize.Large,
                icon = GlassIcon.Play,
                enabled = !state.isBusy,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                text = "重排定时",
                onClick = viewModel::rescheduleAll,
                size = GlassButtonSize.Large,
                icon = GlassIcon.Refresh,
                enabled = !state.isBusy,
            )
        }
    }

    AppearanceSection(themeMode = state.themeMode, onSelect = viewModel::setThemeMode)

    ThemeColorSection(
        accentColor = state.accentColor,
        accentColorText = state.accentColorText,
        onAccentTextChange = viewModel::updateAccentColorText,
        onApplyAccentColor = viewModel::applyAccentColor,
        onSelectAccentColor = viewModel::selectAccentColor,
    )

    GlassCard(title = "定时说明") {
        PreferenceRow(
            title = "Android 定时签到",
            summary = "使用系统闹钟在设定时间后台自动执行。Android 12+ 请允许本 App 的「闹钟和提醒」权限，并关闭电池优化，以保证准时执行。",
        )
    }

    GlassCard(title = "隐私数据") {
        PreferenceRow(
            title = "本机私有存储",
            summary = "任务 Cookie、坐标和通知 Key 均保存在本机 App 私有存储中。卸载 App 会清除这些数据。",
        )
    }
}

@Composable
private fun AppearanceSection(themeMode: String, onSelect: (String) -> Unit) {
    val modes = listOf(BjmfStore.THEME_SYSTEM, BjmfStore.THEME_LIGHT, BjmfStore.THEME_DARK)
    GlassCard(title = "外观") {
        Text(
            text = "深色模式可跟随系统，或固定为浅色 / 深色",
            color = Glass.palette.secondaryLabel,
            fontSize = 15.sp,
        )
        SegmentedControl(
            options = listOf("跟随系统", "浅色", "深色"),
            selectedIndex = modes.indexOf(themeMode).coerceAtLeast(0),
            onSelect = { index -> onSelect(modes[index]) },
        )
    }
}

private val PresetColors = listOf(
    0xFF3482FF, 0xFF30D158, 0xFFFF375F, 0xFFFF9F0A,
    0xFFAF52DE, 0xFF64D2FF, 0xFFFFD60A, 0xFF8E8E93,
)

@Composable
private fun ThemeColorSection(
    accentColor: Long,
    accentColorText: String,
    onAccentTextChange: (String) -> Unit,
    onApplyAccentColor: () -> Unit,
    onSelectAccentColor: (Long) -> Unit,
) {
    GlassCard(title = "界面取色") {
        PreferenceRow(
            title = "当前主题色",
            summary = accentColor.toHexColorText(),
            indicatorColor = Color(accentColor),
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledField(
                label = "HEX 颜色",
                value = accentColorText,
                onValueChange = onAccentTextChange,
                placeholder = "#3482FF",
                keyboardType = KeyboardType.Ascii,
                modifier = Modifier.weight(1f),
            )
            GlassButton(text = "应用", onClick = onApplyAccentColor)
        }
        PresetColors.chunked(4).forEach { rowColors ->
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
        }
    }
}

@Composable
private fun ColorSwatch(color: Long, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(Color(color))
            .border(1.dp, Color.White.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            GlassIconGlyph(GlassIcon.Check, color = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

// endregion
