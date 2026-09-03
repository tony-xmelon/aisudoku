package io.github.tonyxmelon.aisudoku.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import io.github.tonyxmelon.aisudoku.BuildConfig
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
 * They live in the app's own external folder, which needs no permission, and go out
 * through a provider that grants read access to those files and nothing else - one at a
 * time from the list, or all of them at once with [shareAll] from the overflow menu,
 * which is where they are wanted the moment a scan has just failed. The app still has no
 * internet permission: sharing hands the files to whatever the user picks, and that app
 * does the sending.
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
        val folder = folder(context)
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
        .listFiles { file: File -> file.name.startsWith(PREFIX) }
        ?.sortedByDescending { it.name }
        ?.mapNotNull { describe(it) }
        .orEmpty()

    /**
     * Reads back what the file name recorded.
     *
     * The name is the whole record - there is no index to keep in step, and a folder of
     * files that describe themselves survives the app being reinstalled around them.
     */
    /** Internal so the parsing can be tested; the file name is the whole record. */
    internal fun describe(file: File): Refused? {
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
     * What is worth saying about this phone alongside the photographs.
     *
     * The build number is here because it is the first question asked of any report - the
     * app shows it under the title for the same reason - and the phone and its Android
     * version because a scan that fails on one phone and nowhere else is the whole reason
     * this exists. Nothing here identifies the user: no account, no location, no file
     * paths outside the app's own folder.
     */
    fun report(context: Context): String {
        val kept = refused(context)
        val when_ = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.UK).format(Date())
        return buildString {
            appendLine("AI Sudoku ${BuildConfig.VERSION_NAME}")
            appendLine("$when_, ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
            appendLine()
            if (kept.isEmpty()) {
                appendLine("No photograph has been refused since the app was last installed.")
            } else {
                appendLine("${kept.size} refused photograph(s), newest first:")
                for (scan in kept) {
                    appendLine("  ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK).format(scan.at)}  ${scan.reason}")
                }
            }
        }
    }

    /**
     * Hands over everything gathered at once: every refused photograph, and the report.
     *
     * Sending them one at a time was the only way to do this, which meant that reporting
     * a bad run of five meant five separate shares, each one a separate message at the
     * other end with nothing saying which phone or which build it came from.
     *
     * Returns false only when nothing could be sent at all, so the caller can say so.
     */
    fun shareAll(context: Context): Boolean {
        val files = refused(context).map { it.file }
        val note = report(context)

        // With no photographs there is still something worth sending: which build is on
        // the phone, and that nothing was refused - which is itself an answer, and the
        // one the user gets when the trouble was that the shutter never fired at all.
        if (files.isEmpty()) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "AI Sudoku diagnostics")
                putExtra(Intent.EXTRA_TEXT, note)
            }
            return start(context, send, "Send diagnostics")
        }
        return share(context, files, note)
    }

    /**
     * Hands the kept photographs to whatever the user picks to send them with.
     *
     * Read-only, and only these files. Returns false when there is nothing to send or no
     * app willing to take them, so the caller can say so rather than appearing to do
     * nothing.
     */
    fun share(context: Context, files: List<File>, note: String? = null): Boolean {
        if (files.isEmpty()) return false

        val uris = ArrayList(
            files.map { FileProvider.getUriForFile(context, "${context.packageName}.scans", it) }
        )
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                putExtra(Intent.EXTRA_SUBJECT, "AI Sudoku scan")
                note?.let { putExtra(Intent.EXTRA_TEXT, it) }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, "AI Sudoku scans")
                note?.let { putExtra(Intent.EXTRA_TEXT, it) }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return start(context, send, "Send these scans")
    }

    /**
     * Puts the chooser on screen, and says whether anything was willing to take it.
     *
     * A phone with nothing that can send an image is unusual but not impossible, and the
     * caller needs to be able to say so rather than appearing to do nothing.
     */
    private fun start(context: Context, send: Intent, title: String): Boolean = runCatching {
        context.startActivity(
            Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    /**
     * Where the photographs go.
     *
     * The external folder first, because it can be reached over a cable without the app's
     * help. But it is on removable storage and can simply not be there, and when that
     * happened the write failed, the failure was swallowed, and the app cheerfully said
     * the photo had been kept - so there is a fallback inside private storage, which
     * always exists. Both are shareable.
     */
    private fun folder(context: Context): File =
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: File(context.filesDir, "scans")

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
