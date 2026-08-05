/**
 * Records how long a touch-down waited between the browser timestamping the event and our
 * handler running (mirror of Android's InputLatencyProbe).
 *
 * Pointer events are dispatched on the main thread, so a slow frame delays the note before
 * the audio graph is involved at all — latency no amount of audio tuning can explain. Keeping
 * it separate says whether a late-feeling tap is an audio problem or a rendering one.
 */
let lastDispatchMs = -1;
let worstDispatchMs = -1;

export function recordInputDispatch(delayMs: number): void {
  const d = Math.max(0, delayMs);
  lastDispatchMs = d;
  if (d > worstDispatchMs) worstDispatchMs = d;
}

export function inputDispatchReport(): { lastMs: number; worstMs: number } {
  return { lastMs: lastDispatchMs, worstMs: worstDispatchMs };
}
