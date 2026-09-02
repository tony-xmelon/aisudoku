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
 * Both kinds are worth keeping. A refused photograph explains why a scan would not start;
 * an accepted one explains why the digits came out wrong.
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

    /** Every photograph kept so far, newest first. */
    fun kept(context: Context): List<File> = folder(context)
        ?.listFiles { file: File -> file.name.startsWith(PREFIX) }
        ?.sortedByDescending { it.name }
        .orEmpty()

    /**
     * Hands the kept photographs to whatever the user picks to send them with.
     *
     * Read-only, and only these files. Returns false when there is nothing to send or no
     * app willing to take them, so the caller can say so rather than appearing to do
     * nothing.
     */
    fun share(context: Context, files: List<File> = kept(context)): Boolean {
        if (files.isEmpty()) return false

        val uris = ArrayList(
            files.map { FileProvider.getUriForFile(context, "${context.packageName}.scans", it) }
        )
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "AI Sudoku scans")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
