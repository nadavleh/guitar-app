package app.guitar.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.File

/**
 * Song pack loading + caching — the Android twin of chorect-web/src/app/songPack.ts.
 *
 * The pack is a directory Nadav keeps himself (index.json plus one JSON per song, built by
 * tools/build_songpack.py). It carries lyric text, so it is never part of the APK
 * or the repo: the app reads it off the device at runtime.
 *
 * Two layers, exactly as on web and for the same reason:
 *
 *   cache file      the whole parsed pack, written to filesDir. This is what makes
 *                   the tab work on later launches, and it survives the source
 *                   directory being moved or deleted — the songs live in the app's
 *                   own storage, not behind the tree URI.
 *   tree URI        persisted so "Refresh" is one tap. Re-reading needs the
 *                   permission to still hold; the cache does not, which is why the
 *                   content is cached separately rather than just the URI.
 *
 * SAF (the Storage Access Framework) is used rather than a hard-coded path because
 * Android 11+ blocks direct filesystem reads outside the app's own directory; the
 * owner picks the folder once and the grant is persisted.
 */
object SongPackStore {

    /** The on-disk shape tools/build_songpack.py writes. */
    const val SUPPORTED_FORMAT = 1

    private const val CACHE_FILE = "songpack_cache.json"
    private const val PREFS = "songpack"
    private const val KEY_TREE_URI = "treeUri"

    data class Line(
        /** Chord positions over the lyric line: column → symbol, in order. */
        val chords: List<Pair<Int, String>>,
        val lyric: String,
    )

    data class Section(val label: String, val lines: List<Line>)

    data class Song(
        val id: String,
        val title: String,
        val artist: String,
        val key: String?,
        val capo: Int,
        /** True for the Hebrew sheets, which are laid out right-to-left. */
        val rtl: Boolean,
        val url: String,
        val site: String,
        val sections: List<Section>,
    )

    data class IndexRow(
        val id: String,
        val title: String,
        val artist: String,
        val key: String?,
        val capo: Int,
        val rtl: Boolean,
        val chords: Int,
        val lyrics: Int,
    )

    data class Pack(
        val format: Int,
        val count: Int,
        val digest: String,
        val rows: List<IndexRow>,
        val bodies: Map<String, Song>,
        val loadedAt: Long,
    )

    // ---------- parsing ----------

    private fun parseSong(o: JSONObject): Song {
        val sections = mutableListOf<Section>()
        val secArr = o.optJSONArray("sections")
        if (secArr != null) {
            for (i in 0 until secArr.length()) {
                val s = secArr.getJSONObject(i)
                val lines = mutableListOf<Line>()
                val lnArr = s.optJSONArray("lines")
                if (lnArr != null) {
                    for (j in 0 until lnArr.length()) {
                        val ln = lnArr.getJSONObject(j)
                        val chords = mutableListOf<Pair<Int, String>>()
                        val cArr = ln.optJSONArray("chords")
                        if (cArr != null) {
                            for (k in 0 until cArr.length()) {
                                val pair = cArr.getJSONArray(k)
                                chords.add(pair.getInt(0) to pair.getString(1))
                            }
                        }
                        lines.add(Line(chords, ln.optString("lyric", "")))
                    }
                }
                sections.add(Section(s.optString("label", "Verse"), lines))
            }
        }
        return Song(
            id = o.getString("id"),
            title = o.optString("title", ""),
            artist = o.optString("artist", ""),
            key = if (o.isNull("key")) null else o.optString("key", null),
            capo = o.optInt("capo", 0),
            rtl = o.optBoolean("rtl", false),
            url = o.optString("url", ""),
            site = o.optString("site", ""),
            sections = sections,
        )
    }

    private fun parseRow(o: JSONObject): IndexRow = IndexRow(
        id = o.getString("id"),
        title = o.optString("title", ""),
        artist = o.optString("artist", ""),
        key = if (o.isNull("key")) null else o.optString("key", null),
        capo = o.optInt("capo", 0),
        rtl = o.optBoolean("rtl", false),
        chords = o.optInt("chords", 0),
        lyrics = o.optInt("lyrics", 0),
    )

    // ---------- reading a picked directory ----------

