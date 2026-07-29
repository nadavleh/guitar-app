// Reactive app state, ported from app/.../AppState.kt (Phase 1 subset: Fretboard,
// Tuner, Options). Persistence uses localStorage in place of Android DataStore.
//
// State is a plain observable: mutating methods change fields then call notify(),
// and the UI subscribes via subscribe() to re-render. (No framework Ã¢â‚¬â€ the Kotlin
// app's Compose recomposition is replaced by an explicit render pass.)

import {
  Instrument, InstrumentInfo, Tuning, Note, note, FretPosition, fp, fpKey,
  noteAt, stringCount, parseNote,
  ChordShape, VoicingStyle, parseChord, ChordShapeGenerator,
} from "../theory";
import * as Tunings from "../theory/tunings";
import { WebAudioEngine, Timbre, Timbres, midiToFreqA4, SampleBank } from "../audio";

export const DISPLAY_FRETS = 14;
/** App version shown beside the header wordmark. Keep in sync with package.json on release. */
export const APP_VERSION = "2.49.0";
const MIDI_MIN = 28; // E1
const MIDI_MAX = 84; // C6

export interface ChallengeScore {
  score: number;
  total: number;
  durationMs: number;
  dateMillis: number;
  /** Which trainer produced this ("progression" | "inversions" | "augdim" |
   *  "flavor" | "intervals" | "note2chord"). Legacy rows load as "progression". */
  kind?: string;
}

/** Higher score first; ties broken by faster (smaller) completion time. */
export const CHALLENGE_SCORE_ORDER = (a: ChallengeScore, b: ChallengeScore): number =>
  b.score - a.score || a.durationMs - b.durationMs;

export enum DisplayMode { None = "None", Chord = "Chord", Scale = "Scale", Pick = "Pick" }
export enum LabelMode { Notes = "Notes", Intervals = "Intervals", Empty = "Empty" }
export enum Sheet { Fretboard = "Fretboard", Options = "Options", Tuner = "Tuner", Loop = "Loop", EarTraining = "EarTraining", SambaLooper = "SambaLooper", Decompose = "Decompose", CavaqProgressions = "CavaqProgressions", RhythmUnits = "RhythmUnits", Metronome = "Metronome", ScalesTriads = "ScalesTriads" }
export enum ChordScaleView { AllNotes = "AllNotes", Positions = "Positions" }

/** Selectable guitar sound: "Synth" is the Karplus-Strong synth voice (always
 *  available); the others are sampled banks fetched on demand (Task 1 assets
 *  not shipped yet, so those fetches currently fail and fall back to Synth). */
export type SoundName = "Synth" | "Acoustic" | "Nylon" | "Electric";

/** One EQ band's runtime gain, in dB (Ã‚Â±12 typical range). */
export type EqBand = "bass" | "mid" | "treble";

/** Per-sound runtime EQ applied on the modern chain's biquad EQ (bass =
 *  low-shelf, mid = peaking, treble = high-shelf). Mirrors AppState.EqSettings
 *  on Android. */
export interface EqSettings {
  bass: number;
  mid: number;
  treble: number;
}

const FLAT_EQ: EqSettings = { bass: 0, mid: 0, treble: 0 };

/** Default per-sound EQ map: flat for every sound except Nylon, which gets a
 *  slight mid scoop (mirrors the Android default). */
function defaultEq(): Record<SoundName, EqSettings> {
  return {
    Synth: { ...FLAT_EQ },
    Acoustic: { ...FLAT_EQ },
    Nylon: { bass: 0, mid: -4, treble: 0 },
    Electric: { ...FLAT_EQ },
  };
}

function defaultReverb(): Record<SoundName, number> {
  return { Synth: 0.03, Acoustic: 0.03, Nylon: 0.03, Electric: 0.03 }; // default 3% reverb (all sounds/instruments)
}

const LS_KEY = "chorect-web.v1";

/** Default Play-mode quick-chord palette: the open-chord workhorses. */
export const DEFAULT_CHORD_SLOTS: readonly string[] = ["C", "G", "Am", "F", "D", "Em", "E", "A"];

/** The 5 user-swappable ACT accents (see style.css `[data-accent]` overrides
 *  and app/.../Theme.kt `Accent`). "coral" is the default and maps to no
 *  `data-accent` attribute at all. */
export type AccentName = "coral" | "amber" | "teal" | "blue" | "purple";
export const ALL_ACCENTS: readonly AccentName[] = ["coral", "amber", "teal", "blue", "purple"];

