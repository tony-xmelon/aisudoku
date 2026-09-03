package io.github.tonyxmelon.aisudoku.app

import java.io.File
import java.util.Calendar
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The name of a refused photograph is the whole record of it.
 *
 * There is no index beside these files, deliberately, so that a folder of them survives
 * the app being reinstalled around it. That makes the parsing the only thing standing
 * between the user and a list that silently loses entries.
 */
class DiagnosticsNameTest {

    private fun refused(name: String) = Diagnostics.describe(File(name))

    @Test
    fun `a well formed name gives back its moment and its reason`() {
        val parsed = requireNotNull(refused("scan-20260902-174233-no-grid-found.jpg"))

        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply { time = parsed.at }
        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, calendar.get(Calendar.MONTH))
        assertEquals(2, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(17, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(42, calendar.get(Calendar.MINUTE))
        assertEquals(33, calendar.get(Calendar.SECOND))

        // The reason is a slug of unknown length, so everything after the time belongs
        // to it - taking only the third part would truncate every multi-word reason.
        assertEquals("No grid found", parsed.reason)
    }

    @Test
    fun `a one word reason survives`() {
        assertEquals("Blurred", refused("scan-20260902-174233-blurred.jpg")?.reason)
    }

    @Test
    fun `names that are not records are ignored rather than guessed at`() {
        assertNull(refused("scan-20260902.jpg"), "no time and no reason")
        assertNull(refused("scan-notadate-174233-blurred.jpg"), "unparseable date")
        assertNull(refused("holiday.jpg"), "not one of ours at all")
        assertNull(refused("scan-.jpg"), "prefix and nothing else")
    }
}
