# The Variety of Samba Chord Progressions (by function)

A survey of the **functional** (Roman-numeral, key-independent) chord progressions used in
samba, compiled from the **top 40 most-accessed samba songs** on CifraClub
(`mais-acessadas/samba/`). Each song was reduced from its chord sheet to its harmonic
skeleton; this document aggregates those into the recurring **functional families and cells**,
so you can see *what* samba actually does harmonically rather than any one key.

> Reviewable analysis (reductions simplify extended/slash/passing voicings to their role).
> Notation: uppercase = major, lowercase = minor, `7` = dominant, `maj7`, `°` = dim,
> `ø` = half-dim, `V7/x` = secondary dominant, `b` = flat/borrowed. Minor degrees are named
> relative to the major scale (`bIII bVI bVII`). n/40 = songs in the sample using that move.

## The big picture

Samba harmony is overwhelmingly **tonal-functional with a jazz/choro accent**: it lives on the
**ii–V–I** axis, decorates it with **secondary dominants and descending circles of fifths**,
and colours it with **borrowed minor subdominants (IV→iv)**, **chromatic passing diminished
chords**, and **line clichés**. Roughly:

| Functional family | Roman-numeral shape | ~n/40 |
|---|---|---|
| **ii–V–I & turnarounds** | `ii7–V7–I`, `I–vi–ii–V`, `I–V7/V–ii–V7` | ~14 |
| **Circle of fifths / secondary-dominant chains** | `iii–VI7–ii–V7–I`; minor `i–IV7–bVII7–bIII–bVI–iiø–V7–i` | ~9 |
| **Borrowed subdominant (IV→iv)** | `…IV–iv–I…` (also `I–iv–I`) | ~6 |
| **Minor tonic cadence** | `iv–iiø–V7–i`, `i–V7–i` (± line cliché) | ~8 |
| **I–IV vamp / diatonic I–IV–vi–V** | `I–IV`, `I–IV–vi–V` | ~2 |
| **Minor descending** | `i–bVII–bVI` | 1 |

(Families overlap — a song often threads two, e.g. a ii–V turnaround with a IV→iv colour.)

## The recurring functional cells

Samba is built by chaining a small vocabulary of **2–4-chord cells**:

1. **ii–V (–I)** — the engine. `ii7–V7–I`. Everywhere.
2. **I–VI7–ii–V7** — the *quadradinho* turnaround (VI7 = secondary dominant V7/ii). The single
   most idiomatic samba loop.
3. **I–vi–ii–V** — rhythm-changes turnaround (interchangeable with #2).
4. **Descending circle of fifths** — chains of dominants a 5th apart: `…VI7–ii–V7–I`,
   or fully domified `III7–VI7–II7–V7–I`. In minor: `i–IV7–bVII7–bIII–bVI–iiø–V7–i`
   (this IS the "médio minor" sequence in the app).
5. **IV–iv** — major subdominant sliding to its minor (the wistful samba turn); often `I–IV–iv–I`.
6. **Backdoor / bVII** — `bVII7–I` and `bVII7–bIII`, from the minor-borrowing side.
7. **Minor cadence** — `iv–iiø–V7–i`; the V is a borrowed dominant (harmonic-minor leading tone).
8. **Chromatic passing diminished** — `#i°`, `#ii°`, `#iv°`, `#v°` inserted between diatonic
   chords (e.g. `I–#i°–ii7`, `V–#v°–vi`). A texture layered over the cells above; very common.
9. **Line clichés** — a static root with a descending/ascending inner voice:
   `i – i(maj7) – i7 – i6` (minor) or `I – I+ – I6 – I+` (major, e.g. *Carinhoso*).

## By family, with examples (from the sample)

**ii–V–I & turnarounds** — O Mundo É Um Moinho (`ii–V–iii–I–IV–ii–V`), Nos Braços da Batucada
(`I–vi–ii–V`), Deixa a Vida Me Levar (`I–V7–ii–V7–I`), Lucidez (`I–V7/V–ii–V7–I`),
Se a Fila Andar (`I–V7/V–ii–V7`), Tiro ao Álvaro (`I–vi–ii–V`), O Bem (`vi–ii–V–I`),
Mulheres (`ii–V–I` then `vi–iiø–V7–i`), Carinhoso (`ii–V–I` in the bridge), Já é, Água de Chuva.

**Circle of fifths / secondary dominants** — Meu Lugar, Disritmia, Problema Emocional
(dense `V7/x` chain), Enredo Do Meu Samba, Você Me Vira a Cabeça (minor circle), Andança
(`I–bVI–bII–iiø–V7`), Coisa de Pele, O Que É o Amor, Papel de Pão (`i–I7–iv–bVII7–bIII`).

**IV→iv borrowed subdominant** — Eu e Você Sempre (`I–v–IV–iv–I`), Antigas Paixões, Mais Feliz
(`I7–IV–iv–I`), Você Abusou (`IV–iv–V7`), Pé Na Areia (`I7–IV–iv–bVI`), Conselho (`IV–iv–iii`).

**Minor tonic cadence** — Trem das Onze (`iv–iiø–i`), Preciso Me Encontrar, Retalhos de Cetim,
Não Deixe O Samba Morrer (`i–iv–iiø–V7–i`), Iracema, As Rosas Não Falam (`i–iv–V7/V–V7–i`),
A Loba (minor line clichés + `iiø`), Papel de Pão.

**I–IV vamp / diatonic** — Fulminante (`I–IV` vamp), Quando a Gira Girou (`I–IV–iii–vi–V7/V–V–I`).

**Minor descending** — Ezequiel 47 (`i–bVII–bVI`).

## Takeaways for the cavaquinho player

- Master **ii–V–I**, the **quadradinho (I–VI7–ii–V7)**, and the **descending circle of fifths** —
  they cover the large majority of samba.
- Learn the **IV→iv** colour and the **minor `iv–iiø–V7–i`** cadence for the melancholic repertoire.
- Treat **passing diminished** chords and **line clichés** as decorations you can add over any of
  the above, not separate progressions.

*Source: CifraClub weekly samba ranking (top 40), 2026-07. Per-song reductions live in
`docs/cavaquinho-samba-songs.md`; the app's family→sequence matching is in `theory/CavaqSongs`.*
