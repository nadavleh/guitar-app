package app.guitar.theory

@JvmInline
value class PitchClass(val value: Int) {
    init { require(value in 0..11) { "PitchClass must be 0..11, got $value" } }

    operator fun plus(semitones: Int): PitchClass = of(value + semitones)
    operator fun plus(interval: Interval): PitchClass = of(value + interval.semitones)
    operator fun minus(other: PitchClass): Interval = Interval(((value - other.value) % 12 + 12) % 12)

    companion object {
        val C  = PitchClass(0)
        val Cs = PitchClass(1)
        val D  = PitchClass(2)
        val Ds = PitchClass(3)
        val E  = PitchClass(4)
        val F  = PitchClass(5)
        val Fs = PitchClass(6)
        val G  = PitchClass(7)
        val Gs = PitchClass(8)
        val A  = PitchClass(9)
        val As = PitchClass(10)
        val B  = PitchClass(11)

        fun of(value: Int): PitchClass = PitchClass(((value % 12) + 12) % 12)
    }
}
