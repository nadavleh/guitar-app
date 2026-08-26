# The CAGED shape sheet

Archive of `~/Desktop/fretboard.pdf` — Nadav's hand-drawn layout of the 5 CAGED
positions in **G**, one column per CAGED chord shape (E, D, C, A, G = boxes 1–5),
each with the major and parallel-minor scale, pentatonic and triad.

This is the source of truth for `theory/.../CagedShapeTable.kt` and
`chorect-web/src/theory/cagedShapeTable.ts`, which encode it verbatim. The sheet
replaced the old fret-window generator, which approximated these fingerings but
never matched them.

**Do not hand-edit the tables below to change the app.** Edit the two shape-table
files (they are the code) and mirror the change here.

## How to read it

Every dot is a **fret offset from the key's low-E root fret**. G's root sits on
the low E at fret 3, so the offset of a dot the sheet draws at fret *n* is
`n − 3`. In the grids the fret numbers are absolute (i.e. in G), `R` is a root
and `o` is any other note; the top row is the high e string.

The compact spec is the exact string the code carries, low E first:
`"E:-1,0*,2 | A:-1,0,2 | ..."`.

## Corrections applied

The sheet is self-consistent apart from four slips. Each was verified by
rendering the diagram and re-deriving it, and each is pinned by a test
(`CagedShapeTableTest`, and the same checks in `chorect-web/test/verify.ts`):

| Diagram | Slip | Fix |
| --- | --- | --- |
| Minor scale box 1 pattern 1 | drawn in **A minor** — roots on fret 5, while every other diagram is in G | shifted down 2 frets |
| Minor pentatonic box 1 | A string's 2nd dot on fret 6 = **D#**, not in G minor pentatonic | moved to fret 5 (D) |
| Minor pentatonic box 4 | an exact **copy of box 3** | rebuilt at frets 10–13 — which is exactly the pentatonic subset of the sheet's own *minor scale box 4 pattern 2*, so the fix comes from the sheet |
| Minor triad box 4 | box 3's shape with the low-E dot on fret 8 = **C**, not a G-minor chord tone | rebuilt as the triad subset of the corrected minor pentatonic box 4 — the relationship every other triad diagram on the sheet obeys |

Two properties confirm the transcription as a whole: every triad diagram is the
triad subset of its own pentatonic diagram (exact on major box 4 and minor boxes
2, 3 and 5), and the five boxes together cover **every** G major and G minor tone
between frets 2 and 12 with no holes.

## The 34 diagrams


### Box 1 — CAGED shape E

**Major scale box 1 pattern 1**

```
      2   3   4   5
e    o   R   .   o 
B    .   o   .   o 
G    o   .   o   o 
D    o   .   o   R 
A    o   o   .   o 
E    o   R   .   o 
```
`E:-1,0*,2 | A:-1,0,2 | D:-1,1,2* | G:-1,1,2 | B:0,2 | e:-1,0*,2`

**Major scale box 1 pattern 2**

```
      3   4   5   6   7
e    R   .   o   .   o 
B    o   .   o   .   o 
G    .   o   o   .   o 
D    .   o   R   .   o 
A    o   .   o   .   o 
E    R   .   o   .   o 
```
`E:0*,2,4 | A:0,2,4 | D:1,2*,4 | G:1,2,4 | B:0,2,4 | e:0*,2,4`

**Major pentatonic scale box 1**

```
      2   3   4   5
e    .   R   .   o 
B    .   o   .   o 
G    o   .   o   . 
D    o   .   .   R 
A    o   .   .   o 
E    .   R   .   o 
```
`E:0*,2 | A:-1,2 | D:-1,2* | G:-1,1 | B:0,2 | e:0*,2`

**Major triad box 1**

```
      2   3   4   5   6   7
e    .   R   .   .   .   o 
B    .   o   .   .   .   . 
G    .   .   o   .   .   . 
D    .   .   .   R   .   . 
A    o   .   .   o   .   . 
E    .   R   .   .   .   o 
```
`E:0*,4 | A:-1,2 | D:2* | G:1 | B:0 | e:0*,4`

