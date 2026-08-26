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

    @Test fun `a round's new slot appears only when the playhead reaches it`() {
        // Round 3 of a 4-chord progression is ALLOWED 2 slots, but slot 2 (index 1) must
        // stay hidden until the playhead is actually sounding it — reading the answer
        // before hearing the chord defeats the drill.
        assertEquals(2, CarMode.revealedSlots(3, 4))
        assertEquals(1, CarMode.revealedSlotsAt(3, 0, 4))
        assertEquals(2, CarMode.revealedSlotsAt(3, 1, 4))
        assertEquals(2, CarMode.revealedSlotsAt(3, 3, 4))
    }

    @Test fun `slots earned in earlier rounds never un-reveal`() {
        // Walking the whole exercise bar by bar, the count only ever grows.
        var prev = 0
        for (r in 1..CarMode.ROUNDS) {
            for (p in 0 until 4) {
                val now = CarMode.revealedSlotsAt(r, p, 4)
                assertTrue(now >= prev, "round $r bar $p dropped from $prev to $now")
                assertTrue(now <= CarMode.revealedSlots(r, 4), "round $r bar $p ran ahead of the schedule")
                prev = now
            }
        }
        // The last bar of the last round has handed over the whole progression.
        assertEquals(4, prev)
    }

    @Test fun `the lead-in and round 1 reveal nothing wherever the playhead is`() {
        for (p in -1 until 4) {
            assertEquals(0, CarMode.revealedSlotsAt(0, p, 4))
            assertEquals(0, CarMode.revealedSlotsAt(1, p, 4))
        }
        // A playhead left over from the previous exercise cannot leak a reveal, and an
        // out-of-range one cannot overshoot the slot count.
        assertEquals(0, CarMode.revealedSlotsAt(2, -5, 4))
        assertEquals(4, CarMode.revealedSlotsAt(CarMode.ROUNDS, 99, 4))
    }

    @Test fun `a long progression still reaches a full reveal on its last bar`() {
        // 8-chord entries jump several slots per round; each jump is still paced by the
        // playhead, and the exercise must end with everything showing.
        for (slots in 1..8) {
            assertEquals(slots, CarMode.revealedSlotsAt(CarMode.ROUNDS, slots - 1, slots),
                "a $slots-chord progression must be fully revealed on its last bar")
        }
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

    @Test fun `the last round always shows the whole progression`() {
        // The advanced library has 6-, 7- and 8-chord entries. Stepping the reveal by one
        // per round left Pachelbel's Canon (8 chords) showing only 4 when the exercise
        // ended, so the answer was never actually given.
        for (slots in 1..8) {
            assertEquals(slots, CarMode.revealedSlots(CarMode.ROUNDS, slots),
                "a $slots-chord progression must be fully revealed on the last round")
        }
    }

    @Test fun `the reveal ramp never goes backwards and never overshoots`() {
        for (slots in 1..8) {
            var prev = 0
            for (r in 1..CarMode.ROUNDS) {
                val n = CarMode.revealedSlots(r, slots)
                assertTrue(n >= prev, "slots=$slots round=$r revealed $n after $prev")
                assertTrue(n <= slots, "slots=$slots round=$r revealed $n of $slots")
                prev = n
            }
        }
    }

    @Test fun `a long progression reveals more than one slot per round`() {
        // 8 chords over 4 revealing rounds: 2, 4, 6, 8.
        assertEquals(listOf(0, 2, 4, 6, 8), (1..CarMode.ROUNDS).map { CarMode.revealedSlots(it, 8) })
        // 3 chords still steps by one and then holds (nothing left to show).
        assertEquals(listOf(0, 1, 2, 3, 3), (1..CarMode.ROUNDS).map { CarMode.revealedSlots(it, 3) })
    }

    // ---- speechFor: the spoken function label the car-mode voice reads out ----

    @Test fun `case becomes a spoken quality so IV and iv cannot be confused`() {
        // The whole reason the voice exists: over road noise "four" alone is useless.
        assertEquals("4 major", CarMode.speechFor("IV"))
        assertEquals("4 minor", CarMode.speechFor("iv"))
        assertEquals("1 major", CarMode.speechFor("I"))
        assertEquals("1 minor", CarMode.speechFor("i"))
    }

    @Test fun `every numeral maps to its degree number, VII not V plus II`() {
        assertEquals(listOf("1 major", "2 major", "3 major", "4 major", "5 major", "6 major", "7 major"),
            listOf("I", "II", "III", "IV", "V", "VI", "VII").map { CarMode.speechFor(it) })
    }

    @Test fun `accidentals are spoken before the degree`() {
        assertEquals("flat 6 major", CarMode.speechFor("bVI"))
        assertEquals("flat 7 major", CarMode.speechFor("bVII"))
        assertEquals("sharp 4 major", CarMode.speechFor("#IV"))
    }

    @Test fun `a suffix quality overrides the case`() {
        assertEquals("7 diminished", CarMode.speechFor("vii°"))
        assertEquals("2 diminished", CarMode.speechFor("ii°"))
        assertEquals("7 diminished 7", CarMode.speechFor("vii°7"))
        assertEquals("sharp 4 diminished 7", CarMode.speechFor("#IV°7"))
        assertEquals("5 augmented", CarMode.speechFor("V+"))
    }

    @Test fun `an uppercase bare 7th is a dominant, but a 6th or add9 is not`() {
        assertEquals("5 dominant 7", CarMode.speechFor("V7"))
        assertEquals("flat 7 dominant 7", CarMode.speechFor("bVII7"))
        assertEquals("5 dominant 13", CarMode.speechFor("V13"))
        assertEquals("1 major 6", CarMode.speechFor("I6"))
        assertEquals("4 major add 9", CarMode.speechFor("IVadd9"))
    }

    @Test fun `maj is spoken once, not doubled by the uppercase case`() {
        assertEquals("1 major 7", CarMode.speechFor("Imaj7"))
        assertEquals("flat 6 major 7", CarMode.speechFor("bVImaj7"))
        assertEquals("4 major 7 sharp 11", CarMode.speechFor("IVmaj7#11"))
    }

    @Test fun `a lowercase 7th keeps its minor quality`() {
        assertEquals("1 minor 7", CarMode.speechFor("i7"))
        assertEquals("2 minor 7", CarMode.speechFor("ii7"))
        assertEquals("6 minor 9", CarMode.speechFor("vi9"))
    }

    @Test fun `every Roman the diatonic library can print is speakable`() {
        val romans = (EarTraining.MAJOR_DEGREES.values + EarTraining.MINOR_DEGREES.values)
            .flatMap { info ->
                listOf(info.roman,
                    EarTraining.romanLabel(info.roman, info.seventhQuality),
                    EarTraining.romanLabel(info.roman, info.extendedQuality)) +
                    info.extendedOptions.map { info.roman + it.second }
            }
        for (r in romans) {
            val spoken = CarMode.speechFor(r)
            assertTrue(spoken.isNotEmpty(), "no speech for Roman '$r'")
            assertTrue(spoken.first().isDigit() || spoken.startsWith("flat") || spoken.startsWith("sharp"),
                "speech for '$r' should open with the degree or its accidental, got '$spoken'")
        }
    }

    @Test fun `the spoken level defaults high and clamps into the slider range`() {
        // 0.35 was the original "sit under the music" level and was inaudible in a moving
        // car; the default now starts high and the slider trades intelligibility for
        // masking. 1.0 is a hard platform ceiling on both sides.
        assertTrue(CarMode.SPEECH_VOLUME >= 0.8f, "default voice level should be loud")
        assertEquals(1.0f, CarMode.SPEECH_VOLUME_MAX)
        assertTrue(CarMode.SPEECH_VOLUME in CarMode.SPEECH_VOLUME_MIN..CarMode.SPEECH_VOLUME_MAX)
        assertEquals(CarMode.SPEECH_VOLUME_MAX, CarMode.clampSpeechVolume(4f))
        assertEquals(CarMode.SPEECH_VOLUME_MIN, CarMode.clampSpeechVolume(-1f))
        assertEquals(0.5f, CarMode.clampSpeechVolume(0.5f))
    }

    @Test fun `an unparseable label is silent rather than gibberish`() {
        assertEquals("", CarMode.speechFor(""))
        assertEquals("", CarMode.speechFor("—"))
        assertEquals("", CarMode.speechFor("?"))
    }
}
