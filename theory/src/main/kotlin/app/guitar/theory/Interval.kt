package app.guitar.theory

@JvmInline
value class Interval(val semitones: Int) {
    operator fun plus(other: Interval): Interval = Interval(semitones + other.semitones)

    companion object {
        val P1    = Interval(0)
        val min2  = Interval(1)
        val maj2  = Interval(2)
        val min3  = Interval(3)
        val maj3  = Interval(4)
        val P4    = Interval(5)
        val TT    = Interval(6)
        val P5    = Interval(7)
        val min6  = Interval(8)
        val maj6  = Interval(9)
        val min7  = Interval(10)
        val maj7  = Interval(11)
        val P8    = Interval(12)
        val b9    = Interval(13)
        val maj9  = Interval(14)
        val P11   = Interval(17)
        val s11   = Interval(18)
        val maj13 = Interval(21)
    }
}
