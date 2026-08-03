import { Progression, TrainingMode } from "./eartraining";

/** Real-song ear-training workout: the first-2-months curriculum (Claude-revised
 *  merge of Nadav's two ChatGPT plans — see
 *  docs/superpowers/specs/2026-08-03-ear-workout-theory-tab-design.md).
 *
 *  Track A = 32 song-based sessions (8 weeks × 4). Track B = the stricter
 *  one-song-per-week "deep" plan. Spoilers are normalized approximations —
 *  version differences are expected. Mirror of Kotlin EarWorkout.kt. */

/** A named recording; `version` pins the performance the exercise was built on. */
export interface WorkoutSong { title: string; artist: string; version?: string; }

/** One 45-minute Track-A session. `song` is null for student-choice/exam
 *  sessions (`songNote` then describes what to pick). `spoiler` is the answer
 *  key (empty = no key). `loop` is set only when the spoiler is a clean 4-bar
 *  diatonic loop the app can play back. */
export interface WorkoutSession {
  number: number;
  week: number;
  title: string;
  song: WorkoutSong | null;
  songNote?: string;
  focus: string;
  melody: string;
  harmonization: string;
  passGoal: string;
  spoiler: string;
  loop?: Progression;
}

/** One Track-B deep week: a single named recording studied for a whole week
 *  with strict grading boundaries. Week 8 is consolidation (no new song). */
export interface DeepWeek {
  week: number;
  songTitle: string;
  artist: string;
  recording: string;
  section: string;
  target: string;
  melodyTarget: string;
  notGraded: string[];
  labDrills: string[];
  passing: string;
  spoiler: string;
  loop?: Progression;
}

/** Merged global practice/evaluation rules (both tracks). */
export const WORKOUT_GLOBAL_RULES: string[] = [
  "Start every session with a guitarless listen — make an internal guess before the guitar confirms it.",
  "First pass maps the harmony: change points, stability vs tension, bass contour, broad quality — before any chord names.",
  "One hypothesis at a time on guitar: test a predicted root/quality/bass position, don't fish through shapes.",
  "Function before spelling: a correct functional category with an uncertain inversion is partial success, not a wrong answer.",
  "Arrangement details are bounded — an ornament or voicing is not evidence that your ear failed.",
  "Use the exact performer/version listed. Covers, remasters and simplified tutorials can contain different harmony.",
  "Synthetic 7th-chord drills stay for train rides; the 45-minute sessions are song-based. Keep spoilers hidden until you've attempted.",
];

/** The standard 45-minute session frame (minutes → task). */
export const WORKOUT_SESSION_FRAME: [string, string][] = [
  ["0–5", "Guitarless listen: number of chord events, bass contour, chord-quality guesses, resolution points, outside/dominant-like sounds."],
  ["5–18", "Functional chord identification on guitar — function first, then quality: major, minor, dominant, diminished, maj7, m7."],
  ["18–25", "Speed loop: call out function + quality in time over a short 4–8 bar section."],
  ["25–33", "Melody playback: Month 1 allows note-by-note; Month 2 aims for 2–4 note chunks."],
  ["33–42", "Constrained harmonization: choose a functional chord that structurally contains the melody note."],
  ["42–45", "Play-through: bass → chords → melody → reharmonized version."],
];

export const WORKOUT_MONTH1_RULE =
  "Month 1: the melody note must be root, 3rd or 5th of the chord you choose. No 9ths/11ths/13ths as justification. " +
  "Vocabulary: I, ii, IV, V, vi, V/V, V/vi, V/ii.";
export const WORKOUT_MONTH2_RULE =
  "Month 2: still prefer triad tones, but the melody may be the 7th when the function is clear. Extensions stay off-limits. " +
  "Added vocabulary: maj7, m7, V7, ii–V–I, V/IV, minor iv preview, bVII preview.";

export const WORKOUT_MONTH1_GOAL = "Faster recognition of basic function and chord quality in real songs; first controlled exposure to outside dominant color.";
export const WORKOUT_MONTH2_GOAL = "Faster recognition of 7th-chord quality, ii–V motion, secondary dominants, and first borrowed-color previews.";

