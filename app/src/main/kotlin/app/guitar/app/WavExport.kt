package app.guitar.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Where a rendered WAV goes on the device.
 *
 * On Android 10+ this writes into the shared **Downloads** collection through
 * MediaStore, which needs no runtime permission and makes the file visible to Files,
 * a DAW, or a USB transfer. Older releases have no permission-free shared-storage
 * path, so the file lands in the app's own external Music folder instead — still
 * reachable over USB, just further down the tree.
 *
 * Returns a human-readable location to show the user, or null if the write failed.
 */
object WavExport {

    /** Sub-folder of Downloads/ the app's exports are grouped under (Android 10+). */
    private const val SUBDIR = "Chorect"

    fun save(context: Context, fileName: String, bytes: ByteArray): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveViaMediaStore(context, fileName, bytes)
        else saveToAppMusicDir(context, fileName, bytes)
    }.getOrNull()

    private fun saveViaMediaStore(context: Context, fileName: String, bytes: ByteArray): String? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "audio/wav")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR")
            // IS_PENDING hides the half-written file from other apps until it's closed.
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return "Downloads/$SUBDIR/$fileName"
    }

    private fun saveToAppMusicDir(context: Context, fileName: String, bytes: ByteArray): String? {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return null
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
