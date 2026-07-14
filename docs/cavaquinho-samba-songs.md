# Cavaquinho — Samba Functional Chord-Change Library (curation)

Songs pulled from **CifraClub → Mais acessadas → Samba**
(`https://www.cifraclub.com.br/mais-acessadas/samba/`), each reduced to its
**functional progression** (Roman numerals relative to the song's key), so it can be
transposed to any key on the cavaquinho and matched to a functional sequence.

> **Status:** initial curation (top 10 most-accessed). This is a *reviewable* document —
> the functional reductions simplify extended/altered/slash voicings to their harmonic
> role (e.g. `A6(9)` → `I`, `G7` → `♭VII7`, `Bm7(♭5)` → `iiø`). Passing diminished chords
> and inversions are noted but not always given their own degree. Correct anything that
> reads wrong; once settled it becomes the data source for the **Songs** button on the
> cavaquinho **Progressions** screen.
>
> **Roman notation:** uppercase = major, lowercase = minor, `7` = dominant 7th,
> `maj7`/`M7` = major 7th, `º` = diminished, `ø` = half-diminished (m7♭5),
> `V7/x` = secondary dominant of `x`, `♭` = flatted (borrowed) degree.

## The library

| # | Song | Artist | Key | Core functional progression | Family / notes |
|---|------|--------|-----|-----------------------------|----------------|
| 1 | O Mundo É Um Moinho | Cartola | A major | `ii7 – V7 – iii7 – I – IV – ii7 – V7` | ii–V turnaround with a chromatic descending intro; jazz-samba |
| 2 | Eu e Você Sempre | Jorge Aragão | E major | `I – v7 – IV – iv – I` | major→minor subdominant (`IV – iv`) cliché |
| 3 | Meu Lugar | Arlindo Cruz | A minor | `i7 – IV7 – iv7 – ♭VII7 – ♭III – iiø – V7 (→ i)` | descending circle-of-fifths cadence (relative C major) |
| 4 | Disritmia | Martinho da Vila | G minor | `i7 – IV7` vamp, then `iv – ♭VII7 – ♭III – ♭VI – iiø – V7 – i` | minor circle-of-fifths cadence |
| 5 | Nos Braços da Batucada | Arlindo Cruz | C major | `I – vi7 – ii7 – V7` | **rhythm-changes turnaround** `I–vi–ii–V` (matches library key `[1,6,2,5]`) |
| 6 | Deixa a Vida Me Levar | Zeca Pagodinho | E major | `I – V7 – ii7 – V7 – I` · chorus `IV – I – ii7 – V7 – I` | plain diatonic; roda-de-samba staple |
| 7 | Trem das Onze | Adoniran Barbosa | A minor | `i – V7 – i` · cadence `iv – iiø – i` | classic minor samba/samba-de-breque |
| 8 | Fulminante | Mumuzinho | E major | `I – IV` vamp, chorus `IV – V7 – iii7 – vi7 – V7 – IV` | I–IV pagode vamp + vi turn |
| 9 | Lucidez | Jorge Aragão | E major | `I – V7/V – ii7 – V7 – I` · chorus circle `iii – VI7 – ii – V7` | secondary-dominant circle |
| 10 | Problema Emocional | Reinaldo | D major | `I – iiiø – V7/ii – ii – V7/… circle … – V7 – I` | dense secondary-dominant circle of fifths |

## Grouping by functional family (for the future Songs button)

Once the reductions are confirmed, songs cluster into a handful of functional families the
cavaquinho **Progressions** sequences already teach:

- **ii–V–I / turnarounds** — O Mundo É Um Moinho (1), Nos Braços da Batucada (5),
  Deixa a Vida Me Levar (6), Lucidez (9).
- **I – vi – ii – V (rhythm changes)** — Nos Braços da Batucada (5) → maps to the existing
  diatonic library key `[1,6,2,5]`.
- **Circle-of-fifths cadence** (major or minor) — Meu Lugar (3), Disritmia (4),
  Problema Emocional (10). Related to the app's *Circle* generator.
- **Major↔minor subdominant (`IV – iv`)** — Eu e Você Sempre (2).
- **Minor tonic cadence (`iv – iiø – i`)** — Trem das Onze (7).
- **I–IV pagode vamp** — Fulminante (8).

## Sources

Chord sheets (relative paths under `https://www.cifraclub.com.br`):

1. `/cartola/o-mundo-um-moinho/`
2. `/jorge-aragao/eu-voce-sempre/`
3. `/arlindo-cruz/o-meu-lugar/`
4. `/martinho-da-vila/disritimia/`
5. `/arlindo-cruz/nos-bracos-da-batucada/`
6. `/zeca-pagodinho/deixa-vida-me-levar/`
7. `/adoniran-barbosa/trem-das-onze/`
8. `/mumuzinho/fulminante/`
9. `/jorge-aragao/lucidez/`
10. `/reinaldo/problema-emocional/`

## Next steps

1. **Review** the functional reductions above (fix any wrong Roman analysis).
2. **Expand** beyond the top 10 (the ranking list has more; easy to add rows).
3. **Wire the Songs button** on the cavaquinho Progressions screen: for the currently
   selected functional sequence, list the curated songs whose family/progression matches
   (best-effort — the ranking mixes many progressions, so some songs won't match any of
   the taught sequences and simply won't appear until their family is added).
