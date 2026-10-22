package com.bjmf.sign.android.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import top.yukonga.miuix.kmp.basic.Text

// region 主题

/** 与 iOS 系统色一致的一组语义颜色。 */
@Immutable
data class GlassPalette(
    val background: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fill: Color,
    val switchOff: Color,
    val glassFill: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val glassHighlight: Color,
    val red: Color,
    val green: Color,
    val orange: Color,
    val segmentThumb: Color,
)

val LightGlassPalette = GlassPalette(
    background = Color(0xFFF2F2F7),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    fill = Color(0x1F767680),
    switchOff = Color(0x29787880),
    glassFill = Color(0x99FFFFFF),
    glassBorderTop = Color(0xE6FFFFFF),
    glassBorderBottom = Color(0x40FFFFFF),
    glassHighlight = Color(0x40FFFFFF),
    red = Color(0xFFFF3B30),
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    segmentThumb = Color(0xFFFFFFFF),
)

val DarkGlassPalette = GlassPalette(
    background = Color(0xFF000000),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0x99545458),
    fill = Color(0x3D767680),
    switchOff = Color(0x52787880),
    glassFill = Color(0x1FFFFFFF),
    glassBorderTop = Color(0x47FFFFFF),
    glassBorderBottom = Color(0x0FFFFFFF),
    glassHighlight = Color(0x14FFFFFF),
    red = Color(0xFFFF453A),
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9F0A),
    segmentThumb = Color(0xFF636366),
)

val LocalGlassAccent = staticCompositionLocalOf { Color(0xFF3482FF) }
val LocalGlassDark = staticCompositionLocalOf { false }
val LocalGlassPalette = staticCompositionLocalOf { LightGlassPalette }

@Composable
fun GlassTheme(accent: Color, dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalGlassAccent provides accent,
        LocalGlassDark provides dark,
        LocalGlassPalette provides if (dark) DarkGlassPalette else LightGlassPalette,
        content = content,
    )
}

object Glass {
    val accent: Color @Composable get() = LocalGlassAccent.current
    val palette: GlassPalette @Composable get() = LocalGlassPalette.current
}

// endregion

// region 背景与玻璃表面

/** 以主题色生成的柔和网格渐变背景（对应 iOS 的 MeshGradient）。 */
@Composable
fun GlassBackground(modifier: Modifier = Modifier) {
    val accent = Glass.accent
    val palette = Glass.palette
    val k = if (LocalGlassDark.current) 0.6f else 1f
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(palette.background)
        val w = size.width
        val h = size.height
        val d = maxOf(w, h)
        blob(Offset(0f, 0f), d * 0.75f, accent.copy(alpha = 0.55f * k))
        blob(Offset(w * 0.5f, 0f), d * 0.55f, accent.copy(alpha = 0.25f * k))
        blob(Offset(w, 0f), d * 0.6f, Color(0xFFAF52DE).copy(alpha = 0.25f * k))
        blob(Offset(w * 0.6f, h * 0.45f), d * 0.5f, accent.copy(alpha = 0.18f * k))
        blob(Offset(w, h * 0.5f), d * 0.45f, Color(0xFF32ADE6).copy(alpha = 0.2f * k))
        blob(Offset(w, h), d * 0.6f, accent.copy(alpha = 0.22f * k))
    }
}

private fun DrawScope.blob(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = radius),
        radius = radius,
        center = center,
    )
}

/** 液态玻璃表面：半透明填充 + 顶部高光 + 渐变描边，可叠加着色。 */
@Composable
fun Modifier.glassSurface(shape: Shape, tint: Color? = null): Modifier {
    val palette = Glass.palette
    val fill = palette.glassFill
    val highlight = Brush.verticalGradient(
        0f to palette.glassHighlight,
        0.5f to Color.Transparent,
    )
    val border = Brush.verticalGradient(listOf(palette.glassBorderTop, palette.glassBorderBottom))
    return this
        .clip(shape)
        .background(fill)
        .then(if (tint != null) Modifier.background(tint) else Modifier)
        .background(highlight)
        .border(1.dp, border, shape)
}

private val CardShape = RoundedCornerShape(26.dp)