/** UI theme mode (Settings' Personalize section). "Auto" follows the OS/browser
 *  `prefers-color-scheme` live (see ui.ts's `matchMedia` listener); mirrors
 *  Android's `ThemeMode` enum + `theme_mode` pref exactly, so the persisted
 *  string round-trips identically on both platforms. */
export type ThemeMode = "Dark" | "Light" | "Auto";
export const ALL_THEME_MODES: readonly ThemeMode[] = ["Dark", "Light", "Auto"];
function isThemeMode(v: unknown): v is ThemeMode {
  return typeof v === "string" && (ALL_THEME_MODES as readonly string[]).includes(v);
}

/** One user-configurable tab destination Ã¢â‚¬â€ names mirror Android's `TabDest`
 *  enum (Shell.kt) EXACTLY, so a tab-order value round-trips identically on
 *  both platforms. "More" is not a TabDest; it's the fixed 5th nav item. */
export type TabDestName = "Neck" | "Ear" | "Rhythm" | "Loop" | "Tuner" | "Decompose" | "CavaqProgressions" | "RhythmUnits" | "Metronome" | "ScalesTriads";
export const ALL_TAB_DESTS: readonly TabDestName[] = ["Neck", "Ear", "Rhythm", "Loop", "Tuner", "Decompose", "CavaqProgressions", "RhythmUnits", "Metronome", "ScalesTriads"];
/** Default tab set/order for a fresh install (matches Android's DEFAULT_TAB_ORDER). */
export const DEFAULT_TAB_ORDER: readonly TabDestName[] = ["Neck", "Ear", "Rhythm", "Tuner"];
function isTabDestName(v: unknown): v is TabDestName {
  return typeof v === "string" && (ALL_TAB_DESTS as readonly string[]).includes(v);
}

interface Persisted {
  instrument: string;
  tuningName: string;
  labelMode: string;
  leftHanded: boolean;
  darkTheme: boolean;
  themeMode: string;
  accent: string;
  tabOrder: string[];
  voicingShell: boolean;
  a4Hz: number;
  ringSustainMs: number;
  strumMs: number;
  tapOnTouchDown: boolean;
  sound: string;
  eq: Record<SoundName, EqSettings>;
  reverb: Record<SoundName, number>;
  customTunings: Record<string, number[]>;
  challengeScores: ChallengeScore[];
  progressionMistakes: Record<string, number>;
  drumPatterns: Record<string, string>;
  drumBlocks: Record<string, string>;
  drumTrackPresets: Record<string, string>;
  drumVolumes: Record<string, number>;
  chordSlots?: string[];
}

export class AppState {
  instrument = Instrument.Guitar;
  tuningName = "Standard";
  liveTuning: Tuning = Tunings.standard;
  isEditedTuning = false;

  chordInput = "Cmaj7";
  scaleRoot = "A";
  scaleType = "minor pentatonic";

  labelMode = LabelMode.Intervals;
  selectedPosition: FretPosition | null = null;
  leftHanded = false;
  /** UI theme; dark is the original look. Dead now that `themeMode` drives the
   *  UI (Settings' Theme segmented) Ã¢â‚¬â€ kept only as the migration fallback's
   *  source (see `load()`), mirroring Android's AppState.darkTheme. */
  darkTheme = true;
  /** UI theme mode (Settings' Personalize section): Dark/Light/Auto. "Auto"
   *  resolves against `prefers-color-scheme` in ui.ts's render(). Fresh
   *  installs default to Light (v2.1.0); a never-configured profile falls
   *  back through the `darkTheme` migration in `load()`, so existing users
   *  keep whatever they already had. */
  themeMode: ThemeMode = "Light";
  /** ACT accent (Personalize / Settings). "coral" is the default. */
  accent: AccentName = "coral";
  /** User-configurable tab set + order (Settings' "Tabs & order" editor); the
   *  fixed 5th nav item ("More") isn't part of this list. */
  tabOrder: TabDestName[] = [...DEFAULT_TAB_ORDER];

  displayMode = DisplayMode.None;
  currentSheet: Sheet | null = Sheet.SambaLooper;   // app opens on the drum machine
  lastSheet: Sheet | null = null;
  chordView = ChordScaleView.AllNotes;
  scaleView = ChordScaleView.AllNotes;
  chordPositionIndex = 0;
  scalePositionIndex = 0;

  pickedPositions = new Set<string>(); // fpKey strings
  mutedStrings = new Set<number>();
  /** Play-mode quick-chord slots (chord symbols), applied via applyChordSlot. Persisted. */
  chordSlots: string[] = [...DEFAULT_CHORD_SLOTS];
  /** Index of the slot whose grip is currently on the board (highlights its chip);
   *  Ã¢Ë†â€™1 once the grip is hand-edited or cleared. */
  activeChordSlot = -1;

