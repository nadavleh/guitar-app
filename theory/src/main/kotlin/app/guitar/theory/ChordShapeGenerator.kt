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
        // Prefer the canonical chord-dictionary voicings when they exist for this
        // (style, quality, tuning) and the user didn't restrict to a sub-range:
        //  - Standard → 5 CAGED shapes spread along the neck.
        //  - Shell    → jazz drop-2 voicings (jazzguitar.be dictionary). When the
        //    quality has no jazz drop-2 table (triads, suspended, augmented), we
        //    fall back to CAGED rather than the brute-force generator — otherwise
        //    Shell mode clusters voicings in one fret area for these chords.
        // The brute-force fallback below applies only when neither canonical table
        // has anything for the (quality, tuning) combination.
        if (fretRange == null) {
            // Cavaquinho (4-string) has its own curated voicings — try them first
            // regardless of voicing style, since CAGED is a guitar-only system.
            if (tuning.stringCount == 4) {
                val cavaq = cavaquinhoShapesFor(root, quality, tuning, frets)
                if (cavaq.isNotEmpty()) return cavaq
            }
            val canonical = when (style) {
                VoicingStyle.Standard -> cagedShapesFor(root, quality, tuning, frets)
                VoicingStyle.Shell -> {
                    val jazz = jazzShellVoicingsFor(root, quality, tuning, frets)
                    if (jazz.isNotEmpty()) jazz
                    else cagedShapesFor(root, quality, tuning, frets)
                }
            }
            if (canonical.isNotEmpty()) return canonical
        }
        val chordPcs: Set<PitchClass> = quality.notesFrom(root).toSet()
        val essentialPcs: Set<PitchClass> = when (style) {
            // Standard: every chord tone EXCEPT the perfect 5th, which is optional once the
            // chord has 4+ tones (so 7ths form compact closed voicings). Triads keep all.
            VoicingStyle.Standard -> {
                val fifth = PitchClass((root.value + 7) % 12)
                if (chordPcs.size >= 4 && fifth in chordPcs) chordPcs - fifth else chordPcs
            }
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
                if (!isValid(shapeFrets, chordPcs, essentialPcs, tuning, root)) return@enumerate
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

        val ranked = results.sortedWith(
            compareByDescending<ChordShape> { it.hasRootInBass }
                .thenBy { it.position }
                .thenBy { it.mutedCount }
                .thenBy { it.fretSpan }
        )
        // 4-string instruments (cavaquinho): present a CAGED-like canonical set. Prefer
        // full (no-mute) voicings — muting should be rare — then keep the single best
        // voicing at each distinct neck position and cap at 5, so the position scroller
        // steps through up to 5 canonical shapes spread along the neck. (6-string guitar
        // keeps every voicing.)
        if (tuning.stringCount != 4) return ranked
        val full = ranked.filter { it.mutedCount == 0 }
        val base = if (full.isNotEmpty()) full else ranked.filter { it.mutedCount <= 1 }
        val bestPerPosition = LinkedHashMap<Int, ChordShape>()
        for (sh in base) bestPerPosition.putIfAbsent(sh.position, sh)
        return bestPerPosition.values.sortedBy { it.position }.take(5)
    }

    private fun isValid(
        shapeFrets: List<Int?>,
        chordPcs: Set<PitchClass>,
        essentialPcs: Set<PitchClass>,
        tuning: Tuning,
        root: PitchClass,
    ): Boolean {
        var played = 0
        var minFretted = Int.MAX_VALUE
        var maxFretted = Int.MIN_VALUE
        var hasOpen = false
        val playedPcs = HashSet<PitchClass>()
        val midis = arrayOfNulls<Int>(shapeFrets.size)
        for (i in shapeFrets.indices) {
            val f = shapeFrets[i] ?: continue
            played++
            if (f == 0) hasOpen = true
            if (f > 0) {
                if (f < minFretted) minFretted = f
                if (f > maxFretted) maxFretted = f
            }
            val note = Fretboard.noteAt(tuning, FretPosition(i, f))
            midis[i] = note.midi.value
            playedPcs.add(note.pitchClass)
        }
        // In Shell mode we allow fewer strings (2 jazz "guide tones" voicings are valid).
        val minStrings = if (style == VoicingStyle.Shell) 2 else minStringsPlayed
        if (played < minStrings) return false
        // Don't double the SAME note (unison) on two physically adjacent strings.
        for (i in 0 until shapeFrets.size - 1) {
            val a = midis[i]; val b = midis[i + 1]
            if (a != null && b != null && a == b) return false
        }
        // An open string only makes sense in first position: a shape may NOT combine
        // an open string (fret 0) with any note fretted above the 3rd fret.
        if (hasOpen && maxFretted != Int.MIN_VALUE && maxFretted > 3) return false
        if (minFretted != Int.MAX_VALUE) {
            val span = maxFretted - minFretted
            // Cap the fretted span at maxFretSpan; hard cap 5 (guitar) / 4 (cavaquinho, 4-string).
            val hardCap = if (tuning.stringCount == 4) 4 else 5
            if (span > maxFretSpan || span > hardCap) return false
        }
        // All-chord-tones rule (Standard mode). The tonic is mandatory; the PERFECT 5th
        // is optional whenever the chord has 4+ tones and actually contains one — this
        // lets 7ths (and 6ths/extensions) form the compact closed voicings a cavaquinho
        // player uses (many drop the 5th), instead of wide 4-fret grips. Triads keep all
        // three tones; diminished / m7b5 keep their (flatted) 5th, which is a defining tone.
        if (style == VoicingStyle.Standard && requireAllChordTones) {
            val fifth = PitchClass((root.value + 7) % 12)
            val need = if (chordPcs.size >= 4 && fifth in chordPcs) chordPcs - fifth else chordPcs
            if (!playedPcs.containsAll(need)) return false
        }
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
