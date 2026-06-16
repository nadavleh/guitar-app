package app.guitar.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.guitar.theory.Tuning
import app.guitar.theory.TuningCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tuningDataStore by preferencesDataStore(name = "guitar_prefs")

/**
 * One progression-challenge result for the high-score table.
 *
 * @param score        bars answered correctly (0..[total])
 * @param total        maximum possible bars (challengeTotal × 4)
 * @param durationMs   wall-clock time taken to finish the challenge
 * @param dateMillis   epoch ms when the challenge finished
 */
data class ChallengeScore(
    val score: Int,
    val total: Int,
    val durationMs: Long,
    val dateMillis: Long,
)

/** Ranking: higher score first; ties broken by the faster (smaller) completion time. */
val CHALLENGE_SCORE_ORDER: Comparator<ChallengeScore> =
    compareByDescending<ChallengeScore> { it.score }.thenBy { it.durationMs }

class TuningRepository(private val context: Context) {
    private val keyCustom = stringPreferencesKey("custom_tunings")
    private val keySelected = stringPreferencesKey("selected_tuning")
    private val keyLeftHanded = booleanPreferencesKey("left_handed")

    val customTunings: Flow<Map<String, Tuning>> =
        context.tuningDataStore.data.map { prefs ->
            runCatching { TuningCodec.decodeMap(prefs[keyCustom] ?: "") }.getOrElse { emptyMap() }
        }

    val selectedTuningName: Flow<String> =
        context.tuningDataStore.data.map { prefs ->
            prefs[keySelected] ?: "Standard"
        }

    suspend fun saveTuning(name: String, tuning: Tuning) {
        context.tuningDataStore.edit { prefs ->
            val current = runCatching {
                TuningCodec.decodeMap(prefs[keyCustom] ?: "")
            }.getOrElse { emptyMap<String, Tuning>() }
            val updated = LinkedHashMap(current).apply { put(name, tuning) }
            prefs[keyCustom] = TuningCodec.encodeMap(updated)
        }
    }

    suspend fun deleteTuning(name: String) {
        context.tuningDataStore.edit { prefs ->
            val current = runCatching {
                TuningCodec.decodeMap(prefs[keyCustom] ?: "")
            }.getOrElse { emptyMap<String, Tuning>() }
            val updated = LinkedHashMap(current).apply { remove(name) }
            prefs[keyCustom] = TuningCodec.encodeMap(updated)
        }
    }

    suspend fun setSelected(name: String) {
        context.tuningDataStore.edit { prefs ->
            prefs[keySelected] = name
        }
    }

    val leftHanded: Flow<Boolean> =
        context.tuningDataStore.data.map { prefs -> prefs[keyLeftHanded] ?: false }

    suspend fun setLeftHanded(value: Boolean) {
        context.tuningDataStore.edit { prefs ->
            prefs[keyLeftHanded] = value
        }
    }

    private val keyVoicingShell = booleanPreferencesKey("voicing_shell")

    val voicingShell: Flow<Boolean> =
        context.tuningDataStore.data.map { prefs -> prefs[keyVoicingShell] ?: false }

    suspend fun setVoicingShell(value: Boolean) {
        context.tuningDataStore.edit { prefs ->
            prefs[keyVoicingShell] = value
        }
    }

    private val keyLabelMode = stringPreferencesKey("label_mode")

    val labelMode: Flow<String> =
        context.tuningDataStore.data.map { prefs -> prefs[keyLabelMode] ?: "Intervals" }

    suspend fun setLabelMode(value: String) {
        context.tuningDataStore.edit { prefs ->
            prefs[keyLabelMode] = value
        }
    }

    private val keyA4 = floatPreferencesKey("a4_hz")

    /** Reference A4 frequency in Hz (default 440). Range 435..445 in UI. */
    val a4Hz: Flow<Float> =
        context.tuningDataStore.data.map { prefs -> prefs[keyA4] ?: 440f }

    suspend fun setA4Hz(value: Float) {
        context.tuningDataStore.edit { prefs ->
            prefs[keyA4] = value
        }
    }

    private val keyRingSustain = intPreferencesKey("ring_sustain_ms")

    /** Ring sustain in milliseconds (default 1500 = 1.5 s). */
    val ringSustainMs: Flow<Int> =
        context.tuningDataStore.data.map { prefs -> prefs[keyRingSustain] ?: 1500 }

    suspend fun setRingSustainMs(value: Int) {
        context.tuningDataStore.edit { prefs ->
            prefs[keyRingSustain] = value
        }
    }

    private val keyStrumMs = intPreferencesKey("strum_ms")

    /** Strum/arpeggio spread in ms between consecutive chord notes (default 30). */
    val strumMs: Flow<Int> =
        context.tuningDataStore.data.map { prefs -> prefs[keyStrumMs] ?: 30 }

    suspend fun setStrumMs(value: Int) {
        context.tuningDataStore.edit { prefs ->
            prefs[keyStrumMs] = value
        }
    }

