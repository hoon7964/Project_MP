package com.dalmuri.bus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/** 달무리버스는 심야 서비스라 항상 다크 테마 하나만 쓴다 (폰 설정·배경화면 색을 따라가지 않음). */
private val DalmuriColorScheme = darkColorScheme(
    primary = BoardBlue,
    onPrimary = Night,
    secondary = MoonGold,
    onSecondary = Night,
    tertiary = Ok,
    onTertiary = Night,
    background = Night,
    onBackground = Ink,
    surface = Night2,
    onSurface = Ink,
    surfaceVariant = Night3,
    onSurfaceVariant = Ink2,
    surfaceContainerLowest = Night,
    surfaceContainerLow = Night2,
    surfaceContainer = Night2,
    surfaceContainerHigh = Night3,
    surfaceContainerHighest = Night3,
    outline = Line,
    outlineVariant = Line,
    error = Warn,
    onError = Night,
)

@Composable
fun DalmuriBusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DalmuriColorScheme,
        typography = Typography,
        content = content,
    )
}
