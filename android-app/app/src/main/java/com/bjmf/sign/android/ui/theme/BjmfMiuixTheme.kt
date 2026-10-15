package com.bjmf.sign.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.bjmf.sign.android.data.BjmfStore
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/** 当前主题是否为深色（考虑「跟随系统 / 浅色 / 深色」设置）。 */
@Composable
fun isAppInDarkTheme(themeMode: String): Boolean = when (themeMode) {
    BjmfStore.THEME_LIGHT -> false
    BjmfStore.THEME_DARK -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun BjmfMiuixTheme(
    keyColor: Color = Color(0xFF3482FF),
    themeMode: String = BjmfStore.THEME_SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = isAppInDarkTheme(themeMode)
    val controller = remember(keyColor, dark) {
        ThemeController(
            colorSchemeMode = if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
            keyColor = keyColor,
            paletteStyle = ThemePaletteStyle.TonalSpot,
            colorSpec = ThemeColorSpec.Spec2025,
        )
    }

    // 状态栏 / 导航栏图标颜色跟随 App 主题（而非仅跟随系统）。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MiuixTheme(
        controller = controller,
        content = content,
    )
}
