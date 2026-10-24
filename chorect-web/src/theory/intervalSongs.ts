/** Interval → reference-song lookup for the Theory tab and the interval trainer.
 *
 *  DESCENDING comes from Nadav's "Descending Interval Song References" PDF;
 *  ASCENDING is the canonical companion list Claude generated. `inversion` is the
 *  octave complement — NOT the same two pitches backward.
 *
 *  Every song carries a few-note MIDI `melody` snippet that contains the interval:
 *  space-separated `offset/length` tokens, offset in semitones from the snippet's
 *  first note, length in eighth notes.
 *
 *  Mirror of Kotlin IntervalSongs.kt — the data below is GENERATED from it; edit the
 *  Kotlin file, then regenerate, so the two lists cannot drift. */
export interface IntervalSong { title: string; artist: string; cue: string; melody: string; }

export interface IntervalSongRef {
  ascending: boolean;
  /** Short interval name, e.g. "m2", "P5", "TT", "P8". */
  interval: string;
  intervalLong: string;
  /** Distance in semitones, 1..12. */
  semitones: number;
  /** Octave-complement note, e.g. "inverts to M7 ascending". */
  inversion: string;
  /** Reference songs, best-known first. Empty only where no clean example exists. */
  songs: IntervalSong[];
  /** Shown instead of songs when `songs` is empty. */
  note?: string;
}

/** One melody note: absolute MIDI pitch and length in eighth notes. */
export interface MelodyNote { midi: number; eighths: number; }

export const INTERVAL_COMPLEMENT_NOTE =
  "The inversion listed is the octave complement — not the same two pitches played backward. " +
  "Major and minor switch, perfect intervals stay perfect, the tritone inverts to itself.";

/** Playback length of one eighth note in a melody snippet (150 bpm). */
export const INTERVAL_EIGHTH_MS = 200;

function s(title: string, artist: string, cue: string, melody: string): IntervalSong {
  return { title, artist, cue, melody };
}
function asc(interval: string, intervalLong: string, semitones: number, inversion: string, songs: IntervalSong[]): IntervalSongRef {
  return { ascending: true, interval, intervalLong, semitones, inversion, songs };
}
function desc(interval: string, intervalLong: string, semitones: number, inversion: string, songs: IntervalSong[]): IntervalSongRef {
  return { ascending: false, interval, intervalLong, semitones, inversion, songs };
}

// Shared by two rows each (the snippet holds both intervals).
const ENTERTAINER_UP = s("The Entertainer", "Scott Joplin",
  "The E in the pickup leaping up to the high C.", "0/0.5 1/0.5 2/0.5 10/1 2/0.5 10/1 2/0.5 10/3");
const ENTERTAINER_DOWN: IntervalSong = { ...ENTERTAINER_UP, cue: "The high C falling back to the E." };
const RAINBOW = "0/4 12/4 11/2 7/1 9/1 11/2 12/4 0/4 9/4 7/6";

