package app.guitar.theory

/**
 * Voicing style for the chord shape generator.
 *
 * - **Standard**: every chord tone must be present in the shape (current default).
 * - **Shell**: only the *essential* intervals must be present (the chord-defining
 *   3rd or substitute + the 7th + color tones). The 5th is dropped; for 7th+
 *   chords the root is also dropped (it's covered by the bass in jazz comping
 *   contexts). Produces 2-4 note voicings useful for comping.
 *   See `guitar lesson chord shapes.pdf`.
 */
enum class VoicingStyle { Standard, Shell }

/**
 * Returns the set of intervals that MUST be present in a shell-voicing of [quality].
 * Always includes the chord-defining 3rd (or sus substitute), the 7th if present,
 * the diminished-7th's bb7 and b5, half-dim's b5, and any extensions (9/11/13).
 * Drops P5 unconditionally; drops P1 (root) for chords of size ≥ 4.
 */
fun essentialShellIntervals(quality: ChordQuality): Set<Interval> {
    val ints = quality.intervals.toSet()
    val essential = LinkedHashSet<Interval>()

    // A shell voicing is ROOT + 3rd + 7th, with the (perfect or altered) 5th OMITTED —
    // the root sits in the bass, the 3rd and 7th are the guide tones above it.
    essential += Interval.P1                                   // root (in the bass)

    // Chord-defining third (or sus substitute)
    if (Interval.maj3 in ints) essential += Interval.maj3
    if (Interval.min3 in ints) essential += Interval.min3
    if (Interval.maj2 in ints) essential += Interval.maj2     // sus2
    if (Interval.P4   in ints) essential += Interval.P4       // sus4

    // Seventh — or the 6th (for 6 / m6 chords, and dim7's bb7 = major 6th)
    if (Interval.maj7 in ints) essential += Interval.maj7
    if (Interval.min7 in ints) essential += Interval.min7
    if (Interval.maj7 !in ints && Interval.min7 !in ints && Interval.maj6 in ints) essential += Interval.maj6

    // Augmented triad: the #5 (min6 in our encoding) is the defining tone (no plain 5th to drop)
    if (quality.symbol == "aug" && Interval.min6 in ints) essential += Interval.min6

    // Color / extension tones sit on top of the shell — keep them when present
    if (Interval.b9    in ints) essential += Interval.b9
    if (Interval.maj9  in ints) essential += Interval.maj9
    if (Interval.P11   in ints) essential += Interval.P11
    if (Interval.s11   in ints) essential += Interval.s11
    if (Interval.maj13 in ints) essential += Interval.maj13

    return essential
}
