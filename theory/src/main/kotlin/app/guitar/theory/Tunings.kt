package app.guitar.theory

object Tunings {
    // ---- Guitar (6-string) ----
    val standard      = Tuning.of("E2", "A2", "D3", "G3", "B3", "E4")
    val dropD         = Tuning.of("D2", "A2", "D3", "G3", "B3", "E4")
    val dadgad        = Tuning.of("D2", "A2", "D3", "G3", "A3", "D4")
    val openG         = Tuning.of("D2", "G2", "D3", "G3", "B3", "D4")
    val openD         = Tuning.of("D2", "A2", "D3", "F#3", "A3", "D4")
    val halfStepDown  = Tuning.of("D#2", "G#2", "C#3", "F#3", "A#3", "D#4")
    val wholeStepDown = Tuning.of("D2", "G2", "C3", "F3", "A3", "D4")

    val guitarPresets: Map<String, Tuning> = linkedMapOf(
        "Standard" to standard,
        "Drop D" to dropD,
        "DADGAD" to dadgad,
        "Open G" to openG,
        "Open D" to openD,
        "Half-step down" to halfStepDown,
        "Whole-step down" to wholeStepDown,
    )

    // ---- Cavaquinho (4-string, tuned an octave higher than guitar strings 4-1) ----
    /** Portuguese / Madeira standard: D4 G4 B4 E5 (same intervals as guitar's
     *  strings 4-1, all one octave higher). */
    val cavaqDgbe = Tuning.of("D4", "G4", "B4", "E5")
    /** Brazilian standard: D4 G4 B4 D5 — re-entrant: the highest string drops
     *  back down to D, an octave above the 4th string. */
    val cavaqDgbd = Tuning.of("D4", "G4", "B4", "D5")

    val cavaquinhoPresets: Map<String, Tuning> = linkedMapOf(
        "DGBe" to cavaqDgbe,
        "DGBD" to cavaqDgbd,
    )

    /** Preset tunings filtered by instrument. */
    fun presetsFor(instrument: Instrument): Map<String, Tuning> = when (instrument) {
        Instrument.Guitar     -> guitarPresets
        Instrument.Cavaquinho -> cavaquinhoPresets
    }

    /** Default tuning when the user switches to the given instrument. */
    fun defaultFor(instrument: Instrument): Tuning = when (instrument) {
        Instrument.Guitar     -> standard
        Instrument.Cavaquinho -> cavaqDgbe
    }

    /** Default tuning-preset NAME when switching to the given instrument. */
    fun defaultNameFor(instrument: Instrument): String = when (instrument) {
        Instrument.Guitar     -> "Standard"
        Instrument.Cavaquinho -> "DGBe"
    }

    /** Union of all preset tunings across all instruments. Used for name → Tuning
     *  lookups (the preset names don't collide). UI for picking tunings should
     *  use [presetsFor] instead so users only see tunings appropriate to their
     *  current instrument. */
    val all: Map<String, Tuning> = guitarPresets + cavaquinhoPresets
}
