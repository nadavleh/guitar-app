/** Interval → reference-song lookup for the Theory tab and the interval trainer.
 *
 *  DESCENDING comes from Nadav's "Descending Interval Song References" PDF
 *  (kept verbatim); ASCENDING is the canonical companion list Claude generated.
 *  `inversion` is the octave complement — NOT the same two pitches backward:
 *  major↔minor switch, perfect stays perfect, the tritone inverts to itself.
 *  Mirror of Kotlin IntervalSongs.kt. */
export interface IntervalSongRef {
  ascending: boolean;
  /** Short interval name, e.g. "m2", "P5", "TT", "P8". */
  interval: string;
  intervalLong: string;
  /** Octave-complement note, e.g. "inverts to M7 ascending". */
  inversion: string;
  song: string;
  artist: string;
  cue: string;
}

export const INTERVAL_COMPLEMENT_NOTE =
  "The inversion listed is the octave complement — not the same two pitches played backward. " +
  "Major and minor switch, perfect intervals stay perfect, the tritone inverts to itself.";

function asc(interval: string, intervalLong: string, inversion: string, song: string, artist: string, cue: string): IntervalSongRef {
  return { ascending: true, interval, intervalLong, inversion, song, artist, cue };
}
function desc(interval: string, intervalLong: string, inversion: string, song: string, artist: string, cue: string): IntervalSongRef {
  return { ascending: false, interval, intervalLong, inversion, song, artist, cue };
}

/** Ascending references (canonical picks; Claude-generated companion list). */
export const INTERVAL_SONGS_ASCENDING: IntervalSongRef[] = [
  asc("m2", "Minor 2nd", "inverts to M7 descending", "Jaws — main theme", "John Williams", "The two-note ostinato: E–F, rising."),
  asc("M2", "Major 2nd", "inverts to m7 descending", "Happy Birthday", "traditional", "“Hap-py birth-” — the first step up."),
  asc("m3", "Minor 3rd", "inverts to M6 descending", "Greensleeves", "traditional", "“A-las my love” — the opening leap."),
  asc("M3", "Major 3rd", "inverts to m6 descending", "When the Saints Go Marching In", "traditional", "“Oh when the...” — the first two notes."),
  asc("P4", "Perfect 4th", "inverts to P5 descending", "Here Comes the Bride", "Wagner", "“Here comes” — the opening leap. Also “A-ma-zing Grace”."),
  asc("TT", "Tritone", "inverts to TT descending", "The Simpsons — main theme", "Danny Elfman", "“The Simp-sons” — the sung opening. Also “Ma-ri-a” (West Side Story)."),
  asc("P5", "Perfect 5th", "inverts to P4 descending", "Twinkle Twinkle Little Star", "traditional", "“Twin-kle twin-kle” — note 2 to note 3. Also the Star Wars main theme opening."),
  asc("m6", "Minor 6th", "inverts to M3 descending", "Manhã de Carnaval (Black Orpheus)", "Luiz Bonfá", "The famous opening melodic leap. Also “Go Down Moses” (“When Is-rael...”)."),
  asc("M6", "Major 6th", "inverts to m3 descending", "My Bonnie Lies Over the Ocean", "traditional", "“My Bon-nie” — the opening leap. Also the NBC chimes (first two notes)."),
  asc("m7", "Minor 7th", "inverts to M2 descending", "Somewhere (West Side Story)", "Bernstein", "“There's a place” — “There's a...” leaps a m7. Also the original Star Trek fanfare."),
  asc("M7", "Major 7th", "inverts to m2 descending", "Take On Me", "a-ha", "The chorus leap up to the final high “me”. Or construct it: octave up, then a minor 2nd down."),
  asc("P8", "Perfect octave", "inverts to unison", "Somewhere Over the Rainbow", "Harold Arlen", "“Some-where” — the opening octave leap."),
];

/** Descending references (from the PDF). */
export const INTERVAL_SONGS_DESCENDING: IntervalSongRef[] = [
  desc("m2", "Minor 2nd", "inverts to M7 ascending", "Für Elise", "Beethoven", "First two notes: E to D#."),
  desc("M2", "Major 2nd", "inverts to m7 ascending", "Mary Had a Little Lamb", "traditional", "First two notes: “Ma-ry”."),
  desc("m3", "Minor 3rd", "inverts to M6 ascending", "Hey Jude", "The Beatles", "Opening words: “Hey Jude”."),
  desc("M3", "Major 3rd", "inverts to m6 ascending", "Symphony No. 5", "Beethoven", "In “da-da-da-DUM”, the third short note drops to the long note."),
  desc("P4", "Perfect 4th", "inverts to P5 ascending", "Under Pressure", "Queen & David Bowie", "The exposed downward leap in the famous opening bass riff."),
  desc("TT", "Tritone", "inverts to TT ascending", "Black Sabbath", "Black Sabbath", "The high note dropping back to the low note in the main guitar riff."),
  desc("P5", "Perfect 5th", "inverts to P4 ascending", "Game of Thrones — main theme", "Ramin Djawadi", "The prominent descending-fifth gesture in the main theme."),
  desc("m6", "Minor 6th", "inverts to M3 ascending", "Chega de Saudade", "João Gilberto", "Opening melodic gesture on “Vai, minha tristeza...”."),
  desc("M6", "Major 6th", "inverts to m3 ascending", "No Surprises", "Radiohead", "The conspicuous wide downward leap in the vocal melody."),
  desc("m7", "Minor 7th", "inverts to M2 ascending", "Till There Was You", "The Beatles", "“All” down to “till” in “I never heard them at all / till there was you”."),
  desc("M7", "Major 7th", "inverts to m2 ascending", "(no clean familiar example)", "", "Construct it: an octave downward, then a minor 2nd upward."),
  desc("P8", "Perfect octave", "inverts to unison", "My Sharona", "The Knack", "The octave drop in the main guitar riff."),
];
