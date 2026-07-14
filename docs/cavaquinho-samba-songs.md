# Cavaquinho — Samba Functional Chord-Change Library (curation)

Songs pulled from **CifraClub → Mais acessadas → Samba**
(`https://www.cifraclub.com.br/mais-acessadas/samba/`), each reduced to its
**functional progression** (Roman numerals relative to the song's key), so it can be
transposed to any key on the cavaquinho and matched to a functional sequence.

> **Status:** top-30 most-accessed (2026-07). This is a *reviewable* document — the
> functional reductions simplify extended/altered/slash/dim voicings to their harmonic role.
> Correct anything that reads wrong; it is the source behind the **Songs** button on the
> cavaquinho **Progressions** screen (`theory/CavaqSongs`). The `family` column is the tag the
> button matches against.
>
> **Roman notation:** uppercase = major, lowercase = minor, `7` = dominant, `maj7` = major 7th,
> `°` = diminished, `ø` = half-diminished, `V7/x` = secondary dominant, `b` = flat (borrowed);
> minor degrees are named relative to the major scale (`bIII`, `bVI`, `bVII`).
>
> **Families:** `ii-V-I` (turnarounds / ii–V), `I-vi-ii-V` (rhythm-changes),
> `circle` (circle-of-fifths / secondary-dominant chains), `IV-iv` (major↔minor subdominant),
> `minor-cadence` (minor `iv–iiø–V–i` / `i–V–i`), `I-IV-vamp`, `minor-vamp`.

## The library (top 30)

| # | Song | Artist | Key | Core functional progression | Family |
|---|------|--------|-----|-----------------------------|--------|
| 1 | O Mundo É Um Moinho | Cartola | A | `ii7 – V7 – iii7 – I – IV – ii7 – V7` | ii-V-I |
| 2 | Eu e Você Sempre | Jorge Aragão | E | `I – v7 – IV – iv – I` | IV-iv |
| 3 | Meu Lugar | Arlindo Cruz | Am | `i7 – IV7 – iv7 – bVII7 – bIII – iiø – V7` | circle |
| 4 | Disritmia | Martinho da Vila | Gm | `i7 – IV7` · `iv – bVII7 – bIII – bVI – iiø – V7 – i` | circle |
| 5 | Nos Braços da Batucada | Arlindo Cruz | C | `I – vi7 – ii7 – V7` | I-vi-ii-V |
| 6 | Deixa a Vida Me Levar | Zeca Pagodinho | E | `I – V7 – ii7 – V7 – I` | ii-V-I |
| 7 | Trem das Onze | Adoniran Barbosa | Am | `i – V7 – i` · `iv – iiø – i` | minor-cadence |
| 8 | Fulminante | Mumuzinho | E | `I – IV` vamp · `IV – V7 – iii7 – vi7 – V7 – IV` | I-IV-vamp |
| 9 | Lucidez | Jorge Aragão | E | `I – V7/V – ii7 – V7 – I` | ii-V-I |
| 10 | Problema Emocional | Reinaldo | D | secondary-dominant circle → `V7 – I` | circle |
| 11 | Preciso Me Encontrar | Cartola | Dm | `i – iv6 – iv – #iv° – V7 (i)` | minor-cadence |
| 12 | Água de Chuva No Mar | Beth Carvalho | C | `I – ii7 – V7` · `IV – I – V7/ii – ii – V7` | ii-V-I |
| 13 | Retalhos de Cetim | Benito Di Paula | Am | `i – v – i – bIII7 – bVImaj7 – #iv° – V7 – i` | minor-cadence |
| 14 | Laços do Amor | Fundo de Quintal | C | `vi – V7/vi – I – IV – v/ii` | I-vi-ii-V |
| 15 | O Show Tem Que Continuar | Fundo de Quintal | C | `I – ii7 – V7` (chromatic passing dims) | ii-V-I |
| 16 | Enredo Do Meu Samba | Jorge Aragão | A | circle of fifths / secondary dominants | circle |
| 17 | Antigas Paixões | Fundo de Quintal | D | `I – IV – iv – V7/V – I – V7/ii – V7/V` | IV-iv |
| 18 | Não Deixe O Samba Morrer | Alcione | Bm | `i – iv – iiø – V7 – i` | minor-cadence |
| 19 | Ezequiel 47 | Thiago Brito | Gm | `i – bVII – bVI` (descending vamp) | minor-vamp |
| 20 | Você Me Vira a Cabeça | Alcione | F#m | `i – V7 – i – iv – bVII7 – bIII – bVI – iiø – V7` | circle |
| 21 | Já é | Jorge Aragão | D | `IV – V7 – iii7 – v7 – I7` | ii-V-I |
| 22 | Se a Fila Andar | Toninho Geraes | Bb | `I – V7/V – ii – V7` | ii-V-I |
| 23 | Ah! Como Eu Amei | Benito Di Paula | Bb | `I (line cliché) – IV – I – ii – V` | ii-V-I |
| 24 | O Bem | Arlindo Cruz | D | `vi7 – ii7 – V7 – I – V7/ii` | I-vi-ii-V |
| 25 | Iracema | Adoniran Barbosa | Bm | `i – V7 – i – I7 – iv – V7 – i – bVI7 – V7` | minor-cadence |
| 26 | Mais Feliz | Zeca Pagodinho | G | `I – V7/V – iv – V7 – I` · `I7 – IV – iv – I` | IV-iv |
| 27 | As Rosas Não Falam | Cartola | Dm | `i – iv – V7/V – V7 – i` | minor-cadence |
| 28 | Tiro ao Álvaro | Adoniran Barbosa | Bb | `I – vi – ii – V` (chorus) · `I – V7/ii – ii – V7` | I-vi-ii-V |
| 29 | Será Que É Amor | Arlindo Cruz | A | `I – iiiø – V7/ii – V7/V – V7` | ii-V-I |
| 30 | Carinhoso | Pixinguinha | G | `ii7 – V7 – I` (D section) · I line cliché | ii-V-I |

## Family → taught cavaquinho sequence (Songs-button mapping)

| Sequence (id) | Chords | Matched families |
|---|---|---|
| Quadradinho (`quadradinho_maj`) | I VI7 ii V7 | `ii-V-I`, `I-vi-ii-V` |
| II–V–I (`ii_v_i_maj`) | ii V I | `ii-V-I` |
| Basic minor (`basic_min`) | i I7 iv V7 | `minor-cadence` |
| Médio (`medio_maj`) | 13-chord | `ii-V-I`, `circle` |
| Harmonic field (`campo_maj`) | I ii iii IV V vi vii° | — (scale exercise) |

Families `IV-iv`, `I-IV-vamp`, `minor-vamp` have no taught sequence yet, so those songs don't
appear in the button until a matching sequence is added.

## Sources

All under `https://www.cifraclub.com.br` (ranks 1–30 of the weekly samba ranking). Individual
paths omitted for brevity — searchable by "Artist — Title" on CifraClub.

## Next steps

1. **Review** the functional reductions (fix any wrong Roman analysis).
2. Optionally add sequences for the `IV-iv` / `I-IV-vamp` families so more songs surface.
3. Expand past the top 30 as desired.
