package app.guitar.theory

/** One reference song for an interval: who/what, where to listen, and a short MIDI
 *  [melody] snippet that contains the interval.
 *
 *  [melody] is space-separated `offset/length` tokens: `offset` in semitones from the
 *  snippet's FIRST note, `length` in eighth notes (decimals allowed, e.g. `1.5`).
 *  Only a few notes — enough to hear the interval in context, nothing more. */
data class IntervalSong(
    val title: String,
    val artist: String,
    val cue: String,
    val melody: String,
)

/** Interval → reference-song lookup for the Theory tab and the interval trainer.
 *
 *  DESCENDING comes from Nadav's "Descending Interval Song References" PDF;
 *  ASCENDING is the canonical companion list Claude generated. [inversion] is the
 *  octave complement — NOT the same two pitches backward: major↔minor switch,
 *  perfect stays perfect, the tritone inverts to itself.
 *
 *  [semitones] drives in-app playback of the bare interval, so a row can always be
 *  HEARD even when it has no song (the descending major 7th).
 *  Mirrored in chorect-web intervalSongs.ts. */
data class IntervalSongRef(
    val ascending: Boolean,
    /** Short interval name, e.g. "m2", "P5", "TT", "P8". */
    val interval: String,
    val intervalLong: String,
    /** Distance in semitones, 1..12. */
    val semitones: Int,
    /** Octave-complement note, e.g. "inverts to M7 descending". */
    val inversion: String,
    /** Reference songs, best-known first. Empty only where no clean example exists. */
    val songs: List<IntervalSong>,
    /** Shown instead of songs when [songs] is empty. */
    val note: String = "",
)

/** One melody note: absolute MIDI pitch and length in eighth notes. */
data class MelodyNote(val midi: Int, val eighths: Double)

object IntervalSongs {

    const val COMPLEMENT_NOTE =
        "The inversion listed is the octave complement — not the same two pitches played backward. " +
            "Major and minor switch, perfect intervals stay perfect, the tritone inverts to itself."

    /** Playback length of one eighth note in a melody snippet (150 bpm). */
    const val EIGHTH_MS = 200

    private fun s(title: String, artist: String, cue: String, melody: String) =
        IntervalSong(title, artist, cue, melody)

    private fun asc(i: String, long: String, st: Int, inv: String, vararg songs: IntervalSong) =
        IntervalSongRef(true, i, long, st, inv, songs.toList())
    private fun desc(i: String, long: String, st: Int, inv: String, vararg songs: IntervalSong) =
        IntervalSongRef(false, i, long, st, inv, songs.toList())

    // Shared by two rows each (the snippet holds both intervals).
    private val ENTERTAINER_UP = s("The Entertainer", "Scott Joplin",
        "The E in the pickup leaping up to the high C.", "0/0.5 1/0.5 2/0.5 10/1 2/0.5 10/1 2/0.5 10/3")
    private val ENTERTAINER_DOWN = ENTERTAINER_UP.copy(cue = "The high C falling back to the E.")
    // “Some-where o-ver the rain-bow, way up high”: the octave up on “Some-where”, and
    // the octave down from “-bow” to “way”.
    private const val RAINBOW = "0/4 12/4 11/2 7/1 9/1 11/2 12/4 0/4 9/4 7/6"

