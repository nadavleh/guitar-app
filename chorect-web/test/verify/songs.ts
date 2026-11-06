// Area "songs" of the web parity checks (split out of the old monolithic verify.ts).
// Run alone: npm run verify -- songs

import { parseChord, parseKey, prefersFlats, transposeSymbol, transposeKey, degreeLabel, degreeLabels } from "../../src/theory";
import { SONGS, SONGS_WITH_CHORDS, SONG_ARTISTS, SONG_LIBRARY_DIGEST, Song, songHasChords, songChordVocabulary, searchSongs } from "../../src/theory/songLibrary";
import { check } from "./harness";

export function run(): void {

  // --- Songs library: one generator writes both ports, so they must agree exactly ---
  // Digest and counts are copied from the generated Kotlin SongLibrary; regenerate
  // with tools/build_song_library.py after changing either JSON input.
  check("song library digest matches Kotlin", SONG_LIBRARY_DIGEST === "d3e6ba15f25d8d7c");
  check("song count matches Kotlin", SONGS.length === 229);
  check("songs with chord data matches Kotlin", SONGS_WITH_CHORDS.length === 15);
  check("every song row is usable (title, http url, sane capo)",
    SONGS.every((s) => s.title.trim().length > 0 && s.url.startsWith("http") &&
      s.site.trim().length > 0 && s.capo >= 0 && s.capo <= 12));
  check("no song appears twice", (() => {
    const keys = SONGS.map((s) => (s.artist + "|" + s.title).toLowerCase());
    return new Set(keys).size === keys.length;
  })());
  // Compare the two keys SEPARATELY, the way the generator (Python tuple sort) and
  // SongLibraryTest (compareBy) both do. Joining them with "|" is NOT equivalent:
  // 0x7C sorts after space, so "Bob|x" > "Bob Dylan|y" while ("bob","x") < ("bob dylan","y").
  check("songs are sorted by artist then title", (() => {
    for (let i = 1; i < SONGS.length; i++) {
      const a = SONGS[i - 1], b = SONGS[i];
      const aa = a.artist.toLowerCase(), ba = b.artist.toLowerCase();
      if (aa > ba) return false;
      if (aa === ba && a.title.toLowerCase() > b.title.toLowerCase()) return false;
    }
    return true;
  })());
  // Storing SOUNDING symbols only pays off if the engine can resolve them - an
  // unparseable symbol would render as a dead row in the tab.
  check("every chord symbol in the library parses", SONGS_WITH_CHORDS.every((s) =>
    s.sections.every((sec) => sec.chords.length > 0 && sec.chords.every((c) => parseChord(c) !== null))));
  check("every song with chords names a key that parses",
    SONGS_WITH_CHORDS.every((s) => s.key !== null && parseChord(s.key) !== null));
  check("a song without chords is still a valid row", (() => {
    const bare = SONGS.filter((s) => !songHasChords(s));
    return bare.length > 0 && bare.every((s) => s.sections.length === 0 && s.key === null &&
      songChordVocabulary(s).length === 0);
  })());
  check("chord vocabulary de-dupes in first-seen order", (() => {
    const s = SONGS_WITH_CHORDS.find((x) => x.sections.reduce((n, sec) => n + sec.chords.length, 0) > 3)!;
    const v = songChordVocabulary(s);
    return new Set(v).size === v.length && v[0] === s.sections[0].chords[0];
  })());
  check("search matches title and artist; blank returns everything",
    searchSongs("   ").length === SONGS.length &&
    searchSongs("beatles").length > 0 &&
    searchSongs("beatles").every((s) => s.artist.toLowerCase().includes("beatles")) &&
    searchSongs("zzzznotasong").length === 0);
  check("artists are distinct and never blank",
    new Set(SONG_ARTISTS).size === SONG_ARTISTS.length && SONG_ARTISTS.every((a) => a.trim().length > 0));
  // Seeds come from common musical knowledge, not from Nadav's own sheets; the tab
  // marks them so nothing poses as his transcription.
  check("the shipped library is seeds only", SONGS_WITH_CHORDS.every((s) => s.seeded));

  // --- Song sheet: transposition and degrees ---
  // Mirrors SongSheetTest.kt. If these two drift, the same song shows different
  // degree labels on the phone and on the web.
  check("a key parses from a chord symbol", (() => {
    const g = parseKey("G"), am = parseKey("Am"), fsm = parseKey("F#m");
    return g?.tonic === 7 && !g.minor && am?.tonic === 9 && am.minor &&
      fsm?.tonic === 6 && fsm.minor;
  })());
  check("a major-seventh key is not mistaken for minor", parseKey("Cmaj")?.minor === false);
  check("nonsense is not a key", parseKey("") === null && parseKey("H") === null);
  check("flat keys prefer flat spelling",
    prefersFlats(parseKey("F")) && prefersFlats(parseKey("Bb")) &&
    !prefersFlats(parseKey("G")) && !prefersFlats(null));
  check("transposing keeps the quality",
    transposeSymbol("C", 2) === "D" && transposeSymbol("Cm7", 2) === "Dm7" &&
    transposeSymbol("Cmaj7", 2) === "Dmaj7");
  check("transposing moves the slash bass with the chord",
    transposeSymbol("D/F#", 2) === "E/G#" && transposeSymbol("G/B", 5) === "C/E");
  check("transposing can spell flat",
    transposeSymbol("A", 1, true) === "Bb" && transposeSymbol("A", 1, false) === "A#" &&
    transposeSymbol("D/F#", 1, true) === "Eb/G");
  check("transposing wraps the octave and accepts negatives",
    transposeSymbol("C", 12) === "C" && transposeSymbol("C", -1) === "B" &&
    transposeSymbol("C", 11) === "B");
  check("an unparseable symbol transposes to itself",
    transposeSymbol("N.C.", 3) === "N.C." && transposeSymbol("%", 3) === "%");
  check("the key transposes with the chords",
    transposeKey(parseKey("G")!, 2) === "A" && transposeKey(parseKey("Am")!, 2) === "Bm" &&
    transposeKey(parseKey("Am")!, 1, true) === "Bbm");
  check("diatonic chords get their Roman numerals in a major key", (() => {
    const c = parseKey("C")!;
    return degreeLabel("C", c) === "I" && degreeLabel("Dm", c) === "ii" &&
      degreeLabel("Em", c) === "iii" && degreeLabel("F", c) === "IV" &&
      degreeLabel("G", c) === "V" && degreeLabel("Am", c) === "vi";
  })());
  check("the quality rides along with the numeral", (() => {
    const c = parseKey("C")!;
    return degreeLabel("G7", c) === "V7" && degreeLabel("Cmaj7", c) === "Imaj7" &&
      degreeLabel("Dm7", c) === "ii7" && degreeLabel("Bm7b5", c) === "viiø7";
  })());
  check("chromatic chords keep their accidental", (() => {
    const c = parseKey("C")!;
    return degreeLabel("Bb", c) === "bVII" && degreeLabel("Eb", c) === "bIII" &&
      degreeLabel("D7", c) === "II7";
  })());
  check("an inversion names the bass degree", (() => {
    const c = parseKey("C")!;
    return degreeLabel("C/E", c) === "I/3" && degreeLabel("C/G", c) === "I/5" &&
      degreeLabel("G/B", c) === "V/7";
  })());
  check("a minor key labels its own diatonic set", (() => {
    const am = parseKey("Am")!;
    return degreeLabel("Am", am) === "i" && degreeLabel("Dm", am) === "iv" &&
      degreeLabel("Em", am) === "v" && degreeLabel("G", am) === "VII" &&
      degreeLabel("C", am) === "III";
  })());
  check("degrees are invariant under transposition", (() => {
    const prog = ["C", "Am", "F", "G7", "C/E"];
    const inC = degreeLabels(prog, parseKey("C")!);
    const inD = degreeLabels(prog.map((s) => transposeSymbol(s, 2)), parseKey("D")!);
    return JSON.stringify(inC) === JSON.stringify(inD);
  })());
}
