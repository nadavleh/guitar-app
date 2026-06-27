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
        // Surdo (big bass drum): a ringing open boom, a muted (damped) bass, and a
        // light muted stick tap.
        PercussionInstrument.Surdo to listOf(
            PercussionVoice(PercussionInstrument.Surdo, 0, "open (ring)", "●"),
            PercussionVoice(PercussionInstrument.Surdo, 1, "muted bass", "◐"),
            PercussionVoice(PercussionInstrument.Surdo, 2, "tap", "·"),
        ),
        // Tamborim: a high, fast-attack "clack", a muted (choked) clack, and a light tap.
        PercussionInstrument.Tamborim to listOf(
            PercussionVoice(PercussionInstrument.Tamborim, 0, "clack", "●"),
            PercussionVoice(PercussionInstrument.Tamborim, 1, "muted clack", "◐"),
            PercussionVoice(PercussionInstrument.Tamborim, 2, "tap", "·"),
        ),
        // Pandeiro (frame drum): two low-mid bass notes (open + muted), a slap, and
        // a jingle (platinela) shimmer.
        PercussionInstrument.Pandeiro to listOf(
            PercussionVoice(PercussionInstrument.Pandeiro, 0, "bass (open)", "●"),
            PercussionVoice(PercussionInstrument.Pandeiro, 1, "bass (muted)", "◐"),
            PercussionVoice(PercussionInstrument.Pandeiro, 2, "slap", "✦"),
            PercussionVoice(PercussionInstrument.Pandeiro, 3, "jingle", "○"),
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
