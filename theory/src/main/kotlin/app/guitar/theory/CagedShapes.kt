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

    // ===== Half-diminished m7b5 (1 b3 b5 b7) — full 5 CAGED shapes =====
    val m7b5: Map<CagedShape, List<Int?>> = mapOf(
        // Cm7b5 at X=3:  x 3 1 3 4 2   (b3 on D, b7 on G, b3 on B, b5 on e)
        CagedShape.C to listOf(null,  0, -2,  0, +1, -1),
        // Am7b5 at X=0:  x 0 1 0 1 x
        CagedShape.A to listOf(null,  0, +1,  0, +1, null),
        // Gm7b5 at X=3:  3 1 3 3 2 3   (b3 on A, b7 on D, b3 on G, b5 on B, R on e)
        CagedShape.G to listOf(   0, -2,  0,  0, -1,  0),
        // Em7b5 at X=0:  0 1 0 0 x x
        CagedShape.E to listOf(   0, +1,  0,  0, null, null),
        // Dm7b5 at X=0:  x x 0 1 1 1
        CagedShape.D to listOf(null, null,  0, +1, +1, +1),
    )

    // ===== Diminished 7 (1 b3 b5 bb7=maj6). Symmetric chord, repeats every 3 frets. =====
    // All 5 CAGED shapes so every root has voicings on strings 6, 5, and 4.
    val dim7: Map<CagedShape, List<Int?>> = mapOf(
        // Cdim7 at X=3:  x 3 1 2 1 2   (b3 on D, bb7 on G, R on B, b5 on e)
        CagedShape.C to listOf(null,  0, -2, -1, -2, -1),
        // Adim7 at X=0:  x 0 1 2 1 2
        CagedShape.A to listOf(null,  0, +1, +2, +1, +2),
        // Gdim7 at X=3:  3 1 2 3 2 3   (b3 on A, bb7 on D, b3 on G, b5 on B, R on e)
        CagedShape.G to listOf(   0, -2, -1,  0, -1,  0),
        // Edim7 at X=0:  0 1 2 0 2 x
        CagedShape.E to listOf(   0, +1, +2,  0, +2, null),
        // Ddim7 at X=0:  x x 0 1 0 1
        CagedShape.D to listOf(null, null,  0, +1,  0, +1),
    )

    // ===== Diminished triad (1 b3 b5) — three notes, symmetric every 3 frets. =====
    val dim: Map<CagedShape, List<Int?>> = mapOf(
        // Cdim at X=3:  x 3 4 5 4 x   (b5 on D, R-oct on G, b3 on B)
        // Same fingerprint as the A-shape; we offset the C-shape to live below
        // the root for visual variety (see G-shape comment).
        // Cdim "C-shape" at X=4 (C#dim):  x 4 2 0 2 x   — root, b3, b5, R doubled
        CagedShape.C to listOf(null,  0, -2, -4, -2, null),
        // Adim at X=0:  x 0 1 2 1 x
        CagedShape.A to listOf(null,  0, +1, +2, +1, null),
        // Gdim "G-shape" at X=8 for Cdim:  8 6 4 5 4 8   (b3, b5, R, b3, R)
        CagedShape.G to listOf(   0, -2, -4, -3, -4,  0),
        // Edim at X=0:  0 1 2 0 2 x   (b5 on A, R on D, b3 on G, b5 on B)
        CagedShape.E to listOf(   0, +1, +2,  0, +2, null),
        // Ddim at X=0:  x x 0 1 x 1   (b5 on G, b3 on e — B muted, since open B is not in Ddim)
        CagedShape.D to listOf(null, null,  0, +1, null, +1),
    )

    // ===== Augmented triad (1 3 #5) — three notes, symmetric every 4 frets. =====
    val aug: Map<CagedShape, List<Int?>> = mapOf(
        // Caug at X=3:  x 3 2 1 1 0   (maj3 on D, #5 on G, R-oct on B, maj3 on e)
        CagedShape.C to listOf(null,  0, -1, -2, -2, -3),
        // Aaug at X=0:  x 0 3 2 2 1   (#5 on D, R-oct on G, maj3 on B, #5 on e)
        CagedShape.A to listOf(null,  0, +3, +2, +2, +1),
        // Gaug "G-shape" at X=3:  3 2 1 0 x 3   (B muted to keep span compact)
        CagedShape.G to listOf(   0, -1, -2, -3, null, 0),
        // Eaug at X=0:  0 3 2 1 1 0
        CagedShape.E to listOf(   0, +3, +2, +1, +1,  0),
        // Daug at X=0:  x x 0 3 3 2
        CagedShape.D to listOf(null, null,  0, +3, +3, +2),
    )

    // ===== Dominant 9 (1 3 5 b7 9) — 5 notes ===========================
    val ninth: Map<CagedShape, List<Int?>> = mapOf(
        // C9 "C-shape" at X=3:  x 3 2 3 3 0   (maj3 on D, b7 on G, 9 on B, maj3 on e — open)
        CagedShape.C to listOf(null,  0, -1,  0,  0, -3),
        // C9 "A-shape" at X=3:  x 3 2 3 3 3   (the classic "James Brown" 9 — P5 on top)
        CagedShape.A to listOf(null,  0, -1,  0,  0,  0),
        // G9 G-shape at X=3:    3 2 0 2 0 1   (maj3, P5, 9, maj3, b7)
        CagedShape.G to listOf(   0, -1, -3, -1, -3, -2),
        // E9 E-shape at X=0:    0 2 0 1 0 2   (P5, b7, maj3, P5, 9)
        CagedShape.E to listOf(   0, +2,  0, +1,  0, +2),
        // C9 D-shape at X=10:   x x 10 9 11 10   (maj3 on G, b7 on B, 9 on e — no P5)
        CagedShape.D to listOf(null, null,  0, -1, +1,  0),
    )

    // ===== 13 (1 3 5 b7 9 13) — 6 notes; 5th and 9th typically omitted ====
    val thirteen: Map<CagedShape, List<Int?>> = mapOf(
        // C13 C-shape at X=3:  x 3 2 3 5 5   (maj3 on D, b7 on G, maj3-oct on B, 13 on e)
        CagedShape.C to listOf(null,  0, -1,  0, +2, +2),
        // C13 A-shape at X=3:  x 3 5 3 5 5   (P5 on D, b7 on G, maj3 on B, 13 on e)
        CagedShape.A to listOf(null,  0, +2,  0, +2, +2),
        // C13 G-shape at X=8:  8 7 8 5 5 5   (maj3, b7, R-oct, maj3, 13)
        CagedShape.G to listOf(   0, -1,  0, -3, -3, -3),
        // E13 E-shape at X=0:  0 2 0 1 2 2   (P5 on A, b7 on D, maj3 on G, 13 on B, 9 on e)
        CagedShape.E to listOf(   0, +2,  0, +1, +2, +2),
        // D13 D-shape at X=0:  x 2 0 2 1 2   (13 on A, P5 on G, b7 on B, maj3 on e — all
        // four essential chord tones in a 2-fret span; works at every root since the
        // template stays within +2 of X, so no high-fret overrun.)
        CagedShape.D to listOf(null,  +2,  0, +2, +1, +2),
    )

    // ===== sus2 (1 2 5) =====
    val sus2: Map<CagedShape, List<Int?>> = mapOf(
        // Csus2 at X=3:  x 3 0 0 1 x   (e muted — open e at X-3 plays maj3 which is not in sus2)
        CagedShape.C to listOf(null,  0, -3, -3, -2, null),
        // Asus2 at X=0:  x 0 2 2 0 0
        CagedShape.A to listOf(null,  0, +2, +2,  0,  0),
        // Gsus2 at X=3:  3 0 0 0 x 3   (B-string muted)
        CagedShape.G to listOf(   0, -3, -3, -3, null, 0),
        // Esus2 at X=0:  0 2 4 4 0 0
        CagedShape.E to listOf(   0, +2, +4, +4,  0,  0),
        // Dsus2 at X=0:  x x 0 2 3 0
        CagedShape.D to listOf(null, null,  0, +2, +3,  0),
    )

    // ===== sus4 (1 4 5) =====
    val sus4: Map<CagedShape, List<Int?>> = mapOf(
        // Csus4 at X=3:  x 3 3 0 1 1
        CagedShape.C to listOf(null,  0,  0, -3, -2, -2),
        // Asus4 at X=0:  x 0 2 2 3 0
        CagedShape.A to listOf(null,  0, +2, +2, +3,  0),
        // Gsus4 at X=3:  3 3 0 0 1 3
        CagedShape.G to listOf(   0,  0, -3, -3, -2,  0),
        // Esus4 at X=0:  0 2 2 2 0 0
        CagedShape.E to listOf(   0, +2, +2, +2,  0,  0),
        // Dsus4 at X=0:  x x 0 2 3 3
        CagedShape.D to listOf(null, null,  0, +2, +3, +3),
    )

    // ===== 6 (1 3 5 6) =====
    val sixth: Map<CagedShape, List<Int?>> = mapOf(
        // C6 at X=3:  x 3 2 2 1 0   (omits the 5th on top — Joe Pass voicing)
        CagedShape.C to listOf(null,  0, -1, -1, -2, -3),
        // A6 at X=0:  x 0 2 2 2 2
        CagedShape.A to listOf(null,  0, +2, +2, +2, +2),
        // G6 at X=3:  3 2 0 0 0 0
        CagedShape.G to listOf(   0, -1, -3, -3, -3, -3),
        // E6 at X=0:  0 2 2 1 2 0
        CagedShape.E to listOf(   0, +2, +2, +1, +2,  0),
        // D6 at X=0:  x x 0 2 0 2
        CagedShape.D to listOf(null, null,  0, +2,  0, +2),
    )

    // ===== m6 (1 b3 5 6) =====
    val minor6: Map<CagedShape, List<Int?>> = mapOf(
        // Cm6 at X=3:  x 3 1 2 1 3
        CagedShape.C to listOf(null,  0, -2, -1, -2,  0),
        // Am6 at X=0:  x 0 2 2 1 2
        CagedShape.A to listOf(null,  0, +2, +2, +1, +2),
        // Gm6 at X=3:  3 1 2 0 x 0   (B muted, no P5 — uses 6 instead)
        CagedShape.G to listOf(   0, -2, -1, -3, null, -3),
        // Em6 at X=0:  0 2 2 0 2 0
        CagedShape.E to listOf(   0, +2, +2,  0, +2,  0),
        // Dm6 at X=0:  x x 0 2 0 1
        CagedShape.D to listOf(null, null,  0, +2,  0, +1),
    )

    // ===== add9 (1 3 5 9) =====
    val add9: Map<CagedShape, List<Int?>> = mapOf(
        // Cadd9 at X=3:  x 3 2 0 3 0
        CagedShape.C to listOf(null,  0, -1, -3,  0, -3),
        // Aadd9 at X=0:  x 0 2 4 2 0    (9 on G-string, P5 on A and B, root on A and e)
        CagedShape.A to listOf(null,  0, +2, +4, +2,  0),
        // Gadd9 at X=3:  3 2 0 2 0 3
        CagedShape.G to listOf(   0, -1, -3, -1, -3,  0),
        // Eadd9 at X=0:  0 2 4 1 0 0
        CagedShape.E to listOf(   0, +2, +4, +1,  0,  0),
        // Dadd9 at X=0:  x x 0 2 3 0
        CagedShape.D to listOf(null, null,  0, +2, +3,  0),
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
        "dim"         -> dim
        "aug"         -> aug
        "sus2"        -> sus2
        "sus4"        -> sus4
        "6"           -> sixth
        "m6"          -> minor6
        "9"           -> ninth
        "add9"        -> add9
        "13"          -> thirteen
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
        cagedShape = shape,
    )
}
