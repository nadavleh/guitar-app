package app.guitar.theory

/** One movable cavaquinho form of a chord. [bass] = semitones (0..11) of the LOWEST
 *  sounding note above the root, which is what names the form ("3 in the bass").
 *  [branches] are the form's branch-outs: the same grip with ONE string moved to
 *  another chord tone within reach (C's 5 5 5 5 → 2 5 5 5, the 3rd in the bass). */
data class CavaqForm(val bass: Int, val shape: ChordShape, val branches: List<ChordShape> = emptyList())

/**
 * The cavaquinho's "CAGED": every chord has one movable four-string form per chord tone
 * in the bass — 3 for a triad (R, 3, 5 in the bass), 4 for a four-note chord. A chord
 * with more than four tones drops the 5th, then the 9th, then the 11th, until four
 * remain (C13 → R 3 ♭7 13).
 *
 * Each form is the most compact grip (all four strings fretted, no open strings — so it
 * MOVES; span ≤ 3 frets, widening to 4 then 5 only for a bass with no tighter grip) that sounds
 * exactly the form's tones. A form lying wholly at fret 12+ is shown an octave lower
 * when it fits, so G's 5-in-the-bass barre shows as the open 0 0 0 0.
 */
object CavaqForms {
    /** The chord types offered in the Shapes picker, in display order. */
    val QUALITIES: List<String> = listOf(
        "", "m", "7", "maj7", "m7", "6", "m6", "9", "maj9", "m9", "13", "add9",
        "sus4", "sus2", "7sus4", "mMaj7", "dim", "dim7", "m7b5", "aug", "7#5", "7b9",
    )

    /** Name of a chord tone by its semitones above the root ("R", "♭3", "5", "♭7"…). */
    fun bassLabel(semis: Int): String = when (((semis % 12) + 12) % 12) {
        0 -> "R"; 1 -> "♭9"; 2 -> "9"; 3 -> "♭3"; 4 -> "3"; 5 -> "4"; 6 -> "♭5"
        7 -> "5"; 8 -> "♯5"; 9 -> "6"; 10 -> "♭7"; else -> "7"
    }

    /** The (≤ 4) tones the forms sound, as semitones mod 12 above the root. */
    fun formTones(quality: ChordQuality): Set<Int> {
        var tones = quality.intervals.map { ((it.semitones % 12) + 12) % 12 }.toSet()
        for (drop in listOf(7, 2, 5)) if (tones.size > 4 && drop in tones) tones = tones - drop
        return tones
    }

    fun formsFor(root: PitchClass, quality: ChordQuality, tuning: Tuning, maxFret: Int = 15): List<CavaqForm> {
        val tones = formTones(quality)
        val pcs = tones.map { (root.value + it) % 12 }.toSet()
        val n = tuning.stringCount
        val opens = tuning.openStrings.map { it.midi.value }
        val name = "${NoteSpeller.spell(root)}${quality.symbol}"
        val found = HashMap<Int, List<Int>>()
        for (maxSpan in listOf(3, 4, 5)) {
            val best = HashMap<Int, Pair<Pair<Int, Int>, List<Int>>>()
            val frets = IntArray(n) { 1 }
            while (true) {
                val lo = frets.min(); val hi = frets.max()
                if (hi - lo <= maxSpan) {
                    val midis = frets.indices.map { opens[it] + frets[it] }
                    val unison = (0 until n - 1).any { midis[it] == midis[it + 1] }
                    if (!unison && midis.map { it % 12 }.toSet() == pcs) {
                        val bass = ((midis.min() % 12 - root.value) % 12 + 12) % 12
                        val key = (hi - lo) to lo
                        val cur = best[bass]
                        if (bass !in found && (cur == null || compareKey(key, cur.first) < 0))
                            best[bass] = key to frets.toList()
                    }
                }
                var i = n - 1
                while (i >= 0) { frets[i]++; if (frets[i] <= maxFret) break; frets[i] = 1; i-- }
                if (i < 0) break
            }
            best.forEach { (b, v) -> found[b] = v.second }
            if (found.size == tones.size) break
        }
        val mains = found.mapValues { (_, fr) -> if (fr.min() >= 12) fr.map { it - 12 } else fr }
        val mainGrips = mains.values.toSet()
        fun sounds(fr: List<Int>): List<Int> = fr.indices.map { opens[it] + fr[it] }
        fun bassOf(fr: List<Int>) = ((sounds(fr).min() % 12 - root.value) % 12 + 12) % 12
        return mains.map { (bass, fr) ->
            // Branch-outs: move ONE string to another chord tone. Still every form tone,
            // still fretted (an open string only when the form itself sits at the nut),
            // a ≤ 4-fret hand, no unison on adjacent strings, and not another main form.
            val minFret = if (fr.min() == 0) 0 else 1
            val branches = LinkedHashSet<List<Int>>()
            for (s in 0 until n) for (f in minFret..maxFret) {
                if (f == fr[s] || (opens[s] + f) % 12 !in pcs) continue
                val alt = fr.toMutableList().also { it[s] = f }
                val fretted = alt.filter { it > 0 }
                if (fretted.isNotEmpty() && fretted.max() - fretted.min() > 4) continue
                val m = sounds(alt)
                if ((0 until n - 1).any { m[it] == m[it + 1] }) continue
                if (m.map { it % 12 }.toSet() != pcs || alt in mainGrips) continue
                branches += alt
            }
            CavaqForm(bass, ChordShape(name, root, quality, fr, tuning,
                templateName = "${bassLabel(bass)} in the bass"),
                branches.map { ChordShape(name, root, quality, it, tuning,
                    templateName = "${bassLabel(bassOf(it))} in the bass") })
        }.sortedWith(compareBy({ it.shape.frets.filterNotNull().min() }, { it.bass }))
    }

    private fun compareKey(a: Pair<Int, Int>, b: Pair<Int, Int>): Int =
        if (a.first != b.first) a.first - b.first else a.second - b.second

    /** Drop a grip that only adds open strings under ANOTHER grip's fretted notes (Dm's
     *  0 2 3 3 is 3 2 3 3 with the open D in the bass) — the same form, not a new one. */
    fun dropOpenStringCopies(shapes: List<ChordShape>): List<ChordShape> = shapes.filterNot { a ->
        val opens = a.frets.count { it == 0 }
        opens > 0 && shapes.any { b ->
            b.frets.count { it == 0 } < opens &&
                a.frets.indices.all { s -> val f = a.frets[s]; f == null || f == 0 || b.frets[s] == f }
        }
    }
}
