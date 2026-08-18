package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pure reveal/timing schedule behind hands-free Car mode. No audio, no UI —
 *  the platform state layers drive it, so this is the whole testable surface. */
class CarModeTest {

    @Test fun `round 1 reveals nothing and round 5 reveals every slot`() {
        // Round 1 is the guitarless blind pass — the entire point of the drill.
        assertEquals(0, CarMode.revealedSlots(1, 4))
        assertEquals(4, CarMode.revealedSlots(5, 4))
    }

    @Test fun `each round from the 2nd reveals exactly one more slot`() {
        for (r in 2..CarMode.ROUNDS) {
            assertEquals(1, CarMode.revealedSlots(r, 4) - CarMode.revealedSlots(r - 1, 4),
                "round $r should reveal exactly one more slot than round ${r - 1}")
        }
    }

    @Test fun `reveal count clamps to the slot count and never goes negative`() {
        // 3-chord advanced/circle progressions run out of slots before round 5.
        assertEquals(3, CarMode.revealedSlots(4, 3))
        assertEquals(3, CarMode.revealedSlots(5, 3))
        // Round 0 is idle (nothing drawn / nothing started).
        assertEquals(0, CarMode.revealedSlots(0, 4))
        assertEquals(0, CarMode.revealedSlots(-1, 4))
    }

    @Test fun `lead-in is three beeps half a second apart`() {
        assertEquals(3, CarMode.BEEPS)
        assertEquals(500, CarMode.BEEP_GAP_MS)
        // The first chord lands one more gap after the third beep — "3-2-1-go".
        assertEquals(1500, CarMode.LEAD_IN_MS)
    }

    @Test fun `five rounds per exercise`() {
        assertEquals(5, CarMode.ROUNDS)
    }

    @Test fun `exercise length at 140 bpm over 4 bars is about 36 seconds`() {
        // Pinned to the exact value, NOT a range: verify.ts asserts the same literal,
        // which is what makes the two ports provably agree. 60000/140 truncates to 428,
        // so 1500 + 5 * 4 * 428 * 4 = 35740.
        assertEquals(35_740L, CarMode.exerciseMs(140, 4))
    }

    @Test fun `exerciseMs scales with rounds and bars and never divides by zero`() {
        // Twice the bars, twice the sounding time (the fixed lead-in aside).
        val four = CarMode.exerciseMs(140, 4) - CarMode.LEAD_IN_MS
        val eight = CarMode.exerciseMs(140, 8) - CarMode.LEAD_IN_MS
        assertEquals(2 * four, eight)
        // A nonsense BPM must not blow up — it clamps.
        assertTrue(CarMode.exerciseMs(0, 4) > 0)
    }

    @Test fun `the beep sits above the ear-training chord register`() {
        // Progression voicings live at MIDI 45-70 (110-490 Hz); the cue must cut
        // through them and through the sub-500 Hz road-noise hump in a car.
        assertTrue(CarMode.BEEP_HZ > 500.0, "cue at ${CarMode.BEEP_HZ} Hz would sit inside the chords")
        assertTrue(CarMode.BEEP_ATTACK_MS > 0, "a zero-attack sine clicks on onset")
        assertTrue(CarMode.BEEP_PEAK in 0.0f..1.0f)
    }

    @Test fun `there is a silent self-assessment gap before auto-advance`() {
        assertTrue(CarMode.GAP_MS >= 2000, "too short to say the answer to yourself")
    }
}