/** 液态玻璃卡片（对应 iOS 的 GlassCard）。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    tint: Color? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(CardShape, tint)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null || action != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (title != null) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Glass.palette.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (action != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = action,
                    )
                }
            }
        }
        content()
    }
}

// endregion

// region 按钮

enum class GlassButtonStyle { Glass, Prominent, Destructive }

enum class GlassButtonSize(val height: Dp, val horizontalPadding: Dp, val fontSize: TextUnit, val iconSize: Dp) {
    Small(34.dp, 14.dp, 14.sp, 14.dp),
    Regular(44.dp, 18.dp, 16.sp, 17.dp),
    Large(50.dp, 22.dp, 17.sp, 18.dp),
}

private val CapsuleShape = RoundedCornerShape(50)

/** 胶囊玻璃按钮（对应 iOS 的 .glass / .glassProminent），按下有弹性缩放。 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassButtonStyle = GlassButtonStyle.Glass,
    size: GlassButtonSize = GlassButtonSize.Regular,
    icon: GlassIcon? = null,
    enabled: Boolean = true,
) {
    val accent = Glass.accent
    val palette = Glass.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "glassButtonScale",
    )
    val contentColor = when (style) {
        GlassButtonStyle.Prominent -> Color.White
        GlassButtonStyle.Destructive -> palette.red
        GlassButtonStyle.Glass -> accent
    }
    val surface = if (style == GlassButtonStyle.Prominent) {
        Modifier
            .clip(CapsuleShape)
            .background(Brush.verticalGradient(listOf(lerp(accent, Color.White, 0.18f), accent)))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f))), CapsuleShape)
    } else {
        Modifier.glassSurface(CapsuleShape)
    }

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .then(surface)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = size.height)
            .padding(horizontal = size.horizontalPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            GlassIconGlyph(icon = icon, color = contentColor, modifier = Modifier.size(size.iconSize))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = contentColor,
            fontSize = size.fontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 圆形玻璃图标按钮。 */
@Composable
fun GlassIconButton(
    icon: GlassIcon,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    style: GlassButtonStyle = GlassButtonStyle.Glass,
) {
    val accent = Glass.accent
    val palette = Glass.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "glassIconScale",
    )
    val color = when (style) {
        GlassButtonStyle.Prominent -> Color.White
        GlassButtonStyle.Destructive -> palette.red
        GlassButtonStyle.Glass -> accent
    }
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .then(
                if (style == GlassButtonStyle.Prominent) Modifier.clip(CircleShape).background(accent) else Modifier.glassSurface(CircleShape),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        GlassIconGlyph(icon = icon, color = color, modifier = Modifier.size(size * 0.44f))
    }
}

// endregion

// region 表单控件

/** 带标题的输入框，内部使用轻量填充（对应 iOS 的 LabeledField）。 */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = label,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    val palette = Glass.palette
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.secondaryLabel)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 4,
            textStyle = TextStyle(color = palette.label, fontSize = 16.sp),
            cursorBrush = SolidColor(Glass.accent),
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { this.contentDescription = label },
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.fill)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    if (value.isEmpty()) {
                        Text(text = placeholder, color = palette.tertiaryLabel, fontSize = 16.sp, maxLines = 1)
                    }
                    inner()
                }
            },
        )
    }
}

/** iOS 风格分段控件。 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Glass.palette
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.fill)
            .padding(2.dp),
    ) {
        val segmentWidth = maxWidth / options.size.coerceAtLeast(1)
        val offset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0)),
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
            label = "segmentOffset",
        )
        // 滑块与文字同高；文字行高度随系统字体缩放。
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .offset(x = offset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .shadow(2.dp, RoundedCornerShape(8.dp))
                    .background(palette.segmentThumb, RoundedCornerShape(8.dp)),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 30.dp)
                .height(IntrinsicSize.Min),
        ) {
            options.forEachIndexed { index, option ->
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { selected = index == selectedIndex }
                        .clickable(interactionSource = interaction, indication = null, role = Role.Tab) { onSelect(index) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        fontSize = 14.sp,
                        fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Medium,
                        color = palette.label,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** iOS 风格开关。 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val palette = Glass.palette
    val track by animateColorAsState(if (checked) palette.green else palette.switchOff, label = "switchTrack")
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "switchThumb",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(width = 51.dp, height = 31.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .clip(CapsuleShape)
            .background(track)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interaction,
                indication = null,
            )
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(27.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** 胶囊标签：普通态为主题色浅底，选中态为主题色实底；可附带移除标记。 */
@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    removable: Boolean = false,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val accent = Glass.accent
    Row(
        modifier = modifier
            .clip(CapsuleShape)
            .background(if (selected) accent else accent.copy(alpha = 0.14f))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics {
                this.selected = selected
                if (contentDescription != null) this.contentDescription = contentDescription
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        if (removable) {
            Spacer(modifier = Modifier.width(5.dp))
            GlassIconGlyph(GlassIcon.Close, color = accent.copy(alpha = 0.75f), modifier = Modifier.size(10.dp))
        }
    }
}

