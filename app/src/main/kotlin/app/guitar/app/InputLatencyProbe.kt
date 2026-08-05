package app.guitar.app

import android.os.SystemClock

/**
 * Records how long a touch-down waited between the finger physically landing and the app's
 * gesture handler running.
 *
 * Touch events are delivered on the UI thread, so a slow frame delays them before the audio
 * engine is even asked for a note — a delay no amount of engine tuning can explain or fix.
 * Isolating it matters: it says whether "the tap sounds late" is an audio problem or a
 * rendering problem.
 *
 * A tiny process-global rather than plumbing a parameter through every FretboardView call
 * site: it is pure diagnostics, written on the UI thread and read by the Settings panel.
 */
object InputLatencyProbe {

    /** Dispatch delay of the most recent touch-down, in ms; -1 before the first tap. */
    @Volatile
    var lastDispatchMs: Long = -1L
        private set

    /** Worst dispatch delay seen this session — jank is intermittent, so the peak is the
     *  number that explains an occasional late-feeling tap. */
    @Volatile
    var worstDispatchMs: Long = -1L
        private set

    /** [eventUptimeMillis] is the pointer event's own timestamp (same clock as
     *  [SystemClock.uptimeMillis]). */
    fun record(eventUptimeMillis: Long) {
        val delay = (SystemClock.uptimeMillis() - eventUptimeMillis).coerceAtLeast(0)
        lastDispatchMs = delay
        if (delay > worstDispatchMs) worstDispatchMs = delay
    }

    fun reset() {
        lastDispatchMs = -1L
        worstDispatchMs = -1L
    }
}
