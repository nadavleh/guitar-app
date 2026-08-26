// Car-mode chord voice for the web. Mirrors app/src/main/kotlin/app/guitar/app/Speaker.kt.
//
// Deliberately dumb and fire-and-forget: earTrainingState calls it with a plain string
// (built by the pure `CarMode.speechFor`, so the words themselves are unit-tested) and
// never has to know whether the browser has a voice. A call the engine cannot serve is
// DROPPED rather than queued, because a chord label spoken three seconds late is worse
// than silence: it would name the wrong bar.
//
// Every utterance cancels the previous one, so a fast tempo cuts the old label off
// instead of building a backlog that drifts further behind the playhead each bar.
//
// The voice is an OVERDUB, not a replacement: it plays at CarMode.SPEECH_VOLUME
// alongside the chord rather than ducking it. speechSynthesis is a separate output from
// the WebAudio graph, so nothing has to be attenuated for the two to overlap.

import { CarMode } from "../theory/carMode";

/** The engine, or null where the browser has none (or blocks it). */
function synth(): SpeechSynthesis | null {
  try {
    return typeof speechSynthesis !== "undefined" ? speechSynthesis : null;
  } catch {
    return null;
  }
}

/** True when an utterance would actually be heard. */
export function speechAvailable(): boolean {
  return synth() !== null && typeof SpeechSynthesisUtterance !== "undefined";
}

/** Speak `text`, cutting off whatever is still sounding. No-op when empty or
 *  unavailable. An empty string is therefore also the "stop talking" call. */
export function speak(text: string): void {
  const s = synth();
  if (!s) return;
  try {
    s.cancel();
    if (!text) return;
    const u = new SpeechSynthesisUtterance(text);
    u.volume = CarMode.SPEECH_VOLUME;
    // Slightly quick: the label has to land inside one bar at 100+ BPM.
    u.rate = 1.15;
    u.lang = "en-US";
    s.speak(u);
  } catch {
    // A browser that refuses to speak (autoplay policy, no voices) just stays silent.
  }
}
