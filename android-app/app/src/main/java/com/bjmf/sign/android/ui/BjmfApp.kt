package com.bjmf.sign.android.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.bjmf.sign.android.data.I18n
import com.bjmf.sign.android.data.SIGN_ZONE
import com.bjmf.sign.android.ui.glass.GlassSwitch
import java.time.LocalDate
import top.yukonga.miuix.kmp.window.WindowDialog
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
import com.bjmf.sign.android.data.tr
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
    val context = LocalContext.current
    // 跟随壁纸取色（Monet）：Android 12+ 使用系统动态强调色。
    val accent = if (state.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color(context.getColor(android.R.color.system_accent1_500))
    } else {
        Color(state.accentColor)
    }
    BjmfMiuixTheme(keyColor = accent, themeMode = state.themeMode) {
        GlassTheme(accent = accent, dark = dark) {
            if (state.showOnboarding) {
                OnboardingScreen(state, viewModel)
                return@GlassTheme
            }
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
                            title = tr(screen.title),
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
    BjmfScreen.Login -> tr("扫码登录")
    BjmfScreen.Task -> tr("签到任务")
    BjmfScreen.Logs -> tr("执行日志")
    BjmfScreen.Settings -> tr("本机管理")
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
            Text(text = tr("班级魔方签到"), fontSize = 15.sp, color = palette.secondaryLabel)
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
                    text = tr("本机任务 {0}/{1}", enabledCount, state.tasks.size),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = tr("登录、配置、签到、日志和定时任务均在本机完成"),
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
    GlassCard(title = tr("微信扫码添加账号"), modifier = Modifier.animateContentSize()) {
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
                text = tr("可截图后在微信「扫一扫 → 相册」中识别，或用另一台设备的微信扫描"),
                color = palette.secondaryLabel,
                fontSize = 13.sp,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                text = if (state.qrImageDataUrl == null) tr("获取二维码") else tr("重新获取"),
                onClick = viewModel::createQrSession,
                style = GlassButtonStyle.Prominent,
                size = GlassButtonSize.Large,
                icon = GlassIcon.Qr,
                enabled = !state.isBusy && !state.isPolling,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                text = tr("清除"),
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
    GlassCard(title = tr("当前扫码账号")) {
        Column {
            PreferenceRow(title = tr("姓名"), value = user.name)
            ListDivider()
            PreferenceRow(title = tr("班级"), value = user.className)
            ListDivider()
            PreferenceRow(title = tr("班级 ID"), value = user.classId)
            ListDivider()
            PreferenceRow(title = tr("班级码"), value = user.classCode)
        }
    }
}

@Composable
internal fun QrCodeImage(dataUrl: String, modifier: Modifier = Modifier) {
    val bitmap = remember(dataUrl) { dataUrl.toImageBitmapOrNull() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            Text(tr("二维码解析失败"), color = Color.Black)
        } else {
            Image(
                bitmap = bitmap,
                contentDescription = tr("微信登录二维码"),
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
        SectionHeader(tr("签到位置"))
        Text(
            text = selected?.let { tr("已选择：经度 {0}，纬度 {1}", it.second.toCoordText(), it.first.toCoordText()) } ?: tr("点击地图选择签到点"),
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
                contentDescription = tr("地图选点，点按地图设置签到位置"),
                initialLat = initial.first,
                initialLng = initial.second,
                hasSelection = selected != null,
                onCoordinateSelected = onCoordinateSelected,
            )
            GlassIconButton(
                icon = GlassIcon.Location,
                onClick = requestCurrentLocation,
                contentDescription = tr("定位并收藏"),
                enabled = !isBusy,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                text = tr("定位并收藏"),
                onClick = requestCurrentLocation,
                style = GlassButtonStyle.Prominent,
                icon = GlassIcon.Location,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                text = tr("收藏选点"),
                onClick = onFavoriteSelectedLocation,
                icon = GlassIcon.Star,
                enabled = !isBusy && selected != null,
            )
        }

        if (favoriteLocations.isEmpty()) {
            PreferenceRow(title = tr("位置收藏"), summary = tr("可收藏当前位置或地图选点"))
        } else {
            Text(text = tr("位置收藏"), color = palette.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                                    text = tr("选用"),
                                    onClick = { onUseFavoriteLocation(location) },
                                    size = GlassButtonSize.Small,
                                )
                                GlassButton(
                                    text = tr("删除"),
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
    GlassCard(title = tr("本机管理")) {
        Column {
            PreferenceRow(title = tr("任务总数"), value = state.tasks.size.toString())
            ListDivider()
            PreferenceRow(title = tr("启用任务"), value = state.tasks.count { it.enabled }.toString())
            ListDivider()
            PreferenceRow(title = tr("日志数量"), value = state.logs.size.toString())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                text = tr("执行全部"),
                onClick = viewModel::runAllEnabledNow,
                style = GlassButtonStyle.Prominent,
                size = GlassButtonSize.Large,
                icon = GlassIcon.Play,
                enabled = !state.isBusy,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                text = tr("重排定时"),
                onClick = viewModel::rescheduleAll,
                size = GlassButtonSize.Large,
                icon = GlassIcon.Refresh,
                enabled = !state.isBusy,
            )
        }
    }

    AppearanceSection(themeMode = state.themeMode, onSelect = viewModel::setThemeMode)

    LanguageSection(language = state.language, onSelect = viewModel::setLanguage)

    ThemeColorSection(
        accentColor = state.accentColor,
        accentColorText = state.accentColorText,
        dynamicColor = state.dynamicColor,
        onDynamicColorChange = viewModel::setDynamicColor,
        onAccentTextChange = viewModel::updateAccentColorText,
        onApplyAccentColor = viewModel::applyAccentColor,
        onSelectAccentColor = viewModel::selectAccentColor,
    )

    BackupSection(isBusy = state.isBusy, viewModel = viewModel)

    GlassCard(title = tr("使用引导")) {
        PreferenceRow(
            title = tr("重新查看引导"),
            summary = tr("再次完成扫码登录、通知权限和电池优化设置"),
            action = {
                GlassButton(text = tr("打开"), onClick = viewModel::reopenOnboarding, size = GlassButtonSize.Small)
            },
        )
    }

    GlassCard(title = tr("定时说明")) {
        PreferenceRow(
            title = tr("Android 定时签到"),
            summary = tr("使用系统闹钟在设定时间后台自动执行。Android 12+ 请允许本 App 的「闹钟和提醒」权限，并关闭电池优化，以保证准时执行。"),
        )
    }

    GlassCard(title = tr("隐私数据")) {
        PreferenceRow(
            title = tr("本机私有存储"),
            summary = tr("任务 Cookie、坐标和通知 Key 均保存在本机 App 私有存储中。卸载 App 会清除这些数据。"),
        )
    }
}

@Composable
private fun AppearanceSection(themeMode: String, onSelect: (String) -> Unit) {
    val modes = listOf(BjmfStore.THEME_SYSTEM, BjmfStore.THEME_LIGHT, BjmfStore.THEME_DARK)
    GlassCard(title = tr("外观")) {
        Text(
            text = tr("深色模式可跟随系统，或固定为浅色 / 深色"),
            color = Glass.palette.secondaryLabel,
            fontSize = 15.sp,
        )
        SegmentedControl(
            options = listOf("跟随系统", tr("浅色"), tr("深色")),
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
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    onAccentTextChange: (String) -> Unit,
    onApplyAccentColor: () -> Unit,
    onSelectAccentColor: (Long) -> Unit,
) {
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    GlassCard(title = tr("界面取色")) {
        PreferenceRow(
            title = tr("跟随壁纸取色"),
            summary = if (dynamicSupported) tr("使用系统 Monet 动态取色，主题色随壁纸变化") else tr("需要 Android 12 及以上"),
            action = {
                GlassSwitch(
                    checked = dynamicColor && dynamicSupported,
                    onCheckedChange = onDynamicColorChange,
                    enabled = dynamicSupported,
                    contentDescription = tr("跟随壁纸取色"),
                )
            },
        )
        ListDivider()
        PreferenceRow(
            title = tr("当前主题色"),
            summary = if (dynamicColor && dynamicSupported) tr("正在跟随壁纸") else accentColor.toHexColorText(),
            indicatorColor = Glass.accent,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledField(
                label = tr("HEX 颜色"),
                value = accentColorText,
                onValueChange = onAccentTextChange,
                placeholder = "#3482FF",
                keyboardType = KeyboardType.Ascii,
                modifier = Modifier.weight(1f),
            )
            GlassButton(text = tr("应用"), onClick = onApplyAccentColor)
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
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = tr("主题色 {0}", color.toHexColorText())
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            GlassIconGlyph(GlassIcon.Check, color = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

// endregion

@Composable
private fun LanguageSection(language: String, onSelect: (String) -> Unit) {
    val options = listOf(I18n.SYSTEM, I18n.ZH_HANS, I18n.ZH_HANT, I18n.EN)
    GlassCard(title = tr("语言")) {
        Text(
            text = tr("界面语言，跟随系统时按系统语言显示"),
            color = Glass.palette.secondaryLabel,
            fontSize = 15.sp,
        )
        SegmentedControl(
            options = listOf(tr("跟随系统"), "简体", "繁體", "English"),
            selectedIndex = options.indexOf(language).coerceAtLeast(0),
            onSelect = { index -> onSelect(options[index]) },
        )
    }
}

@Composable
private fun BackupSection(isBusy: Boolean, viewModel: BjmfViewModel) {
    var dialog by remember { mutableStateOf<String?>(null) }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = pendingExportPassword
        pendingExportPassword = null
        if (uri != null && password != null) viewModel.exportBackup(uri, password)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            dialog = "import"
        }
    }

    GlassCard(title = tr("备份与迁移")) {
        Text(
            text = tr("把任务、位置收藏和界面设置导出为加密文件，换手机时直接导入。Android 与 iOS 通用。"),
            color = Glass.palette.secondaryLabel,
            fontSize = 15.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                text = tr("导出备份"),
                onClick = { dialog = "export" },
                style = GlassButtonStyle.Prominent,
                icon = GlassIcon.Upload,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                text = tr("导入备份"),
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                icon = GlassIcon.Download,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
            )
        }
    }

    PasswordDialog(
        show = dialog == "export",
        title = tr("设置备份密码"),
        summary = tr("导入时需要输入此密码，请牢记。至少 6 位。"),
        requireConfirm = true,
        onDismiss = { dialog = null },
        onConfirm = { password ->
            dialog = null
            pendingExportPassword = password
            val date = LocalDate.now(SIGN_ZONE).toString().replace("-", "")
            exportLauncher.launch("bjmfsign-backup-$date.bjmf")
        },
    )
    PasswordDialog(
        show = dialog == "import",
        title = tr("输入备份密码"),
        summary = tr("导入会替换本机现有的任务和位置收藏。"),
        requireConfirm = false,
        onDismiss = {
            dialog = null
            pendingImportUri = null
        },
        onConfirm = { password ->
            dialog = null
            pendingImportUri?.let { viewModel.importBackup(it, password) }
            pendingImportUri = null
        },
    )
}

@Composable
private fun PasswordDialog(
    show: Boolean,
    title: String,
    summary: String,
    requireConfirm: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember(show) { mutableStateOf("") }
    var confirm by remember(show) { mutableStateOf("") }
    var error by remember(show) { mutableStateOf<String?>(null) }
    WindowDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledField(label = tr("密码"), value = password, onValueChange = { password = it }, password = true)
            if (requireConfirm) {
                LabeledField(label = tr("再次输入密码"), value = confirm, onValueChange = { confirm = it }, password = true)
            }
            error?.let { Text(text = it, color = Glass.palette.red, fontSize = 14.sp) }
            DialogButtons(
                confirmText = tr("确定"),
                onDismiss = onDismiss,
                onConfirm = {
                    error = when {
                        requireConfirm && password.length < 6 -> tr("密码至少 6 位")
                        requireConfirm && password != confirm -> tr("两次输入的密码不一致")
                        password.isEmpty() -> tr("请输入密码")
                        else -> null
                    }
                    if (error == null) onConfirm(password)
                },
            )
        }
    }
}
