package app.guitar.theory

/** Notation type of one element in a rhythmic unit — drives how it's DRAWN
 *  (notehead/rest-glyph, stem, beam/flag, dot). For a rest, the type still encodes
 *  its DURATION (Eighth = eighth rest, Sixteenth = sixteenth rest, …). */
enum class RhythmNoteType { Quarter, DottedEighth, Eighth, Sixteenth, TripletEighth }

/** One element of a [RhythmUnit]: its duration in grid slots, its notation type, and
 *  whether it's a rest (silent — no click, drawn as a rest glyph). */
data class RNote(val slots: Int, val type: RhythmNoteType, val rest: Boolean = false)

/**
 * One basic one-beat rhythmic unit — an ordered list of [notes] (notes and/or rests)
 * whose slot durations sum to one beat ([subdivision] slots). Non-rest note starts are
 * the click onsets; the [count] string is the spoken counting of the PLAYED positions.
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

    /** Slot index (0-based) where each element begins (notes AND rests) — for drawing. */
    val starts: List<Int>
        get() {
            val out = ArrayList<Int>(notes.size)
            var acc = 0
            for (n in notes) { out.add(acc); acc += n.slots }
            return out
        }

    /** Fraction (0..1) within the beat where each PLAYED note starts — the clicks. */
    fun clickFractions(): List<Double> {
        val out = ArrayList<Double>()
        var acc = 0
        for (n in notes) {
            if (!n.rest) out.add(acc.toDouble() / subdivision)
            acc += n.slots
        }
        return out
    }
}

/** The basic one-beat rhythmic units. [ALL] = the plain (no-rest) units; [RESTS] =
 *  units that include eighth/sixteenth rests. */
object RhythmUnits {
    private fun typeOf(slots: Int, sub: Int): RhythmNoteType = when {
        sub == 3 -> RhythmNoteType.TripletEighth
        slots >= 4 -> RhythmNoteType.Quarter
        slots == 3 -> RhythmNoteType.DottedEighth
        slots == 2 -> RhythmNoteType.Eighth
        else -> RhythmNoteType.Sixteenth
    }

    /** Build a unit from signed slot counts: a NEGATIVE value is a rest of that many slots. */
    private fun unit(id: String, name: String, count: String, sub: Int, vararg slots: Int): RhythmUnit {
        val notes = slots.map { s ->
            val abs = kotlin.math.abs(s)
            RNote(abs, typeOf(abs, sub), rest = s < 0)
        }
        return RhythmUnit(id, name, count, sub, notes)
    }

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

    /** One-beat units that include rests. A negative slot count = a rest. Many sound the
     *  same as a plain unit under a clave click (which can't sustain), but the notation —
     *  and the feel of resting on a beat — is the point. */
    val RESTS: List<RhythmUnit> = listOf(
        unit("rest-eighth-eighthrest", "Eighth + eighth rest", "1", 4, 2, -2),
        unit("rest-eighthrest-eighth", "Eighth rest + eighth", "&", 4, -2, 2),
        unit("rest-two16-eighthrest", "Two sixteenths + eighth rest", "1 e", 4, 1, 1, -2),
        unit("rest-eighthrest-two16", "Eighth rest + two sixteenths", "& a", 4, -2, 1, 1),
        unit("rest-eighth-16-16rest", "Eighth, sixteenth, sixteenth rest", "1 &", 4, 2, 1, -1),
        unit("rest-eighth-16rest-16", "Eighth, sixteenth rest, sixteenth", "1 a", 4, 2, -1, 1),
        unit("rest-16-16rest-eighth", "Sixteenth, sixteenth rest, eighth", "1 &", 4, 1, -1, 2),
        unit("rest-16rest-16-eighth", "Sixteenth rest, sixteenth, eighth", "e &", 4, -1, 1, 2),
        unit("rest-three16-16rest", "Three sixteenths + sixteenth rest", "1 e &", 4, 1, 1, 1, -1),
        unit("rest-offbeat-16s", "Off-beat sixteenths", "e a", 4, -1, 1, -1, 1),
    )

    fun byId(id: String): RhythmUnit? = (ALL + RESTS).firstOrNull { it.id == id }
}