  voicingStyle = VoicingStyle.Standard;

  a4Hz = 440;
  ringSustainMs = 1500;
  strumMs = 30;
  tapOnTouchDown = true;

  /** Selected guitar sound; "Synth" plays through the plucked-string synth,
   *  the rest through a fetched sampled bank (see `setSound`). */
  sound: SoundName = "Synth";
  /** Per-sound runtime EQ (bass/mid/treble dB), applied to the modern chain's
   *  biquad EQ whenever the matching sound is active. */
  eq: Record<SoundName, EqSettings> = defaultEq();
  /** Per-sound reverb send (0..1), applied to the modern chain when the sound is active. */
  reverb: Record<SoundName, number> = defaultReverb();
  /** True while a sampled bank fetch triggered by `setSound` is in flight. */
  soundLoading = false;
  /** Sampled banks already fetched this session, keyed by lowercase id. */
  private bankCache = new Map<string, SampleBank>();

  customTunings = new Map<string, Tuning>();
  challengeScores: ChallengeScore[] = [];
  /** Progression mistake-drill counts: progressionKey → number of times missed. */
  progressionMistakes: Record<string, number> = {};
  /** Saved drum beats: name Ã¢â€ â€™ encoded PercussionPattern string (insertion order). */
  drumPatterns = new Map<string, string>();
  /** Saved drum BLOCKS (phrase sequences), name -> DrumBlock.encode(). */
  drumBlocks = new Map<string, string>();
  /** USER-DEFINED phrases (custom track presets), label -> encodePresetTrack(). */
  drumTrackPresets = new Map<string, string>();
  /** Drum mixer volumes: "<instId>" (global) or "<instId>:<voice>" Ã¢â€ â€™ 0..1. */
  drumVolumes = new Map<string, number>();

  private listeners = new Set<() => void>();

  constructor(public readonly audio: WebAudioEngine) {
    this.load();
    // Cavaquinho opens on the G-major chord (by position, intervals); the display
    // state isn't persisted, so this seeds the default each launch for cavaquinho.
    if (this.instrument === Instrument.Cavaquinho) this.applyCavaquinhoFretboardDefaults();
    if (this.sound !== "Synth") {
      this.applySound(this.sound);
    } else {
      const e = this.eq.Synth;
      this.audio.setEq(e.bass, e.mid, e.treble);
      this.audio.setReverbSend(this.reverb.Synth);
    }
  }

  // ---------- reactivity ----------

  subscribe(fn: () => void): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  private notify(): void {
    for (const fn of this.listeners) fn();
  }

  // ---------- persistence ----------

