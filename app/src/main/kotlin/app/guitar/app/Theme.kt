package app.guitar.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------- "Signal" design tokens ----------
// See docs/superpowers/specs/2026-07-10-signal-gui-redesign-design.md §"Design tokens".
// Indigo ground, dual accents with fixed semantics: coral (default) = ACT (play,
// primary actions, selection, destructive); teal = FEEDBACK (correct/in-tune/
// current tone/accents). The user can swap the ACT accent among 5 swatches
// ([Accent]); feedback stays teal unless the chosen accent IS teal, in which case
// it falls back to blue so the two roles never collide (see [signalPalette]).

object SignalColors {
    // Dark (default theme)
    val bgDark        = Color(0xFF10141E)   // screen ground
    val surfaceDark    = Color(0xFF191F2E)  // cards
    val surface2Dark   = Color(0xFF20283C)  // inset cards, answer keys, transport
    val textDark       = Color(0xFFEAEEF7)  // primary text
    val mutedDark      = Color(0xFF7C86A2)  // secondary text, labels
    val lineDark       = Color(0xFF273049)  // hairlines, borders
    val onActDark      = Color(0xFF2A0A09)  // text on the (default coral) act fill
    val feedbackDark   = Color(0xFF3DDCC8)  // teal
    val errorDark      = Color(0xFFD34D52)  // reddish, kept distinct from any act accent

    // Light
    // Creamy warm light scheme (user feedback: the cool near-white read too bright).
    val bgLight        = Color(0xFFF3EDDF)   // warm cream ground
    val surfaceLight   = Color(0xFFFBF7EC)   // soft ivory cards
    val surface2Light  = Color(0xFFEAE1CD)   // deeper cream insets
    val textLight      = Color(0xFF2B241A)   // warm near-black ink
    val mutedLight     = Color(0xFF7C7159)   // warm taupe secondary
    val lineLight      = Color(0xFFDCD1B8)   // cream hairlines
    val onActLight     = Color(0xFFFFFFFF)
    val feedbackLight  = Color(0xFF159C8B)  // darkened teal, contrast on light ground
    val errorLight     = Color(0xFFB3282E)
}

/**
 * User-swappable ACT accent (play / primary actions / selection). Each entry
 * carries a dark-theme and a light-theme value; the light value is darkened /
 * saturated for contrast against the light ground, per the design's accent-picker
 * section. Coral and Teal's light values are spelled out in the spec exactly
 * (`#E03E39`, `#159C8B`); Amber/Blue/Purple's light values aren't given explicitly
 * there and were derived here the same way (darkened, same hue family).
 */
enum class Accent(val dark: Color, val light: Color, val label: String) {
    Coral(Color(0xFFFF5C57), Color(0xFFE03E39), "Coral"),
    Amber(Color(0xFFFFB454), Color(0xFFB8720E), "Amber"),
    Teal(Color(0xFF3DDCC8), Color(0xFF159C8B), "Teal"),
    Blue(Color(0xFF8AA3FF), Color(0xFF3D5CC7), "Blue"),
    Purple(Color(0xFFC98ADF), Color(0xFF9C4FBD), "Purple"),
}

/** Resolved Signal palette for the current (dark/light × accent) combination.
 *  Provided down the tree via [LocalSignal] — this is what restyled screens read;
 *  it reacts live to the theme + accent choice (unlike the static [GuitarColors]
 *  compatibility layer below). */
data class SignalPalette(
    /** Whether this palette is the dark variant — components with their own
     *  non-token art (e.g. the fretboard wood) switch on this. */
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val act: Color,
    val onAct: Color,
    val feedback: Color,
)

/** Fretboard "wood" art palette — the neck's non-token colors. Two fixed sets:
 *  dark walnut and cream maple (light theme), selected in FretboardView via
 *  [SignalPalette.isDark]. Fretboard-v3 material pass: the wood is a vertical
 *  [woodA]→[woodB]→[woodA] gradient, frets are two-tone metal ([fretWire] bright
 *  edge over [fretWireDark]), the dark-theme nut is bone (real nuts aren't black),
 *  and hollow scale-tone dots knock the wood back with [scaleFill]. */
data class BoardColors(
    val woodA: Color,
    val woodB: Color,
    val woodGrain: Color,
    val nut: Color,
    val fretWire: Color,
    val fretWireDark: Color,
    val inlay: Color,
    val stringWound: Color,
    val stringPlain: Color,
    /** Translucent backing inside hollow (scale-tone) dots so the label stays legible. */
    val scaleFill: Color,
) {
    companion object {
        val Dark = BoardColors(
            woodA = Color(0xFF4A3320), woodB = Color(0xFF33200F), woodGrain = Color(0xFF2C1C10),
            nut = Color(0xFFEDE6D6), fretWire = Color(0xFF9B9BA3), fretWireDark = Color(0xFF5A5A61),
            inlay = Color(0xFFE8E4D9),
            stringWound = Color(0xFFC9A876), stringPlain = Color(0xFFDCC698),
            scaleFill = Color(0xB810141E),
        )
        val Light = BoardColors(
            woodA = Color(0xFFF0E2C0), woodB = Color(0xFFDFC99C), woodGrain = Color(0xFFD8C49B),
            nut = Color(0xFF4A4136), fretWire = Color(0xFFA9A9AF), fretWireDark = Color(0xFF6E6E74),
            inlay = Color(0xFF6B5B44),
            stringWound = Color(0xFF8A6F45), stringPlain = Color(0xFF6E6046),
            scaleFill = Color(0xC7FBF7EC),
        )
    }
}

val LocalSignal = staticCompositionLocalOf<SignalPalette> {
    error("LocalSignal not provided — wrap content in GuitarTheme")
}

