# Progression Songs — Raw Research (work in progress)

Companion data for `2026-07-03-progression-song-examples-design.md`.
Collected 2026-07-03. **Not yet integrated into code.** Statuses below track what still needs doing.

## Status

| Section | Status | Notes |
|---|---|---|
| Minor (diatonic, 6) | ✅ collected | below |
| Circle of fifths (7 windows + 4 minor-sub variants) | ✅ collected | below |
| Major (diatonic, 9) | ❌ RE-RUN NEEDED | research agent hit a `400 content filtering` API error twice. Re-dispatch tomorrow (a song title/lyric in results likely tripped the filter — instruct agent to return titles/artists only, no lyrics, and to skip any track it can't name cleanly). |
| Advanced batch 1 (1–12) | ⏳ re-run / check | was still running in background at wrap-up; treat as not captured — re-dispatch tomorrow. |
| Advanced batch 2 (13–24) | ⏳ re-run / check | same. |

Design decisions already locked: all four sections get clickable song lists; advanced/circle
labeled "characteristic examples"; circle section becomes 7 clickable windows plus a minor-chord
(vii° → vii) substitution variant for the 4 windows containing vii°; data lives in a pure-Kotlin
`ProgressionSongs` object in the theory module, mirrored into chorect-web; playlist markdown at
`docs/progression_songs.md`; version bump to v1.19.0.

---

## Minor (diatonic)

### i – VI – III – VII  (degrees [1,6,3,7])
- Save Tonight — Eagle-Eye Cherry
- Zombie — The Cranberries
- Numb — Linkin Park
- Otherside — Red Hot Chili Peppers
- Californication — Red Hot Chili Peppers
- Self Esteem — The Offspring
- Love the Way You Lie — Eminem ft. Rihanna
- Wake Me Up — Avicii
- Faded — Alan Walker
- Despacito — Luis Fonsi ft. Daddy Yankee
- Dragostea Din Tei — O-Zone
- 21 Guns — Green Day

### i – iv – V – i  (degrees [1,4,5,1])
- Since I've Been Loving You — Led Zeppelin
- The Thrill Is Gone — B.B. King
- Black Magic Woman — Santana
- I Put a Spell on You — Nina Simone
- Summertime — Ella Fitzgerald
- Bésame Mucho — Consuelo Velázquez
- St. James Infirmary Blues — Louis Armstrong
- Hava Nagila — Traditional
- We Three Kings — Traditional

### i – VI – VII – i  (degrees [1,6,7,1])
- Livin' on a Prayer — Bon Jovi
- You Give Love a Bad Name — Bon Jovi
- It's My Life — Bon Jovi
- Psycho Killer — Talking Heads
- Toxic — Britney Spears
- He's a Pirate — Klaus Badelt
- Lux Aeterna (Requiem for a Dream) — Clint Mansell

### ii° – V – i – i  (degrees [2,5,1,1])
- Autumn Leaves — Nat King Cole
- Fly Me to the Moon — Frank Sinatra
- Blue Bossa — Kenny Dorham
- Black Orpheus (Manhã de Carnaval) — Luiz Bonfá
- My Funny Valentine — Chet Baker
- 'Round Midnight — Thelonious Monk
- Summertime — Miles Davis
- Nature Boy — Nat King Cole
- Stella by Starlight — Miles Davis
- Softly, as in a Morning Sunrise — John Coltrane
- Beautiful Love — Bill Evans

### i – VII – VI – V  (degrees [1,7,6,5])
- Hit the Road Jack — Ray Charles
- Sultans of Swing — Dire Straits
- Stray Cat Strut — Stray Cats
- Runaway — Del Shannon
- Happy Together — The Turtles
- California Dreamin' — The Mamas & the Papas
- Good Vibrations — The Beach Boys
- Walk, Don't Run — The Ventures
- Smooth Criminal — Michael Jackson
- Like a Hurricane — Neil Young

### i – iv – VII – III  (degrees [1,4,7,3])
- I Will Survive — Gloria Gaynor
- Fly Me to the Moon — Frank Sinatra
- You Never Give Me Your Money — The Beatles
- All the Things You Are — Charlie Parker
- Autumn Leaves — Nat King Cole
- Still Got the Blues — Gary Moore
- Nature Boy — Nat King Cole

---

## Circle of fifths  (windows along I – IV – vii° – iii – vi – ii – V)

### W1: I – IV – vii° – iii
- I Will Survive — Gloria Gaynor
- Fly Me to the Moon — Frank Sinatra
- Autumn Leaves — Nat King Cole
- Still Got the Blues — Gary Moore
- Concerto in A minor, Op. 3 No. 8 (L'estro armonico) — Antonio Vivaldi
- Brandenburg Concerto No. 2 — J.S. Bach

### W2: IV – vii° – iii – vi
- I Will Survive — Gloria Gaynor
- Fly Me to the Moon — Frank Sinatra
- Autumn Leaves — Nat King Cole
- Still Got the Blues — Gary Moore
- You Never Give Me Your Money — The Beatles

### W3: vii° – iii – vi – ii
- I Will Survive — Gloria Gaynor
- Fly Me to the Moon — Frank Sinatra
- Autumn Leaves — Nat King Cole
- Still Got the Blues — Gary Moore
- You Never Give Me Your Money — The Beatles

### W4: iii – vi – ii – V
- Greatest Love of All — Whitney Houston
- September — Earth, Wind & Fire
- Never Gonna Give You Up — Rick Astley
- Fly Me to the Moon — Frank Sinatra
- I Will Survive — Gloria Gaynor
- I Got Rhythm — George Gershwin

### W5: vi – ii – V – I
- I Will Survive — Gloria Gaynor
- Fly Me to the Moon — Frank Sinatra
- All the Things You Are — Jerome Kern
- Wild World — Cat Stevens
- Barbie Girl — Aqua
- Blue Moon — The Marcels

### W6: ii – V – I – IV
- Autumn Leaves — Nat King Cole
- All the Things You Are — Jerome Kern
- Still Got the Blues — Gary Moore
- You Never Give Me Your Money — The Beatles
- Take the "A" Train — Duke Ellington

### W7: V – I – IV – vii°
- Fly Me to the Moon — Frank Sinatra
- Autumn Leaves — Nat King Cole
- Still Got the Blues — Gary Moore
- You Never Give Me Your Money — The Beatles
- I Will Survive — Gloria Gaynor
- Europa (Earth's Cry Heaven's Smile) — Santana

### W1-min: I – IV – vii – iii
- I Will Survive — Gloria Gaynor
- Fly Me to the Moon — Frank Sinatra
- Autumn Leaves — Nat King Cole
- Still Got the Blues — Gary Moore

### W2-min: IV – vii – iii – vi
- I Will Survive — Gloria Gaynor
- Autumn Leaves — Nat King Cole
- Still Got the Blues — Gary Moore
- You Never Give Me Your Money — The Beatles

### W3-min: vii – iii – vi – ii
- I Will Survive — Gloria Gaynor
- Fly Me to the Moon — Frank Sinatra
- Autumn Leaves — Nat King Cole
- You Never Give Me Your Money — The Beatles

### W7-min: V – I – IV – vii
- Fly Me to the Moon — Frank Sinatra
- Autumn Leaves — Nat King Cole
- Still Got the Blues — Gary Moore
- I Will Survive — Gloria Gaynor

---

## Notes for tomorrow / cleanup

- Circle-of-fifths results are heavily repeated across windows (same jazz standards). Before
  shipping, curate: assign each famous circle song to the 1–2 windows its loop best matches, and
  add window-specific variety, so each window doesn't show the identical four songs.
- Minor "i – iv – VII – III" list overlaps the circle standards — fine, but de-dup obvious repeats
  where the attribution is weak.
- Some entries need a sanity pass (e.g. "Smooth Criminal" under i–VII–VI–V; "Never Gonna Give You
  Up" under iii–vi–ii–V) — verify before final integration.
