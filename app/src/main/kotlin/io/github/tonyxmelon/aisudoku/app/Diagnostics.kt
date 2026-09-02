package io.github.tonyxmelon.aisudoku.app

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps a copy of a photograph the app refused, so it can be looked at afterwards.
 *
 * Written because a scan failed on a phone that no amount of reasoning here could
 * reproduce: the same scene, photographed and measured on a desktop, sailed through the
 * very check that was rejecting it. A screenshot cannot settle that - it is a picture of a
 * screen showing a re-encoded, cropped, resized version of the frame - and neither can a
 * photograph taken with a different camera app. Only the bytes the app itself refused can.
 *
 * Kept in the app's own external folder, which needs no permission and is visible over USB
 * at Android/data/io.github.tonyxmelon.aisudoku/files/Pictures. Nothing is sent anywhere;
 * the app has no internet permission at all.
 */
object Diagnostics {

    /** How many rejected photographs to keep before the oldest is dropped. */
    private const val KEEP = 12

    private val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.UK)

    /**
     * Writes [bytes] to a file named after the reason it was refused.
     *
     * Returns the file name to show the user, or null if it could not be written - a
     * diagnostic that gets in the way of the thing it is diagnosing is worse than none.
     */
    fun keep(context: Context, bytes: ByteArray, reason: String): String? = runCatching {
        val folder = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
        folder.mkdirs()

        val name = "rejected-${stamp.format(Date())}-${slug(reason)}.jpg"
        File(folder, name).writeBytes(bytes)
        prune(folder)
        name
    }.getOrNull()

    /** The reason as a file-name fragment: lower case, words joined by dashes. */
    private fun slug(reason: String): String = reason
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .trim()
        .split(" ")
        .filter { it.isNotEmpty() }
        .take(4)
        .joinToString("-")

    /** Oldest first out, so a phone does not fill up with failures nobody asked for. */
    private fun prune(folder: File) {
        val kept = folder.listFiles { file: File -> file.name.startsWith("rejected-") }
            ?.sortedByDescending { it.name }
            ?: return
        for (old in kept.drop(KEEP)) old.delete()
    }
}