**Minor scale box 1 pattern 1**  *(corrected: shifted down 2 frets (was drawn in A minor))*

```
      1   2   3   4   5
e    o   .   R   .   o 
B    .   .   o   o   . 
G    .   o   o   .   o 
D    o   .   o   .   R 
A    o   .   o   .   o 
E    o   .   R   .   o 
```
`E:-2,0*,2 | A:-2,0,2 | D:-2,0,2* | G:-1,0,2 | B:0,1 | e:-2,0*,2`

**Minor scale box 1 pattern 2**

```
      3   4   5   6   7
e    R   .   o   o   . 
B    o   o   .   o   . 
G    o   .   o   .   o 
D    o   .   R   .   o 
A    o   .   o   o   . 
E    R   .   o   o   . 
```
`E:0*,2,3 | A:0,2,3 | D:0,2*,4 | G:0,2,4 | B:0,1,3 | e:0*,2,3`

**Minor pentatonic scale box 1**  *(corrected: A string 2nd dot fret 6 (D#) -> fret 5 (D))*

```
      3   4   5   6
e    R   .   .   o 
B    o   .   .   o 
G    o   .   o   . 
D    o   .   R   . 
A    o   .   o   . 
E    R   .   .   o 
```
`E:0*,3 | A:0,2 | D:0,2* | G:0,2 | B:0,3 | e:0*,3`

**Minor triad box 1**

```
      1   2   3   4   5   6
e    .   .   R   .   .   o 
B    .   .   o   .   .   . 
G    .   .   o   .   .   . 
D    .   .   .   .   R   . 
A    o   .   .   .   o   . 
E    .   .   R   .   .   o 
```
`E:0*,3 | A:-2,2 | D:2* | G:0 | B:0 | e:0*,3`


### Box 2 — CAGED shape D

**Major scale box 2 pattern 1**

```
      5   6   7   8   9
e    o   .   o   o   . 
B    o   .   o   R   . 
G    o   .   o   .   . 
D    R   .   o   .   o 
A    o   .   o   .   o 
E    o   .   o   o   . 
```
`E:2,4,5 | A:2,4,6 | D:2*,4,6 | G:2,4 | B:2,4,5* | e:2,4,5`

**Major pentatonic scale box 2**

```
      4   5   6   7   8
e    .   o   .   o   . 
B    .   o   .   .   R 
G    o   .   .   o   . 
D    .   R   .   o   . 
A    .   o   .   o   . 
E    .   o   .   o   . 
```
`E:2,4 | A:2,4 | D:2*,4 | G:1,4 | B:2,5* | e:2,4`

**Major triad box 2**

```
      4   5   6   7   8   9
e    .   .   .   o   .   . 
B    .   .   .   .   R   . 
G    o   .   .   o   .   . 
D    .   R   .   .   .   o 
A    .   o   .   .   .   . 
E    .   .   .   o   .   . 
```
`E:4 | A:2 | D:2*,6 | G:1,4 | B:5* | e:4`

**Minor scale box 2 pattern 1**

```
      5   6   7   8
e    o   o   .   o 
B    .   o   .   R 
G    o   .   o   o 
D    R   .   o   o 
A    o   o   .   o 
E    o   o   .   o 
```
`E:2,3,5 | A:2,3,5 | D:2*,4,5 | G:2,4,5 | B:3,5* | e:2,3,5`

**Minor pentatonic scale box 2**

```
      5   6   7   8
e    .   o   .   o 
B    .   o   .   R 
G    o   .   o   . 
D    R   .   .   o 
A    o   .   .   o 
E    .   o   .   o 
```
`E:3,5 | A:2,5 | D:2*,5 | G:2,4 | B:3,5* | e:3,5`

**Minor triad box 2**

```
      3   4   5   6   7   8
e    .   .   .   o   .   . 
B    .   .   .   .   .   R 
G    o   .   .   .   o   . 
D    .   .   R   .   .   o 
A    .   .   o   .   .   . 
E    .   .   .   o   .   . 
```
`E:3 | A:2 | D:2*,5 | G:0,4 | B:5* | e:3`


### Box 3 — CAGED shape C

**Major scale box 3 pattern 1**

```
      7   8   9  10
e    o   o   .   o 
B    o   R   .   o 
G    o   .   o   . 
D    o   .   o   o 
A    o   .   o   R 
E    o   o   .   o 
```
`E:4,5,7 | A:4,6,7* | D:4,6,7 | G:4,6 | B:4,5*,7 | e:4,5,7`

**Major pentatonic scale box 3**

```
      7   8   9  10
e    o   .   .   o 
B    .   R   .   o 
G    o   .   o   . 
D    o   .   o   . 
A    o   .   .   R 
E    o   .   .   o 
```
`E:4,7 | A:4,7* | D:4,6 | G:4,6 | B:5*,7 | e:4,7`

**Major triad box 3**

```
      7   8   9  10
e    o   .   .   o 
B    .   R   .   . 
G    o   .   .   . 
D    .   .   o   . 
A    .   .   .   R 
E    o   .   .   o 
```
`E:4,7 | A:7* | D:6 | G:4 | B:5* | e:4,7`

**Minor scale box 3 pattern 1**

```
      6   7   8   9  10
e    o   .   o   .   o 
B    .   .   R   .   o 
G    .   o   o   .   o 
D    .   o   o   .   o 
A    o   .   o   .   R 
E    o   .   o   .   o 
```
`E:3,5,7 | A:3,5,7* | D:4,5,7 | G:4,5,7 | B:5*,7 | e:3,5,7`

**Minor pentatonic scale box 3**

```
      7   8   9  10  11
e    .   o   .   o   . 
B    .   R   .   .   o 
G    o   .   .   o   . 
D    .   o   .   o   . 
A    .   o   .   R   . 
E    .   o   .   o   . 
```
`E:5,7 | A:5,7* | D:5,7 | G:4,7 | B:5*,8 | e:5,7`

**Minor triad box 3**

```
      7   8   9  10  11
e    .   .   .   o   . 
B    .   R   .   .   o 
G    o   .   .   .   . 
D    .   o   .   .   . 
A    .   .   .   R   . 
E    .   .   .   o   . 
```
`E:7 | A:7* | D:5 | G:4 | B:5*,8 | e:7`


### Box 4 — CAGED shape A

**Major scale box 4 pattern 1**

```
      8   9  10  11  12
e    o   .   o   .   o 
B    .   .   o   .   o 
G    .   o   .   o   R 
D    .   o   o   .   o 
A    .   o   R   .   o 
E    o   .   o   .   o 
```
`E:5,7,9 | A:6,7*,9 | D:6,7,9 | G:6,8,9* | B:7,9 | e:5,7,9`

**Major scale box 4 pattern 2**

```
     10  11  12  13  14
e    o   .   o   .   o 
B    .   .   o   o   . 
G    .   o   R   .   o 
D    o   .   o   .   o 
A    R   .   o   .   o 
E    o   .   o   .   o 
```
`E:7,9,11 | A:7*,9,11 | D:7,9,11 | G:8,9*,11 | B:9,10 | e:7,9,11`

**Major pentatonic scale box 4**

```
      9  10  11  12
e    .   o   .   o 
B    .   o   .   o 
G    o   .   .   R 
D    o   .   .   o 
A    .   R   .   o 
E    .   o   .   o 
```
`E:7,9 | A:7*,9 | D:6,9 | G:6,9* | B:7,9 | e:7,9`

**Major triad box 4**

```
      9  10  11  12
e    .   o   .   . 
B    .   .   .   o 
G    .   .   .   R 
D    o   .   .   o 
A    .   R   .   . 
E    .   o   .   . 
```
`E:7 | A:7* | D:6,9 | G:9* | B:9 | e:7`

**Minor scale box 4 pattern 1**

```
      8   9  10  11  12
e    o   .   o   o   . 
B    R   .   o   o   . 
G    o   .   o   .   . 
D    o   .   o   .   o 
A    o   .   R   .   o 
E    o   .   o   o   . 
```
`E:5,7,8 | A:5,7*,9 | D:5,7,9 | G:5,7 | B:5*,7,8 | e:5,7,8`

**Minor scale box 4 pattern 2**

```
     10  11  12  13
e    o   o   .   o 
B    o   o   .   o 
G    o   .   R   . 
D    o   .   o   o 
A    R   .   o   o 
E    o   o   .   o 
```
`E:7,8,10 | A:7*,9,10 | D:7,9,10 | G:7,9* | B:7,8,10 | e:7,8,10`

**Minor pentatonic scale box 4**  *(corrected: was a copy of box 3; rebuilt at frets 10-13)*

```
     10  11  12  13
e    o   .   .   o 
B    .   o   .   o 
G    o   .   R   . 
D    o   .   o   . 
A    R   .   .   o 
E    o   .   .   o 
```
`E:7,10 | A:7*,10 | D:7,9 | G:7,9* | B:8,10 | e:7,10`

**Minor triad box 4**  *(corrected: was box 3 with a wrong low-E note; rebuilt)*

```
     10  11  12  13
e    o   .   .   . 
B    .   o   .   . 
G    .   .   R   . 
D    .   .   o   . 
A    R   .   .   o 
E    o   .   .   . 
```
`E:7 | A:7*,10 | D:9 | G:9* | B:8 | e:7`


### Box 5 — CAGED shape G

**Major scale box 5 pattern 1**

```
     12  13  14  15  16
e    o   .   o   R   . 
B    o   o   .   o   . 
G    R   .   o   .   . 
D    o   .   o   .   o 
A    o   .   o   o   . 
E    o   .   o   R   . 
```
`E:9,11,12* | A:9,11,12 | D:9,11,13 | G:9*,11 | B:9,10,12 | e:9,11,12*`

**Major pentatonic scale box 5**

```
     12  13  14  15
e    o   .   .   R 
B    o   .   .   o 
G    R   .   o   . 
D    o   .   o   . 
A    o   .   o   . 
E    o   .   .   R 
```
`E:9,12* | A:9,11 | D:9,11 | G:9*,11 | B:9,12 | e:9,12*`

**Major triad box 5**

```
     12  13  14  15
e    .   .   .   R 
B    o   .   .   o 
G    R   .   .   . 
D    o   .   .   . 
A    .   .   o   . 
E    .   .   .   R 
```
`E:12* | A:11 | D:9 | G:9* | B:9,12 | e:12*`

**Minor scale box 5 pattern 1**

```
     11  12  13  14  15
e    o   .   o   .   R 
B    .   .   o   .   o 
G    .   R   .   o   o 
D    .   o   o   .   o 
A    .   o   o   .   o 
E    o   .   o   .   R 
```
`E:8,10,12* | A:9,10,12 | D:9,10,12 | G:9*,11,12 | B:10,12 | e:8,10,12*`

**Minor pentatonic scale box 5**

```
     12  13  14  15
e    .   o   .   R 
B    .   o   .   o 
G    R   .   .   o 
D    o   .   .   o 
A    .   o   .   o 
E    .   o   .   R 
```
`E:10,12* | A:10,12 | D:9,12 | G:9*,12 | B:10,12 | e:10,12*`

**Minor triad box 5**

```
     12  13  14  15
e    .   .   .   R 
B    .   .   .   o 
G    R   .   .   o 
D    o   .   .   . 
A    .   o   .   . 
E    .   .   .   R 
```
`E:12* | A:10 | D:9 | G:9*,12 | B:12 | e:12*`

