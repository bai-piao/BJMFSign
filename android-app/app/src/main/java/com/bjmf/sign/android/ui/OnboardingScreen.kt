package com.bjmf.sign.android.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bjmf.sign.android.BjmfUiState
import com.bjmf.sign.android.BjmfViewModel
import com.bjmf.sign.android.data.SignNotifier
import com.bjmf.sign.android.data.tr
import com.bjmf.sign.android.ui.glass.Glass
import com.bjmf.sign.android.ui.glass.GlassBackground
import com.bjmf.sign.android.ui.glass.GlassButton
import com.bjmf.sign.android.ui.glass.GlassButtonSize
import com.bjmf.sign.android.ui.glass.GlassButtonStyle
import com.bjmf.sign.android.ui.glass.GlassCard
import com.bjmf.sign.android.ui.glass.GlassIcon
import com.bjmf.sign.android.ui.glass.GlassIconGlyph
import top.yukonga.miuix.kmp.basic.Text

private const val STEP_COUNT = 3

/** 首次使用引导：扫码登录 → 通知权限 → 电池优化与精确闹钟。 */
@Composable
internal fun OnboardingScreen(state: BjmfUiState, viewModel: BjmfViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(PermissionStatus.read(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissions = PermissionStatus.read(context) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissions = PermissionStatus.read(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GlassBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // 顶部：进度 + 跳过
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tr("第 {0} 步，共 {1} 步", step + 1, STEP_COUNT),
                    color = Glass.palette.secondaryLabel,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    text = tr("跳过"),
                    onClick = viewModel::completeOnboarding,
                    size = GlassButtonSize.Small,
                )
            }
            StepProgress(current = step, total = STEP_COUNT)

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn())
                        .togetherWith(slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "onboardingStep",
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (current) {
                        0 -> LoginStep(state, viewModel)
                        1 -> NotificationStep(
                            granted = permissions.notifications,
                            onRequest = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    context.openAppNotificationSettings()
                                }
                            },
                        )
                        else -> BackgroundStep(
                            batteryIgnored = permissions.batteryIgnored,
                            exactAlarm = permissions.exactAlarm,
                            onBattery = { context.requestIgnoreBatteryOptimizations() },
                            onExactAlarm = { context.requestExactAlarm() },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) {
                    GlassButton(
                        text = tr("上一步"),
                        onClick = { step -= 1 },
                        size = GlassButtonSize.Large,
                    )
                }
                GlassButton(
                    text = if (step == STEP_COUNT - 1) tr("完成") else tr("下一步"),
                    onClick = { if (step == STEP_COUNT - 1) viewModel.completeOnboarding() else step += 1 },
                    style = GlassButtonStyle.Prominent,
                    size = GlassButtonSize.Large,
                    icon = if (step == STEP_COUNT - 1) GlassIcon.Check else null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 分段进度条：已完成和当前步骤为主题色。 */
@Composable
private fun StepProgress(current: Int, total: Int) {
    val accent = Glass.accent
    val palette = Glass.palette
    val description = tr("第 {0} 步，共 {1} 步", current + 1, total)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { index ->
            val color by animateColorAsState(if (index <= current) accent else palette.fill, label = "stepColor")
            val weight by animateFloatAsState(if (index == current) 2f else 1f, label = "stepWeight")
            Box(
                modifier = Modifier
                    .weight(weight)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}

@Composable
private fun StepHeader(icon: GlassIcon, title: String, summary: String) {
    val accent = Glass.accent
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            GlassIconGlyph(icon = icon, color = accent, modifier = Modifier.size(32.dp))
        }
        Text(
            text = title,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Glass.palette.label,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = summary, fontSize = 16.sp, color = Glass.palette.secondaryLabel)
    }
}

@Composable
private fun LoginStep(state: BjmfUiState, viewModel: BjmfViewModel) {
    StepHeader(
        icon = GlassIcon.Qr,
        title = tr("扫码登录"),
        summary = tr("用微信扫描二维码登录班级魔方，App 会自动获取账号和班级信息。也可以稍后在「登录」页完成。"),
    )
    val account = state.account
    if (account != null) {
        StatusLine(done = true, text = tr("{0} 已登录", account.userInfo.name))
        return
    }
    GlassCard {
        Text(text = state.qrStatusText, color = Glass.palette.secondaryLabel, fontSize = 15.sp)
        state.qrImageDataUrl?.let { dataUrl ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                QrCodeImage(
                    dataUrl = dataUrl,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
            }
        }
        GlassButton(
            text = if (state.qrImageDataUrl == null) tr("获取二维码") else tr("重新获取"),
            onClick = viewModel::createQrSession,
            style = GlassButtonStyle.Prominent,
            icon = GlassIcon.Qr,
            enabled = !state.isBusy && !state.isPolling,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NotificationStep(granted: Boolean, onRequest: () -> Unit) {
    StepHeader(
        icon = GlassIcon.Bell,
        title = tr("通知权限"),
        summary = tr("后台自动签到完成后，会用通知告诉你结果；失败时会说明原因。"),
    )
    if (granted) {
        StatusLine(done = true, text = tr("已允许通知"))
    } else {
        StatusLine(done = false, text = tr("尚未允许通知"))
        GlassButton(
            text = tr("允许通知"),
            onClick = onRequest,
            style = GlassButtonStyle.Prominent,
            size = GlassButtonSize.Large,
            icon = GlassIcon.Bell,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BackgroundStep(
    batteryIgnored: Boolean,
    exactAlarm: Boolean,
    onBattery: () -> Unit,
    onExactAlarm: () -> Unit,
) {
    StepHeader(
        icon = GlassIcon.Battery,
        title = tr("电池优化"),
        summary = tr("系统的省电策略可能推迟或取消定时签到。关闭电池优化、允许精确闹钟后，签到会更准时。"),
    )
    GlassCard {
        PermissionRow(
            title = tr("不受电池优化限制"),
            summary = tr("允许 App 在后台准时运行"),
            granted = batteryIgnored,
            onRequest = onBattery,
        )
        ListDivider()
        PermissionRow(
            title = tr("闹钟和提醒"),
            summary = tr("Android 12+ 需要此权限才能精确定时"),
            granted = exactAlarm,
            onRequest = onExactAlarm,
        )
    }
}

@Composable
private fun PermissionRow(title: String, summary: String, granted: Boolean, onRequest: () -> Unit) {
    PreferenceRow(
        title = title,
        summary = summary,
        action = {
            if (granted) {
                StatusDot(done = true)
            } else {
                GlassButton(text = tr("去设置"), onClick = onRequest, size = GlassButtonSize.Small)
            }
        },
    )
}

@Composable
private fun StatusLine(done: Boolean, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background((if (done) Glass.palette.green else Glass.palette.orange).copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(done)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, color = Glass.palette.label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusDot(done: Boolean) {
    val color = if (done) Glass.palette.green else Glass.palette.orange
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        GlassIconGlyph(
            icon = if (done) GlassIcon.Check else GlassIcon.Clock,
            color = Color.White,
            modifier = Modifier.size(14.dp),
        )
    }
}

// region 权限读取与跳转

internal data class PermissionStatus(
    val notifications: Boolean,
    val batteryIgnored: Boolean,
    val exactAlarm: Boolean,
) {
    companion object {
        fun read(context: Context): PermissionStatus {
            val power = context.getSystemService(PowerManager::class.java)
            val alarm = context.getSystemService(AlarmManager::class.java)
            return PermissionStatus(
                notifications = SignNotifier.canNotify(context),
                batteryIgnored = power?.isIgnoringBatteryOptimizations(context.packageName) ?: true,
                exactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarm?.canScheduleExactAlarms() ?: true else true,
            )
        }
    }
}

@SuppressLint("BatteryLife")
internal fun Context.requestIgnoreBatteryOptimizations() {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    startFirstAvailable(direct, fallback)
}

internal fun Context.requestExactAlarm() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val direct = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
    startFirstAvailable(direct, appDetailsIntent())
}

internal fun Context.openAppNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    startFirstAvailable(intent, appDetailsIntent())
}

private fun Context.appDetailsIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

private fun Context.startFirstAvailable(vararg intents: Intent) {
    for (intent in intents) {
        val launched = runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (launched) return
    }
}

// endregion
