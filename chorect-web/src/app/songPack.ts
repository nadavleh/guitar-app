// Song pack loading + caching.
//
// The pack is a directory the device owner keeps themselves — index.json plus one
// JSON per song, built by tools/build_songpack.py. It holds lyric text, so it is
// NEVER part of the deployed site: the app reads it from local disk at runtime and
// nothing is ever uploaded.
//
// Two layers, because they solve different problems:
//
//   IndexedDB content cache  the whole parsed pack. This is what makes the app
//                            work on later visits — and it survives the directory
//                            being moved, renamed or deleted, because the songs
//                            themselves live in the browser's own storage.
//   Directory handle         persisted so "refresh from folder" is one click.
//                            Re-reading needs the browser's permission again; the
//                            cached content does not, which is why the content is
//                            cached separately rather than just the handle.

export interface PackLine {
  /** [column, symbol] pairs — the chord's position over the lyric line. */
  readonly chords: ReadonlyArray<[number, string]>;
  readonly lyric: string;
}
export interface PackSection {
  readonly label: string;
  readonly lines: ReadonlyArray<PackLine>;
}
export interface PackSong {
  readonly id: string;
  readonly title: string;
  readonly artist: string;
  readonly key: string | null;
  readonly capo: number;
  /** True for the Hebrew sheets, which are laid out right-to-left. */
  readonly rtl: boolean;
  readonly url: string;
  readonly site: string;
  readonly sections: ReadonlyArray<PackSection>;
}
export interface PackIndexRow {
  readonly id: string;
  readonly title: string;
  readonly artist: string;
  readonly key: string | null;
  readonly capo: number;
  readonly rtl: boolean;
  readonly chords: number;
  readonly lyrics: number;
}
export interface SongPack {
  readonly format: number;
  readonly count: number;
  readonly digest: string;
  readonly songs: ReadonlyArray<PackIndexRow>;
  /** Full song bodies, keyed by id. */
  readonly bodies: Record<string, PackSong>;
  /** When this pack was read off disk, for the "last loaded" line in the UI. */
  readonly loadedAt: number;
}

const DB_NAME = "chorect";
const DB_VERSION = 1;
const STORE = "songpack";
const PACK_KEY = "pack";
const HANDLE_KEY = "dirHandle";
/** The format tools/build_songpack.py writes; a mismatch means re-read the folder. */
export const SUPPORTED_FORMAT = 1;

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE);
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

function idbGet<T>(key: string): Promise<T | null> {
  return openDb().then((db) => new Promise<T | null>((resolve, reject) => {
    const tx = db.transaction(STORE, "readonly");
    const req = tx.objectStore(STORE).get(key);
    req.onsuccess = () => resolve((req.result as T) ?? null);
    req.onerror = () => reject(req.error);
  }));
}

function idbPut(key: string, value: unknown): Promise<void> {
  return openDb().then((db) => new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, "readwrite");
    tx.objectStore(STORE).put(value, key);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  }));
}

function idbDelete(key: string): Promise<void> {
  return openDb().then((db) => new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, "readwrite");
    tx.objectStore(STORE).delete(key);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  }));
}

/** True when this browser can open a directory at all (Chrome/Edge desktop). */
export function canPickDirectory(): boolean {
  return typeof (window as any).showDirectoryPicker === "function";
}

/**
 * The cached pack, or null if none has been loaded on this machine.
 *
 * This never touches the filesystem, so it needs no permission and works even if
 * the directory has since been moved or deleted — which is the whole point.
 */
export async function loadCachedPack(): Promise<SongPack | null> {
  try {
    const pack = await idbGet<SongPack>(PACK_KEY);
    if (pack === null) return null;
    if (pack.format !== SUPPORTED_FORMAT) return null;
    return pack;
  } catch {
    return null;
  }
}

/** Forget the cached pack and the remembered directory. */
export async function clearPack(): Promise<void> {
  await idbDelete(PACK_KEY);
  await idbDelete(HANDLE_KEY);
}

async function readPackFrom(dir: any): Promise<SongPack> {
  const indexFile = await dir.getFileHandle("index.json");
  const manifest = JSON.parse(await (await indexFile.getFile()).text());
  if (manifest.format !== SUPPORTED_FORMAT) {
    throw new Error(
      `pack format ${manifest.format} but this build reads ${SUPPORTED_FORMAT} — rebuild the pack`);
  }
  const songsDir = await dir.getDirectoryHandle("songs");
  const bodies: Record<string, PackSong> = {};
  for (const row of manifest.songs as PackIndexRow[]) {
    try {
      const fh = await songsDir.getFileHandle(`${row.id}.json`);
      bodies[row.id] = JSON.parse(await (await fh.getFile()).text());
    } catch {
      // A row with no file is skipped rather than failing the whole load; the UI
      // shows the count actually read so a partial pack is visible, not silent.
    }
  }
  return {
    format: manifest.format,
    count: manifest.count,
    digest: manifest.digest,
    songs: manifest.songs,
    bodies,
    loadedAt: Date.now(),
  };
}

/**
 * Ask for a directory, read the pack, and cache it.
 *
 * Requires a user gesture — the browser will not show a directory picker without
 * one, so this must be called straight from a click handler.
 */
export async function pickAndLoadPack(): Promise<SongPack> {
  const dir = await (window as any).showDirectoryPicker({ id: "chorect-songpack", mode: "read" });
  const pack = await readPackFrom(dir);
  await idbPut(PACK_KEY, pack);
  try {
    await idbPut(HANDLE_KEY, dir);
  } catch {
    // Some browsers refuse to structured-clone a handle. The content cache is what
    // matters; losing the handle only costs one extra click on the next refresh.
  }
  return pack;
}

/**
 * Re-read the pack from the remembered directory, if the browser still grants
 * access. Returns null when there is no remembered directory or permission was
 * declined — the caller falls back to the cached content.
 */
export async function refreshFromRememberedDirectory(): Promise<SongPack | null> {
  const dir = await idbGet<any>(HANDLE_KEY);
  if (dir === null) return null;
  try {
    const opts = { mode: "read" as const };
    let perm = await dir.queryPermission(opts);
    if (perm !== "granted") perm = await dir.requestPermission(opts);
    if (perm !== "granted") return null;
    const pack = await readPackFrom(dir);
    await idbPut(PACK_KEY, pack);
    return pack;
  } catch {
    // Moved or deleted since it was remembered — the cache still stands.
    return null;
  }
}