  private load(): void {
    const raw = localStorage.getItem(LS_KEY);
    if (!raw) return;
    try {
      const p = JSON.parse(raw) as Partial<Persisted>;
      if (p.instrument && p.instrument in InstrumentInfo) this.instrument = p.instrument as Instrument;
      if (p.labelMode && p.labelMode in LabelMode) this.labelMode = p.labelMode as LabelMode;
      if (typeof p.leftHanded === "boolean") this.leftHanded = p.leftHanded;
      if (typeof p.darkTheme === "boolean") this.darkTheme = p.darkTheme;
      // themeMode migration: prefer the new field; if this profile predates it,
      // fall back to the old boolean flag (mirrors Android's TuningRepository
      // theme_mode/dark_theme fallback).
      if (typeof p.themeMode === "string" && isThemeMode(p.themeMode)) {
        this.themeMode = p.themeMode;
      } else if (typeof p.darkTheme === "boolean") {
        this.themeMode = p.darkTheme ? "Dark" : "Light";
      }
      if (typeof p.accent === "string" && (ALL_ACCENTS as readonly string[]).includes(p.accent)) {
        this.accent = p.accent as AccentName;
      }
      if (Array.isArray(p.tabOrder)) {
        const valid = p.tabOrder.filter(isTabDestName);
        if (valid.length === 4 && new Set(valid).size === 4) this.tabOrder = valid;
      }
      if (typeof p.voicingShell === "boolean") this.voicingStyle = p.voicingShell ? VoicingStyle.Shell : VoicingStyle.Standard;
      if (typeof p.a4Hz === "number") this.a4Hz = p.a4Hz;
      if (typeof p.ringSustainMs === "number") this.ringSustainMs = p.ringSustainMs;
      if (typeof p.strumMs === "number") this.strumMs = p.strumMs;
      if (typeof p.tapOnTouchDown === "boolean") this.tapOnTouchDown = p.tapOnTouchDown;
      if (p.sound === "Synth" || p.sound === "Acoustic" || p.sound === "Nylon" || p.sound === "Electric") this.sound = p.sound;
      if (p.eq) {
        for (const key of Object.keys(this.eq) as SoundName[]) {
          const v = p.eq[key];
          if (v && typeof v.bass === "number" && typeof v.mid === "number" && typeof v.treble === "number") {
            this.eq[key] = { bass: v.bass, mid: v.mid, treble: v.treble };
          }
        }
      }
      if (p.reverb) {
        for (const key of Object.keys(this.reverb) as SoundName[]) {
          const v = p.reverb[key];
          if (typeof v === "number") this.reverb[key] = Math.max(0, Math.min(1, v));
        }
      }
      if (p.customTunings) {
        for (const [name, midis] of Object.entries(p.customTunings)) {
          this.customTunings.set(name, { openStrings: midis.map((m) => note(m)) });
        }
      }
      if (Array.isArray(p.challengeScores)) this.challengeScores = p.challengeScores.slice();
      if (p.progressionMistakes && typeof p.progressionMistakes === "object") {
        for (const [k, v] of Object.entries(p.progressionMistakes)) if (typeof v === "number" && v > 0) this.progressionMistakes[k] = v;
      }
      if (p.drumPatterns) for (const [name, enc] of Object.entries(p.drumPatterns)) this.drumPatterns.set(name, enc);
      if (p.drumBlocks) for (const [name, enc] of Object.entries(p.drumBlocks)) this.drumBlocks.set(name, enc);
      if (p.drumTrackPresets) for (const [name, enc] of Object.entries(p.drumTrackPresets)) this.drumTrackPresets.set(name, enc);
      if (p.drumVolumes) for (const [k, v] of Object.entries(p.drumVolumes)) {
        if (typeof v === "number") this.drumVolumes.set(k, Math.min(Math.max(v, 0), 1));
      }
      if (Array.isArray(p.chordSlots) && p.chordSlots.length === DEFAULT_CHORD_SLOTS.length &&
          p.chordSlots.every((s) => typeof s === "string" && parseChord(s) !== null)) {
        this.chordSlots = p.chordSlots.slice();
      }
      // Resolve the saved tuning name against presets + customs for the current instrument.
      const name = p.tuningName ?? Tunings.defaultNameFor(this.instrument);
      const resolved = Tunings.allPresets.get(name) ?? this.customTunings.get(name) ?? Tunings.defaultFor(this.instrument);
      this.tuningName = name;
      this.liveTuning = resolved;
    } catch {
      /* ignore corrupt storage */
    }
  }

  private save(): void {
    const customTunings: Record<string, number[]> = {};
    for (const [name, t] of this.customTunings) customTunings[name] = t.openStrings.map((n) => n.midi);
    const p: Persisted = {
      instrument: this.instrument,
      tuningName: this.tuningName,
      labelMode: this.labelMode,
      leftHanded: this.leftHanded,
      darkTheme: this.darkTheme,
      themeMode: this.themeMode,
      accent: this.accent,
      tabOrder: this.tabOrder,
      voicingShell: this.voicingStyle === VoicingStyle.Shell,
      a4Hz: this.a4Hz,
      ringSustainMs: this.ringSustainMs,
      strumMs: this.strumMs,
      tapOnTouchDown: this.tapOnTouchDown,
      sound: this.sound,
      eq: this.eq,
      reverb: this.reverb,
      customTunings,
      challengeScores: this.challengeScores,
      progressionMistakes: this.progressionMistakes,
      drumPatterns: Object.fromEntries(this.drumPatterns),
      drumBlocks: Object.fromEntries(this.drumBlocks),
      drumTrackPresets: Object.fromEntries(this.drumTrackPresets),
      drumVolumes: Object.fromEntries(this.drumVolumes),
      chordSlots: this.chordSlots,
    };
    localStorage.setItem(LS_KEY, JSON.stringify(p));
  }

  /** Record a finished challenge result (best first, keep top 10 PER KIND). */
  recordChallengeScore(score: number, total: number, durationMs: number, kind = "progression"): void {
    this.commit(() => {
      const all = [...this.challengeScores, { score, total, durationMs, dateMillis: Date.now(), kind }];
      const byKind = new Map<string, ChallengeScore[]>();
      for (const s of all) {
        const k = s.kind ?? "progression";
        if (!byKind.has(k)) byKind.set(k, []);
        byKind.get(k)!.push(s);
      }
      this.challengeScores = [...byKind.values()].flatMap((rows) => rows.sort(CHALLENGE_SCORE_ORDER).slice(0, 10));
    });
  }

