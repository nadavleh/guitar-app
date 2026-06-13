package app.guitar.theory

/**
 * Samba percussion instruments for the rhythm looper. Pure data — no audio, no
 * Android. The audio module maps each (instrument, voiceIndex) to a synthesized
 * one-shot buffer; the app module sequences them.
 */
enum class PercussionInstrument(val displayName: String) {
    Surdo("Surdo"),
    Tamborim("Tamborim"),
    Pandeiro("Pandeiro"),
    Agogo("Agogô"),
}

/**
 * One playable sound of an instrument. [index] is the 0-based voice number
 * (the value stored in a pattern cell); [glyph] is a short cell label.
 */
data class PercussionVoice(
    val instrument: PercussionInstrument,
    val index: Int,
    val displayName: String,
    val glyph: String,
)

object PercussionVoices {
    private val table: Map<PercussionInstrument, List<PercussionVoice>> = mapOf(
        PercussionInstrument.Surdo to listOf(
            PercussionVoice(PercussionInstrument.Surdo, 0, "open", "●"),
            PercussionVoice(PercussionInstrument.Surdo, 1, "muffled", "◍"),
        ),
        PercussionInstrument.Tamborim to listOf(
            PercussionVoice(PercussionInstrument.Tamborim, 0, "open", "●"),
            PercussionVoice(PercussionInstrument.Tamborim, 1, "muted", "◍"),
        ),
        PercussionInstrument.Pandeiro to listOf(
            PercussionVoice(PercussionInstrument.Pandeiro, 0, "low (slap)", "●"),
            PercussionVoice(PercussionInstrument.Pandeiro, 1, "high (open)", "○"),
            PercussionVoice(PercussionInstrument.Pandeiro, 2, "mute (tap)", "·"),
        ),
        PercussionInstrument.Agogo to listOf(
            PercussionVoice(PercussionInstrument.Agogo, 0, "low bell", "▼"),
            PercussionVoice(PercussionInstrument.Agogo, 1, "high bell", "▲"),
        ),
    )

    fun voicesFor(instrument: PercussionInstrument): List<PercussionVoice> = table.getValue(instrument)

    fun voiceCount(instrument: PercussionInstrument): Int = table.getValue(instrument).size

    fun voice(instrument: PercussionInstrument, index: Int): PercussionVoice =
        table.getValue(instrument)[index]
}
