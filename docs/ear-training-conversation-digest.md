# Ear-training conversation digest (source of truth for the Workout tab)

Condensed from Nadav's 203-page ChatGPT export `C:\Users\Nadav\Desktop\Ear Training corriculum.pdf`
(plus the two curriculum PDFs on the Desktop). **Read this instead of re-reading the PDF** — it holds
every load-bearing fact. Written 2026-08-03.

Derived artifacts:
- Data: `theory/src/main/kotlin/app/guitar/theory/EarWorkout.kt` + `chorect-web/src/theory/earWorkout.ts`
- UI: `app/.../EarWorkoutView.kt` + `workoutView()` in `chorect-web/src/app/earTrainingUI.ts`
- Spec: `docs/superpowers/specs/2026-08-03-ear-workout-theory-tab-design.md`

## His diagnosed profile (self-corrected during the conversation)

| Skill | Level |
|---|---|
| Theory | Advanced |
| Functional hearing — diatonic | Strong |
| Functional hearing — chromatic | Developing |
| Melody playback | Developing (partly note-by-note searching) |
| Harmonization | Beginning |
| Song transcription | Developing |
| Rhythm | **Strong** — he plays percussion; explicitly NOT a bottleneck |

He pushed back on an early over-estimate ("you can analyze secondary dominants / diminished /
modal borrowing / inversions" → **"i cant"**). He understands them on paper; the ear hasn't caught up.
Progress therefore comes from **repeated exposure**, not more theory.

## The three real bottlenecks

1. **Speed** — knows it, but not fast enough; wants the ear to say "that's V/V" before analysis starts.
2. **Whole structures** — hears "that's IV"; wants "we're approaching a cadence", "this prolongs tonic",
   "they're delaying the dominant".
3. **Hear → fingers** — biggest gap. Hears, then searches on the guitar.

## Master goals (his framing, keep these as the north star)

1 Functional harmony · 2 Melodic audiation · 3 Harmonization ("the most musician goal") ·
4 Complete song transcription · 5 Real-time playing. A 6th (instant rhythmic reproduction) was
proposed then **dropped** because rhythm is a strength.
North star: *hear an unfamiliar song once, play along, explain why the harmony works, and build a
convincing accompaniment if the original chords disappeared.*

## Hard preferences — these are corrections he made, do not regress them

- **Spiral, not blocks**: harmony + melody + bass + harmonization + prediction EVERY week from week 1;
  only complexity rises. "Month 5 = harmonization" was rejected as too artificial.
- **Cram more per week**; many tasks take 10 minutes, not an hour.
- **Synthetic progressions are train-ride work**, never inside a 45-minute session. Sessions = real songs.
- **Work directly in FUNCTION.** Guitar is interval-based; once the key is centred, function ≈ knowing the
  actual chords. Don't split "find chords" from "convert to Roman numerals".
- **Always start guitarless** — not to name the key, but to form an internal guess first.
- **No** written check-and-log step (it's integral), **no** key-centre drill, **no** meter/groove drill,
  **no** rhythmic dictation, **no** solfège/sight-singing (he thinks in scale degrees).
- **Spoilers in an appendix only**, and the session text must not even reveal major/minor mode.
- **Milestone/mastery rule**: advance when you pass the month exam, not when 4 weeks elapse.
- Monthly **graduation project**: one complete transcription of a song never learned before.
- Metric is **songs mastered**, not hours practised.

## Harmonization constraint ladder (his own insight)

He noticed that with free extensions you can justify almost any chord ("D over Cmaj9 = 9th, over F6 = 6th…").
So: **L1** melody = root/3rd/5th → **L2** + 7th when function is clear → **L3** 9/11/13 only when the
function is *already* clear. Ask in order: what degree is the melody → which triads contain it → which
makes functional sense here → would a 7th strengthen it → only then extensions.

## The 45-minute session frame (final agreed version)

`0–5` guitarless listen · `5–18` function + quality on guitar · `18–25` **speed loop** (call function +
quality in time) · `25–33` melody playback · `33–42` constrained harmonization · `42–45` play-through
(bass → chords → melody → your reharmonization).

## Prediction drill (the highest-value habit)

Listen 20–40 s of an unfamiliar song; after 3–6 changes, pause and predict the next **function**
(tonic / pre-dominant / dominant / deceptive), not the chord name. Levels: 1 chord ahead → next two →
where the phrase resolves. Goal: first guess is among the musically likely options; "why did you predict
it" matters more than exactness. Daily version: on radio/café music ask key → current numeral → what
degree the singer is on.

## Songs classified by the harmonic concept they teach (his preferred organizing principle)

- **L0 pure diatonic**: Stand by Me, Let It Be, Country Roads, Leaving on a Jet Plane, No Woman No Cry,
  Sweet Home Alabama (+ Brown Eyed Girl, Wonderful Tonight, Knockin' on Heaven's Door, I'm Yours,
  Have You Ever Seen the Rain, I Can See Clearly Now)
- **L1 secondary dominants**: Fly Me to the Moon, Autumn Leaves, All of Me, Blue Bossa,
  There Will Never Be Another You, Satin Doll, Tune Up, Misty, Just Friends, On Green Dolphin Street,
  Take the 'A' Train, Ain't Misbehavin', Georgia on My Mind, Dream a Little Dream of Me
- **L2 borrowed iv**: Blackbird, In My Life, Sleep Walk, Creep
- **L3 ♭VII**: Sweet Home Alabama, Hey Jude, Norwegian Wood, Werewolves of London
- **L4 ♭VI**: While My Guitar Gently Weeps, Hotel California, House of the Rising Sun
- **L5 diminished passing chords**: Trem das Onze, Chega de Saudade, Georgia on My Mind,
  The Girl from Ipanema  ← *he already analyses these in Brazilian music*
- **L6 ii–V–I everywhere**: Autumn Leaves, Satin Doll, Blue Bossa, Tune Up, Take the 'A' Train
- **L7 rich functional**: Wave, Chega de Saudade, All the Things You Are, Misty, Stella by Starlight
- **Brazilian pool**: Trem das Onze, Na Minha Casa (Martinho da Vila), Chega de Saudade, Wave,
  Girl from Ipanema, Corcovado, Águas de Março, Desafinado, O Barquinho, Samba de Uma Nota Só,
  Insensatez, Triste, Meditação, Dindi
- **Harmonization melodies**: Twinkle Twinkle, Happy Birthday, Amazing Grace, Scarborough Fair,
  Greensleeves, Silent Night, Aura Lee, Danny Boy, Auld Lang Syne, Home on the Range
- The Beatles are recommended as the single best artist for his stage — "a graded harmony textbook
  disguised as pop music".

### Repertoire corrections (must persist)

- **Three Little Birds: removed** — he dislikes it. Replaced by **I Can See Clearly Now**.
- **All of Me** = the 1931 standard (Marks/Simons), **not** John Legend. Use **Billie Holiday 1941 with
  Lester Young**, **first 8 bars only**, as an excerpt lab — never an early full-song task (chord rhythm
  too fast).
- **You Are My Sunshine** only teaches a secondary dominant if the version contains **I7 → IV**
  (V7/IV → IV); most versions are plain I–IV–V.
- Secondary-dominant ladder: **You Are My Sunshine → Something → Yesterday → Dream a Little Dream
  → All of Me**.
- Standards must always name a performer.

## His two logged attempts (the plan is aimed at these)

**Girl (The Beatles)** — exhausting; couldn't find the ♭VI/♭III motion in the verse; heard the chorus as a
doo-wop progression on E♭. Verdict: melody 7/10, harmony 5/10, persistence 9/10, overall ~7/10; song
difficulty ~9/10 → "Month 3–5 material, not a first-week test".
Why the errors were reasonable: the verse chord is `A♭6`, which is the *same pitch collection* as `Fm7`,
so it behaves as iv-family colour rather than a dramatic ♭VI (verse: `i – V7 – i – i7 / iv – ♭VI6 – ♭III – V7`
in C minor). The chorus genuinely *does* move to the relative major, so hearing E♭ as home was correct —
but it is `I – iii – ii – V7` (E♭ – Gm – Fm – B♭7), not doo-wop `I – vi – IV – V`. Same broad shape
(tonic → softer chord → pre-dominant → dominant), different inner functions. **iii ≠ vi, ii ≠ IV.**

**All of Me, first 8 bars** — he produced `I | III7 | iv7 | ii | III7 | iv | I | ii V7 | I`.
Target: `I | V/vi | V/ii | ii | V/vi | vi | V/V | ii V | I` (in C: `C | E7 | A7 | Dm | E7 | Am | D7 | Dm G7 | C`).
Scores: guitarless 2–3/10, with-guitar recovery 6/10, chord-quality accuracy 4.5/10, big-picture direction
6.5/10, overall ~6–6.5/10. Misses: `iv` where `vi/VI7` belonged, missed `VI7 → ii`, missed `II7 → V`.
**Named bottleneck to drill: iii vs III7 · vi vs VI7 · ii vs II7 · plain chord vs dominant arrow.**

## Time scaling (baseline 3 h/week = 4 sessions × 45 min)

1.5 h ≈ 2× longer · 2 h ≈ 1.5× · **3 h = baseline** · 4 h ≈ 0.75× · 5–6 h ≈ 0.6×.
Does **not** scale linearly — three short sessions beat one long block (sleep consolidation).
(The original 12-month version of this table: 1.5 h → 18–24 months … 8 h+ → 4–6 months.)

## Honest expected progress after ~4 months at 3 h/week

Diatonic functional hearing 85–95% · secondary dominants 60–80% · borrowed chords 70–85% ·
Brazilian/diminished 50–70% · instant melody playback 60–80% · harmonizing simple melodies 70–85% ·
full song transcription 60–80% · real-time accompaniment 50–75%.

## Berklee comparison (summary)

Stronger here: harmony recognition, recording-based transcription, harmonization, playing by ear,
live accompaniment. Stronger at Berklee: melodic + rhythmic dictation, sight singing (movable Do),
notation/reading. Berklee's harmonic ear-training material (diatonic harmony, inversions,
secondary/extended dominants, ii–V patterns, passing diminished chords) overlaps this plan closely.

## Age question

He asked whether a 40-year-old can get from his stage to fluent hearing. Answer given: yes for
**relative** hearing / chord quality / functional hearing / phrase-level melody playback — adult
auditory plasticity is well supported (older-adult music-training studies show gains); perfect-pitch-type
hearing is not the target.

## After these four months (level-2 goals)

Full guitar parts · 30–60 s solos · chord-melody arrangements · harmonize one melody in four styles
(folk/country/bossa/jazz) · improvise from melody not scales · extensions by ear (9, ♭9, #11, 13) ·
modulations instantly · one arrangement per month for guitar+voice · 50-song internal repertoire ·
play with singers without charts. Graduation test: someone sings a melody → play it, harmonize it,
accompany them, then produce a nicer second version.
