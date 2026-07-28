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

    private val keyChordSlots = stringPreferencesKey("chord_slots")

    /** Play-mode quick-chord slots, comma-joined chord symbols; null when never set
     *  (caller falls back to the built-in defaults). */
    val chordSlots: Flow<List<String>?> =
        context.tuningDataStore.data.map { prefs ->
            prefs[keyChordSlots]?.split(",")?.map { it.trim() }
        }

    suspend fun setChordSlots(slots: List<String>) {
        context.tuningDataStore.edit { prefs ->
            prefs[keyChordSlots] = slots.joinToString(",")
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

    /** Delete every recorded challenge result. */
    suspend fun clearChallengeScores() {
        context.tuningDataStore.edit { prefs -> prefs[keyChallengeScores] = "" }
    }

    /** Delete every result of one [kind]. */
    suspend fun clearChallengeScoresOfKind(kind: String) {
        context.tuningDataStore.edit { prefs ->
            val kept = decodeScores(prefs[keyChallengeScores] ?: "").filter { it.kind != kind }
            prefs[keyChallengeScores] = encodeScores(kept)
        }
    }

    /** Delete one result matching [entry] on all fields (removes a single row). */
    suspend fun deleteChallengeScore(entry: ChallengeScore) {
        context.tuningDataStore.edit { prefs ->
            val current = decodeScores(prefs[keyChallengeScores] ?: "")
            var removed = false
            val kept = current.filter {
                if (!removed && it == entry) { removed = true; false } else true
            }
            prefs[keyChallengeScores] = encodeScores(kept)
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

    // ---------- Progression mistake-drill counts ----------

    private val keyProgMistakes = stringPreferencesKey("progression_mistakes")

    /** progressionKey → number of times the user missed it in a Progression Challenge. */
    val progressionMistakes: Flow<Map<String, Int>> =
        context.tuningDataStore.data.map { prefs -> decodeMistakes(prefs[keyProgMistakes] ?: "") }

    suspend fun recordProgressionMistake(key: String) {
        if (key.isEmpty() || key.any { it in "=;" }) return
        context.tuningDataStore.edit { prefs ->
            val m = decodeMistakes(prefs[keyProgMistakes] ?: "").toMutableMap()
            m[key] = (m[key] ?: 0) + 1
            prefs[keyProgMistakes] = encodeMistakes(m)
        }
    }
    suspend fun clearProgressionMistake(key: String) {
        context.tuningDataStore.edit { prefs ->
            val m = decodeMistakes(prefs[keyProgMistakes] ?: "").toMutableMap()
            m.remove(key)
            prefs[keyProgMistakes] = encodeMistakes(m)
        }
    }
    suspend fun clearProgressionMistakes() {
        context.tuningDataStore.edit { prefs -> prefs[keyProgMistakes] = "" }
    }

    private fun encodeMistakes(m: Map<String, Int>): String =
        m.entries.filter { it.value > 0 }.joinToString(";") { "${it.key}=${it.value}" }
    private fun decodeMistakes(raw: String): Map<String, Int> =
        raw.split(";").mapNotNull { row ->
            if (row.isBlank()) return@mapNotNull null
            val idx = row.lastIndexOf('=')
            if (idx <= 0) return@mapNotNull null
            val v = row.substring(idx + 1).toIntOrNull() ?: return@mapNotNull null
            if (v > 0) row.substring(0, idx) to v else null
        }.toMap()

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

    /** User-saved drum beats (loop + optional opening), by name (insertion order
     *  preserved). */
    val drumPatterns: Flow<Map<String, app.guitar.theory.SavedBeat>> =
        context.tuningDataStore.data.map { prefs -> decodeDrumMap(prefs[keyDrumPatterns] ?: "") }

    /** Save/overwrite a beat under [name]. Names with reserved chars are rejected. */
    suspend fun saveDrumPattern(name: String, beat: app.guitar.theory.SavedBeat) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.any { it in "=;|,~" }) return
        context.tuningDataStore.edit { prefs ->
            val current = decodeDrumMap(prefs[keyDrumPatterns] ?: "")
            val updated = LinkedHashMap(current).apply { put(clean, beat) }
            prefs[keyDrumPatterns] = encodeDrumMap(updated)
        }
    }

    suspend fun deleteDrumPattern(name: String) {
        context.tuningDataStore.edit { prefs ->
            val current = decodeDrumMap(prefs[keyDrumPatterns] ?: "")
            prefs[keyDrumPatterns] = encodeDrumMap(LinkedHashMap(current).apply { remove(name) })
        }
    }

    private val keyDrumBlocks = stringPreferencesKey("drum_blocks")

    /** User-saved drum BLOCKS as RAW encoded lines ([app.guitar.theory.DrumBlock.encode]
     *  values, which embed the name). Decoding happens in BlocksState, where the
     *  custom phrase library is available to resolve user-defined phrase labels. */
    val drumBlockLines: Flow<List<String>> =
        context.tuningDataStore.data.map { prefs ->
            (prefs[keyDrumBlocks] ?: "").split("\n").filter { it.contains('=') }
        }

    /** Save/overwrite a block (keyed by the name embedded before '='). */
    suspend fun saveDrumBlock(encoded: String) {
        val name = encoded.substringBefore('=')
        if (name.isEmpty()) return
        context.tuningDataStore.edit { prefs ->
            val lines = (prefs[keyDrumBlocks] ?: "").split("\n")
                .filter { it.contains('=') && it.substringBefore('=') != name } + encoded
            prefs[keyDrumBlocks] = lines.joinToString("\n")
        }
    }

    suspend fun deleteDrumBlock(name: String) {
        context.tuningDataStore.edit { prefs ->
            val lines = (prefs[keyDrumBlocks] ?: "").split("\n")
                .filter { it.contains('=') && it.substringBefore('=') != name }
            prefs[keyDrumBlocks] = lines.joinToString("\n")
        }
    }

    private val keyDrumTrackPresets = stringPreferencesKey("drum_track_presets")

    /** USER-DEFINED phrases (custom track presets), label → decoded PresetTrack.
     *  Stored one [app.guitar.theory.encodePresetTrack] value per line. */
    val drumTrackPresets: Flow<Map<String, app.guitar.theory.PercussionBuiltins.PresetTrack>> =
        context.tuningDataStore.data.map { prefs ->
            val out = LinkedHashMap<String, app.guitar.theory.PercussionBuiltins.PresetTrack>()
            for (line in (prefs[keyDrumTrackPresets] ?: "").split("\n")) {
                val p = app.guitar.theory.decodePresetTrack(line) ?: continue
                out[p.label] = p
            }
            out
        }

    suspend fun saveDrumTrackPreset(encoded: String) {
        val label = encoded.substringBefore('=')
        if (label.isEmpty()) return
        context.tuningDataStore.edit { prefs ->
            val lines = (prefs[keyDrumTrackPresets] ?: "").split("\n")
                .filter { it.contains('=') && it.substringBefore('=') != label } + encoded
            prefs[keyDrumTrackPresets] = lines.joinToString("\n")
        }
    }

    suspend fun deleteDrumTrackPreset(label: String) {
        context.tuningDataStore.edit { prefs ->
            val lines = (prefs[keyDrumTrackPresets] ?: "").split("\n")
                .filter { it.contains('=') && it.substringBefore('=') != label }
            prefs[keyDrumTrackPresets] = lines.joinToString("\n")
        }
    }

    /** Entries "name=<encodedBeat>" joined by newline. A newline is used (not
     *  ';') because an encoded pattern itself contains ';' and '|'/'=' — only a
     *  newline is guaranteed absent from both encode() output and (single-line)
     *  beat names. The value is [app.guitar.theory.SavedBeat.encode] — the plain
     *  pattern string, or "main~opening" when the beat has an opening. */
    private fun encodeDrumMap(map: Map<String, app.guitar.theory.SavedBeat>): String =
        map.entries.joinToString("\n") { (n, b) -> "$n=${b.encode()}" }

    private fun decodeDrumMap(raw: String): Map<String, app.guitar.theory.SavedBeat> {
        val out = LinkedHashMap<String, app.guitar.theory.SavedBeat>()
        for (entry in raw.split("\n")) {
            val eq = entry.indexOf('=')
            if (eq <= 0) continue
            val name = entry.substring(0, eq)
            val beat = app.guitar.theory.SavedBeat.decode(entry.substring(eq + 1)) ?: continue
            out[name] = beat
        }
        return out
    }
}
