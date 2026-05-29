package app.guitar.theory

class ChordShapeGenerator(
    val maxFretSpan: Int = 4,
    val requireAllChordTones: Boolean = true,
    val minStringsPlayed: Int = 3,
    val style: VoicingStyle = VoicingStyle.Standard,
) {
    fun shapesFor(
        root: PitchClass,
        quality: ChordQuality,
        tuning: Tuning,
        frets: Int,
        fretRange: IntRange? = null,
    ): List<ChordShape> {
        require(frets >= maxFretSpan) { "frets ($frets) must be >= maxFretSpan ($maxFretSpan)" }
        val chordPcs: Set<PitchClass> = quality.notesFrom(root).toSet()
        val essentialPcs: Set<PitchClass> = when (style) {
            VoicingStyle.Standard -> chordPcs   // require every chord tone
            VoicingStyle.Shell -> essentialShellIntervals(quality).map { root + it }.toSet()
        }
        val firstFret = (fretRange?.first ?: 0).coerceAtLeast(0)
        val lastFret = (fretRange?.last ?: frets).coerceAtMost(frets)
        if (firstFret > lastFret) return emptyList()

        val seen = HashSet<List<Int?>>()
        val results = ArrayList<ChordShape>()
        val chordName = "${NoteSpeller.spell(root)}${quality.symbol}"

        // Enumerate every anchor window. The window covers [anchor..anchor+maxFretSpan].
        // Open strings (fret 0) are always candidates regardless of anchor, since
        // they don't require finger placement in the window.
        val maxAnchor = (lastFret - maxFretSpan).coerceAtLeast(0)
        val anchorStart = if (firstFret == 0) 0 else firstFret
        for (anchor in anchorStart..maxAnchor) {
            val windowLo = maxOf(anchor, 1, firstFret)
            val windowHi = minOf(anchor + maxFretSpan, lastFret)

            // Per-string candidate lists. Each list contains the fret values
            // (Int) we may pick, plus null for "muted".
            val candidates: List<List<Int?>> = (0 until tuning.stringCount).map { s ->
                val perString = ArrayList<Int?>(8)
                perString.add(null) // muted always allowed
                // Open string allowed if its pitch class is in the chord and 0 is in range
                if (firstFret == 0) {
                    val openPc = tuning.openStrings[s].pitchClass
                    if (openPc in chordPcs) perString.add(0)
                }
                // Fretted notes in the window
                for (f in windowLo..windowHi) {
                    val pc = Fretboard.noteAt(tuning, FretPosition(s, f)).pitchClass
                    if (pc in chordPcs) perString.add(f)
                }
                perString
            }

            enumerate(candidates) { shapeFrets ->
                if (!isValid(shapeFrets, chordPcs, essentialPcs, tuning)) return@enumerate
                if (!seen.add(shapeFrets)) return@enumerate
                results.add(
                    ChordShape(
                        chordName = chordName,
                        root = root,
                        quality = quality,
                        frets = shapeFrets,
                        tuning = tuning,
                    )
                )
            }
        }

        return results.sortedWith(
            compareByDescending<ChordShape> { it.hasRootInBass }
                .thenBy { it.position }
                .thenBy { it.mutedCount }
                .thenBy { it.fretSpan }
        )
    }

    private fun isValid(
        shapeFrets: List<Int?>,
        chordPcs: Set<PitchClass>,
        essentialPcs: Set<PitchClass>,
        tuning: Tuning,
    ): Boolean {
        var played = 0
        var minFretted = Int.MAX_VALUE
        var maxFretted = Int.MIN_VALUE
        val playedPcs = HashSet<PitchClass>()
        for (i in shapeFrets.indices) {
            val f = shapeFrets[i] ?: continue
            played++
            if (f > 0) {
                if (f < minFretted) minFretted = f
                if (f > maxFretted) maxFretted = f
            }
            playedPcs.add(Fretboard.noteAt(tuning, FretPosition(i, f)).pitchClass)
        }
        // In Shell mode we allow fewer strings (2 jazz "guide tones" voicings are valid).
        val minStrings = if (style == VoicingStyle.Shell) 2 else minStringsPlayed
        if (played < minStrings) return false
        if (minFretted != Int.MAX_VALUE) {
            val span = maxFretted - minFretted
            if (span > maxFretSpan) return false
        }
        // All-chord-tones rule applies in Standard mode only.
        if (style == VoicingStyle.Standard && requireAllChordTones &&
            !playedPcs.containsAll(chordPcs)) return false
        // Essential tones must always be present (chordPcs in Standard, shell subset in Shell).
        if (!playedPcs.containsAll(essentialPcs)) return false
        return true
    }

    private inline fun enumerate(
        candidates: List<List<Int?>>,
        crossinline action: (List<Int?>) -> Unit,
    ) {
        val n = candidates.size
        val indices = IntArray(n)
        val current = arrayOfNulls<Int?>(n)
        outer@ while (true) {
            for (i in 0 until n) {
                current[i] = candidates[i][indices[i]]
            }
            @Suppress("UNCHECKED_CAST")
            action((current as Array<Int?>).toList())
            // Increment indices like an odometer
            var i = n - 1
            while (i >= 0) {
                indices[i]++
                if (indices[i] < candidates[i].size) break
                indices[i] = 0
                i--
            }
            if (i < 0) break@outer
        }
    }
}
