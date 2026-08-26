package app.guitar.app

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import app.guitar.theory.CarMode
import java.util.Locale

/**
 * Thin wrapper over Android's [TextToSpeech] for the car-mode chord voice.
 *
 * Deliberately dumb and fire-and-forget: the state layer calls [say] with a plain
 * string (built by the pure `CarMode.speechFor`, so the words themselves are
 * unit-tested) and never has to know whether the engine has finished starting up.
 * A call made before the engine is ready — or on a device with no TTS engine at all —
 * is dropped rather than queued, because a chord label spoken three seconds late is
 * worse than silence: it would name the wrong bar.
 *
 * Every utterance uses QUEUE_FLUSH, so a fast tempo cuts the previous label off
 * instead of building a backlog that drifts further behind the playhead each bar.
 *
 * The voice is an OVERDUB, not a replacement: it goes out on STREAM_MUSIC alongside the
 * chord instead of ducking it, and no audio focus is requested (which is what would make
 * the looper duck). Its level is the caller's [say] argument — a user-facing slider, not
 * a fixed constant, because "under the music" and "audible in a moving car" turned out
 * not to be the same level.
 */
class Speaker(context: Context) {

    /** Flipped by the init callback, which may run on another thread. */
    @Volatile private var ready = false

    /** The language is set on the first [say], not in the init callback: that callback
     *  can fire before the constructor has finished assigning [tts], and touching a
     *  half-constructed engine is exactly the crash this avoids. */
    @Volatile private var languageSet = false

    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    /** True when an utterance would actually be heard. */
    val available: Boolean get() = ready

    /** Speak [text] at [volume] (0..1, clamped), cutting off whatever is still sounding.
     *  No-op when empty or when the engine is not (yet) usable. */
    fun say(text: String, volume: Float) {
        if (text.isEmpty() || !ready) return
        if (!languageSet) {
            languageSet = true
            val r = runCatching { tts.setLanguage(Locale.US) }.getOrNull()
            // The labels are English words plus bare digits ("flat 6 major 7"); with no
            // English voice installed there is nothing sensible to say.
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                ready = false
                return
            }
        }
        // A fresh Bundle per utterance: the level can change between two chords.
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, CarMode.clampSpeechVolume(volume))
        }
        runCatching { tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "chorect-car") }
    }

    /** Stop any utterance in flight without tearing the engine down. */
    fun stop() {
        if (!ready) return
        runCatching { tts.stop() }
    }

    /** Release the engine. Called from the Activity's onDestroy — a leaked
     *  TextToSpeech keeps a bound service alive for the whole process. */
    fun close() {
        ready = false
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
    }
}
