package app.guitar.theory

/**
 * Canonical jazz drop-2 voicings on the top-4 strings (D-G-B-e). All four inversions
 * per chord quality, transposable to any root. These are the standard comping voicings
 * taught in jazz guitar pedagogy (see https://www.jazzguitar.be/blog/jazz-guitar-chord-dictionary/).
 *
 * For style = [VoicingStyle.Shell], [ChordShapeGenerator] returns these in preference to
 * the algorithmic shell voicings. Strict shell voicings (3-note 1-3-7 or 3-7-9) can be
 * built from these by muting either the 5th or the root.
 *
 * Each voicing template carries:
 *  - the string on which the chord ROOT actually sits (so the algorithm knows where to
 *    place the voicing for any chord root R);
 *  - a 6-element fret-offset list relative to that root fret X (null = muted).
 *
 * Standard tuning only — for non-standard tunings, the algorithmic shell falls through.
 */
internal data class JazzVoicing(
    val name: String,
    /** Index of the string carrying the chord root in standard tuning. */
    val rootString: Int,
    /** Per-string fret offsets from the root fret X. null = muted. */
    val offsets: List<Int?>,
)

internal object JazzShellTable {

    // -------- maj7 drop-2 on top 4 strings (D-G-B-e) --------
    // Inversion order: 5-R-3-7 / 7-3-5-R / R-5-7-3 / 3-7-R-5
    private val maj7 = listOf(
        JazzVoicing("maj7 drop-2 root-pos (5-R-3-7)",  rootString = 3, offsets = listOf(null, null,  0,  0,  0, +2)),
        JazzVoicing("maj7 drop-2 1st-inv (7-3-5-R)",   rootString = 5, offsets = listOf(null, null, +1, +1,  0,  0)),
        JazzVoicing("maj7 drop-2 2nd-inv (R-5-7-3)",   rootString = 2, offsets = listOf(null, null,  0, +2, +2, +2)),
        JazzVoicing("maj7 drop-2 3rd-inv (3-7-R-5)",   rootString = 4, offsets = listOf(null, null, +1, +3,  0, +2)),
        // Middle-4 strings variant (very common): root on A-string. R-5-7-3.
        JazzVoicing("maj7 drop-2 middle-4 (R-5-7-3)",  rootString = 1, offsets = listOf(null,  0, +2, +1, +2, null)),
    )

    // -------- m7 drop-2 on top 4 (Cm7: D=Bb G=Eb B=G e=C for the closed form) --------
    private val m7 = listOf(
        // 5-R-b3-b7 → e.g. Cm7: x x 5 5 4 6
        JazzVoicing("m7 drop-2 root-pos (5-R-b3-b7)",  rootString = 3, offsets = listOf(null, null,  0,  0, -1, +1)),
        // b7-b3-5-R → e.g. Cm7: x x 8 8 8 8 (the Freddie-Green m7 box)
        JazzVoicing("m7 drop-2 1st-inv (b7-b3-5-R)",   rootString = 5, offsets = listOf(null, null,  0,  0,  0,  0)),
        // R-5-b7-b3 → e.g. Cm7: x x 10 12 11 11
        JazzVoicing("m7 drop-2 2nd-inv (R-5-b7-b3)",   rootString = 2, offsets = listOf(null, null,  0, +2, +1, +1)),
        // b3-b7-R-5 → e.g. Cm7: x x 1 3 1 3
        JazzVoicing("m7 drop-2 3rd-inv (b3-b7-R-5)",   rootString = 4, offsets = listOf(null, null,  0, +2,  0, +2)),
        // Middle-4 strings: R-5-b7-b3. Root on A-string.
        // For Cm7: x 3 5 3 4 x  (A-3=C, D-5=G, G-3=Bb, B-4=Eb)
        JazzVoicing("m7 drop-2 middle-4 (R-5-b7-b3)",  rootString = 1, offsets = listOf(null,  0, +2,  0, +1, null)),
    )