private fun signalPalette(dark: Boolean, accent: Accent): SignalPalette {
    // Feedback fallback: teal is FEEDBACK's home color, so if the user's ACT
    // accent IS teal, feedback would collide with it visually — fall back to blue.
    val feedback = if (accent == Accent.Teal) {
        if (dark) Accent.Blue.dark else Accent.Blue.light
    } else {
        if (dark) SignalColors.feedbackDark else SignalColors.feedbackLight
    }
    return if (dark) {
        SignalPalette(
            isDark = true,
            bg = SignalColors.bgDark, surface = SignalColors.surfaceDark, surface2 = SignalColors.surface2Dark,
            text = SignalColors.textDark, muted = SignalColors.mutedDark, line = SignalColors.lineDark,
            act = accent.dark, onAct = SignalColors.onActDark, feedback = feedback,
        )
    } else {
        SignalPalette(
            isDark = false,
            bg = SignalColors.bgLight, surface = SignalColors.surfaceLight, surface2 = SignalColors.surface2Light,
            text = SignalColors.textLight, muted = SignalColors.mutedLight, line = SignalColors.lineLight,
            act = accent.light, onAct = SignalColors.onActLight, feedback = feedback,
        )
    }
}

// ---------- Brand-palette compatibility layer ----------
// GuitarColors is a plain `object` — fixed vals that can't react to the live
// theme/accent selection — kept so every call site that hasn't been restructured
// onto LocalSignal/MaterialTheme yet still compiles AND inherits the Signal ground
// immediately (the whole point of this foundation task). It always reflects the
// DEFAULT combination (dark theme, Coral accent); the LIVE accent flows through
// MaterialTheme.colorScheme + [LocalSignal] instead (see [GuitarTheme]), which is
// what restyled screens should read.
object GuitarColors {
    val background     = SignalColors.bgDark
    val surface        = SignalColors.surfaceDark
    val surfaceElev    = SignalColors.surface2Dark
    val divider        = SignalColors.lineDark

    val textPrimary    = SignalColors.textDark
    val textSecondary  = SignalColors.mutedDark
    val textDisabled   = Color(0xFF454E64)

    val primary        = Accent.Coral.dark          // act (default accent)
    val onPrimary      = SignalColors.onActDark

    val rootTone       = Accent.Coral.dark          // root marks: act
    val chordTone      = SignalColors.feedbackDark  // chord-tone marks: feedback (teal)
    val scaleTone      = Color(0xFF8AA3FF)          // scale-tone marks: blue
    val pickSelect     = Accent.Coral.dark

    // Physical fretboard-wood colors — not Signal tokens; the neck keeps its
    // current wood-grain rendering (geometry AND these colors untouched here).
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

/** Builds the M3 [ColorScheme] from Signal tokens for the given theme + resolved
 *  palette. `error` stays a fixed reddish, deliberately NOT tied to `act` — the
 *  user might set act to teal/blue/purple/amber, and errors must still read as
 *  "danger" regardless of the accent choice. */
private fun signalColorScheme(dark: Boolean, palette: SignalPalette): ColorScheme {
    val error = if (dark) SignalColors.errorDark else SignalColors.errorLight
    val onError = if (dark) SignalColors.textDark else Color(0xFFFFFFFF)
    val onSecondary = if (dark) palette.text else Color(0xFFFFFFFF)
    // Tertiary keeps playing the old "scaleTone" role (now the blue token) so any
    // consumer reading MaterialTheme.colorScheme.tertiary still gets a sensible,
    // distinct-from-act-and-feedback color.
    val tertiary = if (dark) Accent.Blue.dark else Accent.Blue.light

    return if (dark) {
        darkColorScheme(
            primary             = palette.act,
            onPrimary           = palette.onAct,
            primaryContainer    = palette.act.copy(alpha = 0.20f),
            onPrimaryContainer  = palette.text,

            secondary           = palette.feedback,
            onSecondary         = onSecondary,
            secondaryContainer  = palette.surface2,
            onSecondaryContainer = palette.text,

            tertiary            = tertiary,
            onTertiary          = onSecondary,

            background          = palette.bg,
            onBackground        = palette.text,

            surface             = palette.surface,
            onSurface           = palette.text,
            surfaceVariant      = palette.surface2,
            onSurfaceVariant    = palette.muted,

            outline             = palette.line,
            outlineVariant      = palette.line,

            error               = error,
            onError             = onError,
        )
    } else {
        lightColorScheme(
            primary             = palette.act,
            onPrimary           = palette.onAct,
            primaryContainer    = palette.act.copy(alpha = 0.18f),
            onPrimaryContainer  = palette.text,

            secondary           = palette.feedback,
            onSecondary         = onSecondary,
            secondaryContainer  = palette.surface2,
            onSecondaryContainer = palette.text,

            tertiary            = tertiary,
            onTertiary          = onSecondary,

            background          = palette.bg,
            onBackground        = palette.text,

            surface             = palette.surface,
            onSurface           = palette.text,
            surfaceVariant      = palette.surface2,
            onSurfaceVariant    = palette.muted,

            outline             = palette.line,
            outlineVariant      = palette.line,

            error               = error,
            onError             = onError,
        )
    }
}

@Composable
fun GuitarTheme(
    dark: Boolean = isSystemInDarkTheme(),
    accent: Accent = Accent.Coral,
    content: @Composable () -> Unit,
) {
    val palette = signalPalette(dark, accent)
    CompositionLocalProvider(LocalSignal provides palette) {
        MaterialTheme(
            colorScheme = signalColorScheme(dark, palette),
            typography  = GuitarTypography,
            content     = content,
        )
    }
}
