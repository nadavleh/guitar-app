package app.guitar.theory

/**
 * One of the 5 canonical CAGED-system shapes.
 *
 * Each shape is named after the open chord whose shape it borrows
 * (C-shape, A-shape, G-shape, E-shape, D-shape). For a chord with root R:
 *  - [rootString] is the string on which the lowest occurrence of R sits;
 *  - the chord's actual fret positions are derived by setting X to the fret of R
 *    on [rootString] and applying the per-string offsets in [CagedTemplates].
 *
 * CAGED is intrinsically tied to standard tuning (E A D G B E); for other tunings,
 * fall back to the constraint-based [ChordShapeGenerator].
 */
enum class CagedShape(val displayName: String, val rootString: Int) {
    C("C-shape", rootString = 1),
    A("A-shape", rootString = 1),
    G("G-shape", rootString = 0),
    E("E-shape", rootString = 0),
    D("D-shape", rootString = 2);
}

/**
 * Per-string fret offsets relative to the shape's root fret X. Strings are indexed
 * 0 (low E) → 5 (high E). null = muted.
 *
 * The example voicings noted in comments are for the *open*-position root of each
 * shape (e.g. C major in C-shape at X=3, A major in A-shape at X=0).
 */
internal object CagedTemplates {

    // ===== Major triads (1 3 5) =====
    val major: Map<CagedShape, List<Int?>> = mapOf(
        // C major at X=3:  x 3 2 0 1 0
        CagedShape.C to listOf(null,  0, -1, -3, -2, -3),
        // A major at X=0:  x 0 2 2 2 0
        CagedShape.A to listOf(null,  0, +2, +2, +2,  0),
        // G major at X=3:  3 2 0 0 0 3
        CagedShape.G to listOf(   0, -1, -3, -3, -3,  0),
        // E major at X=0:  0 2 2 1 0 0
        CagedShape.E to listOf(   0, +2, +2, +1,  0,  0),
        // D major at X=0:  x x 0 2 3 2
        CagedShape.D to listOf(null, null,  0, +2, +3, +2),
    )

    // ===== Minor triads (1 b3 5) =====
    val minor: Map<CagedShape, List<Int?>> = mapOf(
        // C-shape minor with high-e muted (otherwise e at X-3 would play maj3).
        // Cm at X=3:  x 3 1 0 1 x
        CagedShape.C to listOf(null,  0, -2, -3, -2, null),
        // Am at X=0:  x 0 2 2 1 0
        CagedShape.A to listOf(null,  0, +2, +2, +1,  0),
        // G-shape minor with B-string muted (B at X-3 would play maj3).
        // Gm at X=3:  3 1 0 0 x 3
        CagedShape.G to listOf(   0, -2, -3, -3, null, 0),
        // Em at X=0:  0 2 2 0 0 0
        CagedShape.E to listOf(   0, +2, +2,  0,  0,  0),
        // Dm at X=0:  x x 0 2 3 1
        CagedShape.D to listOf(null, null,  0, +2, +3, +1),
    )

    // ===== Dominant 7 (1 3 5 b7) =====
    val dom7: Map<CagedShape, List<Int?>> = mapOf(
        // C7 at X=3:  x 3 2 3 1 0  (b7 on G-string at X)
        CagedShape.C to listOf(null,  0, -1,  0, -2, -3),
        // A7 at X=0:  x 0 2 0 2 0
        CagedShape.A to listOf(null,  0, +2,  0, +2,  0),
        // G7 at X=3:  3 2 0 0 0 1
        CagedShape.G to listOf(   0, -1, -3, -3, -3, -2),
        // E7 at X=0:  0 2 0 1 0 0
        CagedShape.E to listOf(   0, +2,  0, +1,  0,  0),
        // D7 at X=0:  x x 0 2 1 2
        CagedShape.D to listOf(null, null,  0, +2, +1, +2),
    )

    // ===== Major 7 (1 3 5 7) =====
    val maj7: Map<CagedShape, List<Int?>> = mapOf(
        // Cmaj7 at X=3:  x 3 2 0 0 0   (B-string plays maj7)
        CagedShape.C to listOf(null,  0, -1, -3, -3, -3),
        // Amaj7 at X=0:  x 0 2 1 2 0
        CagedShape.A to listOf(null,  0, +2, +1, +2,  0),
        // Gmaj7 at X=3:  3 2 0 0 0 2
        CagedShape.G to listOf(   0, -1, -3, -3, -3, -1),
        // Emaj7 at X=0:  0 2 1 1 0 0
        CagedShape.E to listOf(   0, +2, +1, +1,  0,  0),
        // Dmaj7 at X=0:  x x 0 2 2 2
        CagedShape.D to listOf(null, null,  0, +2, +2, +2),
    )

