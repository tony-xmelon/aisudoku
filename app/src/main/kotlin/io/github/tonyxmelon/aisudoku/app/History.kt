package io.github.tonyxmelon.aisudoku.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.tonyxmelon.aisudoku.model.Grid
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/** One saved puzzle: the straightened photograph and the grid as it stood. */
data class HistoryEntry(
    val id: Long,
    val photo: File,
    val grid: Grid,
) {
    val date: Date get() = Date(id)
}

/**
 * Every puzzle the app has read, kept on this phone.
 *
 * One JPEG and one small text file per puzzle, in the app's private storage. No database:
 * there is one table with a handful of rows, and files can be listed, deleted and
 * inspected without a migration story.
 *
 * The grid is stored as two blocks of nine rows - what was printed, and everything on the
 * paper - the same shape as the test corpus labels, so a saved puzzle can be dropped
 * straight into the recogniser's test data if it ever reads one wrongly.
 */
class History(context: Context) {

    private val directory = File(context.filesDir, "history").apply { mkdirs() }

    fun save(photo: Bitmap, grid: Grid): HistoryEntry {
        val id = System.currentTimeMillis()
        val image = File(directory, "$id.jpg")
        image.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        File(directory, "$id.txt").writeText(HistoryFormat.encode(grid))
        return HistoryEntry(id, image, grid)
    }

    /**
     * Records a correction, so a puzzle reopened later is where the user left it rather
     * than where recognition first put it.
     */
    fun update(id: Long, grid: Grid) {
        val text = File(directory, "$id.txt")
        if (text.isFile) text.writeText(HistoryFormat.encode(grid))
    }

    /** Newest first. */
    fun list(): List<HistoryEntry> =
        directory.listFiles { f: File -> f.extension == "txt" }
            ?.mapNotNull { text ->
                val id = text.nameWithoutExtension.toLongOrNull() ?: return@mapNotNull null
                val image = File(directory, "$id.jpg")
                if (!image.isFile) return@mapNotNull null
                val grid = runCatching { HistoryFormat.decode(text.readText()) }.getOrNull() ?: return@mapNotNull null
                HistoryEntry(id, image, grid)
            }
            ?.sortedByDescending { it.id }
            ?: emptyList()

    fun loadPhoto(entry: HistoryEntry): Bitmap? =
        runCatching { BitmapFactory.decodeFile(entry.photo.absolutePath) }.getOrNull()

    fun delete(entry: HistoryEntry) {
        entry.photo.delete()
        File(directory, "${entry.id}.txt").delete()
    }

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    companion object {
        /**
         * Patterns rather than formatters, with the locale applied at the moment of use.
         *
         * These were two static SimpleDateFormats built with Locale.getDefault(). That
         * binds whatever locale happened to be set when the class first loaded, so a
         * phone switched to another language went on printing dates in the old one until
         * the process was killed. SimpleDateFormat is also mutable and not thread-safe,
         * and these were shared - harmless while only the main thread formats a date, and
         * exactly the sort of thing that stops being harmless quietly.
         *
         * DateTimeFormatter is immutable, so withLocale returns a new one and the shared
         * value is never written to.
         */
        private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        private fun format(pattern: DateTimeFormatter, at: Date): String =
            pattern.withLocale(Locale.getDefault())
                .format(at.toInstant().atZone(ZoneId.systemDefault()))

        /** Groups entries under day headings, newest day first, for a list that grows. */
        fun byDay(entries: List<HistoryEntry>): List<Pair<String, List<HistoryEntry>>> =
            entries.groupBy { format(DAY, it.date) }
                .toList()
                .sortedByDescending { (_, group) -> group.maxOf { it.id } }

        fun timeOf(entry: HistoryEntry): String = format(TIME, entry.date)

        /** The same clock, for anything else that happened at a moment worth showing. */
        fun timeOf(at: Date): String = format(TIME, at)
    }
}
