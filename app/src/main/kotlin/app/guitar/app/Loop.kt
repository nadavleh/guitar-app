package app.guitar.app

import androidx.compose.runtime.Immutable

/**
 * One beat (quarter-note by default) of a loop. A bar is a list of slots.
 *
 * @property chordSymbol the chord at this slot, e.g. "Cmaj7". null = sustain
 *   (don't re-strum this beat; let the previous chord ring).
 * @property voicingIndex which voicing from the chord's shape list to play.
 * @property strum strum pattern for this slot.
 */
@Immutable
data class LoopSlot(
    val chordSymbol: String? = null,
    val voicingIndex: Int = 0,
    val strum: StrumPattern = StrumPattern.Down,
)

enum class StrumPattern(val displayName: String, val glyph: String) {
    Down("Down",       "↓"),
    Up("Up",           "↑"),
    Arpeggio("Arp",    "≋"),
    Sustain("Sustain", "·"), // do not re-strum
}

/** Default progression a fresh loop starts with: ii-V-I in C with the E-shape voicing. */
val DEFAULT_PROGRESSION: List<List<LoopSlot>> = listOf(
    listOf(LoopSlot("Dm7")),
    listOf(LoopSlot("G7")),
    listOf(LoopSlot("Cmaj7")),
    listOf(LoopSlot("Cmaj7")),
)