    /** Read the pack out of a picked tree, or throw with a message worth showing. */
    fun readFrom(context: Context, treeUri: Uri): Pack {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("that folder could not be opened")
        val indexDoc = root.findFile("index.json")
            ?: throw IllegalStateException("no index.json in that folder — is it the song pack?")
        val manifest = JSONObject(readText(context, indexDoc.uri))
        val format = manifest.optInt("format", 0)
        if (format != SUPPORTED_FORMAT) {
            throw IllegalStateException(
                "pack format $format but this build reads $SUPPORTED_FORMAT — rebuild the pack")
        }
        val songsDir = root.findFile("songs")
            ?: throw IllegalStateException("no songs/ folder inside the pack")

        // One listFiles() and a lookup map: calling findFile() per song is O(n^2)
        // over SAF, which is slow enough to be visible at 100+ songs.
        val byName = songsDir.listFiles().associateBy { it.name }

        val rows = mutableListOf<IndexRow>()
        val bodies = mutableMapOf<String, Song>()
        val arr = manifest.getJSONArray("songs")
        for (i in 0 until arr.length()) {
            val row = parseRow(arr.getJSONObject(i))
            rows.add(row)
            val doc = byName["${row.id}.json"] ?: continue
            try {
                bodies[row.id] = parseSong(JSONObject(readText(context, doc.uri)))
            } catch (_: Exception) {
                // A malformed song is skipped rather than failing the whole load; the
                // screen reports how many were actually read.
            }
        }
        return Pack(format, manifest.optInt("count", rows.size),
            manifest.optString("digest", ""), rows, bodies, System.currentTimeMillis())
    }

    private fun readText(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("could not read $uri")

    // ---------- cache ----------

    private fun cacheFile(context: Context) = File(context.filesDir, CACHE_FILE)

    /**
     * Write the whole parsed pack to app storage.
     *
     * Re-serialised from the parsed form rather than copied file-by-file, so the
     * cache is one self-contained document that no longer depends on the source
     * directory existing at all.
     */
    fun saveCache(context: Context, pack: Pack) {
        val root = JSONObject()
        root.put("format", pack.format)
        root.put("count", pack.count)
        root.put("digest", pack.digest)
        root.put("loadedAt", pack.loadedAt)
        val rows = org.json.JSONArray()
        for (r in pack.rows) {
            rows.put(JSONObject().apply {
                put("id", r.id); put("title", r.title); put("artist", r.artist)
                put("key", r.key ?: JSONObject.NULL); put("capo", r.capo)
                put("rtl", r.rtl); put("chords", r.chords); put("lyrics", r.lyrics)
            })
        }
        root.put("songs", rows)
        val bodies = JSONObject()
        for ((id, s) in pack.bodies) {
            val secs = org.json.JSONArray()
            for (sec in s.sections) {
                val lines = org.json.JSONArray()
                for (ln in sec.lines) {
                    val chords = org.json.JSONArray()
                    for ((col, sym) in ln.chords) {
                        chords.put(org.json.JSONArray().put(col).put(sym))
                    }
                    lines.put(JSONObject().apply { put("chords", chords); put("lyric", ln.lyric) })
                }
                secs.put(JSONObject().apply { put("label", sec.label); put("lines", lines) })
            }
            bodies.put(id, JSONObject().apply {
                put("id", s.id); put("title", s.title); put("artist", s.artist)
                put("key", s.key ?: JSONObject.NULL); put("capo", s.capo); put("rtl", s.rtl)
                put("url", s.url); put("site", s.site); put("sections", secs)
            })
        }
        root.put("bodies", bodies)
        cacheFile(context).writeText(root.toString())
    }

    /** The cached pack, or null when nothing has been loaded on this device. This
     *  touches only app storage, so it needs no permission and cannot prompt. */
    fun loadCache(context: Context): Pack? {
        val f = cacheFile(context)
        if (!f.exists()) return null
        return try {
            val root = JSONObject(f.readText())
            if (root.optInt("format") != SUPPORTED_FORMAT) return null
            val rows = mutableListOf<IndexRow>()
            val arr = root.getJSONArray("songs")
            for (i in 0 until arr.length()) rows.add(parseRow(arr.getJSONObject(i)))
            val bodies = mutableMapOf<String, Song>()
            val b = root.getJSONObject("bodies")
            for (id in b.keys()) bodies[id] = parseSong(b.getJSONObject(id))
            Pack(root.optInt("format"), root.optInt("count", rows.size),
                root.optString("digest", ""), rows, bodies, root.optLong("loadedAt", 0L))
        } catch (_: Exception) {
            null
        }
    }

    /** Forget the cached pack and the remembered folder. */
    fun clear(context: Context) {
        cacheFile(context).delete()
        prefs(context).edit().remove(KEY_TREE_URI).apply()
    }

    // ---------- remembered folder ----------

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Persist the grant so the folder stays readable across launches. */
    fun rememberTree(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Not offered by every picker; the content cache is what matters.
        }
        prefs(context).edit().putString(KEY_TREE_URI, uri.toString()).apply()
    }

    fun rememberedTree(context: Context): Uri? =
        prefs(context).getString(KEY_TREE_URI, null)?.let(Uri::parse)
}