  /** Delete every recorded challenge result (undoable). */
  clearChallengeScores(): void {
    this.commit(() => { this.challengeScores = []; });
  }

  /** Delete one recorded result by identity, or every result of one kind (undoable). */
  deleteChallengeScore(entry: ChallengeScore): void {
    this.commit(() => { this.challengeScores = this.challengeScores.filter((s) => s !== entry); });
  }
  clearChallengeScoresOfKind(kind: string): void {
    this.commit(() => { this.challengeScores = this.challengeScores.filter((s) => (s.kind ?? "progression") !== kind); });
  }

  /** Increment the mistake count for a progression (its progressionKey). */
  recordProgressionMistake(key: string): void {
    this.commit(() => { this.progressionMistakes = { ...this.progressionMistakes, [key]: (this.progressionMistakes[key] ?? 0) + 1 }; });
  }
  /** Drop one progression from the drill list (resets its count). */
  clearProgressionMistake(key: string): void {
    this.commit(() => { const m = { ...this.progressionMistakes }; delete m[key]; this.progressionMistakes = m; });
  }
  /** Reset every progression mistake count. */
  clearProgressionMistakes(): void {
    this.commit(() => { this.progressionMistakes = {}; });
  }

  saveDrumTrackPreset(name: string, encoded: string): void {
    this.commit(() => { this.drumTrackPresets.set(name, encoded); });
  }
  deleteDrumTrackPreset(name: string): void {
    this.commit(() => { this.drumTrackPresets.delete(name); });
  }
  saveDrumBlock(name: string, encoded: string): void {
    this.commit(() => { this.drumBlocks.set(name, encoded); });
  }
  deleteDrumBlock(name: string): void {
    this.commit(() => { this.drumBlocks.delete(name); });
  }
  saveDrumPattern(name: string, encoded: string): void {
    this.commit(() => { this.drumPatterns.set(name, encoded); });
  }
  deleteDrumPattern(name: string): void {
    this.commit(() => { this.drumPatterns.delete(name); });
  }

  /** Persist one drum mixer volume entry (global or per-voice). */
  setDrumVolume(key: string, value: number): void {
    this.commit(() => { this.drumVolumes.set(key, Math.min(Math.max(value, 0), 1)); });
  }

  /** Mutate + persist + re-render in one shot. */
  private commit(mutate: () => void): void {
    mutate();
    this.save();
    this.notify();
  }

  // ---------- timbre ----------

  private get timbre(): Timbre {
    return this.instrument === Instrument.Guitar ? Timbres.Guitar : Timbres.Cavaquinho;
  }

  // ---------- instrument / tuning ----------

  setInstrument(value: Instrument): void {
    if (this.instrument === value) return;
    this.commit(() => {
      this.instrument = value;
      this.tuningName = Tunings.defaultNameFor(value);
      this.liveTuning = Tunings.defaultFor(value);
      this.isEditedTuning = false;
      this.chordPositionIndex = 0;
      if (value === Instrument.Cavaquinho) this.applyCavaquinhoFretboardDefaults();
      else this.displayMode = DisplayMode.None;   // guitar opens with an empty board
    });
  }

  /** Cavaquinho opens the fretboard on the G-major chord, shown by position with
   *  interval labels, rather than the empty board the guitar uses. */
  applyCavaquinhoFretboardDefaults(): void {
    this.chordInput = "G";
    this.displayMode = DisplayMode.Chord;
    this.chordView = ChordScaleView.Positions;
    this.labelMode = LabelMode.Intervals;
    this.chordPositionIndex = 0;
  }

  selectTuning(name: string, tuning: Tuning): void {
    this.commit(() => {
      this.tuningName = name;
      this.liveTuning = tuning;
      this.isEditedTuning = false;
      this.chordPositionIndex = 0;
    });
  }

  adjustString(stringIdx: number, delta: number): void {
    const current = this.liveTuning.openStrings[stringIdx];
    const newMidi = Math.min(Math.max(current.midi + delta, MIDI_MIN), MIDI_MAX);
    if (newMidi === current.midi) return;
    this.commit(() => {
      const open = this.liveTuning.openStrings.slice();
      open[stringIdx] = note(newMidi);
      this.liveTuning = { openStrings: open };
      this.isEditedTuning = true;
    });
  }

  saveCustomTuning(name: string): void {
    const clean = name.trim();
    if (clean.length === 0 || clean.includes("|") || clean.includes(";")) return;
    this.commit(() => {
      this.customTunings.set(clean, { openStrings: this.liveTuning.openStrings.slice() });
      this.tuningName = clean;
      this.isEditedTuning = false;
    });
  }