    /** Ascending references (canonical picks). */
    val ASCENDING: List<IntervalSongRef> = listOf(
        asc("m2", "Minor 2nd", 1, "inverts to M7 descending",
            s("Jaws — main theme", "John Williams", "The rising two-note ostinato that opens the theme.",
                "0/4 1/4 0/3 1/3 0/2 1/2 0/1 1/1 0/1 1/1"),
            s("White Christmas", "Irving Berlin", "“I'm dream-” — the first step up.",
                "0/2 1/2 0/2 -1/2 0/2 1/2 2/2 3/6"),
            s("The Pink Panther Theme", "Henry Mancini", "Each sneaking pair of the opening.",
                "0/1 1/5 3/1 4/5 0/1 1/1 3/1 4/1 9/1 8/4")),
        asc("M2", "Major 2nd", 2, "inverts to m7 descending",
            s("Happy Birthday", "traditional", "The step up onto the second syllable of “birth-day”.",
                "0/1.5 0/0.5 2/2 0/2 5/2 4/4 0/1.5 0/0.5 2/2 0/2 7/2 5/4"),
            s("Frère Jacques", "traditional", "“Frè-re” — the first step.",
                "0/2 2/2 4/2 0/2 0/2 2/2 4/2 0/2 4/2 5/2 7/4"),
            s("Silent Night", "Franz Gruber", "“Si-lent” — the first two notes.",
                "0/3 2/1 0/2 -3/6 0/3 2/1 0/2 -3/6")),
        asc("m3", "Minor 3rd", 3, "inverts to M6 descending",
            s("Greensleeves", "traditional", "The opening upward leap of the tune.",
                "0/2 3/4 5/2 7/3 8/1 7/2 5/4 2/2 -2/3 0/1 2/2 3/4"),
            s("Smoke on the Water", "Deep Purple", "The first two notes of the riff.",
                "0/2 3/2 5/3 0/1 3/2 6/1 5/4 0/2 3/2 5/3 3/1 0/4"),
            s("Brahms' Lullaby", "Brahms", "“Lul-la-by” — up from the repeated note.",
                "0/1 0/1 3/4 0/1 0/1 3/4 0/1 3/1 8/2 7/3 5/1 5/2")),
        asc("M3", "Major 3rd", 4, "inverts to m6 descending",
            s("When the Saints Go Marching In", "traditional", "The first two notes of the melody.",
                "0/2 4/2 5/2 7/8 0/2 4/2 5/2 7/8"),
            s("Kumbaya", "traditional", "“Kum-ba-” — the first two notes.",
                "0/1 4/1 7/4 7/1 7/1 9/2 9/2 7/4")),
        asc("P4", "Perfect 4th", 5, "inverts to P5 descending",
            s("Bridal Chorus (“Here Comes the Bride”)", "Wagner", "The opening leap.",
                "0/2 5/1.5 5/0.5 5/4 0/2 7/1.5 4/0.5 5/4"),
            s("Amazing Grace", "traditional", "“A-ma-” — the pickup into the first beat.",
                "0/2 5/4 9/1 5/1 9/4 7/2 5/4 2/2 0/4"),
            s("Auld Lang Syne", "traditional", "“Should auld” — the pickup leap.",
                "0/2 5/3 5/1 5/2 9/2 7/3 5/1 7/2 9/2")),
        asc("TT", "Tritone", 6, "inverts to TT descending",
            s("Maria (West Side Story)", "Leonard Bernstein",
                "“Ma-ri-a” — the leap onto “ri”, which then resolves up a half step.",
                "0/1 6/3 7/6 0/1 6/3 7/6")),
        asc("P5", "Perfect 5th", 7, "inverts to P4 descending",
            s("Twinkle Twinkle Little Star", "traditional", "“Twin-kle, twin-kle” — up onto the second pair.",
                "0/2 0/2 7/2 7/2 9/2 9/2 7/4 5/2 5/2 4/2 4/2 2/2"),
            s("Star Wars — main theme", "John Williams", "The opening leap of the main theme.",
                "0/6 7/6 5/1 4/1 2/1 12/6 7/3 5/1 4/1 2/1 12/6 7/3")),
        asc("m6", "Minor 6th", 8, "inverts to M3 descending",
            ENTERTAINER_UP),
        asc("M6", "Major 6th", 9, "inverts to m3 descending",
            s("My Bonnie Lies Over the Ocean", "traditional", "“My Bon-” — the opening leap.",
                "0/2 9/3 7/1 5/2 7/2 5/2 2/2 0/2 -3/6"),
            s("NBC chimes", "NBC", "The first two chimes.", "0/2 9/2 5/4 0/2 9/2 5/4"),
            s("Jingle Bells (verse)", "traditional", "“Dash-ing” — the first two notes of the verse.",
                "0/1 9/1 7/1 5/1 0/4 0/0.5 0/0.5 0/1 9/1 7/1 5/1 2/4")),
        asc("m7", "Minor 7th", 10, "inverts to M2 descending",
            s("Somewhere (West Side Story)", "Leonard Bernstein", "“There's a place” — the wide opening leap.",
                "0/2 10/4 9/6 0/2 10/4 9/6")),
        asc("M7", "Major 7th", 11, "inverts to m2 descending",
            s("Somewhere Over the Rainbow", "Harold Arlen",
                "1st note to 3rd note: “Some-(where) o-” — up an octave, then down a half step.", RAINBOW)),
        asc("P8", "Perfect octave", 12, "inverts to unison",
            s("Somewhere Over the Rainbow", "Harold Arlen", "The octave leap on the very first two notes.",
                RAINBOW)),
    )

