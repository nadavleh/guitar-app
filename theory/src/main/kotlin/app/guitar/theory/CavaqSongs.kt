package app.guitar.theory

/**
 * Curated famous samba songs (CifraClub "mais acessadas / samba", top 30) tagged by the
 * FUNCTIONAL FAMILY of their core progression. Feeds the "Songs" button on the cavaquinho
 * Progressions screen: a taught [CavaqSequence] maps to one or more families, and
 * [forSequence] returns the songs in those families. Pure data — see
 * docs/cavaquinho-samba-songs.md for the per-song functional reductions (reviewable).
 */
data class CavaqSong(val title: String, val artist: String, val keyLabel: String, val family: String)

object CavaqSongs {

    val ALL: List<CavaqSong> = listOf(
        CavaqSong("O Mundo É Um Moinho", "Cartola", "A", "ii-V-I"),
        CavaqSong("Eu e Você Sempre", "Jorge Aragão", "E", "IV-iv"),
        CavaqSong("Meu Lugar", "Arlindo Cruz", "Am", "circle"),
        CavaqSong("Disritmia", "Martinho da Vila", "Gm", "circle"),
        CavaqSong("Nos Braços da Batucada", "Arlindo Cruz", "C", "I-vi-ii-V"),
        CavaqSong("Deixa a Vida Me Levar", "Zeca Pagodinho", "E", "ii-V-I"),
        CavaqSong("Trem das Onze", "Adoniran Barbosa", "Am", "minor-cadence"),
        CavaqSong("Fulminante", "Mumuzinho", "E", "I-IV-vamp"),
        CavaqSong("Lucidez", "Jorge Aragão", "E", "ii-V-I"),
        CavaqSong("Problema Emocional", "Reinaldo", "D", "circle"),
        CavaqSong("Preciso Me Encontrar", "Cartola", "Dm", "minor-cadence"),
        CavaqSong("Água de Chuva No Mar", "Beth Carvalho", "C", "ii-V-I"),
        CavaqSong("Retalhos de Cetim", "Benito Di Paula", "Am", "minor-cadence"),
        CavaqSong("Laços do Amor", "Grupo Fundo de Quintal", "C", "I-vi-ii-V"),
        CavaqSong("O Show Tem Que Continuar", "Grupo Fundo de Quintal", "C", "ii-V-I"),
        CavaqSong("Enredo Do Meu Samba", "Jorge Aragão", "A", "circle"),
        CavaqSong("Antigas Paixões", "Grupo Fundo de Quintal", "D", "IV-iv"),
        CavaqSong("Não Deixe O Samba Morrer", "Alcione", "Bm", "minor-cadence"),
        CavaqSong("Ezequiel 47", "Thiago Brito", "Gm", "minor-vamp"),
        CavaqSong("Você Me Vira a Cabeça", "Alcione", "F#m", "circle"),
        CavaqSong("Já é", "Jorge Aragão", "D", "ii-V-I"),
        CavaqSong("Se a Fila Andar", "Toninho Geraes", "Bb", "ii-V-I"),
        CavaqSong("Ah! Como Eu Amei", "Benito Di Paula", "Bb", "ii-V-I"),
        CavaqSong("O Bem", "Arlindo Cruz", "D", "I-vi-ii-V"),
        CavaqSong("Iracema", "Adoniran Barbosa", "Bm", "minor-cadence"),
        CavaqSong("Mais Feliz", "Zeca Pagodinho", "G", "IV-iv"),
        CavaqSong("As Rosas Não Falam", "Cartola", "Dm", "minor-cadence"),
        CavaqSong("Tiro ao Álvaro", "Adoniran Barbosa", "Bb", "I-vi-ii-V"),
        CavaqSong("Será Que É Amor", "Arlindo Cruz", "A", "ii-V-I"),
        CavaqSong("Carinhoso", "Pixinguinha", "G", "ii-V-I"),
    )

    /** Functional families each taught sequence (by id) covers. */
    private val SEQUENCE_FAMILIES: Map<String, Set<String>> = mapOf(
        "quadradinho_maj" to setOf("ii-V-I", "I-vi-ii-V"),
        "basic_min" to setOf("minor-cadence"),
        "medio_maj" to setOf("ii-V-I", "circle"),
        "medio_min" to setOf("minor-cadence", "circle"),
        "campo_maj" to emptySet(),
    )

    /** Curated songs whose functional family matches the given sequence id. */
    fun forSequence(sequenceId: String): List<CavaqSong> {
        val fams = SEQUENCE_FAMILIES[sequenceId] ?: emptySet()
        return ALL.filter { it.family in fams }
    }
}
