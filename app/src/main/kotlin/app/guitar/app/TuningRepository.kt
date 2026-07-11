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
    /** Which trainer produced this result ("progression", "inversions", "augdim",
     *  "flavor", "intervals", "note2chord"). Legacy rows decode as "progression". */
    val kind: String = "progression",
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

    /** Legacy pre-Signal boolean theme pref — no longer read/written directly
     *  (superseded by [keyThemeMode]); kept only as [themeMode]'s migration
     *  fallback for installs from before that pref existed. */
    private val keyDarkTheme = booleanPreferencesKey("dark_theme")

    private val keyThemeMode = stringPreferencesKey("theme_mode")

    /** UI theme mode: "dark" / "light" / "auto" (Settings → Personalize). Migration-
     *  safe: if `theme_mode` was never written (installs from before this pref
     *  existed), fall back to the old [keyDarkTheme] boolean mapped onto "dark"/
     *  "light" rather than defaulting blindly to dark. */
    val themeMode: Flow<String> =
        context.tuningDataStore.data.map { prefs ->
            // Never-configured installs default to LIGHT (user decision, v2.1.0);
            // pre-theme_mode installs keep whatever dark_theme they had chosen.
            prefs[keyThemeMode] ?: prefs[keyDarkTheme]?.let { if (it) "dark" else "light" } ?: "light"
        }

    suspend fun setThemeMode(value: String) {
        context.tuningDataStore.edit { prefs -> prefs[keyThemeMode] = value }
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
        context.tuningDataStore.data.map { prefs -> prefs[keyTapOnTouchDown] ?: true }

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

    private val keyGuitarSound = stringPreferencesKey("guitar_sound")

    /** Selected guitar voice/timbre (GuitarSound enum name). Default "Synth". */
    val guitarSound: Flow<String> =
        context.tuningDataStore.data.map { prefs -> prefs[keyGuitarSound] ?: "Synth" }

    suspend fun setGuitarSound(value: String) {
        context.tuningDataStore.edit { prefs -> prefs[keyGuitarSound] = value }
    }

    private val keyGuitarEq = stringPreferencesKey("guitar_eq")

    /** Encoded per-sound EQ ("Name,bass,mid,treble;..."). Empty = use code defaults. */
    val guitarEq: Flow<String> =
        context.tuningDataStore.data.map { prefs -> prefs[keyGuitarEq] ?: "" }

    suspend fun setGuitarEq(value: String) {
        context.tuningDataStore.edit { prefs -> prefs[keyGuitarEq] = value }
    }

    private val keyAccent = stringPreferencesKey("accent")

    /** Selected ACT accent (Accent enum name). Default "Coral". */
    val accent: Flow<String> =
        context.tuningDataStore.data.map { prefs -> prefs[keyAccent] ?: Accent.Coral.name }

    suspend fun setAccent(value: String) {
        context.tuningDataStore.edit { prefs -> prefs[keyAccent] = value }
    }

    private val keyTabOrder = stringPreferencesKey("tab_order")

    /** Configured bottom-tab set + order, comma-joined (e.g. "Neck,Ear,Rhythm,Tuner").
     *  "More" is fixed and not part of this list. Consumed by the Signal tab bar (M3). */
    val tabOrder: Flow<String> =
        context.tuningDataStore.data.map { prefs -> prefs[keyTabOrder] ?: "Neck,Ear,Rhythm,Tuner" }

    suspend fun setTabOrder(value: String) {
        context.tuningDataStore.edit { prefs -> prefs[keyTabOrder] = value }
    }

    private val keyGuitarReverb = stringPreferencesKey("guitar_reverb")

    /** Encoded per-sound reverb amount ("Name,amount;..."). Empty = use code defaults. */
    val guitarReverb: Flow<String> =
        context.tuningDataStore.data.map { prefs -> prefs[keyGuitarReverb] ?: "" }

    suspend fun setGuitarReverb(value: String) {
        context.tuningDataStore.edit { prefs -> prefs[keyGuitarReverb] = value }
    }

    // ---------- Progression-challenge high scores ----------

    private val keyChallengeScores = stringPreferencesKey("challenge_scores")
    private val maxScoresKept = 10

    /** Top progression-challenge results, best first (score desc, time asc). */
    val challengeScores: Flow<List<ChallengeScore>> =
        context.tuningDataStore.data.map { prefs ->
            decodeScores(prefs[keyChallengeScores] ?: "")
        }

    /** Insert a result; keep the top [maxScoresKept] PER KIND by [CHALLENGE_SCORE_ORDER]. */
    suspend fun addChallengeScore(entry: ChallengeScore) {
        context.tuningDataStore.edit { prefs ->
            val current = decodeScores(prefs[keyChallengeScores] ?: "")
            val updated = (current + entry)
                .groupBy { it.kind }
                .flatMap { (_, rows) -> rows.sortedWith(CHALLENGE_SCORE_ORDER).take(maxScoresKept) }
            prefs[keyChallengeScores] = encodeScores(updated)
        }
    }

    /** Serialize as "score,total,durationMs,dateMillis,kind" rows joined by ';'
     *  (kind added later — 4-field legacy rows decode as "progression"). */
    private fun encodeScores(list: List<ChallengeScore>): String =
        list.joinToString(";") { "${it.score},${it.total},${it.durationMs},${it.dateMillis},${it.kind}" }

    private fun decodeScores(raw: String): List<ChallengeScore> =
        raw.split(";").mapNotNull { row ->
            val p = row.split(",")
            if (p.size !in 4..5) return@mapNotNull null
            val s = p[0].toIntOrNull() ?: return@mapNotNull null
            val t = p[1].toIntOrNull() ?: return@mapNotNull null
            val d = p[2].toLongOrNull() ?: return@mapNotNull null
            val dt = p[3].toLongOrNull() ?: return@mapNotNull null
            ChallengeScore(s, t, d, dt, p.getOrNull(4) ?: "progression")
        }.sortedWith(CHALLENGE_SCORE_ORDER)

    // ---------- Drum-machine mixer volumes ----------

    private val keyDrumVolumes = stringPreferencesKey("drum_volumes")

    /** Per-instrument and per-voice playback volumes, keyed by "<instId>" (global)
     *  or "<instId>:<voiceIndex>" (single voice); value in 0f..1f. Absent keys use
     *  their code default (1f, or 0.5f for the two soft tamborim voices). Persisted
     *  so the mix survives closing the app. */
    val drumVolumes: Flow<Map<String, Float>> =
        context.tuningDataStore.data.map { prefs -> decodeVolumes(prefs[keyDrumVolumes] ?: "") }

    /** Set one volume entry (global or per-voice) and persist the whole map. */
    suspend fun setDrumVolume(key: String, value: Float) {
        if (key.isEmpty() || key.any { it in "=;" }) return
        context.tuningDataStore.edit { prefs ->
            val current = decodeVolumes(prefs[keyDrumVolumes] ?: "").toMutableMap()
            current[key] = value.coerceIn(0f, 1f)
            prefs[keyDrumVolumes] = encodeVolumes(current)
        }
    }

    /** Entries "key=value" joined by ';'. Keys never contain '=' or ';'. */
    private fun encodeVolumes(map: Map<String, Float>): String =
        map.entries.joinToString(";") { (k, v) -> "$k=$v" }

    private fun decodeVolumes(raw: String): Map<String, Float> {
        val out = LinkedHashMap<String, Float>()
        for (entry in raw.split(";")) {
            val eq = entry.indexOf('=')
            if (eq <= 0) continue
            val value = entry.substring(eq + 1).toFloatOrNull() ?: continue
            out[entry.substring(0, eq)] = value.coerceIn(0f, 1f)
        }
        return out
    }

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

    /** Entries "name=<encodedPattern>" joined by newline. A newline is used (not
     *  ';') because an encoded pattern itself contains ';' and '|'/'=' — only a
     *  newline is guaranteed absent from both encode() output and (single-line)
     *  beat names. */
    private fun encodeDrumMap(map: Map<String, app.guitar.theory.PercussionPattern>): String =
        map.entries.joinToString("\n") { (n, p) -> "$n=${p.encode()}" }

    private fun decodeDrumMap(raw: String): Map<String, app.guitar.theory.PercussionPattern> {
        val out = LinkedHashMap<String, app.guitar.theory.PercussionPattern>()
        for (entry in raw.split("\n")) {
            val eq = entry.indexOf('=')
            if (eq <= 0) continue
            val name = entry.substring(0, eq)
            val pattern = app.guitar.theory.PercussionPattern.decode(entry.substring(eq + 1)) ?: continue
            out[name] = pattern
        }
        return out
    }
}
