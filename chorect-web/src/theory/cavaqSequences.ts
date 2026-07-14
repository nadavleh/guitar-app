// Cavaquinho functional chord sequences (Betto Correa). Mirror of the Kotlin
// CavaqSequences — one functional template per distinct sequence, in C, transposed
// via resolveNamed(). AdvChord = semitone-above-tonic + quality + roman.
import { AdvChord, NamedProgression, TrainingMode } from "./eartraining";

export interface CavaqSequence {
  id: string;
  namePt: string;
  nameEn: string;
  prog: NamedProgression;
}

const ac = (semitone: number, quality: string, roman: string): AdvChord => ({ semitone, quality, roman });
const seq = (id: string, pt: string, en: string, mode: TrainingMode, chords: AdvChord[]): CavaqSequence =>
  ({ id, namePt: pt, nameEn: en, prog: { name: pt, explanation: en, tonicMode: mode, chords } });

export const CAVAQ_SEQUENCES: CavaqSequence[] = [
  seq("quadradinho_maj", "Quadradinho (Maior)", "Quadradinho (Major)", TrainingMode.Major,
    [ac(0, "", "I"), ac(9, "7", "VI7"), ac(2, "m", "ii"), ac(7, "7", "V7")]),
  seq("basic_min", "Sequência Menor (Básico)", "Basic Minor", TrainingMode.Minor,
    [ac(0, "m", "i"), ac(0, "7", "I7"), ac(5, "m", "iv"), ac(7, "7", "V7")]),
  seq("medio_maj", "Sequência Médio (Maior)", "Extended Major (Médio)", TrainingMode.Major,
    [ac(0, "", "I"), ac(9, "7", "VI7"), ac(2, "m", "ii"), ac(7, "7", "V7"),
     ac(7, "m", "v"), ac(0, "7", "I7"), ac(5, "", "IV"), ac(5, "m", "iv"),
     ac(4, "m", "iii"), ac(9, "7", "VI7"), ac(2, "m", "ii"), ac(7, "7", "V7"), ac(0, "", "I")]),
  seq("ii_v_i_maj", "II–V–I Maior", "II–V–I (Major)", TrainingMode.Major,
    [ac(2, "m7", "ii7"), ac(7, "7", "V7"), ac(0, "maj7", "Imaj7")]),
  seq("campo_maj", "Campo Harmônico Maior", "Harmonic Field (Major)", TrainingMode.Major,
    [ac(0, "", "I"), ac(2, "m", "ii"), ac(4, "m", "iii"), ac(5, "", "IV"),
     ac(7, "", "V"), ac(9, "m", "vi"), ac(11, "dim", "vii°")]),
];

export function cavaqSequenceById(id: string): CavaqSequence | undefined {
  return CAVAQ_SEQUENCES.find((s) => s.id === id);
}