function maj(...d: number[]): Progression { return { mode: TrainingMode.Major, degrees: d }; }

/** Track A — 32 sessions (8 weeks × 4). */
export const WORKOUT_SESSIONS: WorkoutSession[] = [
  // ---- Month 1 ----
  { number: 1, week: 1, title: "Core loop quality",
    song: { title: "Stand by Me", artist: "Ben E. King", version: "original 1961 single" },
    focus: "Differentiate home, soft relative color, open pre-dominant color and dominant pull. Call quality in time, not after long analysis.",
    melody: "First vocal phrase. Note-by-note searching is acceptable; aim for a recognizable contour.",
    harmonization: "Only four basic functions; melody note must be root, 3rd or 5th.",
    passGoal: "You can call the loop functions and qualities in time after several repetitions.",
    spoiler: "Loop: I – vi – IV – V (quality: major – minor – major – dominant/major). In A: A – F#m – D – E.",
    loop: maj(1, 6, 4, 5) },
  { number: 2, week: 1, title: "Deceptive vs resolved motion",
    song: { title: "Let It Be", artist: "The Beatles", version: "original" },
    focus: "Hear when a dominant-type motion resolves and when it avoids full resolution. Compare similar surface loops.",
    melody: "Chorus melody in 2–3 note chunks where possible.",
    harmonization: "Try one replacement between two pre-dominant choices, only if the melody note structurally fits.",
    passGoal: "You can say which moments feel resolved and which remain softer/unfinished.",
    spoiler: "Sections include I – V – vi – IV and I – V – IV – I. Notice V→vi vs V→I.",
    loop: maj(1, 5, 6, 4) },
  { number: 3, week: 1, title: "Simple function, clean quality",
    song: { title: "I Can See Clearly Now", artist: "Johnny Nash", version: "original" },
    focus: "Major/minor quality and simple functional direction without rhythmic/harmonic clutter.",
    melody: "Chorus or main hook; long notes first.",
    harmonization: "Harmonize a short phrase with only basic triads that contain the melody note.",
    passGoal: "You can accompany the main section and identify quality quickly.",
    spoiler: "Mostly simple major-key function around I, IV, V with occasional relative-minor color depending on section/version." },
  { number: 4, week: 1, title: "Secondary-dominant preview",
    song: { title: "Something", artist: "The Beatles", version: "original — opening/verse only" },
    focus: "Listen for a stable sound becoming more tense/forward-moving. Do not analyze the whole song.",
    melody: "Opening vocal phrase only.",
    harmonization: "Plain triadic version first, then one version with a chord that points into the next chord if melody allows.",
    passGoal: "You hear a chord gaining directional color rather than just becoming “weird”.",
    spoiler: "Opening/verse teaches I → I7-ish color → IV direction, plus chromatic line color. Use only the opening at first." },
  { number: 5, week: 2, title: "Pre-dominant alternatives",
    song: { title: "Take Me Home, Country Roads", artist: "John Denver", version: "original" },
    focus: "Compare two chords that can both prepare a dominant but have different quality and color.",
    melody: "Opening vocal phrase.",
    harmonization: "Two 2–4 bar versions using different pre-dominant choices; melody must fit structurally.",
    passGoal: "You can choose between similar functions using both quality and melody.",
    spoiler: "Mostly the I, IV, V, vi family. Listen for pre-dominant alternatives and cadences." },
  { number: 6, week: 2, title: "Same quality, different function",
    song: { title: "Brown Eyed Girl", artist: "Van Morrison", version: "original" },
    focus: "Both home and away chords can be major — focus on function, not just quality.",
    melody: "Main vocal hook.",
    harmonization: "One alternate version using the relative-minor color, only where the melody allows.",
    passGoal: "You can distinguish function even when chord quality is identical.",
    spoiler: "Common verse feel: I – IV – I – V. Both I and IV are major, but the functions differ.",
    loop: maj(1, 4, 1, 5) },
  { number: 7, week: 2, title: "Melody constrains harmony",
    song: { title: "Wonderful Tonight", artist: "Eric Clapton", version: "original" },
    focus: "Use the melody to limit possible chord choices. List possible triads for long melody notes.",
    melody: "Find the long vocal notes before decorations.",
    harmonization: "For each important note, list the possible triads, then choose the most functional one.",
    passGoal: "You understand that harmonization is constrained choice, not guessing.",
    spoiler: "Simple major-key I, V, IV area — good for melody constraining chord choice." },
  { number: 8, week: 2, title: "Slow secondary-dominant lab",
    song: { title: "You Are My Sunshine", artist: "traditional", version: "version-dependent" },
    songNote: "Revision note: this lab needs a version with I7 → IV (i.e. V7/IV → IV). If your version lacks it, use the song as simple-function practice instead.",
    focus: "Hear when a normally stable chord is altered to point forward.",
    melody: "Chorus melody.",
    harmonization: "A plain version, and a forward-pointing version only if the melody note fits the altered dominant chord structurally.",
    passGoal: "You hear the difference between plain home and home turned into a dominant color.",
    spoiler: "Many simple versions are only I – IV – V. The lab requires I7 → IV, i.e. V7/IV → IV." },
  { number: 9, week: 3, title: "Familiar loop, quicker reaction",
    song: { title: "I'm Yours", artist: "Jason Mraz", version: "original" },
    focus: "Recognize a familiar functional loop in a different song. Train speed more than perfection.",
    melody: "Chorus melody; try to hear 2-note chunks.",
    harmonization: "Replace one chord only if the melody note fits the new triad.",
    passGoal: "You call functions faster than in Week 1.",
    spoiler: "Common loop: I – V – vi – IV. Notice the V→vi avoided resolution.",
    loop: maj(1, 5, 6, 4) },
  { number: 10, week: 3, title: "Avoided resolution",
    song: { title: "Have You Ever Seen the Rain", artist: "Creedence Clearwater Revival", version: "original" },
    focus: "Compare true resolution with relative-minor landing.",
    melody: "Chorus melody; mark the final notes of phrases.",
    harmonization: "Two endings for the same melody: one resolved, one softer/avoided.",
    passGoal: "You hear the emotional difference between full and avoided resolution.",
    spoiler: "The I, V, vi, IV family; compare V→I with V→vi." },
  { number: 11, week: 3, title: "Optional excerpt lab: outside dominants",
    song: { title: "All of Me", artist: "Billie Holiday", version: "1941 version with Lester Young — first 8 bars ONLY" },
    focus: "Do not learn the full song. Use the excerpt to hear dominant arrows and compare m7 vs dominant-7 quality.",
    melody: "Opening phrase only.",
    harmonization: "Optional: a simple version, then restore one outside dominant you can actually hear.",
    passGoal: "You identify at least one outside dominant by its TARGET, not by a memorized chord name.",
    spoiler: "First 8 bars, simplified: I | V/vi | V/ii | ii | V/vi | vi | V/V | ii V | I — in C: C | E7 | A7 | Dm | E7 | Am | D7 | Dm G7 | C." },
  { number: 12, week: 3, title: "Common loop across style",
    song: { title: "No Woman, No Cry", artist: "Bob Marley & The Wailers", version: "live (1975) or studio" },
    focus: "Same family of functions as earlier songs, different feel. Recognize the grammar across style.",
    melody: "Main vocal phrase.",
    harmonization: "Replace a broad pre-dominant with a more directed one where melody permits.",
    passGoal: "The loop feels familiar rather than newly confusing.",
    spoiler: "Loop family: I – V – vi – IV / I – IV – I – V depending on section/version.",
    loop: maj(1, 5, 6, 4) },
  { number: 13, week: 4, title: "ii vs IV diagnosis",
    song: { title: "Knockin' on Heaven's Door", artist: "Bob Dylan", version: "original 1973" },
    focus: "Train the distinction between a minor pre-dominant and a major pre-dominant.",
    melody: "Vocal phrase.",
    harmonization: "Create both versions of a short phrase; keep the one that supports the melody best.",
    passGoal: "You can explain why both function similarly but sound different.",
    spoiler: "Alternating cycles: I – V – ii and I – V – IV (G – D – Am and G – D – C). Train ii vs IV." },
  { number: 14, week: 4, title: "Dominant of dominant, slow context",
    song: { title: "Leaving on a Jet Plane", artist: "John Denver", version: "or the Peter, Paul and Mary version" },
    focus: "Practice hearing an added push into the dominant.",
    melody: "Verse melody.",
    harmonization: "Insert a dominant-of-dominant color only when the melody fits structurally.",
    passGoal: "You hear forward pull rather than only “an extra chord”.",
    spoiler: "Mostly the I – IV – V – I family. Optional color: V/V → V before a cadence in some versions." },
  { number: 15, week: 4, title: "Girl recovery lab",
    song: { title: "Girl", artist: "The Beatles", version: "original — only two short loops" },
    focus: "Return to the song that exposed the bottleneck, in small sections. Compare similar broad shapes with different chord quality.",
    melody: "One phrase you already found; aim for fluency, not new material.",
    harmonization: "None — this is quality/function diagnosis.",
    passGoal: "You can distinguish similar-looking progressions by quality and function.",
    spoiler: "Verse opening: i – V7 – i. Verse later: iv → bVI → bIII → V7. Chorus (relative major): I – iii – ii – V7 — compare with doo-wop I – vi – IV – V." },
  { number: 16, week: 4, title: "Month 1 exam",
    song: null, songNote: "Unfamiliar simple song — pop, country, folk or classic rock.",
    focus: "Identify the main functional loop and chord qualities: major, minor, dominant if present.",
    melody: "Play one recognizable melody phrase.",
    harmonization: "A 2–4 bar triadic harmonization.",
    passGoal: "Pass if you get a usable musician result, not a perfect transcription.",
    spoiler: "" },
  // ---- Month 2 ----
  { number: 17, week: 5, title: "ii–V as one object",
    song: { title: "Fly Me to the Moon", artist: "Frank Sinatra with Count Basie", version: "or a slow backing track" },
    focus: "Hear preparation → dominant → resolution as a single unit.",
    melody: "A-section phrase.",
    harmonization: "Triads first; then allow 7ths only when the melody is root/3rd/5th/7th.",
    passGoal: "You hear the direction of the cell before naming every chord.",
    spoiler: "A-section direction: vi – ii – V – I circle motion (jazz charts: vi7 – ii7 – V7 – Imaj7).",
    loop: maj(6, 2, 5, 1) },
  { number: 18, week: 5, title: "Circle motion and quality",
    song: { title: "Autumn Leaves", artist: "Cannonball Adderley / Miles Davis", version: "or a slow vocal version" },
    focus: "Track root motion first, then refine quality.",
    melody: "A-section melody, slowly.",
    harmonization: "Simplify a cadence, then restore the preparatory chord.",
    passGoal: "You can reduce a dense section to large functional movement.",
    spoiler: "Core study: ii – V – I and circle-of-fifths motion, then minor-key resolution." },
  { number: 19, week: 5, title: "Minor-key function",
    song: { title: "Blue Bossa", artist: "Kenny Dorham", version: "or the Dexter Gordon version" },
    focus: "Hear the minor tonic area and dominant pull in minor without chasing extensions.",
    melody: "Main melody.",
    harmonization: "Triads first; 7ths only where clear.",
    passGoal: "You do not confuse color tones with basic function.",
    spoiler: "Core study: minor tonic area, iv, and ii – V motion." },
  { number: 20, week: 5, title: "Repeated cells",
    song: { title: "Satin Doll", artist: "Duke Ellington" },
    focus: "Hear repeated preparation–dominant cells and their targets.",
    melody: "One phrase only.",
    harmonization: "Expand a simple dominant-resolution into a fuller preparation–dominant–resolution.",
    passGoal: "You hear the repeated cell as a unit.",
    spoiler: "Repeated ii – V cells; hear them as units pointing toward targets." },
  { number: 21, week: 6, title: "Moving key centers",
    song: { title: "Tune Up", artist: "Miles Davis", version: "slow it down if needed" },
    focus: "Each cell has the same grammar even as targets move.",
    melody: "Opening melody, slowly.",
    harmonization: "Simplify each target arrival, then restore the preparatory chord.",
    passGoal: "You follow the structure without panic.",
    spoiler: "ii – V – I cells moving through different key centers." },
  { number: 22, week: 6, title: "Dominant chains, slower",
    song: { title: "Dream a Little Dream of Me", artist: "Ella Fitzgerald & Louis Armstrong" },
    focus: "Hear dominant arrows in a slower, vocal standard context.",
    melody: "First phrase, long notes first.",
    harmonization: "Insert one outside dominant into a plain phrase only if melody fits root/3rd/5th/7th.",
    passGoal: "You hear “this chord points somewhere” rather than isolated chromaticism.",
    spoiler: "Dominant chains and standard harmony; the Ella & Louis version is the clearest." },
  { number: 23, week: 6, title: "All of Me review lab",
    song: { title: "All of Me", artist: "Billie Holiday", version: "1941 — first 8 bars only" },
    focus: "Revisit the excerpt after slower preparation. Focus on quality: m7 vs dominant-7.",
    melody: "Opening phrase, more fluently.",
    harmonization: "Restore only the outside dominants you can hear by target.",
    passGoal: "You correct at least one earlier quality/function mistake.",
    spoiler: "I | V/vi | V/ii | ii | V/vi | vi | V/V | ii V | I — in C: C | E7 | A7 | Dm | E7 | Am | D7 | Dm G7 | C." },
  { number: 24, week: 6, title: "Rich harmony reduction",
    song: { title: "Georgia on My Mind", artist: "Ray Charles", version: "opening only" },
    focus: "Reduce rich harmony to simple function and dominant arrows. Ignore extensions at first.",
    melody: "Opening phrase, long notes first.",
    harmonization: "Simple version first, then one secondary dominant.",
    passGoal: "You can simplify without losing the song.",
    spoiler: "Reduces to I, secondary dominants, ii, V and approach colors. Use the Ray Charles opening only at first." },
  { number: 25, week: 7, title: "Borrowed minor color",
    song: { title: "In My Life", artist: "The Beatles", version: "original" },
    focus: "Hear major-to-minor color change as COLOR, not a wrong chord.",
    melody: "Main phrase.",
    harmonization: "Add borrowed minor color only if the melody structurally fits.",
    passGoal: "You identify the color shift without overanalyzing.",
    spoiler: "Borrowed/minor-color moments — listen for IV → iv → I-type color where present." },
  { number: 26, week: 7, title: "Modal rock color",
    song: { title: "Hey Jude", artist: "The Beatles", version: "original" },
    focus: "Distinguish modal rock color from classical dominant function.",
    melody: "Chorus or outro melody.",
    harmonization: "Compare a plain dominant ending with a modal-color ending.",
    passGoal: "You do not mistake bVII for V.",
    spoiler: "Modal rock color in the outro; compare bVII – IV – I with IV – V – I." },
  { number: 27, week: 7, title: "Same melody, richer harmony",
    song: { title: "Sleep Walk", artist: "Santo & Johnny", version: "original" },
    focus: "Use a slow instrumental melody to connect chord quality and melody support.",
    melody: "Main melody; aim for phrase chunks.",
    harmonization: "Three versions: triads, 7ths, one outside dominant.",
    passGoal: "You feel how harmony color changes while the melody remains stable.",
    spoiler: "Slow melody over I – vi – IV – V-type and/or richer dominant/borrowed colors depending on arrangement.",
    loop: maj(1, 6, 4, 5) },
  { number: 28, week: 7, title: "Girl structured return",
    song: { title: "Girl", artist: "The Beatles", version: "original — verse fragment and chorus only" },
    focus: "Revisit the exact failure points: the ambiguous minor/relative-major area and I–iii–ii–V vs the doo-wop shape.",
    melody: "One already-known phrase.",
    harmonization: "None — quality/function comparison only.",
    passGoal: "You hear why your earlier guess was close in shape but wrong in quality.",
    spoiler: "Verse: i – V7 – i; later iv → bVI → bIII → V7. Chorus: relative-major I – iii – ii – V7 vs doo-wop I – vi – IV – V." },
  { number: 29, week: 8, title: "Unfamiliar simple song",
    song: null, songNote: "Student choice — simple pop/country/folk/classic rock.",
    focus: "Apply Month 1 skills without a prepared answer.",
    melody: "One phrase.",
    harmonization: "Triadic reharmonization.",
    passGoal: "You identify the main loop and qualities within 45 minutes.",
    spoiler: "" },
  { number: 30, week: 8, title: "Unfamiliar standard-like song",
    song: null, songNote: "Student choice — slow old standard, vocal version preferred.",
    focus: "Apply Month 2 skills: ii–V, dominant 7, secondary dominant.",
    melody: "One phrase.",
    harmonization: "Simplify harmony first; add one 7th or outside dominant if clear.",
    passGoal: "You produce a usable simplified lead-sheet version.",
    spoiler: "" },
  { number: 31, week: 8, title: "Unfamiliar Beatles/classic-rock song",
    song: null, songNote: "Student choice — a Beatles or classic-rock song you don't know well.",
    focus: "Look for borrowed color, iii vs vi, ii vs IV, bVII vs V.",
    melody: "One difficult phrase only.",
    harmonization: "Harmonize 2 bars under constraints.",
    passGoal: "You diagnose one 4-bar section instead of getting exhausted by the whole song.",
    spoiler: "" },
  { number: 32, week: 8, title: "Month 2 exam",
    song: null, songNote: "Unfamiliar song — prefer one with at least one non-basic chord.",
    focus: "Identify the main loop, qualities, and at least one special function if present.",
    melody: "Play a recognizable phrase.",
    harmonization: "2–4 bars; triad tone preferred, 7th allowed if clear.",
    passGoal: "Pass if you can accompany, explain, and reshape a small section.",
    spoiler: "" },
];

