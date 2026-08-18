// Screen Wake Lock — keeps the display awake while a hands-free exercise runs.
//
// The API is HTTPS-only (the site is on GitHub Pages, so that's fine) and missing on
// older Safari, so every call is guarded: a browser without it still runs car mode,
// it just lets the screen sleep. Requesting must happen inside a user gesture.

/** The subset of WakeLockSentinel we use. TS 5.6's lib.dom may not declare it. */
export interface WakeLockHandle { release(): Promise<void> }

type NavWithWakeLock = Navigator & {
  wakeLock?: { request(type: "screen"): Promise<WakeLockHandle> };
};

/** Request a screen wake lock, or null if unsupported/denied. Never throws. */
export async function acquireWakeLock(): Promise<WakeLockHandle | null> {
  try {
    const wl = (navigator as NavWithWakeLock).wakeLock;
    if (!wl) return null;
    return await wl.request("screen");
  } catch {
    // Denied (e.g. the document isn't visible) or unsupported — not an error here.
    return null;
  }
}

/** Release a lock if we hold one. Never throws. */
export async function releaseWakeLock(handle: WakeLockHandle | null): Promise<void> {
  if (!handle) return;
  try { await handle.release(); } catch { /* already released */ }
}
