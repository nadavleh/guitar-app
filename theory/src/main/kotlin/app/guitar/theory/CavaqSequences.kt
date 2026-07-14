package app.guitar.theory

/**
 * A named cavaquinho functional chord sequence (samba/choro), from Betto Correa's
 * "Sequências de acordes para cavaquinho". Defined ONCE in C via an
 * [EarTraining.NamedProgression] (AdvChord = semitone-above-tonic + quality + roman)
 * and transposed to any key by [EarTraining.NamedProgression.resolve]. The 7-key
 * modules in the source are all the same functional template transposed, so we store
 * one definition per distinct sequence.
 */
data class CavaqSequence(
    val id: String,
    val namePt: String,
    val nameEn: String,
    val prog: EarTraining.NamedProgression,
)

object CavaqSequences {
    private fun ac(semitone: Int, quality: String, roman: String) =
        EarTraining.AdvChord(semitone, quality, roman)

    private fun seq(
        id: String, pt: String, en: String,
        mode: TrainingMode = TrainingMode.Major,
        vararg chords: EarTraining.AdvChord,
    ) = CavaqSequence(id, pt, en, EarTraining.NamedProgression(pt, en, mode, chords.toList()))

    /** The sequences, in learning order. */
    val ALL: List<CavaqSequence> = listOf(
        // Quadradinho — the staple I VI7 ii V7 turnaround (C A7 Dm G7).
        seq("quadradinho_maj", "Quadradinho (Maior)", "Quadradinho (Major)", TrainingMode.Major,
            ac(0, "", "I"), ac(9, "7", "VI7"), ac(2, "m", "ii"), ac(7, "7", "V7")),
        // Basic minor — i I7 iv V7 (Cm C7 Fm G7).
        seq("basic_min", "Sequência Menor (Básico)", "Basic Minor", TrainingMode.Minor,
            ac(0, "m", "i"), ac(0, "7", "I7"), ac(5, "m", "iv"), ac(7, "7", "V7")),
        // Médio — extended major turnaround.
        seq("medio_maj", "Sequência Médio (Maior)", "Extended Major (Médio)", TrainingMode.Major,
            ac(0, "", "I"), ac(9, "7", "VI7"), ac(2, "m", "ii"), ac(7, "7", "V7"),
            ac(7, "m", "v"), ac(0, "7", "I7"), ac(5, "", "IV"), ac(5, "m", "iv"),
            ac(4, "m", "iii"), ac(9, "7", "VI7"), ac(2, "m", "ii"), ac(7, "7", "V7"), ac(0, "", "I")),
        // Médio — extended minor sequence (Cm C7 Fm Bb7 Eb Ab Dm7b5 G7 Cm):
        // i I7 iv bVII7 bIII bVI iiø V7 i — the minor circle-of-fifths cadence.
        seq("medio_min", "Sequência Médio (Menor)", "Extended Minor (Médio)", TrainingMode.Minor,
            ac(0, "m", "i"), ac(0, "7", "I7"), ac(5, "m", "iv"), ac(10, "7", "bVII7"),
            ac(3, "", "bIII"), ac(8, "", "bVI"), ac(2, "m7b5", "iiø"), ac(7, "7", "V7"), ac(0, "m", "i")),
        // Campo harmônico — the diatonic field, for scale/mode study.
        seq("campo_maj", "Campo Harmônico Maior", "Harmonic Field (Major)", TrainingMode.Major,
            ac(0, "", "I"), ac(2, "m", "ii"), ac(4, "m", "iii"), ac(5, "", "IV"),
            ac(7, "", "V"), ac(9, "m", "vi"), ac(11, "dim", "vii°")),
    )

    fun byId(id: String): CavaqSequence? = ALL.firstOrNull { it.id == id }
}
