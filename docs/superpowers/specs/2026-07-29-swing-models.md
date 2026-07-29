# Drum-machine swing models (canonical reference)

Date: 2026-07-29. This is the **single source of truth** for the 16th-note swing
feels — read this instead of inferring from code. Kept in lock-step with
`PercussionTiming.swingOffset` (Kotlin) / `swingOffset` (web `percussion.ts`) and
`SWING_MODEL_FORMULA` in both.

## Frame

- The **quarter-note beat = unit length**.
- The four straight 16ths sit at **[0, 1/4, 1/2, 3/4]** (positions p = 0,1,2,3).
- A full **hemiola** (swing 100 %) puts them at **[0, 1/3, 1/2, 2/3]**.
- Knob **p = swingPercent** (0..100); write **q = p/100**. Swing interpolates the
  onsets linearly from straight (q=0) toward the model's target.
- Note `1/3 − 1/4 = 3/4 − 2/3 = 1/12` beat = **1/3 of a 16th slot**.

`swingOffset(pos, s, model)` returns the offset in **slot units** (1 slot = 1/4 beat);
the 2nd-16th shift is `d = q/3` slots. Onsets are rounded independently so beat
anchors stay on-grid and the loop length is preserved. All models keep onsets
strictly increasing at every p.

## Models (played 16th positions, in beat units)

**V1 — Hemiola-based** (default):
```
[ 0,  1/4 + q·(1/3−1/4),  1/2,  3/4 − q·(3/4−2/3) ]
```

**V2** — V1 plus the outer 16ths pulled half as far (1st delayed, 4th anticipated):
```
[ q·(1/3−1/4)/2,  1/4 + q·(1/3−1/4),  1/2,  3/4 − q·(3/4−2/3)/2 ]
```
(The 1st-16th term is implemented as `q·(1/3−1/4)/2` — half the 2nd's shift,
mirroring the 4th — matching "delayed by half this amount". If a literal `q/2`
was intended instead, change `swingOffset` pos 0 accordingly.)

**V3** — V1's 2nd delay, plus the 3rd anticipated by half that amount:
```
[ 0,  1/4 + q·(1/3−1/4),  1/2 − q·(1/3−1/4)/2,  3/4 ]
```

### As slot-unit offsets δ (what `swingOffset` returns), s = q
| model | δ(0) | δ(1) | δ(2) | δ(3) |
|-------|------|------|------|------|
| V1    | 0        | +s/3 | 0     | −s/3 |
| V2    | +s/6     | +s/3 | 0     | −s/6 |
| V3    | 0        | +s/3 | −s/6  | 0    |

### Onsets within the beat at p = 100 % (beat units)
| model | 1st | 2nd | 3rd | 4th |
|-------|-----|-----|-----|-----|
| V1 | 0 | 1/3 | 1/2 | 2/3 |
| V2 | 1/24 | 1/3 | 1/2 | 17/24 |
| V3 | 0 | 1/3 | 5/12 | 3/4 |

## Retired model (NOT in the toggle)

The app's prior swing — never any of V1–V3 — kept the 1st & 2nd on-grid and
anticipated the 3rd/4th (samba "push", ≈2× measured deviations):
```
[ 0,  1/4,  1/2 − q·(1/16),  3/4 − q·(1/10) ]
```
Documented here for reference; removed from the selector per the V1–V3 rename.
