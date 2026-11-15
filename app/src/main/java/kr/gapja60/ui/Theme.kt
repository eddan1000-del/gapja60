package kr.gapja60.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kr.gapja60.R

// 먹색 · 한지색 · 주홍
val Ink = Color(0xFF1E1A17)
val Hanji = Color(0xFFF4EEE1)
val Vermilion = Color(0xFFB8432F)
val VermilionLight = Color(0xFFE07A5F)

/** 간지 표시에 쓰는 명조체(Noto Serif CJK에서 간지 글자만 추린 것, SIL OFL) */
val SerifGapja = FontFamily(Font(R.font.gapja_serif, FontWeight.Bold))

private val LightColors = lightColorScheme(
    primary = Vermilion, onPrimary = Color.White,
    primaryContainer = Color(0xFFF6DDD5), onPrimaryContainer = Color(0xFF4A140A),
    secondary = Color(0xFF6E6258), onSecondary = Color.White,
    background = Hanji, onBackground = Ink,
    surface = Color(0xFFFBF7EE), onSurface = Ink,
    surfaceVariant = Color(0xFFEAE1D1), onSurfaceVariant = Color(0xFF5A5048),
    outline = Color(0xFFCBBFAC),
)

private val DarkColors = darkColorScheme(
    primary = VermilionLight, onPrimary = Color(0xFF2A0C05),
    primaryContainer = Color(0xFF5C2216), onPrimaryContainer = Color(0xFFFFDBD1),
    secondary = Color(0xFFCFC3B5), onSecondary = Ink,
    background = Color(0xFF121110), onBackground = Color(0xFFEDE6DA),
    surface = Color(0xFF1C1A18), onSurface = Color(0xFFEDE6DA),
    surfaceVariant = Color(0xFF2B2724), onSurfaceVariant = Color(0xFFBFB4A6),
    outline = Color(0xFF4A433D),
)

@Composable
fun GapjaTheme(theme: Int, content: @Composable () -> Unit) {
    val dark = when (theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = Typography(), content = content)
}