    // -------- dom7 drop-2 on top 4 --------
    private val dom7 = listOf(
        // 5-R-3-b7 → e.g. C7: x x 5 5 5 6
        JazzVoicing("7 drop-2 root-pos (5-R-3-b7)",    rootString = 3, offsets = listOf(null, null,  0,  0,  0, +1)),
        // b7-3-5-R → e.g. C7: x x 8 9 8 8
        JazzVoicing("7 drop-2 1st-inv (b7-3-5-R)",     rootString = 5, offsets = listOf(null, null,  0, +1,  0,  0)),
        // R-5-b7-3 → e.g. C7: x x 10 12 11 12
        JazzVoicing("7 drop-2 2nd-inv (R-5-b7-3)",     rootString = 2, offsets = listOf(null, null,  0, +2, +1, +2)),
        // 3-b7-R-5 → e.g. C7: x x 2 3 1 3
        JazzVoicing("7 drop-2 3rd-inv (3-b7-R-5)",     rootString = 4, offsets = listOf(null, null, +1, +2,  0, +2)),
        // Middle-4: R-5-b7-3 on A-D-G-B. For C7: x 3 5 3 5 x  (A-3=C, D-5=G, G-3=Bb, B-5=E)
        JazzVoicing("7 drop-2 middle-4 (R-5-b7-3)",    rootString = 1, offsets = listOf(null,  0, +2,  0, +2, null)),
    )

    // -------- m7b5 drop-2 on top 4 --------
    private val m7b5 = listOf(
        // b5-R-b3-b7 → e.g. Cm7b5: x x 4 5 4 6
        JazzVoicing("m7b5 drop-2 root-pos (b5-R-b3-b7)", rootString = 3, offsets = listOf(null, null, -1,  0, -1, +1)),
        // b7-b3-b5-R → e.g. Cm7b5: x x 8 8 7 8
        JazzVoicing("m7b5 drop-2 1st-inv (b7-b3-b5-R)",  rootString = 5, offsets = listOf(null, null,  0,  0, -1,  0)),
        // R-b5-b7-b3 → e.g. Cm7b5: x x 10 11 11 11
        JazzVoicing("m7b5 drop-2 2nd-inv (R-b5-b7-b3)",  rootString = 2, offsets = listOf(null, null,  0, +1, +1, +1)),
        // b3-b7-R-b5 → e.g. Cm7b5: x x 1 3 1 2
        JazzVoicing("m7b5 drop-2 3rd-inv (b3-b7-R-b5)",  rootString = 4, offsets = listOf(null, null,  0, +2,  0, +1)),
    )

    // -------- dim7 (symmetric: same shape every 3 frets) --------
    // The 4 "inversions" are really one shape shifted by 3, 6, 9 frets.
    // We provide ONE template; the user gets ascending-neck versions by enharmonic shifts.
    private val dim7 = listOf(
        // Cdim7: x x 1 2 1 2  (R on B-string at fret 1)
        JazzVoicing("dim7 drop-2 (b3-bb7-R-b5)",        rootString = 4, offsets = listOf(null, null,  0, +1,  0, +1)),
        // Same shape one octave up on the middle-4: x 0 1 2 1 x (Cdim7 starts at A-fret-0...
        // actually let me re-derive). Adim7 on A-string at 0: x 0 1 2 1 2 (this is the
        // movable A-shape dim7 we already have in CAGED; equivalent here).
        JazzVoicing("dim7 A-shape (R-b5-bb7-b3)",       rootString = 1, offsets = listOf(null,  0, +1, +2, +1, null)),
    )

    // -------- 6 drop-2 on top 4 (1 3 5 6) --------
    // (Note: drop-2 root-pos of C6 is x x 5 5 5 5 — same notes as Am7's Freddie-Green box.)
    private val sixth = listOf(
        // 5-R-3-6 → C6: x x 5 5 5 5
        JazzVoicing("6 drop-2 root-pos (5-R-3-6)",      rootString = 3, offsets = listOf(null, null,  0,  0,  0,  0)),
        // 6-3-5-R → C6: x x 7 9 8 8
        JazzVoicing("6 drop-2 1st-inv (6-3-5-R)",       rootString = 5, offsets = listOf(null, null, -1, +1,  0,  0)),
        // R-5-6-3 → C6: x x 10 12 10 12
        JazzVoicing("6 drop-2 2nd-inv (R-5-6-3)",       rootString = 2, offsets = listOf(null, null,  0, +2,  0, +2)),
        // 3-6-R-5 → C6: x x 2 2 1 3
        JazzVoicing("6 drop-2 3rd-inv (3-6-R-5)",       rootString = 4, offsets = listOf(null, null, +1, +1,  0, +2)),
    )