  deleteCustomTuning(name: string): void {
    this.commit(() => {
      this.customTunings.delete(name);
      if (this.tuningName === name) {
        this.tuningName = "Standard";
        this.liveTuning = Tunings.standard;
        this.isEditedTuning = false;
      }
    });
  }

  resetTuningToSaved(): void {
    this.commit(() => {
      this.liveTuning =
        Tunings.allPresets.get(this.tuningName) ?? this.customTunings.get(this.tuningName) ?? Tunings.standard;
      this.isEditedTuning = false;
    });
  }

  // ---------- simple setters ----------

  setChordInput(symbol: string): void { this.commit(() => { this.chordInput = symbol; this.chordPositionIndex = 0; }); }
  setScaleRoot(name: string): void { this.commit(() => { this.scaleRoot = name; this.scalePositionIndex = 0; }); }
  setScaleType(name: string): void { this.commit(() => { this.scaleType = name; this.scalePositionIndex = 0; }); }
  setDisplayMode(m: DisplayMode): void { this.commit(() => { this.displayMode = m; }); }
  setChordView(v: ChordScaleView): void { this.commit(() => { this.chordView = v; }); }
  setScaleView(v: ChordScaleView): void { this.commit(() => { this.scaleView = v; }); }
  setLabelMode(m: LabelMode): void { this.commit(() => { this.labelMode = m; }); }
  toggleLeftHanded(v: boolean): void { this.commit(() => { this.leftHanded = v; }); }
  toggleDarkTheme(v: boolean): void { this.commit(() => { this.darkTheme = v; }); }
  /** Persist the chosen Theme mode (Personalize's segmented Dark/Light/Auto).
   *  Resolving "Auto" against the live system preference happens in ui.ts's
   *  render() (a DOM/`matchMedia` concern, not state). */
  setThemeMode(m: ThemeMode): void { this.commit(() => { this.themeMode = m; }); }
  /** Persist the chosen ACT accent and apply it immediately: coral (the
   *  default) clears `[data-accent]` entirely (style.css's un-attributed
   *  `:root` rules already are coral); any other accent sets the attribute so
   *  the matching style.css `[data-accent="..."]` override takes effect. */
  setAccent(a: AccentName): void {
    this.commit(() => { this.accent = a; });
    if (a === "coral") delete document.documentElement.dataset.accent;
    else document.documentElement.dataset.accent = a;
  }
  /** Persist the user's chosen tab set/order (Settings' "Tabs & order"
   *  editor). Invalid input (wrong count, dupes, unknown names) resets to
   *  the default 4 Ã¢â‚¬â€ mirrors Android's AppState.setTabOrder guard. */
  setTabOrder(order: readonly string[]): void {
    const valid = order.filter(isTabDestName);
    const unique = [...new Set(valid)];
    const next: TabDestName[] = unique.length === 4 ? unique : [...DEFAULT_TAB_ORDER];
    this.commit(() => { this.tabOrder = next; });
  }
  setTapOnTouchDown(v: boolean): void { this.commit(() => { this.tapOnTouchDown = v; }); }
  setA4Hz(v: number): void { this.commit(() => { this.a4Hz = Math.min(Math.max(Math.round(v), 435), 445); }); }
  setRingSustainMs(v: number): void { this.commit(() => { this.ringSustainMs = Math.min(Math.max(Math.round(v), 300), 4000); }); }
  setStrumMs(v: number): void { this.commit(() => { this.strumMs = Math.min(Math.max(Math.round(v), 0), 150); }); }
  toggleVoicingStyle(shell: boolean): void { this.commit(() => { this.voicingStyle = shell ? VoicingStyle.Shell : VoicingStyle.Standard; this.chordPositionIndex = 0; }); }

  // ---------- sound (sampled-guitar bank selection) ----------

  setSound(s: SoundName): void {
    if (this.sound === s) return;
    this.commit(() => { this.sound = s; });
    this.applySound(s);
  }

  /** Apply `s` to the audio engine: clear the bank for Synth, otherwise fetch
   *  (or reuse a cached) sampled bank. If the fetch fails Ã¢â‚¬â€ expected until the
   *  sample assets ship Ã¢â‚¬â€ the engine falls back to synth voices. Also pushes
   *  `s`'s EQ settings, independent of whether the bank fetch succeeds. */
  private applySound(s: SoundName): void {
    const e = this.eq[s];
    this.audio.setReverbSend(this.reverb[s]);
    if (s === "Synth") {
      this.audio.setInstrument(null);
      this.audio.setEq(e.bass, e.mid, e.treble);
      return;
    }
    const id = s.toLowerCase();
    const cached = this.bankCache.get(id);
    if (cached) {
      this.audio.setInstrument(cached);
      this.audio.setEq(e.bass, e.mid, e.treble);
      return;
    }
    this.soundLoading = true;
    this.notify();
    this.audio.setEq(e.bass, e.mid, e.treble);
    this.audio
      .loadBank(id)
      .then((bank) => {
        this.bankCache.set(id, bank);
        if (this.sound === s) this.audio.setInstrument(bank);
      })
      .catch(() => {
        // Load failed: leave the previously-active bank in place (matches Android),
        // rather than forcing playback back to synth.
      })
      .finally(() => {
        if (this.sound === s) this.soundLoading = false;
        this.notify();
      });
  }

