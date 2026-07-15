package app.guitar.theory

/**
 * TRUE jazz **shell voicings** on standard guitar (EADGBE) — the beginner shells taught at
 * https://www.jazzguitar.be/blog/shell-jazz-guitar-chords-beginners/.
 *
 * A shell voicing is just three notes: the **root** on a bass string plus the **3rd and 7th**
 * (the "guide tones") on higher strings — **the 5th is omitted** (it adds little and muddies
 * the voicing; extensions like 9/13 are added on top of the shell instead). Two standard shapes:
 *
 *  - **6th-string root**: R (6th), 7th (4th), 3rd (3rd)   → e.g. Gmaj7 = 3 x 4 4 x x
 *  - **5th-string root**: R (5th), 7th (3rd), 3rd (2nd)   → e.g. Cmaj7 = x 3 x 4 5 x
 *
 * The shapes are derived generically from the chord's 3rd and 7th (or 6th for 6/dim7 chords),
 * so they work for maj7, 7, m7, mMaj7, m7b5, dim7, 6 and m6. Triads (no 7th/6th) have no shell
 * and fall through to CAGED. Standard tuning only.
 */
internal data class JazzVoicing(
    val name: String,
    /** Index of the string carrying the chord root in standard tuning. */
    val rootString: Int,
    /** Per-string fret offsets from the root fret X. null = muted. */
    val offsets: List<Int?>,
)

/** Realise the two shell shapes for [root] [quality] in standard [tuning]. */
fun jazzShellVoicingsFor(
    root: PitchClass,
    quality: ChordQuality,
    tuning: Tuning,
    maxFrets: Int,
): List<ChordShape> {
    if (tuning != Tunings.standard) return emptyList()
    val ints = quality.intervals.toSet()
    val third = when {
        Interval.maj3 in ints -> 4
        Interval.min3 in ints -> 3
        else -> return emptyList()      // sus/no-3rd chords: no shell
    }
    // Shell 7th = the maj7 / b7, or the 6th (for 6, m6, dim7's bb7 = major 6th).
    val seventh = when {
        Interval.maj7 in ints -> 11
        Interval.min7 in ints -> 10
        Interval.maj6 in ints -> 9
        else -> return emptyList()      // triads: no shell
    }
    // Fret offset (relative to the root fret) of a note `iv` semitones above the root,
    // on a string tuned `openGap` semitones above the root string, kept small (−5..+6).
    fun off(openGap: Int, iv: Int): Int {
        var o = ((iv - openGap) % 12 + 12) % 12
        if (o > 6) o -= 12
        return o
    }
    // Open-string pitch gaps between adjacent strings in EADGBE: E→A +5, A→D +5, D→G +5, G→B +4.
    // 6th-root shape uses E(root)/D(7th)/G(3rd): D is +10 above E, G is +15 above E.
    val shape6 = JazzVoicing(
        "shell 6th-string root (R-7-3)", rootString = 0,
        offsets = listOf(0, null, off(10, seventh), off(15, third), null, null),
    )
    // 5th-root shape uses A(root)/G(7th)/B(3rd): G is +10 above A, B is +14 above A.
    val shape5 = JazzVoicing(
        "shell 5th-string root (R-7-3)", rootString = 1,
        offsets = listOf(null, 0, null, off(10, seventh), off(14, third), null),
    )
    val out = ArrayList<ChordShape>(2)
    for (v in listOf(shape6, shape5)) realizeShell(v, root, quality, tuning, maxFrets)?.let { out.add(it) }
    return out.sortedBy { it.position }
}

private fun realizeShell(
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
