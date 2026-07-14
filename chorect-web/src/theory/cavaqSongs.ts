// Curated famous samba songs (CifraClub "mais acessadas / samba", top 30) tagged by the
// FUNCTIONAL FAMILY of their core progression. Mirror of theory/CavaqSongs.kt. Feeds the
// "Songs" button on the cavaquinho Progressions screen. See docs/cavaquinho-samba-songs.md.

export interface CavaqSong { title: string; artist: string; keyLabel: string; family: string; }

export const CAVAQ_SONGS: CavaqSong[] = [
  { title: "O Mundo É Um Moinho", artist: "Cartola", keyLabel: "A", family: "ii-V-I" },
  { title: "Eu e Você Sempre", artist: "Jorge Aragão", keyLabel: "E", family: "IV-iv" },
  { title: "Meu Lugar", artist: "Arlindo Cruz", keyLabel: "Am", family: "circle" },
  { title: "Disritmia", artist: "Martinho da Vila", keyLabel: "Gm", family: "circle" },
  { title: "Nos Braços da Batucada", artist: "Arlindo Cruz", keyLabel: "C", family: "I-vi-ii-V" },
  { title: "Deixa a Vida Me Levar", artist: "Zeca Pagodinho", keyLabel: "E", family: "ii-V-I" },
  { title: "Trem das Onze", artist: "Adoniran Barbosa", keyLabel: "Am", family: "minor-cadence" },
  { title: "Fulminante", artist: "Mumuzinho", keyLabel: "E", family: "I-IV-vamp" },
  { title: "Lucidez", artist: "Jorge Aragão", keyLabel: "E", family: "ii-V-I" },
  { title: "Problema Emocional", artist: "Reinaldo", keyLabel: "D", family: "circle" },
  { title: "Preciso Me Encontrar", artist: "Cartola", keyLabel: "Dm", family: "minor-cadence" },
  { title: "Água de Chuva No Mar", artist: "Beth Carvalho", keyLabel: "C", family: "ii-V-I" },
  { title: "Retalhos de Cetim", artist: "Benito Di Paula", keyLabel: "Am", family: "minor-cadence" },
  { title: "Laços do Amor", artist: "Grupo Fundo de Quintal", keyLabel: "C", family: "I-vi-ii-V" },
  { title: "O Show Tem Que Continuar", artist: "Grupo Fundo de Quintal", keyLabel: "C", family: "ii-V-I" },
  { title: "Enredo Do Meu Samba", artist: "Jorge Aragão", keyLabel: "A", family: "circle" },
  { title: "Antigas Paixões", artist: "Grupo Fundo de Quintal", keyLabel: "D", family: "IV-iv" },
  { title: "Não Deixe O Samba Morrer", artist: "Alcione", keyLabel: "Bm", family: "minor-cadence" },
  { title: "Ezequiel 47", artist: "Thiago Brito", keyLabel: "Gm", family: "minor-vamp" },
  { title: "Você Me Vira a Cabeça", artist: "Alcione", keyLabel: "F#m", family: "circle" },
  { title: "Já é", artist: "Jorge Aragão", keyLabel: "D", family: "ii-V-I" },
  { title: "Se a Fila Andar", artist: "Toninho Geraes", keyLabel: "Bb", family: "ii-V-I" },
  { title: "Ah! Como Eu Amei", artist: "Benito Di Paula", keyLabel: "Bb", family: "ii-V-I" },
  { title: "O Bem", artist: "Arlindo Cruz", keyLabel: "D", family: "I-vi-ii-V" },
  { title: "Iracema", artist: "Adoniran Barbosa", keyLabel: "Bm", family: "minor-cadence" },
  { title: "Mais Feliz", artist: "Zeca Pagodinho", keyLabel: "G", family: "IV-iv" },
  { title: "As Rosas Não Falam", artist: "Cartola", keyLabel: "Dm", family: "minor-cadence" },
  { title: "Tiro ao Álvaro", artist: "Adoniran Barbosa", keyLabel: "Bb", family: "I-vi-ii-V" },
  { title: "Será Que É Amor", artist: "Arlindo Cruz", keyLabel: "A", family: "ii-V-I" },
  { title: "Carinhoso", artist: "Pixinguinha", keyLabel: "G", family: "ii-V-I" },
];

/** Functional families each taught sequence (by id) covers. */
const SEQUENCE_FAMILIES: Record<string, string[]> = {
  quadradinho_maj: ["ii-V-I", "I-vi-ii-V"],
  ii_v_i_maj: ["ii-V-I"],
  basic_min: ["minor-cadence"],
  medio_maj: ["ii-V-I", "circle"],
  campo_maj: [],
};

/** Curated songs whose functional family matches the given sequence id. */
export function cavaqSongsForSequence(sequenceId: string): CavaqSong[] {
  const fams = SEQUENCE_FAMILIES[sequenceId] ?? [];
  return CAVAQ_SONGS.filter((s) => fams.includes(s.family));
}
