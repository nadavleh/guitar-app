package app.guitar.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
}
