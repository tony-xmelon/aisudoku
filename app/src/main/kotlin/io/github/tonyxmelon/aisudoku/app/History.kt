package io.github.tonyxmelon.aisudoku.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.model.Grid
import java.io.File
import java.text.SimpleDateFormat
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
        File(directory, "$id.txt").writeText(encode(grid))
        return HistoryEntry(id, image, grid)
    }

    /**
     * Records a correction, so a puzzle reopened later is where the user left it rather
     * than where recognition first put it.
     */
    fun update(id: Long, grid: Grid) {
        val text = File(directory, "$id.txt")
        if (text.isFile) text.writeText(encode(grid))
    }

    /** Newest first. */
    fun list(): List<HistoryEntry> =
        directory.listFiles { f: File -> f.extension == "txt" }
            ?.mapNotNull { text ->
                val id = text.nameWithoutExtension.toLongOrNull() ?: return@mapNotNull null
                val image = File(directory, "$id.jpg")
                if (!image.isFile) return@mapNotNull null
                val grid = runCatching { decode(text.readText()) }.getOrNull() ?: return@mapNotNull null
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

    private fun encode(grid: Grid): String {
        fun rows(predicate: (Cell) -> Boolean) = (0 until 9).joinToString("\n") { r ->
            (0 until 9).joinToString("") { c ->
                val cell = grid[r * 9 + c]
                if (cell.isFilled && predicate(cell)) cell.digit.toString() else "."
            }
        }
        return rows { it.source == CellSource.GIVEN } + "\n--\n" + rows { true }
    }

    private fun decode(text: String): Grid {
        val (givensBlock, writtenBlock) = text.split("\n--\n")
        val givens = givensBlock.filterNot { it.isWhitespace() }
        val written = writtenBlock.filterNot { it.isWhitespace() }
        require(givens.length == 81 && written.length == 81) { "malformed history entry" }

        var grid = Grid.Empty
        for (i in 0 until 81) {
            grid = when {
                givens[i] != '.' -> grid.with(i, Cell.given(givens[i] - '0'))
                written[i] != '.' -> grid.with(i, Cell.guess(written[i] - '0'))
                else -> grid
            }
        }
        return grid
    }

    companion object {
        private val DAY = SimpleDateFormat("EEEE d MMMM yyyy", Locale.getDefault())
        private val TIME = SimpleDateFormat("HH:mm", Locale.getDefault())

        /** Groups entries under day headings, newest day first, for a list that grows. */
        fun byDay(entries: List<HistoryEntry>): List<Pair<String, List<HistoryEntry>>> =
            entries.groupBy { DAY.format(it.date) }
                .toList()
                .sortedByDescending { (_, group) -> group.maxOf { it.id } }

        fun timeOf(entry: HistoryEntry): String = TIME.format(entry.date)
    }
}
