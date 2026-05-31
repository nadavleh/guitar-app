package app.guitar.theory

/**
 * Curated 4-string chord voicings for the cavaquinho.
 *
 * The Portuguese **DGBe** tuning is intervallically identical to the guitar's
 * top 4 strings, one octave higher — so the canonical drop-2 voicings we
 * already use for guitar transplant directly. The Brazilian **DGBD** tuning's
 * re-entrant high-D string changes which voicings sit comfortably; for now
 * (Phase 2) DGBe is curated and DGBD falls through to the brute-force
 * generator with `maxFretSpan = 5`.
 *
 * Per-template structure mirrors [JazzVoicing]: [rootString] is which of the 4
 * strings (0 = low D, 3 = high) holds the chord root, and [offsets] gives the
 * fret on each string relative to the root fret X on [rootString].
 */
internal data class CavaqVoicing(
    val name: String,
    val rootString: Int,        // 0..3
    val offsets: List<Int?>,    // exactly 4 entries (4 strings)
)

internal object CavaquinhoTemplates {

    // ============================================================
    //  DGBe — D4 G4 B4 E5 (Portuguese standard).
    //  Same fret patterns as the top 4 strings of a guitar (D G B e).
    //  Voicings are the standard jazz drop-2 dictionary, adapted to 4 strings.
    // ============================================================
    object DGBe {

        // ---- Major triad: R, 3, 5 ----
        val major = listOf(
            // C-shape: Cmaj at X=1 (B-1=C):  2 0 1 0
            //   D-2=E(3) · G-0=G(5) · B-1=C(R) · e-0=E(3)
            CavaqVoicing("major (C-shape, 3-5-R-3)",   rootString = 2,
                offsets = listOf(+1, -1,  0, -1)),
            // A-shape barre: Cmaj at X=5 (G-5=C):  5 5 5 3
            //   D-5=G(5) · G-5=C(R) · B-5=E(3) · e-3=G(5)
            CavaqVoicing("major (A-shape, 5-R-3-5)",   rootString = 1,
                offsets = listOf( 0,  0,  0, -2)),
            // D-shape: Cmaj at X=10 (D-10=C):  10 9 8 8
            //   D-10=C(R) · G-9=E(3) · B-8=G(5) · e-8=C(R)
            CavaqVoicing("major (D-shape, R-3-5-R)",   rootString = 0,
                offsets = listOf( 0, -1, -2, -2)),
        )

        // ---- Minor triad: R, ♭3, 5 ----
        val minor = listOf(
            // A-shape barre: Cm at X=5 (G-5=C):  5 5 4 3
            //   D-5=G(5) · G-5=C(R) · B-4=E♭(♭3) · e-3=G(5)
            CavaqVoicing("minor (A-shape, 5-R-b3-5)",  rootString = 1,
                offsets = listOf( 0,  0, -1, -2)),
            // E-shape barre: Cm at X=10 (D-10=C):  10 8 8 8
            //   D-10=C(R) · G-8=E♭(♭3) · B-8=G(5) · e-8=C(R)
            CavaqVoicing("minor (E-shape, R-b3-5-R)",  rootString = 0,
                offsets = listOf( 0, -2, -2, -2)),
        )

        // ---- Dominant 7: R, 3, 5, ♭7 (drop-2 inversions) ----
        val dom7 = listOf(
            CavaqVoicing("7 root-pos (5-R-3-♭7)",      rootString = 1,
                offsets = listOf( 0,  0,  0, +1)),
            CavaqVoicing("7 1st-inv (♭7-3-5-R)",       rootString = 3,
                offsets = listOf( 0, +1,  0,  0)),
            CavaqVoicing("7 2nd-inv (R-5-♭7-3)",       rootString = 0,
                offsets = listOf( 0, +2, +1, +2)),
            CavaqVoicing("7 3rd-inv (3-♭7-R-5)",       rootString = 2,
                offsets = listOf(+1, +2,  0, +2)),
            // Rootless: 3-♭7-3-5 with the root dropped.
            //   For C7 at X=5 (G-5=C):  2 3 5 3
            //     D-2=E(3) · G-3=B♭(♭7) · B-5=E(3 oct) · e-3=G(5) — every note is a C7 chord tone.
            CavaqVoicing("7 rootless (3-♭7-3-5)",      rootString = 1,
                offsets = listOf(-3, -2,  0, -2)),
        )

