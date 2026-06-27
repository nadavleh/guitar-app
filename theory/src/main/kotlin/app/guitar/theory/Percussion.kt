package app.guitar.theory

/**
 * Percussion instruments for the rhythm looper. Pure data — no audio, no Android.
 *
 * An instrument is identified by a stable [PercussionInstrument.id] (used for
 * sample asset names and pattern persistence) and carries an ordered list of
 * [voices]. The audio module maps each (instrument id, voiceIndex) to a one-shot
 * buffer (a bundled sample, or a synth fallback); the app sequences them.
 *
 * [PercussionCatalog] holds the full set of available instruments and the default
 * kit a fresh loop starts with.
 */
data class PercussionInstrument(
    val id: String,
    val displayName: String,
    val voices: List<PercussionVoice>,
) {
    val voiceCount: Int get() = voices.size
}

/**
 * One playable sound of an instrument. The 0-based voice index (the value stored
 * in a pattern cell) is the position of this voice in its instrument's [voices]
 * list. [glyph] is a short cell label.
 */
data class PercussionVoice(
    val displayName: String,
    val glyph: String,
)

/** Build helper: an instrument from id, display name, and (glyph, name) voice pairs. */
private fun inst(id: String, name: String, vararg voices: Pair<String, String>): PercussionInstrument =
    PercussionInstrument(id, name, voices.map { (glyph, vn) -> PercussionVoice(vn, glyph) })

/**
 * The catalog of every instrument the drum machine can use. [DEFAULT_KIT] is the
 * four instruments a fresh loop starts with (unchanged from the original app);
 * the rest are added on demand from the "+ Add instrument" picker. Sample assets
 * are bundled as `assets/drums/<id>_<voiceIndex>.wav`.
 */
object PercussionCatalog {

    // ---- The original four (default kit) — voices match the bundled WAVs. ----
    val Surdo = inst("surdo", "Surdo",
        "●" to "open (ring)", "◐" to "muted bass", "·" to "tap")
    val Tamborim = inst("tamborim", "Tamborim",
        "●" to "clack", "◐" to "muted clack", "·" to "tap")
    val Pandeiro = inst("pandeiro", "Pandeiro",
        "●" to "bass (open)", "◐" to "bass (muted)", "✦" to "slap", "○" to "jingle")
    val Agogo = inst("agogo", "Agogô",
        "▼" to "low bell", "▲" to "high bell")

    /** The kit a fresh loop starts with. */
    val DEFAULT_KIT: List<PercussionInstrument> = listOf(Surdo, Tamborim, Pandeiro, Agogo)

    // ---- Brazilian + Latin additions (sourced from the Latin Percussion pack). ----
    private val additions: List<PercussionInstrument> = listOf(
        inst("cuica", "Cuíca", "▼" to "low", "▲" to "high"),
        inst("caxixi", "Caxixi", "○" to "open", "◌" to "hand", "✺" to "fx"),
        inst("shaker", "Shaker (Ganzá)", "○" to "shaker 1", "◌" to "shaker 2"),
        inst("guiro", "Guiro (Reco-reco)", "▶" to "down", "◀" to "up", "▬" to "long"),
        inst("claves", "Claves", "●" to "clave 1", "◐" to "clave 2"),
        inst("cowbell", "Cowbell", "◉" to "cowbell 1", "◎" to "cowbell 2"),
        inst("triangle", "Triangle", "△" to "open", "▲" to "mute"),
        inst("apito", "Apito (whistle)", "▼" to "low", "◆" to "mid", "▲" to "high"),
        inst("cabasa", "Cabasa", "·" to "short", "▬" to "long", "✺" to "fx"),
        inst("conga", "Conga", "●" to "open", "◐" to "mute", "✦" to "slap", "·" to "tip"),
        inst("quinto", "Quinto", "●" to "open", "◐" to "mute", "✦" to "slap"),
        inst("tumba", "Tumba", "●" to "open", "◐" to "mute", "✦" to "slap"),
        inst("bongo", "Bongo", "▲" to "hi", "▼" to "lo", "◇" to "rim", "✦" to "slap"),
        inst("timbales", "Timbales", "▲" to "hi", "▼" to "lo", "▬" to "cascara", "◇" to "rim"),
        inst("maracas", "Maracas", "○" to "hit", "✺" to "fx"),
        inst("vibraslap", "Vibraslap", "✹" to "hit", "✺" to "pan"),
        inst("castanet", "Castanet", "·" to "single", "▬" to "roll"),
        inst("woodblock", "Wood Block", "▲" to "hi", "◆" to "mid", "▼" to "low"),
        inst("cymbal", "Cymbal", "◉" to "bell", "○" to "open"),
        inst("gong", "Gong", "◯" to "hit"),
    )

    /** Every instrument, in display/picker order (default kit first). */
    val ALL: List<PercussionInstrument> = DEFAULT_KIT + additions

    private val byId: Map<String, PercussionInstrument> = ALL.associateBy { it.id }

    fun byId(id: String): PercussionInstrument? = byId[id]
}

/**
 * Thin facade kept for existing call sites. Voice data now lives on the instrument
 * itself; these helpers just read it.
 */
object PercussionVoices {
    fun voicesFor(instrument: PercussionInstrument): List<PercussionVoice> = instrument.voices
    fun voiceCount(instrument: PercussionInstrument): Int = instrument.voices.size
    fun voice(instrument: PercussionInstrument, index: Int): PercussionVoice = instrument.voices[index]
}