/** 状态小标签（成功 / 失败等）。 */
@Composable
fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(CapsuleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** 表单分组标题（主题色）。 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Glass.accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

@Composable
fun GlassDivider(modifier: Modifier = Modifier, startIndent: Dp = 20.dp) {
    Box(
        modifier = modifier
            .padding(start = startIndent)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Glass.palette.separator),
    )
}

/** 小型旋转进度指示（对应 iOS ProgressView）。 */
@Composable
fun GlassSpinner(modifier: Modifier = Modifier, color: Color = Glass.palette.secondaryLabel) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "spinnerAngle",
    )
    Canvas(modifier = modifier.size(20.dp)) {
        rotate(angle) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = size.minDimension * 0.12f, cap = StrokeCap.Round),
            )
        }
    }
}

// endregion

// region 底部玻璃标签栏

data class GlassTabItem(val title: String, val icon: GlassIcon, val badge: Int? = null)

/** 悬浮胶囊标签栏（对应 iOS 26 的 Liquid Glass TabView）。Android 12+ 使用实时背景模糊。 */
@Composable
fun GlassTabBar(
    items: List<GlassTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    val accent = Glass.accent
    val palette = Glass.palette
    // 模糊层只负责背景模糊，半透明与描边由 glassSurface 统一绘制。
    val hazeStyle = HazeStyle(
        backgroundColor = palette.background,
        tint = null,
        blurRadius = 24.dp,
        noiseFactor = 0f,
        fallbackTint = HazeTint(palette.background.copy(alpha = 0.94f)),
    )
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .height(IntrinsicSize.Min)
            .shadow(16.dp, CapsuleShape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(CapsuleShape)
            .then(if (hazeState != null) Modifier.hazeEffect(state = hazeState, style = hazeStyle) else Modifier.background(palette.background))
            .glassSurface(CapsuleShape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val color = if (selected) accent else palette.label
            val interaction = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CapsuleShape)
                    .background(if (selected) palette.fill else Color.Transparent)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Tab) { onSelect(index) }
                    .semantics(mergeDescendants = true) {
                        this.selected = selected
                        val badge = item.badge
                        contentDescription = if (badge != null && badge > 0) "${item.title}, $badge" else item.title
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box {
                    GlassIconGlyph(icon = item.icon, color = color, modifier = Modifier.size(24.dp))
                    val badge = item.badge
                    if (badge != null && badge > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 10.dp, y = (-5).dp)
                                .height(16.dp)
                                .widthIn(min = 16.dp)
                                .background(palette.red, CapsuleShape)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (badge > 99) "99+" else badge.toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.title,
                    color = color,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

// endregion

// region 图标（线条风格，参照 SF Symbols）

enum class GlassIcon {
    Qr, Checklist, Doc, Gear, Plus, Check, Close, Location, Star, Refresh, Trash, Clock, Pause,
    ChevronDown, ChevronUp, Play, Copy,
    Bell, Battery, ChevronLeft, ChevronRight, Upload, Download, Globe,
}

@Composable
fun GlassIconGlyph(icon: GlassIcon, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(w * x, h * y)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)
        fun polyline(vararg pts: Pair<Float, Float>, close: Boolean = false, fill: Boolean = false) {
            val path = Path().apply {
                moveTo(w * pts[0].first, h * pts[0].second)
                pts.drop(1).forEach { lineTo(w * it.first, h * it.second) }
                if (close) close()
            }
            if (fill) drawPath(path, color) else drawPath(path, color, style = stroke)
        }
        fun roundRect(x: Float, y: Float, rw: Float, rh: Float, r: Float, fill: Boolean = false) {
            if (fill) {
                drawRoundRect(color, p(x, y), Size(w * rw, h * rh), CornerRadius(w * r))
            } else {
                drawRoundRect(color, p(x, y), Size(w * rw, h * rh), CornerRadius(w * r), style = stroke)
            }
        }

        when (icon) {
            GlassIcon.Qr -> {
                // qrcode.viewfinder：四角取景框 + 中间二维码块
                polyline(0.08f to 0.32f, 0.08f to 0.08f, 0.32f to 0.08f)
                polyline(0.68f to 0.08f, 0.92f to 0.08f, 0.92f to 0.32f)
                polyline(0.08f to 0.68f, 0.08f to 0.92f, 0.32f to 0.92f)
                polyline(0.68f to 0.92f, 0.92f to 0.92f, 0.92f to 0.68f)
                roundRect(0.3f, 0.3f, 0.16f, 0.16f, 0.03f, fill = true)
                roundRect(0.54f, 0.3f, 0.16f, 0.16f, 0.03f, fill = true)
                roundRect(0.3f, 0.54f, 0.16f, 0.16f, 0.03f, fill = true)
                roundRect(0.56f, 0.56f, 0.12f, 0.12f, 0.02f, fill = true)
            }
            GlassIcon.Checklist -> {
                listOf(0.22f, 0.5f, 0.78f).forEach { y ->
                    polyline(0.08f to y, 0.15f to y + 0.07f, 0.28f to y - 0.08f)
                    line(0.42f, y, 0.92f, y)
                }
            }
            GlassIcon.Doc -> {
                // doc.text.magnifyingglass
                roundRect(0.14f, 0.06f, 0.56f, 0.8f, 0.08f)
                line(0.26f, 0.26f, 0.58f, 0.26f)
                line(0.26f, 0.42f, 0.5f, 0.42f)
                drawCircle(color, radius = w * 0.15f, center = p(0.68f, 0.68f), style = stroke)
                line(0.79f, 0.79f, 0.93f, 0.93f)
            }
            GlassIcon.Gear -> {
                val c = p(0.5f, 0.5f)
                repeat(8) { i ->
                    val a = (PI * 2 * i / 8).toFloat()
                    drawLine(
                        color,
                        Offset(c.x + cos(a) * w * 0.3f, c.y + sin(a) * h * 0.3f),
                        Offset(c.x + cos(a) * w * 0.42f, c.y + sin(a) * h * 0.42f),
                        strokeWidth = stroke.width * 1.4f,
                        cap = StrokeCap.Round,
                    )
                }
                drawCircle(color, radius = w * 0.27f, center = c, style = stroke)
                drawCircle(color, radius = w * 0.1f, center = c, style = stroke)
            }
            GlassIcon.Plus -> {
                line(0.5f, 0.15f, 0.5f, 0.85f)
                line(0.15f, 0.5f, 0.85f, 0.5f)
            }
            GlassIcon.Check -> polyline(0.16f to 0.54f, 0.4f to 0.76f, 0.84f to 0.26f)
            GlassIcon.Close -> {
                line(0.2f, 0.2f, 0.8f, 0.8f)
                line(0.8f, 0.2f, 0.2f, 0.8f)
            }
            GlassIcon.Location -> polyline(0.86f to 0.14f, 0.12f to 0.46f, 0.47f to 0.53f, 0.54f to 0.88f, close = true, fill = true)
            GlassIcon.Star -> {
                val pts = (0 until 10).map { i ->
                    val r = if (i % 2 == 0) 0.46f else 0.2f
                    val a = (-PI / 2 + PI * i / 5).toFloat()
                    (0.5f + cos(a) * r) to (0.53f + sin(a) * r)
                }
                polyline(*pts.toTypedArray(), close = true)
            }
            GlassIcon.Refresh -> {
                drawArc(
                    color,
                    startAngle = -60f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = p(0.16f, 0.16f),
                    size = Size(w * 0.68f, h * 0.68f),
                    style = stroke,
                )
                polyline(0.62f to 0.1f, 0.68f to 0.22f, 0.55f to 0.3f)
            }
            GlassIcon.Trash -> {
                line(0.14f, 0.24f, 0.86f, 0.24f)
                polyline(0.38f to 0.24f, 0.4f to 0.12f, 0.6f to 0.12f, 0.62f to 0.24f)
                polyline(0.22f to 0.24f, 0.28f to 0.9f, 0.72f to 0.9f, 0.78f to 0.24f)
                line(0.42f, 0.4f, 0.43f, 0.74f)
                line(0.58f, 0.4f, 0.57f, 0.74f)
            }
            GlassIcon.Clock -> {
                drawCircle(color, radius = w * 0.4f, center = p(0.5f, 0.5f), style = stroke)
                polyline(0.5f to 0.26f, 0.5f to 0.5f, 0.68f to 0.6f)
            }
            GlassIcon.Pause -> {
                drawCircle(color, radius = w * 0.4f, center = p(0.5f, 0.5f), style = stroke)
                line(0.42f, 0.36f, 0.42f, 0.64f)
                line(0.58f, 0.36f, 0.58f, 0.64f)
            }
            GlassIcon.ChevronDown -> polyline(0.22f to 0.38f, 0.5f to 0.64f, 0.78f to 0.38f)
            GlassIcon.ChevronUp -> polyline(0.22f to 0.62f, 0.5f to 0.36f, 0.78f to 0.62f)
            GlassIcon.Play -> polyline(0.28f to 0.16f, 0.84f to 0.5f, 0.28f to 0.84f, close = true, fill = true)
            GlassIcon.Bell -> {
                val path = Path().apply {
                    moveTo(w * 0.2f, h * 0.72f)
                    lineTo(w * 0.8f, h * 0.72f)
                    cubicTo(w * 0.7f, h * 0.6f, w * 0.74f, h * 0.48f, w * 0.72f, h * 0.38f)
                    cubicTo(w * 0.7f, h * 0.2f, w * 0.6f, h * 0.12f, w * 0.5f, h * 0.12f)
                    cubicTo(w * 0.4f, h * 0.12f, w * 0.3f, h * 0.2f, w * 0.28f, h * 0.38f)
                    cubicTo(w * 0.26f, h * 0.48f, w * 0.3f, h * 0.6f, w * 0.2f, h * 0.72f)
                    close()
                }
                drawPath(path, color, style = stroke)
                line(0.42f, 0.86f, 0.58f, 0.86f)
            }
            GlassIcon.Battery -> {
                roundRect(0.08f, 0.3f, 0.74f, 0.42f, 0.08f)
                line(0.9f, 0.44f, 0.9f, 0.58f)
                roundRect(0.17f, 0.39f, 0.34f, 0.24f, 0.03f, fill = true)
            }
            GlassIcon.ChevronLeft -> polyline(0.62f to 0.2f, 0.36f to 0.5f, 0.62f to 0.8f)
            GlassIcon.ChevronRight -> polyline(0.38f to 0.2f, 0.64f to 0.5f, 0.38f to 0.8f)
            GlassIcon.Upload -> {
                polyline(0.3f to 0.34f, 0.5f to 0.14f, 0.7f to 0.34f)
                line(0.5f, 0.14f, 0.5f, 0.64f)
                polyline(0.16f to 0.6f, 0.16f to 0.86f, 0.84f to 0.86f, 0.84f to 0.6f)
            }
            GlassIcon.Download -> {
                polyline(0.3f to 0.44f, 0.5f to 0.64f, 0.7f to 0.44f)
                line(0.5f, 0.14f, 0.5f, 0.64f)
                polyline(0.16f to 0.6f, 0.16f to 0.86f, 0.84f to 0.86f, 0.84f to 0.6f)
            }
            GlassIcon.Globe -> {
                drawCircle(color, radius = w * 0.4f, center = p(0.5f, 0.5f), style = stroke)
                drawOval(color, topLeft = p(0.32f, 0.1f), size = Size(w * 0.36f, h * 0.8f), style = stroke)
                line(0.1f, 0.5f, 0.9f, 0.5f)
            }
            GlassIcon.Copy -> {
                roundRect(0.3f, 0.08f, 0.6f, 0.62f, 0.1f)
                polyline(0.18f to 0.3f, 0.1f to 0.3f, 0.1f to 0.92f, 0.66f to 0.92f, 0.66f to 0.84f)
            }
        }
    }
}

// endregion
