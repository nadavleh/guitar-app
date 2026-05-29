package app.guitar.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------- Brand palette (see GUI_DESIGN.md §2.1) ----------

object GuitarColors {
    val background     = Color(0xFF0E1014)
    val surface        = Color(0xFF181B22)
    val surfaceElev    = Color(0xFF20242E)
    val divider        = Color(0xFF262A33)

    val textPrimary    = Color(0xFFF5F0E6)
    val textSecondary  = Color(0xFF9098A6)
    val textDisabled   = Color(0xFF4A5060)

    val primary        = Color(0xFFF2A93B)   // amber
    val onPrimary      = Color(0xFF1A1206)

    val rootTone       = Color(0xFFD34D52)   // crimson
    val chordTone      = Color(0xFF3FB8AF)   // teal
    val scaleTone      = Color(0xFF9B7BF7)   // lavender
    val pickSelect     = Color(0xFFF2A93B)   // amber

    val wood           = Color(0xFF3D2817)
    val woodGrain      = Color(0xFF2C1C10)
    val nut            = Color(0xFF0A0A0B)
    val fretWire       = Color(0xFF6F6F75)
    val inlay          = Color(0xFFE8E4D9)
    val stringWound    = Color(0xFFC9A876)   // bronze base for low 3 strings
    val stringPlain    = Color(0xFFDCC698)   // bright steel for high 3 strings
}

// ---------- Typography (see GUI_DESIGN.md §2.2) ----------

private val GuitarTypography = Typography(
    displayLarge   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,     fontSize = 32.sp, letterSpacing = (-0.5).sp),
    displayMedium  = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,     fontSize = 28.sp),
    displaySmall   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,     fontSize = 22.sp),
    titleLarge     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge      = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 16.sp),
    bodyMedium     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 14.sp),
    bodySmall      = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 12.sp),
    labelLarge     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 14.sp),
    labelMedium    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 12.sp),
    labelSmall     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 11.sp),
)

// ---------- Color scheme bound to Material3 ----------

private val GuitarColorScheme = darkColorScheme(
    primary           = GuitarColors.primary,
    onPrimary         = GuitarColors.onPrimary,
    primaryContainer  = GuitarColors.primary.copy(alpha = 0.20f),
    onPrimaryContainer = GuitarColors.textPrimary,

    secondary         = GuitarColors.chordTone,
    onSecondary       = GuitarColors.textPrimary,
    secondaryContainer = GuitarColors.surfaceElev,
    onSecondaryContainer = GuitarColors.textPrimary,

    tertiary          = GuitarColors.scaleTone,
    onTertiary        = GuitarColors.textPrimary,

    background        = GuitarColors.background,
    onBackground      = GuitarColors.textPrimary,

    surface           = GuitarColors.surface,
    onSurface         = GuitarColors.textPrimary,
    surfaceVariant    = GuitarColors.surfaceElev,
    onSurfaceVariant  = GuitarColors.textSecondary,

    outline           = GuitarColors.divider,
    outlineVariant    = GuitarColors.divider,

    error             = GuitarColors.rootTone,
    onError           = GuitarColors.textPrimary,
)

@Composable
fun GuitarTheme(
    @Suppress("UNUSED_PARAMETER") dark: Boolean = isSystemInDarkTheme(),  // ignored — we are always dark in v1
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = GuitarColorScheme,
        typography  = GuitarTypography,
        content     = content,
    )
}