    private val keyTapOnTouchDown = booleanPreferencesKey("tap_on_touch_down")

    /** Whether tapping the fretboard plays on touch-down (true) or tap-release
     *  (false, default — lets horizontal swipes scroll without sounding a note). */
    val tapOnTouchDown: Flow<Boolean> =
        context.tuningDataStore.data.map { prefs -> prefs[keyTapOnTouchDown] ?: false }

    suspend fun setTapOnTouchDown(value: Boolean) {
        context.tuningDataStore.edit { prefs -> prefs[keyTapOnTouchDown] = value }
    }

    private val keyInstrument = stringPreferencesKey("instrument")

    /** Selected instrument (Guitar / Cavaquinho). Default Guitar. */
    val instrument: Flow<String> =
        context.tuningDataStore.data.map { prefs ->
            prefs[keyInstrument] ?: app.guitar.theory.Instrument.Guitar.name
        }

    suspend fun setInstrument(value: String) {
        context.tuningDataStore.edit { prefs -> prefs[keyInstrument] = value }
    }

    // ---------- Progression-challenge high scores ----------

    private val keyChallengeScores = stringPreferencesKey("challenge_scores")
    private val maxScoresKept = 10

    /** Top progression-challenge results, best first (score desc, time asc). */
    val challengeScores: Flow<List<ChallengeScore>> =
        context.tuningDataStore.data.map { prefs ->
            decodeScores(prefs[keyChallengeScores] ?: "")
        }

    /** Insert a result, keep the top [maxScoresKept] by [CHALLENGE_SCORE_ORDER]. */
    suspend fun addChallengeScore(entry: ChallengeScore) {
        context.tuningDataStore.edit { prefs ->
            val current = decodeScores(prefs[keyChallengeScores] ?: "")
            val updated = (current + entry).sortedWith(CHALLENGE_SCORE_ORDER).take(maxScoresKept)
            prefs[keyChallengeScores] = encodeScores(updated)
        }
    }

    /** Serialize as "score,total,durationMs,dateMillis" rows joined by ';'. */
    private fun encodeScores(list: List<ChallengeScore>): String =
        list.joinToString(";") { "${it.score},${it.total},${it.durationMs},${it.dateMillis}" }

    private fun decodeScores(raw: String): List<ChallengeScore> =
        raw.split(";").mapNotNull { row ->
            val p = row.split(",")
            if (p.size != 4) return@mapNotNull null
            val s = p[0].toIntOrNull() ?: return@mapNotNull null
            val t = p[1].toIntOrNull() ?: return@mapNotNull null
            val d = p[2].toLongOrNull() ?: return@mapNotNull null
            val dt = p[3].toLongOrNull() ?: return@mapNotNull null
            ChallengeScore(s, t, d, dt)
        }.sortedWith(CHALLENGE_SCORE_ORDER)

    // ---------- Saved drum-machine beats ----------

    private val keyDrumPatterns = stringPreferencesKey("drum_patterns")

    /** User-saved drum patterns, by name (insertion order preserved). */
    val drumPatterns: Flow<Map<String, app.guitar.theory.PercussionPattern>> =
        context.tuningDataStore.data.map { prefs -> decodeDrumMap(prefs[keyDrumPatterns] ?: "") }

    /** Save/overwrite a beat under [name]. Names with reserved chars are rejected. */
    suspend fun saveDrumPattern(name: String, pattern: app.guitar.theory.PercussionPattern) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.any { it in "=;|," }) return
        context.tuningDataStore.edit { prefs ->
            val current = decodeDrumMap(prefs[keyDrumPatterns] ?: "")
            val updated = LinkedHashMap(current).apply { put(clean, pattern) }
            prefs[keyDrumPatterns] = encodeDrumMap(updated)
        }
    }

    suspend fun deleteDrumPattern(name: String) {
        context.tuningDataStore.edit { prefs ->
            val current = decodeDrumMap(prefs[keyDrumPatterns] ?: "")
            prefs[keyDrumPatterns] = encodeDrumMap(LinkedHashMap(current).apply { remove(name) })
        }
    }

    /** Entries "name=<encodedPattern>" joined by ';'. */
    private fun encodeDrumMap(map: Map<String, app.guitar.theory.PercussionPattern>): String =
        map.entries.joinToString(";") { (n, p) -> "$n=${p.encode()}" }

    private fun decodeDrumMap(raw: String): Map<String, app.guitar.theory.PercussionPattern> {
        val out = LinkedHashMap<String, app.guitar.theory.PercussionPattern>()
        for (entry in raw.split(";")) {
            val eq = entry.indexOf('=')
            if (eq <= 0) continue
            val name = entry.substring(0, eq)
            val pattern = app.guitar.theory.PercussionPattern.decode(entry.substring(eq + 1)) ?: continue
            out[name] = pattern
        }
        return out
    }
}
