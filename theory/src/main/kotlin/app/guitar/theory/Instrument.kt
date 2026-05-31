package app.guitar.theory

/**
 * Instruments the app can render and play. Each instrument has its own
 * preset-tuning library and its own default chord-shape generation policy
 * (different string counts and fret-span comfort).
 */
enum class Instrument(
    val displayName: String,
    /** Maximum fret span the chord-shape generator should allow for this
     *  instrument. Cavaquinho's smaller body makes 5-fret stretches comfortable;
     *  guitar's longer scale length doesn't. */
    val maxFretSpan: Int,
) {
    Guitar("Guitar", maxFretSpan = 4),
    Cavaquinho("Cavaquinho", maxFretSpan = 5),
}
