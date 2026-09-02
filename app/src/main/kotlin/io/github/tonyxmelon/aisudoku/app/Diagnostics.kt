package io.github.tonyxmelon.aisudoku.app

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last few photographs the app took, so they can be sent to someone who can
 * look at them.
 *
 * Written because a scan failed on a phone in a way nothing here could reproduce: the same
 * scene, photographed and measured on a desktop, sailed through the very check that was
 * rejecting it. A screenshot cannot settle that - it is a picture of a screen showing a
 * cropped, re-encoded copy of the frame - and neither can a photograph taken with a
 * different camera app. Only the bytes this app itself handled can.
 *
 * Only refusals are kept here. A photograph that scanned is already saved as a puzzle,
 * and the straightened copy kept with it is exactly what the recogniser read - so it is
 * the more useful of the two when the digits come out wrong, and it is already in the
 * list where anyone would look for it.
 *
 * They live in the app's own external folder, which needs no permission, and are shared
 * one at a time through a provider that grants read access to those files and nothing
 * else. The app still has no internet permission: sharing hands the file to whatever the
 * user picks, and that app does the sending.
 */
object Diagnostics {

    /** How many photographs to keep. They are several megabytes each. */
    private const val KEEP = 6

    private const val PREFIX = "scan-"

    private val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.UK)

    /**
     * Writes [bytes] to a file named after what became of it.
     *
     * Returns the file name to show the user, or null if it could not be written - a
     * diagnostic that gets in the way of the thing it is diagnosing is worse than none.
     */
    fun keep(context: Context, bytes: ByteArray, outcome: String): String? = runCatching {
        val folder = folder(context) ?: return null
        folder.mkdirs()

        val name = "$PREFIX${stamp.format(Date())}-${slug(outcome)}.jpg"
        File(folder, name).writeBytes(bytes)
        prune(folder)
        name
    }.getOrNull()

    /** A photograph the app would not accept, and why. */
    data class Refused(val file: File, val at: Date, val reason: String)

    /** Every refused photograph kept so far, newest first. */
    fun refused(context: Context): List<Refused> = folder(context)
        ?.listFiles { file: File -> file.name.startsWith(PREFIX) }
        ?.sortedByDescending { it.name }
        ?.mapNotNull { describe(it) }
        .orEmpty()

    /**
     * Reads back what the file name recorded.
     *
     * The name is the whole record - there is no index to keep in step, and a folder of
     * files that describe themselves survives the app being reinstalled around them.
     */
    private fun describe(file: File): Refused? {
        val parts = file.nameWithoutExtension.removePrefix(PREFIX).split("-")
        if (parts.size < 3) return null
        val at = runCatching { stamp.parse("${parts[0]}-${parts[1]}") }.getOrNull() ?: return null
        val reason = parts.drop(2).joinToString(" ").replaceFirstChar { it.uppercase() }
        return Refused(file, at, reason)
    }

    /** Throws one away, when the user is done with it. */
    fun discard(refused: Refused) {
        refused.file.delete()
    }

    /**
     * Hands the kept photographs to whatever the user picks to send them with.
     *
     * Read-only, and only these files. Returns false when there is nothing to send or no
     * app willing to take them, so the caller can say so rather than appearing to do
     * nothing.
     */
    fun share(context: Context, files: List<File>): Boolean {
        if (files.isEmpty()) return false

        val uris = ArrayList(
            files.map { FileProvider.getUriForFile(context, "${context.packageName}.scans", it) }
        )
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                putExtra(Intent.EXTRA_SUBJECT, "AI Sudoku scan")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, "AI Sudoku scans")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return runCatching {
            context.startActivity(
                Intent.createChooser(send, "Send these scans")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }

    private fun folder(context: Context): File? =
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)

    /** The outcome as a file-name fragment: lower case, words joined by dashes. */
    private fun slug(outcome: String): String = outcome
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .trim()
        .split(" ")
        .filter { it.isNotEmpty() }
        .take(4)
        .joinToString("-")
        .ifEmpty { "scan" }

    /** Oldest first out, so a phone does not fill up with photographs nobody asked for. */
    private fun prune(folder: File) {
        val kept = folder.listFiles { file: File -> file.name.startsWith(PREFIX) }
            ?.sortedByDescending { it.name }
            ?: return
        for (old in kept.drop(KEEP)) old.delete()
    }
}