    // -------- m6 drop-2 on top 4 (1 b3 5 6) --------
    private val minor6 = listOf(
        // 5-R-b3-6 → Cm6: x x 5 5 4 5
        JazzVoicing("m6 drop-2 root-pos (5-R-b3-6)",    rootString = 3, offsets = listOf(null, null,  0,  0, -1,  0)),
        // 6-b3-5-R → Cm6: x x 7 8 8 8
        JazzVoicing("m6 drop-2 1st-inv (6-b3-5-R)",     rootString = 5, offsets = listOf(null, null, -1,  0,  0,  0)),
        // R-5-6-b3 → Cm6: x x 10 12 10 11
        JazzVoicing("m6 drop-2 2nd-inv (R-5-6-b3)",     rootString = 2, offsets = listOf(null, null,  0, +2,  0, +1)),
        // b3-6-R-5 → Cm6: x x 1 2 1 3
        JazzVoicing("m6 drop-2 3rd-inv (b3-6-R-5)",     rootString = 4, offsets = listOf(null, null,  0, +1,  0, +2)),
    )

    // -------- 9 (1 3 5 b7 9) — 5-note chord; we use the standard A-rooted form --------
    private val ninth = listOf(
        // C9 at A-3:  x 3 2 3 3 3   (all chord tones — R, 3, b7, 9, 5)
        JazzVoicing("9 standard (R-3-b7-9-5)",          rootString = 1, offsets = listOf(null,  0, -1,  0,  0,  0)),
    )

    fun forQuality(symbol: String): List<JazzVoicing>? = when (symbol) {
        "maj7"        -> maj7
        "m7", "min7"  -> m7
        "7"           -> dom7
        "m7b5"        -> m7b5
        "dim7"        -> dim7
        "6"           -> sixth
        "m6"          -> minor6
        "9"           -> ninth
        else          -> null
    }
}

/**
 * Realize the canonical jazz drop-2 voicings for [root] [quality] in standard [tuning].
 * Returns the playable voicings sorted by position. Voicings whose required X would
 * push frets outside [0..maxFrets] are skipped (no octave-bump for shell voicings —
 * jazz comping voicings are typically played in their natural mid-neck position).
 */
fun jazzShellVoicingsFor(
    root: PitchClass,
    quality: ChordQuality,
    tuning: Tuning,
    maxFrets: Int,
): List<ChordShape> {
    if (tuning != Tunings.standard) return emptyList()
    val table = JazzShellTable.forQuality(quality.symbol) ?: return emptyList()
    val out = ArrayList<ChordShape>(table.size)
    for (v in table) {
        val s = realizeJazzVoicing(v, root, quality, tuning, maxFrets) ?: continue
        out.add(s)
    }
    return out.sortedWith(
        compareBy({ it.position }, { it.frets.filterNotNull().maxOrNull() ?: 0 })
    )
}

private fun realizeJazzVoicing(
    v: JazzVoicing,
    root: PitchClass,
    quality: ChordQuality,
    tuning: Tuning,
    maxFrets: Int,
): ChordShape? {
    if (v.offsets.size != tuning.stringCount) return null
    val openPc = tuning.openStrings[v.rootString].pitchClass.value
    val xBase = ((root.value - openPc) % 12 + 12) % 12
    val minNeg = v.offsets.filterNotNull().minOrNull() ?: 0
    val minX = if (minNeg < 0) -minNeg else 0
    var x = xBase
    while (x < minX) x += 12
    if (x > maxFrets) return null
    val frets = v.offsets.map { off -> if (off == null) null else x + off }
    if (frets.filterNotNull().any { it < 0 || it > maxFrets }) return null
    return ChordShape(
        chordName = "${NoteSpeller.spell(root)}${quality.symbol}",
        root = root,
        quality = quality,
        frets = frets,
        tuning = tuning,
        templateName = v.name,
    )
}
