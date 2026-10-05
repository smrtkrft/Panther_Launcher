package ch.smartkraft.pantherlauncher.data

import android.content.Context
import ch.smartkraft.common.AppLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Automatic copies of all settings, taken before an app update is allowed to change them.
 * They live in the app's private storage, so they survive updates but not an uninstall.
 */
object SettingsSnapshots {

    private const val KEEP = 5
    private const val PREFIX = "settings-"

    data class Snapshot(val file: File, val versionCode: Int, val takenAt: Date)

    private fun dir(context: Context): File = File(context.filesDir, "backups").apply { mkdirs() }

    /** Writes a snapshot named after the version the settings belong to; keeps the last [KEEP]. */
    fun save(context: Context, prefs: Prefs, versionCode: Int): File? {
        return try {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir(context), "$PREFIX$versionCode-$stamp.json")
            file.writeText(prefs.saveToString())
            list(context).drop(KEEP).forEach { it.file.delete() }
            AppLogger.i("SettingsSnapshots", "Saved ${file.name}")
            file
        } catch (e: Exception) {
            AppLogger.e("SettingsSnapshots", "Could not save a settings snapshot", e)
            null
        }
    }

    /** Snapshots, newest first. */
    fun list(context: Context): List<Snapshot> {
        val pattern = Regex("""^$PREFIX(\d+)-(\d{8}-\d{6})\.json$""")
        return dir(context).listFiles().orEmpty().mapNotNull { file ->
            val match = pattern.find(file.name) ?: return@mapNotNull null
            val takenAt = try {
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).parse(match.groupValues[2])
            } catch (_: Exception) {
                null
            } ?: Date(file.lastModified())
            Snapshot(file, match.groupValues[1].toInt(), takenAt)
        }.sortedByDescending { it.takenAt }
    }
}