/** Ascending references (canonical picks). */
export const INTERVAL_SONGS_ASCENDING: IntervalSongRef[] = [
  asc("m2", "Minor 2nd", 1, "inverts to M7 descending", [
    s("Jaws — main theme", "John Williams", "The rising two-note ostinato that opens the theme.",
      "0/4 1/4 0/3 1/3 0/2 1/2 0/1 1/1 0/1 1/1"),
    s("White Christmas", "Irving Berlin", "“I'm dream-” — the first step up.",
      "0/2 1/2 0/2 -1/2 0/2 1/2 2/2 3/6"),
    s("The Pink Panther Theme", "Henry Mancini", "Each sneaking pair of the opening.",
      "0/1 1/5 3/1 4/5 0/1 1/1 3/1 4/1 9/1 8/4"),
    s("Für Elise", "Beethoven", "The D# stepping back up to E — the second and third notes.",
      "0/1 -1/1 0/1 -1/1 0/1 -5/1 -2/1 -4/1 -9/3"),
  ]),
  asc("M2", "Major 2nd", 2, "inverts to m7 descending", [
    s("Happy Birthday", "traditional", "The step up onto the second syllable of “birth-day”.",
      "0/1.5 0/0.5 2/2 0/2 5/2 4/4 0/1.5 0/0.5 2/2 0/2 7/2 5/4"),
    s("Frère Jacques", "traditional", "“Frè-re” — the first step.",
      "0/2 2/2 4/2 0/2 0/2 2/2 4/2 0/2 4/2 5/2 7/4"),
    s("Silent Night", "Franz Gruber", "“Si-lent” — the first two notes.",
      "0/3 2/1 0/2 -3/6 0/3 2/1 0/2 -3/6"),
    s("Rudolph the Red-Nosed Reindeer", "Johnny Marks", "“Ru-dolph” — the first two notes.",
      "0/1 2/2 0/1 -3/2 5/2 2/2 0/6 0/1 2/1 0/1 2/1 0/2"),
    s("Do-Re-Mi", "Rodgers & Hammerstein", "“Doe, a” — do up to re.",
      "0/3 2/1 4/3 0/1 4/2 0/2 4/4 2/3 4/1 5/1 5/1 4/1"),
  ]),
  asc("m3", "Minor 3rd", 3, "inverts to M6 descending", [
    s("Greensleeves", "traditional", "The opening upward leap of the tune.",
      "0/2 3/4 5/2 7/3 8/1 7/2 5/4 2/2 -2/3 0/1 2/2 3/4"),
    s("Smoke on the Water", "Deep Purple", "The first two notes of the riff.",
      "0/2 3/2 5/3 0/1 3/2 6/1 5/4 0/2 3/2 5/3 3/1 0/4"),
    s("Brahms' Lullaby", "Brahms", "“Lul-la-by” — up from the repeated note.",
      "0/1 0/1 3/4 0/1 0/1 3/4 0/1 3/1 8/2 7/3 5/1 5/2"),
    s("Iron Man", "Black Sabbath", "The first two notes of the riff.",
      "0/2 3/2 3/1 5/1 5/2 8/0.5 7/0.5 8/0.5 7/0.5 8/0.5 7/0.5 3/2"),
    s("Seven Nation Army", "The White Stripes", "The riff: the repeated note, then up to the third note.",
      "0/3 0/1 3/1.5 0/1.5 -2/1 -4/4 -5/4"),
  ]),
  asc("M3", "Major 3rd", 4, "inverts to m6 descending", [
    s("When the Saints Go Marching In", "traditional", "The first two notes of the melody.",
      "0/2 4/2 5/2 7/8 0/2 4/2 5/2 7/8"),
    s("Kumbaya", "traditional", "“Kum-ba-” — the first two notes.",
      "0/1 4/1 7/4 7/1 7/1 9/2 9/2 7/4"),
    s("Spring (The Four Seasons)", "Vivaldi", "The opening fanfare: the pickup up to the repeated note.",
      "0/1 4/1 4/1 4/1 2/0.5 0/0.5 7/3 5/0.5 4/0.5 4/1 4/1 4/1"),
  ]),
  asc("P4", "Perfect 4th", 5, "inverts to P5 descending", [
    s("Bridal Chorus (“Here Comes the Bride”)", "Wagner", "The opening leap.",
      "0/2 5/1.5 5/0.5 5/4 0/2 7/1.5 4/0.5 5/4"),
    s("Amazing Grace", "traditional", "“A-ma-” — the pickup into the first beat.",
      "0/2 5/4 9/1 5/1 9/4 7/2 5/4 2/2 0/4"),
    s("Auld Lang Syne", "traditional", "“Should auld” — the pickup leap.",
      "0/2 5/3 5/1 5/2 9/2 7/3 5/1 7/2 9/2"),
    s("Love Me Tender", "Elvis Presley", "“Love me” — the first two notes.",
      "0/2 5/2 4/2 5/2 7/2 2/2 7/4 5/2 4/2 2/2 4/2 5/4"),
    s("We Wish You a Merry Christmas", "traditional", "“We wish” — the first leap.",
      "0/2 5/2 5/1 7/1 5/1 4/1 2/2 2/2 2/2 7/2 7/1 9/1"),
  ]),
  asc("TT", "Tritone", 6, "inverts to TT descending", [
    s("Maria (West Side Story)", "Leonard Bernstein", "“Ma-ri-a” — the leap onto “ri”, which then resolves up a half step.",
      "0/1 6/3 7/6 0/1 6/3 7/6"),
    s("The Simpsons Theme", "Danny Elfman", "The opening line, first note to third (the middle note passes through).",
      "0/3 4/2 6/1 9/2 7/3 4/2 0/2 -3/1 -6/1 -6/1 -6/1 -5/3"),
  ]),
  asc("P5", "Perfect 5th", 7, "inverts to P4 descending", [
    s("Twinkle Twinkle Little Star", "traditional", "“Twin-kle, twin-kle” — up onto the second pair.",
      "0/2 0/2 7/2 7/2 9/2 9/2 7/4 5/2 5/2 4/2 4/2 2/2"),
    s("Star Wars — main theme", "John Williams", "The opening leap of the main theme.",
      "0/6 7/6 5/1 4/1 2/1 12/6 7/3 5/1 4/1 2/1 12/6 7/3"),
    s("My Favorite Things", "Rodgers & Hammerstein", "“Rain-drops” — the first two notes.",
      "0/2 7/2 7/2 2/2 0/2 0/2 7/2 7/2 2/2 0/4"),
    s("Scarborough Fair", "traditional", "“you go-” — the leap after the repeated first note.",
      "0/2 0/2 7/2 7/2 2/3 3/1 2/2 0/6"),
  ]),
  asc("m6", "Minor 6th", 8, "inverts to M3 descending", [
    ENTERTAINER_UP,
  ]),
  asc("M6", "Major 6th", 9, "inverts to m3 descending", [
    s("My Bonnie Lies Over the Ocean", "traditional", "“My Bon-” — the opening leap.",
      "0/2 9/3 7/1 5/2 7/2 5/2 2/2 0/2 -3/6"),
    s("NBC chimes", "NBC", "The first two chimes.",
      "0/2 9/2 5/4 0/2 9/2 5/4"),
    s("Jingle Bells (verse)", "traditional", "“Dash-ing” — the first two notes of the verse.",
      "0/1 9/1 7/1 5/1 0/4 0/0.5 0/0.5 0/1 9/1 7/1 5/1 2/4"),
  ]),
  asc("m7", "Minor 7th", 10, "inverts to M2 descending", [
    s("Somewhere (West Side Story)", "Leonard Bernstein", "“There's a place” — the wide opening leap.",
      "0/2 10/4 9/6 0/2 10/4 9/6"),
  ]),
  asc("M7", "Major 7th", 11, "inverts to m2 descending", [
    s("Somewhere Over the Rainbow", "Harold Arlen", "1st note to 3rd note: “Some-(where) o-” — up an octave, then down a half step.",
      RAINBOW),
  ]),
  asc("P8", "Perfect octave", 12, "inverts to unison", [
    s("Somewhere Over the Rainbow", "Harold Arlen", "The octave leap on the very first two notes.",
      RAINBOW),
  ]),
];