        // ---- Major 7: R, 3, 5, 7 ----
        val maj7 = listOf(
            CavaqVoicing("maj7 root-pos (5-R-3-7)",    rootString = 1,
                offsets = listOf( 0,  0,  0, +2)),
            CavaqVoicing("maj7 1st-inv (7-3-5-R)",     rootString = 3,
                offsets = listOf(+1, +1,  0,  0)),
            CavaqVoicing("maj7 2nd-inv (R-5-7-3)",     rootString = 0,
                offsets = listOf( 0, +2, +2, +2)),
            CavaqVoicing("maj7 3rd-inv (3-7-R-5)",     rootString = 2,
                offsets = listOf(+1, +3,  0, +2)),
            // Rootless: 3-5-7-3 with the root dropped.
            //   For Cmaj7 at X=5 (G-5=C):  2 0 0 0
            //     D-2=E(3) · G-0=G(5) · B-0=B(7) · e-0=E(3 oct) — every note is a Cmaj7 chord tone.
            CavaqVoicing("maj7 rootless (3-5-7-3)",    rootString = 1,
                offsets = listOf(-3, -5, -5, -5)),
        )

        // ---- Minor 7: R, ♭3, 5, ♭7 ----
        val m7 = listOf(
            CavaqVoicing("m7 root-pos (5-R-♭3-♭7)",    rootString = 1,
                offsets = listOf( 0,  0, -1, +1)),
            // Freddie-Green box — all four strings on the same fret.
            CavaqVoicing("m7 Freddie-Green (♭7-♭3-5-R)", rootString = 3,
                offsets = listOf( 0,  0,  0,  0)),
            CavaqVoicing("m7 2nd-inv (R-5-♭7-♭3)",     rootString = 0,
                offsets = listOf( 0, +2, +1, +1)),
            CavaqVoicing("m7 3rd-inv (♭3-♭7-R-5)",     rootString = 2,
                offsets = listOf( 0, +2,  0, +2)),
            // Rootless: ♭3-♭7-♭3-5 with the root dropped.
            //   For Cm7 at X=5 (G-5=C):  1 3 4 3
            //     D-1=E♭(♭3) · G-3=B♭(♭7) · B-4=E♭(♭3 oct) · e-3=G(5) — all Cm7 chord tones.
            CavaqVoicing("m7 rootless (♭3-♭7-♭3-5)",   rootString = 1,
                offsets = listOf(-4, -2, -1, -2)),
        )

        // ---- Half-diminished m7♭5: R, ♭3, ♭5, ♭7 ----
        val m7b5 = listOf(
            CavaqVoicing("m7♭5 root-pos (♭5-R-♭3-♭7)", rootString = 1,
                offsets = listOf(-1,  0, -1, +1)),
            CavaqVoicing("m7♭5 1st-inv (♭7-♭3-♭5-R)",  rootString = 3,
                offsets = listOf( 0,  0, -1,  0)),
            CavaqVoicing("m7♭5 2nd-inv (R-♭5-♭7-♭3)",  rootString = 0,
                offsets = listOf( 0, +1, +1, +1)),
            CavaqVoicing("m7♭5 3rd-inv (♭3-♭7-R-♭5)",  rootString = 2,
                offsets = listOf( 0, +2,  0, +1)),
            // 5-fret stretch with root on the low D string (the user's example):
            //   Am7♭5 = 7 5 4 3. D-7=A(R), G-5=C(♭3), B-4=E♭(♭5), e-3=G(♭7).
            //   Anchor: rootString=0 (D), X=7 for A. Offsets: 0, -2, -3, -4.
            CavaqVoicing("m7♭5 (5-fret stretch, R-♭3-♭5-♭7)", rootString = 0,
                offsets = listOf( 0, -2, -3, -4)),
        )