/** Track B — the stricter one-song-per-week deep plan. */
export const WORKOUT_DEEP_WEEKS: DeepWeek[] = [
  { week: 1, songTitle: "Stand by Me", artist: "Ben E. King", recording: "Original 1961 single recording",
    section: "One complete verse cycle.",
    target: "Map the repeating harmonic events, then classify their relative stability and direction — without assuming the bass riff equals the chord roots.",
    melodyTarget: "A four-note vocal fragment from the verse.",
    notGraded: ["The famous bass figure", "Exact instrumental voicings", "Optional seventh colour added by an individual instrument"],
    labDrills: ["Major triad vs major seventh", "Root position vs first inversion of a major triad", "A separate secondary-dominant-to-minor-resolution drill (not from the song)"],
    passing: "Identify the recurring harmonic events in order and reproduce four adjacent melody notes. One uncertain Roman numeral is fine if stability and direction are correct.",
    spoiler: "Key A major: I – vi – IV – V (A – F#m – D – E), repeating.",
    loop: maj(1, 6, 4, 5) },
  { week: 2, songTitle: "La Bamba", artist: "Ritchie Valens", recording: "Original 1958 recording",
    section: "Two consecutive cycles beneath a sung phrase; exclude the standalone opening guitar riff.",
    target: "Recognize the repeating functional cycle quickly and distinguish chord change from rhythmic emphasis.",
    melodyTarget: "One short sung phrase of 4–6 notes.",
    notGraded: ["The opening guitar riff", "The exact strumming pattern", "Momentary suspensions/colour tones from melody and voicing"],
    labDrills: ["Major triad vs dominant seventh", "Root position vs first inversion", "A separate applied-dominant resolution drill"],
    passing: "Identify the full cycle in three consecutive repetitions and anticipate the point of strongest forward pull before checking the guitar.",
    spoiler: "Concert-pitch representation: I – IV – V (C – F – G), repeating. Voicing details may briefly suggest dominant-7 colour; the cycle stays three primary majors." },
  { week: 3, songTitle: "Knockin' on Heaven's Door", artist: "Bob Dylan", recording: "Original 1973 soundtrack recording",
    section: "One full alternating pair of cycles.",
    target: "Closely related cycles share an opening but diverge near the end — similar beginnings do not guarantee identical endings.",
    melodyTarget: "5–6 notes from a verse phrase.",
    notGraded: ["Guitar embellishments", "Added sevenths in simplified/performance charts", "Exact bass position during the dominant harmony"],
    labDrills: ["Minor triad vs minor seventh", "Root-position vs first-inversion dominant", "Dominant-to-minor resolution vs deceptive motion"],
    passing: "Distinguish the two cycle endings without guitar and identify the broad quality of every harmony, even if one inversion remains unresolved.",
    spoiler: "Key G major, alternating: I – V – ii and I – V – IV (G – D – Am and G – D – C)." },
  { week: 4, songTitle: "Believer", artist: "Imagine Dragons", recording: "Original 2017 studio recording",
    section: "First verse only.",
    target: "Establish the minor tonal centre and track a compact repeating framework under a rhythmically dense arrangement.",
    melodyTarget: "One rhythmically clear 4–6 note vocal cell.",
    notGraded: ["Percussion layers and production effects", "Later-section bass inversions", "Exact instrumental doubling of chord tones"],
    labDrills: ["Minor vs major tonic", "Minor vs major dominant in a minor context", "Root position vs first inversion", "Applied-dominant drills pointing to two different minor-key destinations"],
    passing: "Maintain the correct tonal centre, identify each recurring harmony by broad quality, and reproduce the melodic cell with correct contour.",
    spoiler: "Key Bb minor (normalized): i – VI – V (Bbm – Gb – F)." },
  { week: 5, songTitle: "No Woman, No Cry", artist: "Bob Marley and the Wailers", recording: "1975 Live! version (Lyceum Theatre, London)",
    section: "Intro/refrain in sessions A–B; add one verse in session D.",
    target: "Separate chord identity from bass position: decide the underlying harmony FIRST, only then whether the bass is the root or another chord tone.",
    melodyTarget: "Six notes from one refrain phrase.",
    notGraded: ["Organ fills", "Reggae accompaniment rhythm", "Melodic decorations", "Exact seventh/suspension colour unless it changes the chord identity"],
    labDrills: ["Root position vs first inversion of major chords", "Bass root vs bass third", "Dominant triad vs dominant seventh", "A separate secondary-dominant-to-supertonic drill"],
    passing: "Identify the underlying chord before naming its bass position; don't invent a new root just because the bass moves by step.",
    spoiler: "Key C major, refrain framework: I – V6 – vi – IV | I – IV – I – V (C – G/B – Am – F | C – F – C – G). The assigned inversion is the dominant with its 3rd in the bass.",
    loop: maj(1, 5, 6, 4) },
  { week: 6, songTitle: "Use Me", artist: "Bill Withers", recording: "Original 1972 studio recording (Still Bill)",
    section: "Intro and first verse.",
    target: "A seventh-chord-quality laboratory. Roman-numeral function is NOT graded this week.",
    melodyTarget: "4–6 notes from a vocal phrase.",
    notGraded: ["The clavinet riff", "The bass riff", "Percussion complexity", "Any extension beyond basic seventh-chord identity"],
    labDrills: ["Minor seventh vs dominant seventh", "Major seventh vs minor seventh", "All three seventh qualities in shell voicings", "Separate secondary-dominant exercises outside the song"],
    passing: "At least 8/10 on m7-vs-dom7 shells, 7/10 on maj7-vs-m7 shells, and identify the recorded qualities in three consecutive alternations.",
    spoiler: "Em7 – A7, repeated. Don't force a Roman-numeral reading — the exercise is m7 vs dominant-7 quality." },
  { week: 7, songTitle: "Hallelujah", artist: "Jeff Buckley", recording: "1994 Grace album recording",
    section: "The first-verse passage with the first clear non-diatonic dominant resolution (~8 bars). Do not transcribe the full verse.",
    target: "Detect a harmony outside the basic diatonic set and identify the temporary destination it pulls toward.",
    melodyTarget: "6–8 notes from the assigned passage.",
    notGraded: ["Buckley's fingerpicked voicings", "Exact picking pattern", "Extensions and open-string colour tones", "The complete song form"],
    labDrills: ["Diatonic motion to a minor chord vs applied-dominant motion to it", "Dominant triad vs dominant seventh", "Applied dominant with and without its seventh", "Mixed inversion review"],
    passing: "Hear that the target harmony is outside the diatonic set, predict its destination, and name its applied-dominant function after guitar verification.",
    spoiler: "In the usual C-major transcription the passage contains V7/vi → vi (E7 → Am), audible as an applied dominant targeting the relative minor." },
  { week: 8, songTitle: "Consolidation — no new song", artist: "", recording: "All seven named recordings from weeks 1–7",
    section: "Four sessions over the studied recordings only.",
    target: "Consolidate: no new artist or harmonic language.",
    melodyTarget: "6–8 notes per extraction task.",
    notGraded: [],
    labDrills: [
      "A: random 15-second excerpts from Stand by Me, La Bamba, Knockin' — broad quality + function",
      "B: melody extraction from one unused phrase in Believer and one in No Woman, No Cry (6–8 notes each)",
      "C: ten seventh qualities, ten root/first-inversion pairs, ten ordinary-vs-applied dominant resolutions",
      "D: one unseen section drawn only from the recordings already studied"],
    passing: "See the Month 2 exam targets below.",
    spoiler: "" },
];

