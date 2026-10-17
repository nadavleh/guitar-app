package app.guitar.theory

/** Interval → reference-song lookup for the Theory tab and the interval trainer.
 *
 *  DESCENDING comes from Nadav's "Descending Interval Song References" PDF;
 *  ASCENDING is the canonical companion list Claude generated. [inversion] is the
 *  octave complement — NOT the same two pitches backward: major↔minor switch,
 *  perfect stays perfect, the tritone inverts to itself.
 *
 *  [semitones] drives in-app playback (see the ▶ button in the Theory tab), so a
 *  row can always be HEARD even when it has no clean song example.
 *  Mirrored in chorect-web intervalSongs.ts. */
data class IntervalSongRef(
    val ascending: Boolean,
    /** Short interval name, e.g. "m2", "P5", "TT", "P8". */
    val interval: String,
    val intervalLong: String,
    /** Distance in semitones, 1..12 — what the ▶ button plays. */
    val semitones: Int,
    /** Octave-complement note, e.g. "inverts to M7 ascending". */
    val inversion: String,
    val song: String,
    val artist: String,
    val cue: String,
)

object IntervalSongs {

    const val COMPLEMENT_NOTE =
        "The inversion listed is the octave complement — not the same two pitches played backward. " +
            "Major and minor switch, perfect intervals stay perfect, the tritone inverts to itself."

    private fun asc(i: String, long: String, st: Int, inv: String, song: String, artist: String, cue: String) =
        IntervalSongRef(true, i, long, st, inv, song, artist, cue)
    private fun desc(i: String, long: String, st: Int, inv: String, song: String, artist: String, cue: String) =
        IntervalSongRef(false, i, long, st, inv, song, artist, cue)

    /** Ascending references (canonical picks). */
    val ASCENDING: List<IntervalSongRef> = listOf(
        asc("m2", "Minor 2nd", 1, "inverts to M7 descending", "Jaws — main theme", "John Williams",
            "The rising two-note ostinato that opens the theme."),
        asc("M2", "Major 2nd", 2, "inverts to m7 descending", "Happy Birthday", "traditional",
            "The step up onto the second syllable."),
        asc("m3", "Minor 3rd", 3, "inverts to M6 descending", "Greensleeves", "traditional",
            "The opening upward leap of the tune."),
        asc("M3", "Major 3rd", 4, "inverts to m6 descending", "When the Saints Go Marching In", "traditional",
            "The first two notes of the melody."),
        asc("P4", "Perfect 4th", 5, "inverts to P5 descending", "Bridal Chorus (“Here Comes the Bride”)", "Wagner",
            "The opening leap. Amazing Grace opens with the same interval."),
        asc("TT", "Tritone", 6, "inverts to TT descending", "The Simpsons — main theme", "Danny Elfman",
            "The sung opening leap. The opening leap of “Maria” (West Side Story) is the same interval."),
        asc("P5", "Perfect 5th", 7, "inverts to P4 descending", "Twinkle Twinkle Little Star", "traditional",
            "Note 2 up to note 3. The Star Wars main theme opens with the same leap."),
        asc("m6", "Minor 6th", 8, "inverts to M3 descending", "Manhã de Carnaval (Black Orpheus)", "Luiz Bonfá",
            "The famous opening melodic leap."),
        asc("M6", "Major 6th", 9, "inverts to m3 descending", "My Bonnie Lies Over the Ocean", "traditional",
            "The opening leap. The NBC chimes start with the same interval."),
        asc("m7", "Minor 7th", 10, "inverts to M2 descending", "Somewhere (West Side Story)", "Leonard Bernstein",
            "The wide leap that opens the phrase."),
        asc("M7", "Major 7th", 11, "inverts to m2 descending", "Take On Me", "a-ha",
            "The chorus leap up to the high note. Or construct it: an octave up, then a minor 2nd back down."),
        asc("P8", "Perfect octave", 12, "inverts to unison", "Somewhere Over the Rainbow", "Harold Arlen",
            "The octave leap on the very first two notes."),
    )

