package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Coverage + hygiene guards for the [ProgressionSongs] library. These do not
 * check *which* songs are listed (that is editorial data), only that every
 * progression the trainer can present has a non-empty, well-formed song list —
 * so a progression added later without songs, or a key typo, fails the build.
 */
class ProgressionSongsTest {

    private fun assertClean(where: String, songs: List<SongExample>) {
        assertTrue(songs.isNotEmpty(), "$where has no song examples")
        for (s in songs) {
            assertTrue(s.title.isNotBlank(), "$where has a blank title")
            assertTrue(s.artist.isNotBlank(), "$where ('${s.title}') has a blank artist")
        }
        val keys = songs.map { it.title.lowercase() to it.artist.lowercase() }
        assertTrue(keys.size == keys.toSet().size, "$where has duplicate song entries")
    }

    @Test fun `every major progression has song examples`() {
        for (p in EarTraining.MAJOR_PROGRESSIONS) {
            assertClean("major ${EarTraining.romanLineFor(p)}", ProgressionSongs.forDiatonic(p))
        }
    }

    @Test fun `every minor progression has song examples`() {
        for (p in EarTraining.MINOR_PROGRESSIONS) {
            assertClean("minor ${EarTraining.romanLineFor(p)}", ProgressionSongs.forDiatonic(p))
        }
    }

    @Test fun `every harmonic-minor progression has songs and a dominant V that resolves`() {
        for (p in EarTraining.MINOR_HARMONIC_PROGRESSIONS) {
            assertClean("harmonic-minor ${EarTraining.romanLineFor(p)}", ProgressionSongs.forHarmonicMinor(p))
            // The marked dominant bar(s) must render a MAJOR V (roman starts with "V").
            val resolved = EarTraining.resolveProgression(p, PitchClass.A, ChordTypeLevel.Sevenths)
            for (i in p.dominantBars) {
                assertTrue(resolved[i].romanLabel.startsWith("V"),
                    "harmonic-minor ${EarTraining.romanLineFor(p)}: bar $i should be a dominant V, got ${resolved[i].romanLabel}")
                assertTrue(ChordLibrary.parse(resolved[i].symbol) != null, "unparseable ${resolved[i].symbol}")
            }
        }
    }

    @Test fun `every advanced progression has song examples`() {
        for (p in EarTraining.ADVANCED_PROGRESSIONS) {
            assertClean("advanced ${p.name}", ProgressionSongs.forAdvanced(p.name))
        }
    }

    @Test fun `every SUS and ADVANCED II progression has songs and resolves to parseable chords`() {
        for (p in EarTraining.SUS_PROGRESSIONS + EarTraining.ADVANCED2_PROGRESSIONS) {
            assertClean("category ${p.name}", ProgressionSongs.forAdvanced(p.name))
            for (rc in p.resolve(PitchClass.C)) {
                assertTrue(ChordLibrary.parse(rc.symbol) != null, "${p.name}: unparseable ${rc.symbol}")
            }
        }
    }

    @Test fun `every circle-of-fifths window has song examples`() {
        for (w in EarTraining.CIRCLE_WINDOWS) {
            assertClean("circle ${w.id} (${w.romanLine})", ProgressionSongs.forCircleWindow(w.id))
        }
    }
}
