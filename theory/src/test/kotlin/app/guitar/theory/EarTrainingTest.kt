package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EarTrainingTest {

    // ----- relative major/minor degree equivalence (#6 answer keyboard) -----

    @Test fun `major and relative-minor degrees map to the same shared chords`() {
        // The user's example: major 1-4-5 reads as minor 3-6-7.
        assertEquals(1, EarTraining.majorRelativeDegree(3, TrainingMode.Minor))
        assertEquals(4, EarTraining.majorRelativeDegree(6, TrainingMode.Minor))
        assertEquals(5, EarTraining.majorRelativeDegree(7, TrainingMode.Minor))
        // minor i sits on the major key's 6th degree.
        assertEquals(6, EarTraining.majorRelativeDegree(1, TrainingMode.Minor))
        // Major mode is the identity.
        for (d in 1..7) assertEquals(d, EarTraining.majorRelativeDegree(d, TrainingMode.Major))
    }

    @Test fun `degreeFromMajorRelative inverts majorRelativeDegree`() {
        for (mode in TrainingMode.entries) {
            for (d in 1..7) {
                val rel = EarTraining.majorRelativeDegree(d, mode)
                assertEquals(d, EarTraining.degreeFromMajorRelative(rel, mode))
            }
        }
    }

    @Test fun `equivalent-degree roots resolve to the same pitch class`() {
        // A minor i (A) and C major vi (A) are the same chord; their relative-major
        // degree is 6, and resolving it in either parent key lands on A.
        val cMajVi = EarTraining.degreeRoot(PitchClass.C, 6, TrainingMode.Major)
        val aMinI = EarTraining.degreeRoot(PitchClass.A, 1, TrainingMode.Minor)
        assertEquals(cMajVi, aMinI)
        assertEquals(6, EarTraining.majorRelativeDegree(1, TrainingMode.Minor))
    }

    // ----- degreeRefMidi (degree-reference "♪ notes" octave anchoring) -----

    @Test fun `degree-reference notes ascend from the tonic in every key`() {
        // The v2.63 bug: G major mapped 3=B to 63 but 4=C to 52 — the scale dropped
        // an octave at the pc wrap point. Anchored to the tonic, degree 1 is always
        // the lowest note and 2..7 ascend strictly within the octave above it.
        for (mode in TrainingMode.entries) {
            for (pc in 0..11) {
                val key = PitchClass.of(pc)
                val midis = (1..7).map { EarTraining.degreeRefMidi(key, it, mode) }
                assertEquals(52 + key.value, midis.first())
                for (i in 1 until midis.size) {
                    assertTrue(midis[i] > midis[i - 1],
                        "degree ${i + 1} must sound above degree $i in $key $mode, got $midis")
                }
                assertTrue(midis.last() < midis.first() + 12)
            }
        }
    }

    @Test fun `G major reference puts 1=G below 7=F#`() {
        val g = EarTraining.degreeRefMidi(PitchClass.G, 1, TrainingMode.Major)
        val fs = EarTraining.degreeRefMidi(PitchClass.G, 7, TrainingMode.Major)
        assertEquals(59, g)   // G3, mid guitar register
        assertEquals(70, fs)  // F#4, a major 7th above — no octave drop mid-scale
        assertTrue(g < fs)
    }

    // ----- degreeRoot -----

    @Test fun `I in C major is C`() {
        assertEquals(PitchClass.C, EarTraining.degreeRoot(PitchClass.C, 1, TrainingMode.Major))
    }

    @Test fun `V in C major is G`() {
        assertEquals(PitchClass.G, EarTraining.degreeRoot(PitchClass.C, 5, TrainingMode.Major))
    }

    @Test fun `vi in C major is A`() {
        assertEquals(PitchClass.A, EarTraining.degreeRoot(PitchClass.C, 6, TrainingMode.Major))
    }

    @Test fun `i in A minor is A`() {
        assertEquals(PitchClass.A, EarTraining.degreeRoot(PitchClass.A, 1, TrainingMode.Minor))
    }

    @Test fun `VI in A minor is F`() {
        assertEquals(PitchClass.F, EarTraining.degreeRoot(PitchClass.A, 6, TrainingMode.Minor))
    }

    @Test fun `VII in A minor is G`() {
        assertEquals(PitchClass.G, EarTraining.degreeRoot(PitchClass.A, 7, TrainingMode.Minor))
    }

    // ----- resolve -----

    @Test fun `I in C major triad`() {
        val r = EarTraining.resolve(1, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Triads)
        assertEquals("C", r.symbol)
        assertEquals("I", r.romanLabel)
    }

    @Test fun `ii in C major triad`() {
        val r = EarTraining.resolve(2, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Triads)
        assertEquals("Dm", r.symbol)
        assertEquals("ii", r.romanLabel)
    }

    @Test fun `vii° in C major triad is Bdim`() {
        val r = EarTraining.resolve(7, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Triads)
        assertEquals("Bdim", r.symbol)
        assertEquals("vii°", r.romanLabel)
    }

    @Test fun `Imaj7 in C major`() {
        val r = EarTraining.resolve(1, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Sevenths)
        assertEquals("Cmaj7", r.symbol)
        assertEquals("Imaj7", r.romanLabel)
    }

    @Test fun `V7 in C major`() {
        val r = EarTraining.resolve(5, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Sevenths)
        assertEquals("G7", r.symbol)
        assertEquals("V7", r.romanLabel)
    }

    @Test fun `ii7 in C major strips redundant m`() {
        val r = EarTraining.resolve(2, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Sevenths)
        assertEquals("Dm7", r.symbol)
        assertEquals("ii7", r.romanLabel)    // not "iim7"
    }

    @Test fun `vii°7 in C major is Bm7b5`() {
        val r = EarTraining.resolve(7, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Sevenths)
        assertEquals("Bm7b5", r.symbol)
        assertEquals("vii°7", r.romanLabel)
    }

    @Test fun `i in A minor triad is Am`() {
        val r = EarTraining.resolve(1, PitchClass.A, TrainingMode.Minor, ChordTypeLevel.Triads)
        assertEquals("Am", r.symbol)
        assertEquals("i", r.romanLabel)
    }

    @Test fun `v in A minor triad is natural-minor Em`() {
        // Minor mode is labeled relative to the major scale and uses natural-minor
        // qualities: the 5th is a minor v (Em in A minor), not a harmonic-minor V.
        val r = EarTraining.resolve(5, PitchClass.A, TrainingMode.Minor, ChordTypeLevel.Triads)
        assertEquals("Em", r.symbol)
        assertEquals("v", r.romanLabel)
    }

    @Test fun `v7 in A minor is Em7`() {
        val r = EarTraining.resolve(5, PitchClass.A, TrainingMode.Minor, ChordTypeLevel.Sevenths)
        assertEquals("Em7", r.symbol)
        assertEquals("v7", r.romanLabel)
    }

    @Test fun `ii°7 in A minor is Bm7b5`() {
        val r = EarTraining.resolve(2, PitchClass.A, TrainingMode.Minor, ChordTypeLevel.Sevenths)
        assertEquals("Bm7b5", r.symbol)
        assertEquals("ii°7", r.romanLabel)
    }

    @Test fun `bIIImaj7 in A minor is Cmaj7`() {
        val r = EarTraining.resolve(3, PitchClass.A, TrainingMode.Minor, ChordTypeLevel.Sevenths)
        assertEquals("Cmaj7", r.symbol)
        assertEquals("bIIImaj7", r.romanLabel)
    }

    @Test fun `I extended uses an allowed diatonic extension`() {
        val r = EarTraining.resolve(1, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Extended)
        assertTrue(r.symbol in setOf("C6", "Cadd9", "Cmaj9", "Cmaj13"), "unexpected ${r.symbol}")
        assertTrue(r.romanLabel in setOf("I6", "Iadd9", "Imaj9", "Imaj13"), "unexpected ${r.romanLabel}")
        assertTrue(ChordLibrary.parse(r.symbol) != null, "unparseable ${r.symbol}")
    }

    @Test fun `V extended uses an allowed diatonic extension`() {
        val r = EarTraining.resolve(5, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Extended)
        assertTrue(r.symbol in setOf("G6", "G9", "G11", "G13"), "unexpected ${r.symbol}")
        assertTrue(r.romanLabel in setOf("V6", "V9", "V11", "V13"), "unexpected ${r.romanLabel}")
        assertTrue(ChordLibrary.parse(r.symbol) != null, "unparseable ${r.symbol}")
    }

    @Test fun `ii extended strips m in roman and stays diatonic`() {
        val r = EarTraining.resolve(2, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Extended)
        assertTrue(r.symbol in setOf("Dm6", "Dm9", "Dm11"), "unexpected ${r.symbol}")
        assertTrue(r.romanLabel in setOf("ii6", "ii9", "ii11"), "unexpected ${r.romanLabel}")
        assertTrue(ChordLibrary.parse(r.symbol) != null, "unparseable ${r.symbol}")
    }

    @Test fun `every major degree at extended level is parseable`() {
        // Guards the bug where extended symbols (maj9, m9, ...) weren't in ChordLibrary.
        repeat(50) {
            for (deg in 1..7) {
                val r = EarTraining.resolve(deg, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Extended)
                assertTrue(ChordLibrary.parse(r.symbol) != null, "unparseable ${r.symbol} (degree $deg)")
            }
        }
    }

    // ----- progression library -----

    @Test fun `all major progressions resolve to parseable chord symbols`() {
        for (p in EarTraining.MAJOR_PROGRESSIONS) {
            val chords = EarTraining.resolveProgression(p, PitchClass.C, ChordTypeLevel.Sevenths)
            assertEquals(4, chords.size)
            for (c in chords) {
                assertTrue(
                    ChordLibrary.parse(c.symbol) != null,
                    "ChordLibrary couldn't parse '${c.symbol}' from $p"
                )
            }
        }
    }

    @Test fun `all minor progressions resolve to parseable chord symbols`() {
        for (p in EarTraining.MINOR_PROGRESSIONS) {
            val chords = EarTraining.resolveProgression(p, PitchClass.A, ChordTypeLevel.Sevenths)
            for (c in chords) {
                assertTrue(
                    ChordLibrary.parse(c.symbol) != null,
                    "ChordLibrary couldn't parse '${c.symbol}' from $p"
                )
            }
        }
    }

    @Test fun `every degree resolves to a parseable triad`() {
        for (deg in 1..7) {
            val maj = EarTraining.resolve(deg, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Triads)
            assertTrue(ChordLibrary.parse(maj.symbol) != null, "couldn't parse ${maj.symbol}")
            val min = EarTraining.resolve(deg, PitchClass.A, TrainingMode.Minor, ChordTypeLevel.Triads)
            assertTrue(ChordLibrary.parse(min.symbol) != null, "couldn't parse ${min.symbol}")
        }
    }

    @Test fun `randomProgression returns one of the library entries`() {
        val rng = kotlin.random.Random(42)
        val p = EarTraining.randomProgression(TrainingMode.Major, rng)
        assertTrue(p in EarTraining.MAJOR_PROGRESSIONS)
    }

    // ---- Circle of fifths ----

    @Test fun `circle of fifths yields 4 adjacent descending-fifths chords`() {
        val cycle = EarTraining.CIRCLE_OF_FIFTHS
        assertEquals(7, cycle.size)
        // Roots fall by a fifth around the cycle — a PERFECT fifth (+5 semitones up
        // = fifth down) at every step except the single diatonic DIMINISHED fifth
        // into vii° (F→B°, +6), which is what makes that chord diminished.
        var tritones = 0
        for (i in cycle.indices) {
            val next = cycle[(i + 1) % cycle.size]
            val step = ((next.semitone - cycle[i].semitone) % 12 + 12) % 12
            assertTrue(step == 5 || step == 6, "root ${cycle[i].roman}→${next.roman} step $step not a fifth")
            if (step == 6) tritones++
        }
        assertEquals(1, tritones, "diatonic cycle should have exactly one diminished-fifth link")
        val rng = kotlin.random.Random(7)
        repeat(30) {
            val p = EarTraining.randomCircleOfFifths(rng)
            assertEquals(4, p.chords.size)
            // The 4 chords are a contiguous window of the cycle. Identify the start by
            // root semitone (robust to any dom-7 recolouring of a chord's quality).
            val start = cycle.indexOfFirst { it.semitone == p.chords[0].semitone }
            assertTrue(start >= 0)
            var doms = 0
            for (k in 0 until 4) {
                val expected = cycle[(start + k) % cycle.size]
                assertEquals(expected.semitone, p.chords[k].semitone, "window position $k root")
                if (p.chords[k].quality == "7") {
                    doms++
                    // A secondary dominant never sits on the diminished cycle chord and
                    // never on the last window chord (it needs a following target).
                    assertTrue(expected.quality != "dim", "diminished chord domified at $k")
                    assertTrue(k < 3, "last window chord domified (no resolution target)")
                }
            }
            // Every draw features at least one secondary dominant (the drill's promise).
            assertTrue(doms >= 1, "circle-of-fifths draw had no secondary dominant")
        }
    }

    // ---- Interval trainer (#6) ----

    @Test fun `interval trainer offers 13 intervals from unison to octave`() {
        val iv = IntervalTrainer.INTERVALS
        assertEquals(13, iv.size)
        assertEquals((0..12).toList(), iv.map { it.semitones })
        assertEquals("unison", iv.first().longName)
        assertEquals("octave", iv.last().longName)
        // semitones are unique and ascending
        assertEquals(iv.map { it.semitones }.sorted(), iv.map { it.semitones })
    }

    @Test fun `targetMidi goes above for ascending and below for descending`() {
        val tonic = 60
        assertEquals(67, IntervalTrainer.targetMidi(tonic, 7, ascending = true))   // P5 up
        assertEquals(53, IntervalTrainer.targetMidi(tonic, 7, ascending = false))  // P5 down
        assertEquals(tonic, IntervalTrainer.targetMidi(tonic, 0, ascending = true))    // unison
        assertEquals(72, IntervalTrainer.targetMidi(tonic, 12, ascending = true))      // octave up
    }

    @Test fun `choiceFor maps semitones to the right interval`() {
        assertEquals("M3", IntervalTrainer.choiceFor(4).shortName)
        assertEquals("TT", IntervalTrainer.choiceFor(6).shortName)
    }

    // ---- Mistake-drill progression keys (#drill) ----

    @Test fun `progressionKey round-trips major minor and harmonic-minor`() {
        val cases = listOf(
            Progression(TrainingMode.Major, listOf(1, 5, 6, 4)),
            Progression(TrainingMode.Minor, listOf(1, 6, 3, 7)),
            Progression(TrainingMode.Minor, listOf(1, 4, 5, 1), dominantBars = setOf(2)),
        )
        for (p in cases) {
            val key = EarTraining.progressionKey(p)
            val back = EarTraining.progressionFromKey(key)
            assertEquals(p.mode, back?.mode)
            assertEquals(p.degrees, back?.degrees)
            assertEquals(p.dominantBars, back?.dominantBars)
        }
        assertEquals("maj:1,5,6,4", EarTraining.progressionKey(cases[0]))
        assertEquals("min:1,4,5,1@2", EarTraining.progressionKey(cases[2]))
    }

    @Test fun `natural and harmonic minor with same degrees get distinct keys`() {
        val natural = Progression(TrainingMode.Minor, listOf(1, 4, 5, 1))
        val harmonic = Progression(TrainingMode.Minor, listOf(1, 4, 5, 1), dominantBars = setOf(2))
        assertTrue(EarTraining.progressionKey(natural) != EarTraining.progressionKey(harmonic))
    }

    @Test fun `progressionFromKey rejects malformed keys`() {
        assertEquals(null, EarTraining.progressionFromKey("maj:1,5,6"))     // only 3 degrees
        assertEquals(null, EarTraining.progressionFromKey("maj:1,5,6,8"))   // degree out of range
        assertEquals(null, EarTraining.progressionFromKey("xyz:1,5,6,4"))   // bad prefix
    }

    // ---- Major/minor-ambiguous Roman labels (challenge answer disambiguation) ----

    @Test fun `only the dominant V family reads the same in major and minor`() {
        // Every label the harmonic-minor dominant can print, at every chord level.
        for (r in listOf("V", "V7", "V9", "V6", "V11", "V13")) {
            assertTrue(EarTraining.romanIsModeAmbiguous(r), "$r should be ambiguous")
        }
        // Every other degree of both rows is separated by case or an accidental.
        val unambiguous = (EarTraining.MAJOR_DEGREES.values.map { it.roman } +
            EarTraining.MINOR_DEGREES.values.map { it.roman }).filter { it != "V" }
        for (r in unambiguous) {
            assertTrue(!EarTraining.romanIsModeAmbiguous(r), "$r should not be ambiguous")
        }
        // Near-misses that start with V but are a different numeral.
        for (r in listOf("VI7", "vi", "v7", "bVII", "bVI", "")) {
            assertTrue(!EarTraining.romanIsModeAmbiguous(r), "$r should not be ambiguous")
        }
    }

    @Test fun `both readings of an ambiguous Roman are tagged`() {
        // Tagging only the minor one left "✘ V7" looking self-explanatory next to a
        // guess reading "V7 (minor)" — the pair has to say which is which.
        assertEquals("(minor)", EarTraining.romanModeTag(minorReading = true))
        assertEquals("(major)", EarTraining.romanModeTag(minorReading = false))
    }

    @Test fun `harmonic-minor dominant and major V print the same bare label`() {
        val minorV7 = EarTraining.resolve(5, PitchClass.A, TrainingMode.Minor, ChordTypeLevel.Sevenths, asDominant = true)
        val majorV7 = EarTraining.resolve(5, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Sevenths)
        assertEquals("V7", minorV7.romanLabel)
        assertEquals("V7", majorV7.romanLabel)   // identical — hence the "(minor)" marker
        assertEquals("E7", minorV7.symbol)
        assertEquals("G7", majorV7.symbol)       // ...but different chords entirely
    }
}