/** Descending references (from the PDF). */
export const INTERVAL_SONGS_DESCENDING: IntervalSongRef[] = [
  desc("m2", "Minor 2nd", 1, "inverts to M7 ascending", [
    s("Für Elise", "Beethoven", "The first two notes: E down to D#.",
      "0/1 -1/1 0/1 -1/1 0/1 -5/1 -2/1 -4/1 -9/3"),
    s("Joy to the World", "traditional", "“Joy to” — the first step down.",
      "0/2 -1/1.5 -3/0.5 -5/3 -7/1 -8/2 -10/2 -12/4"),
    s("Fly Me to the Moon", "Bart Howard", "“Fly me” — the first step of the falling scale.",
      "0/3 -1/1 -3/2 -5/2 -7/6 -5/1 -3/1 0/2 -1/2 -3/2 -5/2 -7/4"),
  ]),
  desc("M2", "Major 2nd", 2, "inverts to m7 ascending", [
    s("Mary Had a Little Lamb", "traditional", "The first two notes of the tune.",
      "0/3 -2/1 -4/2 -2/2 0/2 0/2 0/4 -2/2 -2/2 -2/4"),
    s("Three Blind Mice", "traditional", "“Three blind” — the first two notes.",
      "0/2 -2/2 -4/4 0/2 -2/2 -4/4"),
    s("Yesterday", "The Beatles", "“Yes-ter-” — the first two notes.",
      "0/1 -2/1 -2/6 2/1 4/1 6/1 7/1 9/1 10/1 9/1.5 7/0.5 7/4"),
    s("Silent Night", "Franz Gruber", "“-lent night” — the step back down after the first two notes.",
      "0/3 2/1 0/2 -3/6 0/3 2/1 0/2 -3/6"),
    s("The First Noel", "traditional", "“The first” — the first two notes.",
      "0/1 -2/1 -4/3 -2/1 0/1 1/1 3/4 5/1 7/1 8/2 7/2 5/2"),
  ]),
  desc("m3", "Minor 3rd", 3, "inverts to M6 ascending", [
    s("Hey Jude", "The Beatles", "The drop across the first two sung syllables.",
      "0/4 -3/6 -3/1 0/1 2/2 -5/6"),
    s("Frosty the Snowman", "Gene Autry", "“Fros-ty” — the first two notes.",
      "0/4 -3/3 -2/1 0/2 5/4 4/1 5/1 7/2 5/2 4/2 2/2 0/4"),
    s("The Star-Spangled Banner", "John Stafford Smith", "“O-oh” — the first two notes, before the triad keeps falling to “say”.",
      "0/1.5 -3/0.5 -7/2 -3/2 0/2 5/4 9/1.5 7/0.5 5/2 -3/2 -1/2 0/4"),
    s("This Old Man", "traditional", "“This old” — the first two notes.",
      "0/2 -3/2 0/4 0/2 -3/2 0/4 2/2 0/2 -2/2 -3/2 -5/2 -3/2"),
  ]),
  desc("M3", "Major 3rd", 4, "inverts to m6 ascending", [
    s("Symphony No. 5", "Beethoven", "In the four-note motif, the third short note drops to the long one.",
      "0/1 0/1 0/1 -4/6 -2/1 -2/1 -2/1 -5/8"),
    s("Summertime", "George Gershwin", "“Sum-mer-” — the first two notes.",
      "0/1 -4/1 0/6 -2/1 -4/1 -2/1 0/1 -4/1 -7/4"),
  ]),
  desc("P4", "Perfect 4th", 5, "inverts to P5 ascending", [
    s("Under Pressure", "Queen & David Bowie", "The exposed downward leap in the opening bass riff.",
      "0/1 0/1 0/1 0/1 0/1 0/1 -5/2 0/4 0/1 0/1 0/1 -5/2"),
    s("Eine kleine Nachtmusik", "Mozart", "The opening: down a fourth, twice.",
      "0/3 -5/1 0/3 -5/1 0/1 -5/1 0/1 4/1 7/4"),
    s("O Come, All Ye Faithful", "traditional", "“come, all” — the drop after the repeated opening note.",
      "0/2 0/2 -5/2 0/2 2/4 -5/4 4/2 2/2 4/2 5/2 4/4 2/4"),
    s("Shave and a Haircut", "traditional", "“Shave and” — the first drop.",
      "0/2 -5/1 -5/1 -3/2 -5/4 -1/2 0/2"),
  ]),
  desc("TT", "Tritone", 6, "inverts to TT ascending", [
    s("Black Sabbath", "Black Sabbath", "The riff: low note, its octave, then down a tritone.",
      "0/4 12/4 6/8 0/4 12/4 6/8"),
  ]),
  desc("P5", "Perfect 5th", 7, "inverts to P4 ascending", [
    s("Game of Thrones — main theme", "Ramin Djawadi", "The prominent descending-fifth gesture of the theme.",
      "0/2 -7/2 -4/1 -2/1 0/2 -7/2 -4/1 -2/1 0/2 -7/2 -4/1 -2/1"),
  ]),
  desc("m6", "Minor 6th", 8, "inverts to M3 ascending", [
    ENTERTAINER_DOWN,
  ]),
  { ascending: false, interval: "M6", intervalLong: "Major 6th", semitones: 9, inversion: "inverts to m3 ascending", songs: [],
    note: "No song whose melody can be transcribed reliably yet. The ▶ plays the interval; it is the octave complement of the ascending m3 (Greensleeves, Smoke on the Water)." },
  { ascending: false, interval: "m7", intervalLong: "Minor 7th", semitones: 10, inversion: "inverts to M2 ascending", songs: [],
    note: "No song whose melody can be transcribed reliably yet. The ▶ plays the interval; it is the octave complement of the ascending M2 (Happy Birthday, Frère Jacques)." },
  { ascending: false, interval: "M7", intervalLong: "Major 7th", semitones: 11, inversion: "inverts to m2 ascending", songs: [],
    note: "No clean familiar example. Construct it: an octave downward, then a minor 2nd back upward. The ▶ plays exactly this interval." },
  desc("P8", "Perfect octave", 12, "inverts to unison", [
    s("Somewhere Over the Rainbow", "Harold Arlen", "“(rain)-bow” down to “way (up high)”.",
      RAINBOW),
  ]),
];