    /** Descending references (from the PDF). */
    val DESCENDING: List<IntervalSongRef> = listOf(
        desc("m2", "Minor 2nd", 1, "inverts to M7 ascending",
            s("Für Elise", "Beethoven", "The first two notes: E down to D#.",
                "0/1 -1/1 0/1 -1/1 0/1 -5/1 -2/1 -4/1 -9/3"),
            s("Joy to the World", "traditional", "“Joy to” — the first step down.",
                "0/2 -1/1.5 -3/0.5 -5/3 -7/1 -8/2 -10/2 -12/4")),
        desc("M2", "Major 2nd", 2, "inverts to m7 ascending",
            s("Mary Had a Little Lamb", "traditional", "The first two notes of the tune.",
                "0/3 -2/1 -4/2 -2/2 0/2 0/2 0/4 -2/2 -2/2 -2/4"),
            s("Three Blind Mice", "traditional", "“Three blind” — the first two notes.",
                "0/2 -2/2 -4/4 0/2 -2/2 -4/4")),
        desc("m3", "Minor 3rd", 3, "inverts to M6 ascending",
            s("Hey Jude", "The Beatles", "The drop across the first two sung syllables.",
                "0/4 -3/6 -3/1 0/1 2/2 -5/6"),
            s("Frosty the Snowman", "Gene Autry", "“Fros-ty” — the first two notes.",
                "0/4 -3/3 -2/1 0/2 5/4 4/1 5/1 7/2 5/2 4/2 2/2 0/4")),
        desc("M3", "Major 3rd", 4, "inverts to m6 ascending",
            s("Symphony No. 5", "Beethoven", "In the four-note motif, the third short note drops to the long one.",
                "0/1 0/1 0/1 -4/6 -2/1 -2/1 -2/1 -5/8")),
        desc("P4", "Perfect 4th", 5, "inverts to P5 ascending",
            s("Under Pressure", "Queen & David Bowie", "The exposed downward leap in the opening bass riff.",
                "0/1 0/1 0/1 0/1 0/1 0/1 -5/2 0/4 0/1 0/1 0/1 -5/2"),
            s("Eine kleine Nachtmusik", "Mozart", "The opening: down a fourth, twice.",
                "0/3 -5/1 0/3 -5/1 0/1 -5/1 0/1 4/1 7/4")),
        desc("TT", "Tritone", 6, "inverts to TT ascending",
            s("Black Sabbath", "Black Sabbath", "The riff: low note, its octave, then down a tritone.",
                "0/4 12/4 6/8 0/4 12/4 6/8")),
        desc("P5", "Perfect 5th", 7, "inverts to P4 ascending",
            s("Game of Thrones — main theme", "Ramin Djawadi", "The prominent descending-fifth gesture of the theme.",
                "0/2 -7/2 -4/1 -2/1 0/2 -7/2 -4/1 -2/1 0/2 -7/2 -4/1 -2/1")),
        desc("m6", "Minor 6th", 8, "inverts to M3 ascending",
            ENTERTAINER_DOWN),
        IntervalSongRef(false, "M6", "Major 6th", 9, "inverts to m3 ascending", emptyList(),
            note = "No song whose melody can be transcribed reliably yet. The ▶ plays the interval; " +
                "it is the octave complement of the ascending m3 (Greensleeves, Smoke on the Water)."),
        IntervalSongRef(false, "m7", "Minor 7th", 10, "inverts to M2 ascending", emptyList(),
            note = "No song whose melody can be transcribed reliably yet. The ▶ plays the interval; " +
                "it is the octave complement of the ascending M2 (Happy Birthday, Frère Jacques)."),
        IntervalSongRef(false, "M7", "Major 7th", 11, "inverts to m2 ascending", emptyList(),
            note = "No clean familiar example. Construct it: an octave downward, then a minor 2nd " +
                "back upward. The ▶ plays exactly this interval."),
        desc("P8", "Perfect octave", 12, "inverts to unison",
            s("Somewhere Over the Rainbow", "Harold Arlen", "“(rain)-bow” down to “way (up high)”.",
                RAINBOW)),
    )

    /** The reference row for [semitones] (1..12) in one direction, or null (unison). */
    fun refFor(semitones: Int, ascending: Boolean): IntervalSongRef? =
        (if (ascending) ASCENDING else DESCENDING).firstOrNull { it.semitones == semitones }

    /** Parse a [IntervalSong.melody] into absolute notes, transposed so the snippet's
     *  range is centred on [centerMidi] (default F#4 — comfortable for any snippet). */
    fun melodyNotes(melody: String, centerMidi: Int = 66): List<MelodyNote> {
        val raw = melody.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { tok ->
            val (o, l) = tok.split('/')
            o.toInt() to l.toDouble()
        }
        if (raw.isEmpty()) return emptyList()
        val lo = raw.minOf { it.first }
        val hi = raw.maxOf { it.first }
        val base = centerMidi - Math.floorDiv(lo + hi, 2)
        return raw.map { (o, l) -> MelodyNote(base + o, l) }
    }

    /** True when [melody] sounds [semitones] in the given direction between two notes at
     *  most two apart (adjacent, or 1st→3rd for the “count from the first note” cues). */
    fun melodyContains(melody: String, semitones: Int, ascending: Boolean): Boolean {
        val n = melodyNotes(melody).map { it.midi }
        val want = if (ascending) semitones else -semitones
        for (i in n.indices) for (j in i + 1..minOf(i + 2, n.lastIndex)) {
            if (n[j] - n[i] == want) return true
        }
        return false
    }
}