  // ---------- per-sound EQ ----------

  /** `sound`'s current EQ (bass/mid/treble dB). */
  eqFor(sound: SoundName): EqSettings {
    return this.eq[sound];
  }

  /** Update one EQ band for `sound`, persist, and Ã¢â‚¬â€ if `sound` is the active
   *  sound Ã¢â‚¬â€ push the new settings to the audio engine immediately. */
  setEqBand(sound: SoundName, band: EqBand, db: number): void {
    const clamped = Math.min(Math.max(Math.round(db), -12), 12);
    const next: EqSettings = { ...this.eq[sound] };
    if (band === "bass") next.bass = clamped;
    else if (band === "mid") next.mid = clamped;
    else next.treble = clamped;
    this.commit(() => { this.eq[sound] = next; });
    if (sound === this.sound) this.audio.setEq(next.bass, next.mid, next.treble);
  }

  /** Reset `sound`'s EQ to flat (0/0/0). */
  resetEq(sound: SoundName): void {
    const next: EqSettings = { ...FLAT_EQ };
    this.commit(() => { this.eq[sound] = next; });
    if (sound === this.sound) this.audio.setEq(next.bass, next.mid, next.treble);
  }

  // ---------- per-sound reverb ----------

  /** `sound`'s current reverb amount (0..1). */
  reverbFor(sound: SoundName): number {
    return this.reverb[sound];
  }

  /** Update `sound`'s reverb amount, persist, and Ã¢â‚¬â€ if active Ã¢â‚¬â€ push to the engine. */
  setReverb(sound: SoundName, amount: number): void {
    const clamped = Math.max(0, Math.min(1, amount));
    this.commit(() => { this.reverb[sound] = clamped; });
    if (sound === this.sound) this.audio.setReverbSend(clamped);
  }

  // ---------- position scroller ----------

  resetChordPosition(): void { this.chordPositionIndex = 0; }
  resetScalePosition(): void { this.scalePositionIndex = 0; }
  stepChordPosition(delta: number, count: number): void {
    if (count <= 0) return;
    this.commit(() => { this.chordPositionIndex = (((this.chordPositionIndex + delta) % count) + count) % count; });
  }
  stepScalePosition(delta: number, count: number): void {
    if (count <= 0) return;
    this.commit(() => { this.scalePositionIndex = (((this.scalePositionIndex + delta) % count) + count) % count; });
  }

  // ---------- sheets ----------

  openSheet(sheet: Sheet): void {
    this.commit(() => {
      this.currentSheet = sheet;
      this.lastSheet = sheet;
      // Fretboard opens EMPTY until the user picks a chord/scale (no auto-Chord).
    });
  }
  closeSheet(): void { this.commit(() => { this.currentSheet = null; }); }
  reopenLastSheet(): void { if (this.lastSheet) this.openSheet(this.lastSheet); }

  // ---------- audio actions ----------

  tapPosition(pos: FretPosition): void {
    if (pos.stringIndex < 0 || pos.stringIndex >= stringCount(this.liveTuning)) return;
    this.selectedPosition = pos;
    const n = noteAt(this.liveTuning, pos);
    this.audio.playNote(n.midi, this.ringSustainMs, this.timbre);
    this.notify();
  }

  playShape(shape: ChordShape): void {
    const midis = shape.notes.filter((n): n is Note => n !== null).map((n) => n.midi);
    if (midis.length) this.audio.playChord(midis, this.strumMs, this.ringSustainMs, this.timbre);
  }

  playReferencePitch(midi: number): void {
    const freq = midiToFreqA4(midi, this.a4Hz);
    this.audio.playFrequency(freq, this.ringSustainMs, this.timbre);
  }

  // ---------- pick mode ----------

