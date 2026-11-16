package kr.gapja60.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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

/** 간지 표시에 쓰는 명조체(Noto Serif CJK에서 간지 글자만 추린 것, SIL OFL) */
val SerifGapja = FontFamily(Font(R.font.gapja_serif, FontWeight.Bold))

/*
 * 눈의 피로를 줄이기 위한 색 원칙
 * - 순수 흰색·순수 검정 바탕 없음. 따뜻한 미색 / 따뜻한 짙은 회색
 * - 강조색은 채도를 낮춘 황토색 하나. 넓은 면적은 옅은 바탕색 + 테두리로만 강조
 * - 본문 대비 약 8:1 (WCAG AA 4.5:1 이상, 과한 대비는 피함)
 * - 파란 기운이 적은 따뜻한 색온도
 */

// 낮: 미색 바탕
private val Day = lightColorScheme(
    primary = Color(0xFF7A6640), onPrimary = Color(0xFFF4EFE5),
    primaryContainer = Color(0xFFE2D8C3), onPrimaryContainer = Color(0xFF4A433B),
    secondaryContainer = Color(0xFFE2D8C3), onSecondaryContainer = Color(0xFF4A433B),
    background = Color(0xFFECE6DA), onBackground = Color(0xFF4A433B),
    surface = Color(0xFFF4EFE5), onSurface = Color(0xFF4A433B),
    surfaceVariant = Color(0xFFE5DDCE), onSurfaceVariant = Color(0xFF6E655A),
    outline = Color(0xFFCFC5B3), outlineVariant = Color(0xFFDDD4C4),
)

// 밤(기본): 따뜻한 짙은 회색 바탕
private val Night = darkColorScheme(
    primary = Color(0xFFB39A6E), onPrimary = Color(0xFF22201D),
    primaryContainer = Color(0xFF3A342C), onPrimaryContainer = Color(0xFFC9BFAF),
    secondaryContainer = Color(0xFF3A342C), onSecondaryContainer = Color(0xFFC9BFAF),
    background = Color(0xFF22201D), onBackground = Color(0xFFC9BFAF),
    surface = Color(0xFF2B2824), onSurface = Color(0xFFC9BFAF),
    surfaceVariant = Color(0xFF34302B), onSurfaceVariant = Color(0xFFA0968A),
    outline = Color(0xFF47413A), outlineVariant = Color(0xFF3A352F),
)

// 수면: 거의 검은 갈색 바탕, 어두운 호박색 글자
val SleepColors = darkColorScheme(
    primary = Color(0xFF8A6B45), onPrimary = Color(0xFF120E0B),
    primaryContainer = Color(0xFF241B13), onPrimaryContainer = Color(0xFF9A7B55),
    secondaryContainer = Color(0xFF241B13), onSecondaryContainer = Color(0xFF9A7B55),
    background = Color(0xFF120E0B), onBackground = Color(0xFF9A7B55),
    surface = Color(0xFF19140F), onSurface = Color(0xFF9A7B55),
    surfaceVariant = Color(0xFF1F1812), onSurfaceVariant = Color(0xFF7D6548),
    outline = Color(0xFF2C2219), outlineVariant = Color(0xFF241B13),
)

/** 정답 표시용 차분한 올리브 */
val Correct = Color(0xFF6E8059)

fun schemeFor(theme: Int, systemDark: Boolean): ColorScheme = when (theme) {
    1 -> Day
    2 -> Night
    3 -> SleepColors
    else -> if (systemDark) Night else Day
}

@Composable
fun GapjaTheme(theme: Int, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = schemeFor(theme, isSystemInDarkTheme()), typography = Typography(), content = content)
}