    // ===== Minor 7 (1 b3 5 b7) =====
    val m7: Map<CagedShape, List<Int?>> = mapOf(
        // Cm7 at X=3:  x 3 1 3 1 3   (G-string at X plays b7; e at X plays P5)
        CagedShape.C to listOf(null,  0, -2,  0, -2,  0),
        // Am7 at X=0:  x 0 2 0 1 0
        CagedShape.A to listOf(null,  0, +2,  0, +1,  0),
        // Gm7 at X=3:  3 1 0 0 x 1   (B-string muted to avoid maj3)
        CagedShape.G to listOf(   0, -2, -3, -3, null, -2),
        // Em7 at X=0:  0 2 0 0 0 0
        CagedShape.E to listOf(   0, +2,  0,  0,  0,  0),
        // Dm7 at X=0:  x x 0 2 1 1
        CagedShape.D to listOf(null, null,  0, +2, +1, +1),
    )

    // ===== Half-diminished m7b5 (1 b3 b5 b7) =====
    // The C and G shapes for m7b5 are exotic; provide the practical three.
    val m7b5: Map<CagedShape, List<Int?>> = mapOf(
        // Am7b5 at X=0:  x 0 1 0 1 x
        CagedShape.A to listOf(null,  0, +1,  0, +1, null),
        // Em7b5 at X=0:  0 1 0 0 x x
        CagedShape.E to listOf(   0, +1,  0,  0, null, null),
        // Dm7b5 at X=0:  x x 0 1 1 1
        CagedShape.D to listOf(null, null,  0, +1, +1, +1),
    )

    // ===== Diminished 7 (1 b3 b5 bb7=maj6). Symmetric chord, repeats every 3 frets. =====
    val dim7: Map<CagedShape, List<Int?>> = mapOf(
        // Adim7 at X=0:  x 0 1 2 1 2
        CagedShape.A to listOf(null,  0, +1, +2, +1, +2),
        // Edim7 at X=0:  0 1 2 0 2 x
        CagedShape.E to listOf(   0, +1, +2,  0, +2, null),
        // Ddim7 at X=0:  x x 0 1 0 1
        CagedShape.D to listOf(null, null,  0, +1,  0, +1),
    )

    /** Lookup template map by chord-quality symbol. */
    fun forQuality(symbol: String): Map<CagedShape, List<Int?>>? = when (symbol) {
        "", "maj"     -> major
        "m", "min"    -> minor
        "7"           -> dom7
        "maj7"        -> maj7
        "m7", "min7"  -> m7
        "m7b5"        -> m7b5
        "dim7"        -> dim7
        else          -> null
    }
}

/**
 * Compute the canonical CAGED voicings for [root] [quality] in standard [tuning], up to [maxFrets].
 * Returns the list sorted ascending by position along the neck.
 *
 * - Returns an empty list if the tuning is not standard, or if the quality has no
 *   CAGED templates defined (e.g. extensions like 9, 13, sus, aug).
 * - Shapes whose required X value would push frets beyond [maxFrets] are skipped.
 */
fun cagedShapesFor(
    root: PitchClass,
    quality: ChordQuality,
    tuning: Tuning,
    maxFrets: Int,
): List<ChordShape> {
    if (tuning != Tunings.standard) return emptyList()
    val templates = CagedTemplates.forQuality(quality.symbol) ?: return emptyList()
    val results = ArrayList<ChordShape>(5)
    for ((shape, template) in templates) {
        val s = buildCagedShape(shape, template, root, quality, tuning, maxFrets) ?: continue
        results.add(s)
    }
    // Sort by lowest fretted position, then by max fret (handles ties where two shapes
    // both start at the same fret — the one whose centroid is higher comes later).
    return results.sortedWith(
        compareBy({ it.position }, { it.frets.filterNotNull().maxOrNull() ?: 0 })
    )
}

private fun buildCagedShape(
    shape: CagedShape,
    template: List<Int?>,
    root: PitchClass,
    quality: ChordQuality,
    tuning: Tuning,
    maxFrets: Int,
): ChordShape? {
    if (template.size != tuning.stringCount) return null
    val rootStringOpenPc = tuning.openStrings[shape.rootString].pitchClass.value
    val xBase = ((root.value - rootStringOpenPc) % 12 + 12) % 12
    // Bump X up by one octave if needed so every offset stays at fret >= 0.
    val minNegOffset = template.filterNotNull().minOrNull() ?: 0
    val minX = if (minNegOffset < 0) -minNegOffset else 0
    var x = xBase
    while (x < minX) x += 12
    if (x > maxFrets) return null
    val frets = template.map { off -> if (off == null) null else x + off }
    if (frets.filterNotNull().any { it < 0 || it > maxFrets }) return null
    return ChordShape(
        chordName = "${NoteSpeller.spell(root)}${quality.symbol}",
        root = root,
        quality = quality,
        frets = frets,
        tuning = tuning,
    )
}