/** The reference row for `semitones` (1..12) in one direction, or undefined (unison). */
export function intervalRefFor(semitones: number, ascending: boolean): IntervalSongRef | undefined {
  return (ascending ? INTERVAL_SONGS_ASCENDING : INTERVAL_SONGS_DESCENDING).find((r) => r.semitones === semitones);
}

/** Parse a melody into absolute notes, transposed so the snippet's range is centred on
 *  `centerMidi` (default F#4). */
export function melodyNotes(melody: string, centerMidi = 66): MelodyNote[] {
  const raw = melody.trim().split(/\s+/).filter((t) => t.length > 0).map((tok) => {
    const [o, l] = tok.split("/");
    return [parseInt(o, 10), parseFloat(l)] as [number, number];
  });
  if (raw.length === 0) return [];
  const lo = Math.min(...raw.map((r) => r[0]));
  const hi = Math.max(...raw.map((r) => r[0]));
  const base = centerMidi - Math.floor((lo + hi) / 2);
  return raw.map(([o, l]) => ({ midi: base + o, eighths: l }));
}

/** Index of the first note that starts the leap (to the next note or the one after). */
function leapStart(n: number[], semitones: number, ascending: boolean): number | null {
  const want = ascending ? semitones : -semitones;
  for (let i = 0; i < n.length; i++)
    for (let j = i + 1; j <= Math.min(i + 2, n.length - 1); j++)
      if (n[j] - n[i] === want) return i;
  return null;
}

/** Parse `melody` so its first `semitones`-in-this-direction leap starts on
 *  `anchorMidi` — the trainer plays a song on the exact pitches of the question it
 *  just asked. A snippet without that leap is centred on `anchorMidi` instead.
 *  Mirror of Kotlin IntervalSongs.melodyNotesAnchored. */
export function melodyNotesAnchored(melody: string, semitones: number, ascending: boolean, anchorMidi: number): MelodyNote[] {
  const n = melodyNotes(melody);
  const i = leapStart(n.map((x) => x.midi), semitones, ascending);
  if (i === null) return melodyNotes(melody, anchorMidi);
  const shift = anchorMidi - n[i].midi;
  return n.map((x) => ({ midi: x.midi + shift, eighths: x.eighths }));
}

/** True when `melody` sounds `semitones` in the given direction between two notes at
 *  most two apart (adjacent, or 1st→3rd). */
export function melodyContains(melody: string, semitones: number, ascending: boolean): boolean {
  return leapStart(melodyNotes(melody).map((x) => x.midi), semitones, ascending) !== null;
}