/** Appendix B — train-ride synthetic drills (category → progressions). */
export const WORKOUT_TRAIN_DRILLS: [string, string][] = [
  ["Diatonic 7ths", "Imaj7–vi7–IVmaj7–V7  |  Imaj7–vi7–ii7–V7  |  vi7–ii7–V7–Imaj7"],
  ["Secondary dominants", "Imaj7–V/V–V7–Imaj7  |  Imaj7–V/vi–vi7  |  Imaj7–V/ii–ii7–V7"],
  ["Dominant chains", "Imaj7–III7–vi7–II7–V7–Imaj7  |  Imaj7–VI7–ii7–V7"],
  ["Borrowed preview", "Imaj7–IVmaj7–iv–Imaj7  |  Imaj7–bVII–IVmaj7–Imaj7"],
  ["Quick questions", "Diatonic or outside? maj7, m7 or dom7? Where does the dominant want to resolve? Is this ii–V–I? Is this borrowed color?"],
];

/** Month 1 / Month 2 examination targets (skill → target). */
export const WORKOUT_MONTH1_EXAM: [string, string][] = [
  ["Synthetic major/minor triad quality", "8/10"],
  ["Broad functional category in ordinary loops", "7/10"],
  ["Minor dominant vs major dominant in minor", "7/10"],
  ["Melody reproduction", "Five notes within five guitar attempts"],
  ["Simple harmonization", "One plausible triad per structural melody note"],
  ["Secondary-dominant awareness", "Hear an unexpected dominant pull; exact label not yet required"],
];
export const WORKOUT_MONTH2_EXAM: [string, string][] = [
  ["Correct roots/functions in an eight-event excerpt", "At least 6/8"],
  ["Major/minor/dominant broad quality", "At least 8/10"],
  ["Maj7, m7 and dom7 in shell voicings", "At least 7/10"],
  ["Root position vs first inversion", "At least 7/10"],
  ["Identify the target of an applied dominant", "At least 7/10"],
  ["Six-to-eight-note melody", "Correct contour, ≥75% exact pitches"],
  ["Triadic harmonization", "Plausible choice for ≥75% of structural notes"],
];

/** What Claude changed vs the two source PDFs (shown in-app). */
export const WORKOUT_REVISION_NOTES: string[] = [
  "Merged the two ChatGPT documents into one plan: Track A = the 32-session plan (one song per session); Track B = the stricter one-song-per-week deep plan. Same 8 weeks — pick one track, or run A under B's evaluation rules.",
  "Adopted the deep track's evaluation rules globally: guitarless first pass, one hypothesis at a time, function before spelling, bounded arrangement details.",
  "Session 8 (You Are My Sunshine): flagged that the V7/IV lab only works with a version containing I7 → IV — otherwise it's plain I–IV–V practice.",
  "Spot-checked the spoiler keys; they are normalized approximations — version differences are expected, so check broad hearing, don't memorize one chart.",
  "Sessions with a clean 4-bar diatonic loop got a ▶ play button (fixed key C / Am) so the app can sound the answer after you reveal it.",
];