        // ---- Diminished 7: R, ♭3, ♭5, ♭♭7 (symmetric, repeats every 3 frets) ----
        val dim7 = listOf(
            CavaqVoicing("dim7 (R on B-string)",       rootString = 2,
                offsets = listOf( 0, +1,  0, +1)),
            // 5-fret stretch with root on the low D string — the strict-dim7
            // counterpart of the m7♭5 voicing above (♭♭7 instead of ♭7).
            //   Adim7 = 7 5 4 2. D-7=A(R), G-5=C(♭3), B-4=E♭(♭5), e-2=F♯(♭♭7).
            //   Anchor: rootString=0 (D), X=7 for A. Offsets: 0, -2, -3, -5.
            CavaqVoicing("dim7 (5-fret stretch, R-♭3-♭5-♭♭7)", rootString = 0,
                offsets = listOf( 0, -2, -3, -5)),
        )

        // ---- 6: R, 3, 5, 6 ----
        val sixth = listOf(
            CavaqVoicing("6 root-pos (5-R-3-6)",       rootString = 1,
                offsets = listOf( 0,  0,  0,  0)),
            CavaqVoicing("6 1st-inv (6-3-5-R)",        rootString = 3,
                offsets = listOf(-1, +1,  0,  0)),
            CavaqVoicing("6 2nd-inv (R-5-6-3)",        rootString = 0,
                offsets = listOf( 0, +2,  0, +2)),
            CavaqVoicing("6 3rd-inv (3-6-R-5)",        rootString = 2,
                offsets = listOf(+1, +1,  0, +2)),
        )

        // ---- m6: R, ♭3, 5, 6 ----
        val minor6 = listOf(
            CavaqVoicing("m6 root-pos (5-R-♭3-6)",     rootString = 1,
                offsets = listOf( 0,  0, -1,  0)),
            CavaqVoicing("m6 1st-inv (6-♭3-5-R)",      rootString = 3,
                offsets = listOf(-1,  0,  0,  0)),
            CavaqVoicing("m6 2nd-inv (R-5-6-♭3)",      rootString = 0,
                offsets = listOf( 0, +2,  0, +1)),
            CavaqVoicing("m6 3rd-inv (♭3-6-R-5)",      rootString = 2,
                offsets = listOf( 0, +1,  0, +2)),
        )

        fun forQuality(symbol: String): List<CavaqVoicing>? = when (symbol) {
            "", "maj"     -> major
            "m", "min"    -> minor
            "7"           -> dom7
            "maj7"        -> maj7
            "m7", "min7"  -> m7
            "m7b5"        -> m7b5
            "dim7"        -> dim7
            "6"           -> sixth
            "m6"          -> minor6
            else          -> null
        }
    }
}

/** Realize the curated cavaquinho voicings for [root] [quality] in [tuning].
 *  Returns an empty list when the tuning isn't a curated cavaquinho preset OR
 *  the quality has no curated voicings — callers should then fall through to
 *  the brute-force generator. */
fun cavaquinhoShapesFor(
    root: PitchClass,
    quality: ChordQuality,
    tuning: Tuning,
    maxFrets: Int,
): List<ChordShape> {
    val table: List<CavaqVoicing> = when (tuning) {
        Tunings.cavaqDgbe -> CavaquinhoTemplates.DGBe.forQuality(quality.symbol) ?: return emptyList()
        else              -> return emptyList()    // DGBD + custom → brute force
    }
    val out = ArrayList<ChordShape>(table.size)
    for (v in table) {
        val s = realizeCavaqVoicing(v, root, quality, tuning, maxFrets) ?: continue
        out.add(s)
    }
    return out.sortedWith(
        compareBy({ it.position }, { it.frets.filterNotNull().maxOrNull() ?: 0 })
    )
}

private fun realizeCavaqVoicing(
    v: CavaqVoicing,
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
