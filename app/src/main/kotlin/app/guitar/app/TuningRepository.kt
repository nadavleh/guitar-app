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
        context.tuningDataStore.data.map { prefs -> prefs[keyLabelMode] ?: "Notes" }

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
}
