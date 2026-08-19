package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The generated Songs-tab library. Everything here guards the GENERATOR
 *  (tools/build_song_library.py) — the .kt file itself is never hand-edited, so a
 *  failure means the generator or its two JSON inputs are wrong. */
class SongLibraryTest {

    @Test fun `the library is non-empty and every entry is usable`() {
        assertTrue(SongLibrary.SONGS.size > 200, "expected the whole bookmark folder, got ${SongLibrary.SONGS.size}")
        for (s in SongLibrary.SONGS) {
            assertTrue(s.title.isNotBlank(), "a song with no title")
            assertTrue(s.url.startsWith("http"), "${s.title}: bad url ${s.url}")
            assertTrue(s.site.isNotBlank(), "${s.title}: no site")
            assertTrue(s.capo in 0..12, "${s.title}: capo ${s.capo}")
        }
    }

    @Test fun `no song appears twice`() {
        val keys = SongLibrary.SONGS.map { (it.artist + "|" + it.title).lowercase() }
        assertEquals(keys.size, keys.toSet().size, "duplicate artist+title in the library")
    }

    @Test fun `songs are sorted by artist then title`() {
        val sorted = SongLibrary.SONGS.sortedWith(
            compareBy({ it.artist.lowercase() }, { it.title.lowercase() })
        )
        assertEquals(sorted.map { it.artist + "|" + it.title }, SongLibrary.SONGS.map { it.artist + "|" + it.title })
    }

    @Test fun `every chord symbol in the library is playable`() {
        // The whole point of storing SOUNDING symbols is that the existing engine can
        // resolve them — a symbol ChordLibrary can't parse would render as a dead row.
        for (s in SongLibrary.WITH_CHORDS) {
            for (sec in s.sections) {
                assertTrue(sec.chords.isNotEmpty(), "${s.title}: section '${sec.label}' has no chords")
                for (c in sec.chords) {
                    assertNotNull(ChordLibrary.parse(c), "${s.title} / ${sec.label}: '$c' does not parse")
                }
            }
        }
    }

    @Test fun `every song with chords names a key that parses`() {
        for (s in SongLibrary.WITH_CHORDS) {
            val key = s.key
            assertNotNull(key, "${s.title}: has chords but no key")
            assertNotNull(ChordLibrary.parse(key), "${s.title}: key '$key' does not parse")
        }
    }

    @Test fun `a song without chord data still lists`() {
        // The tab is a launcher first: an entry with no progression yet must still be a
        // valid row (title + link), never filtered out or crashing on empty sections.
        val bare = SongLibrary.SONGS.filter { !it.hasChords }
        assertTrue(bare.isNotEmpty(), "expected most of the folder to have no chords yet")
        for (s in bare) {
            assertTrue(s.sections.isEmpty())
            assertTrue(s.chordVocabulary.isEmpty())
            assertEquals(null, s.key)
        }
    }

    @Test fun `chord vocabulary is de-duplicated in first-seen order`() {
        val s = SongLibrary.WITH_CHORDS.first { it.sections.sumOf { sec -> sec.chords.size } > 3 }
        val vocab = s.chordVocabulary
        assertEquals(vocab.size, vocab.toSet().size, "vocabulary repeated a chord")
        assertEquals(s.sections.first().chords.first(), vocab.first())
    }

    @Test fun `search matches title and artist, and blank returns everything`() {
        assertEquals(SongLibrary.SONGS.size, SongLibrary.search("   ").size)
        val beatles = SongLibrary.search("beatles")
        assertTrue(beatles.isNotEmpty(), "no Beatles found")
        assertTrue(beatles.all { it.artist.lowercase().contains("beatles") })
        // Case-insensitive on the title side too.
        assertTrue(SongLibrary.search("HEY JUDE").any { it.title.equals("Hey Jude", ignoreCase = true) })
        assertTrue(SongLibrary.search("zzzznotasong").isEmpty())
    }

    @Test fun `artists are distinct and never blank`() {
        val a = SongLibrary.ARTISTS
        assertEquals(a.size, a.toSet().size)
        assertTrue(a.none { it.isBlank() })
        assertTrue(a.size > 50, "expected a wide spread of artists, got ${a.size}")
    }

    @Test fun `seeded chords are flagged so the UI can offer a correction`() {
        // Seeds are written from common musical knowledge, not from the owner's own
        // sheet; the tab marks them, so nothing silently poses as his transcription.
        assertTrue(SongLibrary.WITH_CHORDS.isNotEmpty())
        assertTrue(SongLibrary.WITH_CHORDS.all { it.seeded },
            "the first library ships seeds only — an unseeded entry means user data leaked into the repo")
    }

    @Test fun `the digest is a stable fingerprint the TS port must match`() {
        assertEquals(16, SongLibrary.DIGEST.length)
        assertFalse(SongLibrary.DIGEST.isBlank())
    }
}
