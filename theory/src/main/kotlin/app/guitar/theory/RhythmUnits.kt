package app.guitar.theory

/** Notation type of one note in a rhythmic unit — drives how it's DRAWN
 *  (notehead/stem/beam/flag/dot); playback only cares about the onset. */
enum class RhythmNoteType { Quarter, DottedEighth, Eighth, Sixteenth, TripletEighth }

/** One note of a [RhythmUnit]: its duration in grid slots + its notation type. */
data class RNote(val slots: Int, val type: RhythmNoteType)

/**
 * One basic one-beat rhythmic unit — an ordered list of [notes] whose slot
 * durations sum to one beat ([subdivision] slots). Note starts are the click
 * onsets; the [count] string is the spoken counting ("1 e & a").
 */
data class RhythmUnit(
    val id: String,
    val name: String,
    val count: String,
    val subdivision: Int,     // 4 = sixteenth grid, 3 = triplet
    val notes: List<RNote>,
) {
    init {
        require(notes.sumOf { it.slots } == subdivision) {
            "unit $id notes must fill one beat ($subdivision slots)"
        }
    }

    /** Slot index (0-based) where each note begins — i.e. the click onsets. */
    val onsets: List<Int>
        get() {
            val out = ArrayList<Int>(notes.size)
            var acc = 0
            for (n in notes) { out.add(acc); acc += n.slots }
            return out
        }

    /** Fraction (0..1) within the beat of each onset — for scheduling + drawing. */
    fun onsetFractions(): List<Double> = onsets.map { it.toDouble() / subdivision }

    /** Fraction (0..1) within the beat where each note STARTS and ENDS —
     *  (start, end) pairs, used by the notation renderer for beam spans. */
    fun noteSpans(): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>(notes.size)
        var acc = 0
        for (n in notes) {
            out.add(acc.toDouble() / subdivision to (acc + n.slots).toDouble() / subdivision)
            acc += n.slots
        }
        return out
    }
}

/** The 8 basic one-beat rhythmic units, in teaching order. */
object RhythmUnits {
    private fun rn(slots: Int, sub: Int): RNote = RNote(
        slots,
        when {
            sub == 3 -> RhythmNoteType.TripletEighth
            slots >= 4 -> RhythmNoteType.Quarter
            slots == 3 -> RhythmNoteType.DottedEighth
            slots == 2 -> RhythmNoteType.Eighth
            else -> RhythmNoteType.Sixteenth
        },
    )

    private fun unit(id: String, name: String, count: String, sub: Int, vararg slots: Int) =
        RhythmUnit(id, name, count, sub, slots.map { rn(it, sub) })

    val ALL: List<RhythmUnit> = listOf(
        unit("quarter", "Quarter", "1", 4, 4),
        unit("two-eighths", "Two eighths", "1  &", 4, 2, 2),
        unit("four-sixteenths", "Four sixteenths", "1 e & a", 4, 1, 1, 1, 1),
        unit("eighth-two-sixteenths", "Eighth + two sixteenths", "1  & a", 4, 2, 1, 1),
        unit("two-sixteenths-eighth", "Two sixteenths + eighth", "1 e &", 4, 1, 1, 2),
        unit("sixteenth-eighth-sixteenth", "Sixteenth–eighth–sixteenth", "1 e   a", 4, 1, 2, 1),
        unit("dotted-eighth-sixteenth", "Dotted eighth + sixteenth", "1     a", 4, 3, 1),
        unit("eighth-triplet", "Eighth-note triplet", "1 trip let", 3, 1, 1, 1),
    )

    fun byId(id: String): RhythmUnit? = ALL.firstOrNull { it.id == id }
}