    /** Descending references (from the PDF). */
    val DESCENDING: List<IntervalSongRef> = listOf(
        desc("m2", "Minor 2nd", 1, "inverts to M7 ascending", "Für Elise", "Beethoven",
            "The first two notes: E down to D#."),
        desc("M2", "Major 2nd", 2, "inverts to m7 ascending", "Mary Had a Little Lamb", "traditional",
            "The first two notes of the tune."),
        desc("m3", "Minor 3rd", 3, "inverts to M6 ascending", "Hey Jude", "The Beatles",
            "The drop across the first two sung syllables."),
        desc("M3", "Major 3rd", 4, "inverts to m6 ascending", "Symphony No. 5", "Beethoven",
            "In the four-note motif, the third short note drops to the long one."),
        desc("P4", "Perfect 4th", 5, "inverts to P5 ascending", "Under Pressure", "Queen & David Bowie",
            "The exposed downward leap in the opening bass riff."),
        desc("TT", "Tritone", 6, "inverts to TT ascending", "Black Sabbath", "Black Sabbath",
            "The high note dropping back to the low one in the main guitar riff."),
        desc("P5", "Perfect 5th", 7, "inverts to P4 ascending", "Game of Thrones — main theme", "Ramin Djawadi",
            "The prominent descending-fifth gesture of the theme."),
        desc("m6", "Minor 6th", 8, "inverts to M3 ascending", "Chega de Saudade", "João Gilberto",
            "The opening melodic gesture — also week 15 of your Workout plan."),
        desc("M6", "Major 6th", 9, "inverts to m3 ascending", "No Surprises", "Radiohead",
            "The conspicuous wide downward leap in the vocal melody."),
        desc("m7", "Minor 7th", 10, "inverts to M2 ascending", "Till There Was You", "The Beatles",
            "The wide downward leap at the end of the first phrase, landing on the title word."),
        desc("M7", "Major 7th", 11, "inverts to m2 ascending", "(no clean familiar example)", "",
            "Construct it: an octave downward, then a minor 2nd back upward. Use the ▶ button — this is exactly why it plays."),
        desc("P8", "Perfect octave", 12, "inverts to unison", "My Sharona", "The Knack",
            "The octave drop in the main guitar riff."),
    )
    /** More well-known songs per interval (title, artist), shown under each row so an
     *  interval can be checked against whichever tune you already know. */
    private val MORE_ASCENDING: Map<String, List<Pair<String, String>>> = mapOf(
        "m2" to listOf("White Christmas" to "Irving Berlin", "The Pink Panther Theme" to "Henry Mancini"),
        "M2" to listOf("Frère Jacques" to "traditional", "Silent Night" to "Franz Gruber"),
        "m3" to listOf("Smoke on the Water" to "Deep Purple", "Brahms' Lullaby" to "Brahms"),
        "M3" to listOf("Kumbaya" to "traditional", "Morning Has Broken" to "Cat Stevens"),
        "P4" to listOf("Amazing Grace" to "traditional", "Auld Lang Syne" to "traditional"),
        "TT" to listOf("Maria (West Side Story)" to "Leonard Bernstein"),
        "P5" to listOf("Star Wars — main theme" to "John Williams", "Can't Help Falling in Love" to "Elvis Presley"),
        "m6" to listOf("The Entertainer" to "Scott Joplin"),
        "M6" to listOf("NBC chimes" to "NBC", "Jingle Bells (verse: “Dashing through…”)" to "traditional"),
        "m7" to listOf("Star Trek — original theme" to "Alexander Courage"),
        "M7" to listOf("Somewhere Over the Rainbow (1st to 3rd note)" to "Harold Arlen"),
        "P8" to listOf("Singin' in the Rain" to "Nacio Herb Brown"),
    )
    private val MORE_DESCENDING: Map<String, List<Pair<String, String>>> = mapOf(
        "m2" to listOf("Joy to the World" to "traditional"),
        "M2" to listOf("Three Blind Mice" to "traditional"),
        "m3" to listOf("Frosty the Snowman" to "Gene Autry"),
        "M3" to listOf("Swing Low, Sweet Chariot" to "traditional"),
        "P4" to listOf("Eine kleine Nachtmusik" to "Mozart"),
        "P5" to listOf("The Flintstones — theme" to "Hoyt Curtin"),
        "M6" to listOf("Nobody Knows the Trouble I've Seen" to "traditional", "Over There" to "George M. Cohan"),
        "P8" to listOf("Willow Weep for Me" to "Ann Ronell"),
    )

    /** The extra songs for [r]'s interval and direction (may be empty). */
    fun moreSongs(r: IntervalSongRef): List<Pair<String, String>> =
        (if (r.ascending) MORE_ASCENDING else MORE_DESCENDING)[r.interval].orEmpty()

    /** The reference row for [semitones] (1..12) in one direction, or null (unison). */
    fun refFor(semitones: Int, ascending: Boolean): IntervalSongRef? =
        (if (ascending) ASCENDING else DESCENDING).firstOrNull { it.semitones == semitones }
}
