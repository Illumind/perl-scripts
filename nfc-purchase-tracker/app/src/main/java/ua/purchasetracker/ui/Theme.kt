package ua.purchasetracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Фірмові кольори Tapsum. */
object Brand {
    val Mint = Color(0xFF3CF2B0)
    val MintDark = Color(0xFF00A876)
    val Teal = Color(0xFF14B8E6)
    val Danger = Color(0xFFFF5C6C)
    val Warning = Color(0xFFFFB547)
}

private val DarkColors = darkColorScheme(
    primary = Brand.Mint,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0F3A30),
    onPrimaryContainer = Brand.Mint,
    secondary = Brand.Teal,
    onSecondary = Color(0xFF00344A),
    background = Color(0xFF0E1218),
    onBackground = Color(0xFFE8ECF2),
    surface = Color(0xFF0E1218),
    onSurface = Color(0xFFE8ECF2),
    surfaceVariant = Color(0xFF1F2630),
    onSurfaceVariant = Color(0xFF9AA4B2),
    surfaceContainer = Color(0xFF171C24),
    surfaceContainerHigh = Color(0xFF1F2630),
    surfaceContainerLow = Color(0xFF13171E),
    outline = Color(0xFF3A4452),
    outlineVariant = Color(0xFF2A323D),
    error = Brand.Danger,
    errorContainer = Color(0xFF3D1C21),
    onErrorContainer = Color(0xFFFFB3BA),
)

private val LightColors = lightColorScheme(
    primary = Brand.MintDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF8E9),
    onPrimaryContainer = Color(0xFF003828),
    secondary = Color(0xFF0A8FB8),
    background = Color(0xFFF3F5F8),
    onBackground = Color(0xFF0E1218),
    surface = Color(0xFFF3F5F8),
    onSurface = Color(0xFF0E1218),
    surfaceVariant = Color(0xFFE4E8EE),
    onSurfaceVariant = Color(0xFF5A6474),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEDF0F4),
    surfaceContainerLow = Color(0xFFF8F9FB),
    outline = Color(0xFFB8C0CC),
    outlineVariant = Color(0xFFDDE2E9),
    error = Color(0xFFD93445),
    errorContainer = Color(0xFFFFE1E4),
    onErrorContainer = Color(0xFF5C0F18),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Стиль великих сум — цифри однакової ширини, щоб не «стрибали». */
val AmountStyle = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold)

@Composable
fun TapsumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
