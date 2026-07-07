package app.guitar.app

import app.guitar.audio.GuitarSample
import app.guitar.audio.SampleInstrument
import app.guitar.audio.WavDecoder
import org.json.JSONArray

/** Loads a bundled guitar bank from assets/guitar/<inst>.json + <inst>_<midi>.wav.
 *  [openAsset] reads an asset path to bytes (supplied by the Activity). Returns null
 *  if the manifest or all samples are missing. Pure of Android APIs beyond the passed opener. */
object GuitarBankLoader {
    fun load(inst: String, openAsset: (String) -> ByteArray?): SampleInstrument? {
        val manifest = openAsset("guitar/$inst.json") ?: return null
        val roots = JSONArray(String(manifest))
        val samples = ArrayList<GuitarSample>()
        for (k in 0 until roots.length()) {
            val midi = roots.getInt(k)
            val wav = openAsset("guitar/${inst}_$midi.wav") ?: continue
            val decoded = WavDecoder.decode(wav) ?: continue
            samples.add(GuitarSample(midi, decoded))
        }
        return if (samples.isEmpty()) null else SampleInstrument(inst, samples)
    }
}
