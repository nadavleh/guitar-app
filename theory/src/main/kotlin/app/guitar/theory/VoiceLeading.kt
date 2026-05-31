package app.guitar.theory

import kotlin.math.abs

/**
 * Pick the next chord voicing in a progression so it minimizes finger movement
 * from the previous voicing — the way a human player naturally chooses voicings
 * that flow smoothly into the next.
 *
 * The metric ([movementCost]) is the sum over all strings of the per-string fret
 * displacement (or a small fixed penalty when one shape mutes a string the other
 * plays). Lower cost = smoother transition.
 *
 * Example: starting from G major in the G-shape (3 2 0 0 0 3), picking C major
 * by min-movement chooses the A-shape barre (x 3 5 5 5 3) with cost ≈ 8 over
 * the open-C C-shape (x 3 2 0 1 0) with cost ≈ 17, the G-shape barred for C
 * (8 7 5 5 5 8) with cost ≈ 15, or the E-shape barred for C (8 10 10 9 8 8)
 * with cost = 30.
 */
object VoiceLeading {

    /** Sum of per-string fret displacement plus a fixed penalty for each
     *  muted-↔-played transition. */
    fun movementCost(prev: ChordShape, next: ChordShape, mutePenalty: Int = 3): Int {
        require(mutePenalty >= 0)
        val n = minOf(prev.frets.size, next.frets.size)
        var cost = 0
        for (s in 0 until n) {
            val a = prev.frets[s]
            val b = next.frets[s]
            cost += when {
                a == null && b == null -> 0
                a == null || b == null -> mutePenalty
                else -> abs(a - b)
            }
        }
        return cost
    }

    /** Index into [candidates] of the voicing closest to [prev] by [movementCost].
     *  Returns 0 if [candidates] is empty (shouldn't happen for callers but safe). */
    fun pickMinMovement(prev: ChordShape, candidates: List<ChordShape>): Int {
        if (candidates.isEmpty()) return 0
        var best = 0
        var bestCost = movementCost(prev, candidates[0])
        for (i in 1 until candidates.size) {
            val c = movementCost(prev, candidates[i])
            if (c < bestCost) {
                bestCost = c
                best = i
            }
        }
        return best
    }
}