  togglePick(pos: FretPosition): void {
    if (pos.stringIndex < 0 || pos.stringIndex >= stringCount(this.liveTuning)) return;
    this.commit(() => {
      if (this.mutedStrings.has(pos.stringIndex)) this.mutedStrings.delete(pos.stringIndex);
      const key = fpKey(pos);
      if (this.pickedPositions.has(key)) this.pickedPositions.delete(key);
      else this.pickedPositions.add(key);
      this.activeChordSlot = -1;   // hand-edited grip Ã¢â‚¬â€ no slot owns it anymore
    });
  }

  toggleMutedString(stringIdx: number): void {
    if (stringIdx < 0 || stringIdx >= stringCount(this.liveTuning)) return;
    this.commit(() => {
      if (this.mutedStrings.has(stringIdx)) {
        this.mutedStrings.delete(stringIdx);
      } else {
        for (const key of [...this.pickedPositions]) {
          if (parseInt(key.split(",")[0], 10) === stringIdx) this.pickedPositions.delete(key);
        }
        this.mutedStrings.add(stringIdx);
      }
      this.activeChordSlot = -1;
    });
  }

  clearPicked(): void {
    this.commit(() => { this.pickedPositions.clear(); this.mutedStrings.clear(); this.activeChordSlot = -1; });
  }

  // ---------- play mode: sweep-to-strum + quick chord slots ----------

  /** Sweep-to-strum: pluck [stringIdx] with the current grip Ã¢â‚¬â€ the highest picked
   *  fret on that string, or the open string when nothing is picked (like a real
   *  guitar with a partial grip). Returns the fret that sounded, or null when the
   *  string is muted / out of range (silence). */
  pluckString(stringIdx: number): number | null {
    if (stringIdx < 0 || stringIdx >= stringCount(this.liveTuning)) return null;
    if (this.mutedStrings.has(stringIdx)) return null;
    let fret = 0;
    for (const key of this.pickedPositions) {
      const [s, f] = key.split(",").map((x) => parseInt(x, 10));
      if (s === stringIdx && f > fret) fret = f;
    }
    const n = noteAt(this.liveTuning, fp(stringIdx, fret));
    this.audio.playNote(n.midi, this.ringSustainMs, this.timbre);
    return fret;
  }

  /** Apply quick-chord slot [index]: set the chord's first voicing on the board as
   *  the picked grip (+ muted Ã¢Å“â€¢ on unplayed strings), ready to strum. */
  applyChordSlot(index: number): void {
    const symbol = this.chordSlots[index];
    if (symbol === undefined) return;
    const parsed = parseChord(symbol);
    if (!parsed) return;
    const shape = this.chordGenerator().shapesFor(parsed[0], parsed[1], this.liveTuning, DISPLAY_FRETS)[0];
    if (!shape) return;
    this.commit(() => {
      this.pickedPositions = new Set(shape.frets.flatMap((f, s) => (f === null ? [] : [fpKey(fp(s, f))])));
      this.mutedStrings = new Set(shape.frets.flatMap((f, s) => (f === null ? [s] : [])));
      this.activeChordSlot = index;
    });
  }

  /** Reassign quick-chord slot [index] to [symbol] (must parse as a chord); persisted. */
  setChordSlot(index: number, symbol: string): void {
    const trimmed = symbol.trim();
    if (index < 0 || index >= this.chordSlots.length) return;
    if (!parseChord(trimmed)) return;
    this.commit(() => {
      this.chordSlots[index] = trimmed;
      if (this.activeChordSlot === index) this.activeChordSlot = -1;
    });
  }

  /** Strum the current picked grip. `up` = up-strum (high string first, highÃ¢â€ â€™low
   *  pitch); default down-strum (lowÃ¢â€ â€™high). Muted strings excluded. */
  strumPicked(up = false, arpeggio = false): void {
    const positions = [...this.pickedPositions]
      .map((k) => fp(parseInt(k.split(",")[0], 10), parseInt(k.split(",")[1], 10)))
      .filter((p) => p.stringIndex < stringCount(this.liveTuning) && !this.mutedStrings.has(p.stringIndex))
      .sort((a, b) => (a.stringIndex - b.stringIndex) || (a.fret - b.fret));
    if (up) positions.reverse();
    const midis = positions.map((p) => noteAt(this.liveTuning, p).midi);
    if (midis.length) {
      this.audio.playChord(midis, arpeggio ? Math.max(this.strumMs * 4, 100) : this.strumMs, this.ringSustainMs, this.timbre);
    }
  }

  // ---------- derived ----------

  chordGenerator(): ChordShapeGenerator {
    return new ChordShapeGenerator(InstrumentInfo[this.instrument].maxFretSpan, true, 3, this.voicingStyle);
  }
}

// Re-export parseChord for the UI without a separate import line.
export { parseChord, parseNote };
