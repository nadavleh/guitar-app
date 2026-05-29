package app.guitar.theory

object Tunings {
    val standard      = Tuning.of("E2", "A2", "D3", "G3", "B3", "E4")
    val dropD         = Tuning.of("D2", "A2", "D3", "G3", "B3", "E4")
    val dadgad        = Tuning.of("D2", "A2", "D3", "G3", "A3", "D4")
    val openG         = Tuning.of("D2", "G2", "D3", "G3", "B3", "D4")
    val openD         = Tuning.of("D2", "A2", "D3", "F#3", "A3", "D4")
    val halfStepDown  = Tuning.of("D#2", "G#2", "C#3", "F#3", "A#3", "D#4")
    val wholeStepDown = Tuning.of("D2", "G2", "C3", "F3", "A3", "D4")

    val all: Map<String, Tuning> = linkedMapOf(
        "Standard" to standard,
        "Drop D" to dropD,
        "DADGAD" to dadgad,
        "Open G" to openG,
        "Open D" to openD,
        "Half-step down" to halfStepDown,
        "Whole-step down" to wholeStepDown,
    )
}
