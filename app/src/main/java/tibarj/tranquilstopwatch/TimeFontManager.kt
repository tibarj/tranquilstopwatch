package tibarj.tranquilstopwatch

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.io.File
import java.io.FileOutputStream

object TimeFontManager {
    private const val FONT_DIR = "time_font"
    private const val FONT_FILE = "time_font.ttf"
    private const val TEMP_FILE = "time_font.tmp"

    private var cachedTypeface: Typeface? = null
    private var cachedPath: String? = null

    fun getTypeface(context: Context): Typeface? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.time_font_path_key)
        val path = prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: return null

        val file = File(path)
        if (!file.exists()) return null

        if (cachedTypeface != null && cachedPath == path) return cachedTypeface

        val typeface = runCatching { Typeface.createFromFile(file) }.getOrNull()
        cachedTypeface = typeface
        cachedPath = path
        return typeface
    }

    fun hasCustomFont(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val pathKey = context.getString(R.string.time_font_path_key)
        val path = prefs.getString(pathKey, null) ?: return false
        return File(path).exists()
    }

    fun getDisplayName(context: Context): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val nameKey = context.getString(R.string.time_font_name_key)
        return prefs.getString(nameKey, null)?.takeIf { it.isNotBlank() }
    }

    fun importFromUri(context: Context, uri: Uri): Result<Unit> {
        return runCatching {
            val dir = File(context.filesDir, FONT_DIR).apply { mkdirs() }
            val dest = File(dir, FONT_FILE)
            val temp = File(dir, TEMP_FILE)

            // Clean any previous temp file from a failed import.
            runCatching { temp.delete() }

            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(temp).use { output ->
                        input.copyTo(output)
                    }
                } ?: error("Unable to open selected file")

                // Replace the previous font file (if any) in a single step.
                if (dest.exists()) {
                    // Ensure rename won't fail due to an existing destination.
                    if (!dest.delete()) {
                        error("Unable to replace existing font")
                    }
                }
                if (!temp.renameTo(dest)) {
                    error("Unable to finalize imported font")
                }
            } finally {
                // Best-effort cleanup in case we error before rename.
                runCatching { temp.delete() }
            }

            val displayName = queryDisplayName(context, uri)

            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit {
                putString(context.getString(R.string.time_font_path_key), dest.absolutePath)
                if (displayName != null) {
                    putString(context.getString(R.string.time_font_name_key), displayName)
                } else {
                    remove(context.getString(R.string.time_font_name_key))
                }
            }

            cachedTypeface = null
            cachedPath = null
        }
    }

    fun clear(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val pathKey = context.getString(R.string.time_font_path_key)
        val path = prefs.getString(pathKey, null)
        if (!path.isNullOrBlank()) {
            runCatching { File(path).delete() }
        }

        // Also remove any temp file that might exist from a failed import.
        runCatching {
            File(File(context.filesDir, FONT_DIR), TEMP_FILE).delete()
        }

        prefs.edit {
            remove(context.getString(R.string.time_font_path_key))
            remove(context.getString(R.string.time_font_name_key))
        }

        cachedTypeface = null
        cachedPath = null
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index < 0) return@use null
                    if (!cursor.moveToFirst()) return@use null
                    cursor.getString(index)
                }
        }.getOrNull()
    }
}
