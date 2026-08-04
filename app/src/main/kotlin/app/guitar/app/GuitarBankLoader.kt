package app.guitar.app

import app.guitar.audio.GuitarSample
import app.guitar.audio.SampleInstrument
import app.guitar.audio.WavDecoder
import org.json.JSONArray

/** Loads a bundled guitar bank from assets/guitar/<inst>.json + <inst>_<midi>.wav.
 *  [openAsset] reads an asset path to bytes (supplied by the Activity). Returns null
 *  if the manifest or all samples are missing. Pure of Android APIs beyond the passed opener.
 *
 *  [targetRate] must be the engine's sample rate: [SampleInstrument] treats its buffers as
 *  being at the engine rate and pitch-shifts by playback ratio, so a bank decoded at the
 *  wrong rate sounds detuned across the whole instrument. */
object GuitarBankLoader {
    fun load(inst: String, targetRate: Int, openAsset: (String) -> ByteArray?): SampleInstrument? {
        val manifest = openAsset("guitar/$inst.json") ?: return null
        // A corrupt/malformed manifest is treated like a missing one (fall back to synth),
        // not a crash out of the loader.
        val roots = runCatching { JSONArray(String(manifest)) }.getOrNull() ?: return null
        val samples = ArrayList<GuitarSample>()
        for (k in 0 until roots.length()) {
            val midi = roots.getInt(k)
            val wav = openAsset("guitar/${inst}_$midi.wav") ?: continue
            val decoded = WavDecoder.decode(wav, targetRate) ?: continue
            samples.add(GuitarSample(midi, decoded))
        }
        return if (samples.isEmpty()) null else SampleInstrument(inst, samples)
    }
}
