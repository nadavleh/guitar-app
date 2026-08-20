package app.guitar.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.guitar.theory.SongSheet

/**
 * Songs tab — the sideloaded chord sheets. Mirror of chorect-web's songsUI.ts.
 *
 * The pack lives on the device, never in the APK: the shipped app carries the
 * chord library only, and the lyric text comes from a folder Nadav copies over and
 * picks once. Once read it is cached in app storage, so the tab keeps working after
 * the folder is moved or deleted — see [SongPackStore].
 *
 * Three things the sheet does, and deliberately no more: show the chords over the
 * lyrics, transpose them, and relabel them by degree. No playback, no shapes.
 */
@Composable
fun SongsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var pack by remember { mutableStateOf<SongPackStore.Pack?>(null) }
    var status by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var shift by remember { mutableStateOf(0) }
    var degrees by remember { mutableStateOf(false) }

    // The cache read touches app storage only, so it cannot prompt.
    LaunchedEffect(Unit) {
        pack = SongPackStore.loadCache(context)
        pack?.let { status = "${it.count} songs · cached" }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val p = SongPackStore.readFrom(context, uri)
            SongPackStore.saveCache(context, p)
            SongPackStore.rememberTree(context, uri)
            pack = p
            val read = p.bodies.size
            status = if (read == p.count) "${p.count} songs loaded and cached"
            else "$read of ${p.count} songs loaded (some files missing)"
        } catch (e: Exception) {
            status = e.message ?: "could not read that folder"
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("SONGS", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { picker.launch(null) }) {
                Text(if (pack == null) "Open song folder…" else "Change folder…")
            }
            if (pack != null) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    val uri = SongPackStore.rememberedTree(context)
                    status = if (uri == null) "no folder remembered" else try {
                        val p = SongPackStore.readFrom(context, uri)
                        SongPackStore.saveCache(context, p)
                        pack = p
                        "${p.count} songs · refreshed"
                    } catch (_: Exception) {
                        "the folder is not reachable — showing the cached copy"
                    }
                }) { Text("Refresh") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    SongPackStore.clear(context)
                    pack = null
                    selected = null
                    status = "cache cleared"
                }) { Text("Forget") }
            }
        }
        if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))

        val current = pack
        if (current == null) {
            Text(
                "No song folder loaded on this phone yet.\n\n" +
                    "Copy the folder built by tools/build_songpack.py onto the device " +
                    "(it holds index.json and songs/), then pick it here. The songs are " +
                    "cached in the app afterwards, so they stay available even if you " +
                    "move or delete the folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        val sel = selected
        val song = if (sel != null) current.bodies[sel] else null
        if (song != null) {
            SongSheetView(song, shift, degrees,
                onBack = { selected = null },
                onShift = { shift = ((shift + it) % 12 + 12) % 12 },
                onReset = { shift = 0 },
                onToggleDegrees = { degrees = !degrees })
            return@Column
        }

        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Search title or artist") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))

        val q = query.trim().lowercase()
        val rows = if (q.isEmpty()) current.rows else current.rows.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            for (r in rows) {
                val meta = buildList {
                    r.key?.let { add(it) }
                    if (r.capo > 0) add("capo ${r.capo}")
                    if (r.lyrics == 0) add("chords only")
                }
                Column(modifier = Modifier.fillMaxWidth()
                    .clickable { selected = r.id; shift = 0 }
                    .padding(vertical = 6.dp)) {
                    Text(r.title, fontWeight = FontWeight.SemiBold)
                    Text(r.artist + if (meta.isEmpty()) "" else " · " + meta.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** One song: header, transpose/degrees controls, then the chord sheet itself. */
@Composable
private fun SongSheetView(
    song: SongPackStore.Song,
    shift: Int,
    degrees: Boolean,
    onBack: () -> Unit,
    onShift: (Int) -> Unit,
    onReset: () -> Unit,
    onToggleDegrees: () -> Unit,
) {
    val key = song.key?.let { SongSheet.parseKey(it) }
    val flats = SongSheet.prefersFlats(key)
    val shownKey = if (key != null) SongSheet.transposeKey(key, shift, flats) else "—"

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onBack) { Text("← Songs") }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(song.title, fontWeight = FontWeight.Bold)
            Text(song.artist, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text(
        "Key $shownKey" + (if (song.capo > 0) " · capo ${song.capo}" else "") +
            (if (shift != 0) " · transposed ${if (shift > 0) "+" else ""}$shift" else ""),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onShift(-1) }) { Text("−") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { onShift(1) }) { Text("+") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onReset) { Text("Reset") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onToggleDegrees) { Text(if (degrees) "Chords" else "Degrees") }
    }
    if (degrees && key == null) {
        Text("no key detected — degrees unavailable",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        for (sec in song.sections) {
            Text(sec.label, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp))
            for (line in sec.lines) {
                val chordText = chordLine(line.chords, key, flats, shift, degrees)
                Column(modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())) {
                    if (chordText.isNotBlank()) {
                        Text(chordText, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = if (song.rtl) TextAlign.End else TextAlign.Start)
                    }
                    if (line.lyric.isNotEmpty()) {
                        Text(line.lyric, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                            textAlign = if (song.rtl) TextAlign.End else TextAlign.Start)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Rebuild a chord line at its original columns.
 *
 * Transposing and relabelling change how wide each symbol is, so the columns are
 * re-laid rather than reused verbatim: each chord starts at its recorded column
 * when there is room, and is pushed right by a single space when the previous one
 * would otherwise run into it. Without that, "C" becoming "C#m7" would silently
 * swallow the next chord. Mirrors SongsUI.chordLine on web.
 */
private fun chordLine(
    chords: List<Pair<Int, String>>,
    key: SongSheet.SongKey?,
    flats: Boolean,
    shift: Int,
    degrees: Boolean,
): String {
    val sb = StringBuilder()
    for ((col, raw) in chords) {
        val sym = if (degrees && key != null) SongSheet.degreeLabel(raw, key)
        else SongSheet.transposeSymbol(raw, shift, flats)
        if (sb.length < col) sb.append(" ".repeat(col - sb.length))
        else if (sb.isNotEmpty()) sb.append(' ')
        sb.append(sym)
    }
    return sb.toString()
}
