package io.github.tonyxmelon.aisudoku.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp


/**
 * The colours the overlay is drawn in, and what each one means.
 *
 * The rule is that no two things share a colour *within one layer*. Colours do repeat
 * across layers, and that is fine, because only one layer is ever on screen and each one
 * prints its own key. Amber is the exception: it means the app's own uncertainty, in
 * every layer and nowhere else.
 *
 * Defined once here so the key under the photograph and the drawing on it cannot drift
 * apart - including whether a thing is drawn as a fill or as an outline.
 */
object Overlays {

    val solution = Color(0xFF4FC3F7)
    val correct = Color(0xFF66BB6A)
    val incorrect = Color(0xFFEF5350)

    /** The reading layer: what the app decided each square held. */
    val printed = Color(0xFF4DD0E1)
    val written = Color(0xFFBA68C8)
    val marks = Color(0xFF9E9E9E)

    /**
     * A hint is the solution for one square, so it is drawn in the solution's colour.
     *
     * It used to be amber, which is the colour of the app's own doubt - so a hint and a
     * square the reader was unsure of looked the same. Sharing the solution's colour is
     * not a compromise: it is the same thing, for one square.
     */
    val hint = solution

    /**
     * The squares that prove a hint.
     *
     * Shares the handwriting colour. That used to be safe on the grounds that the two
     * never appeared together; they can now, since a square the user typed is drawn as
     * handwriting in every layer including the hint. They stay apart by shape rather than
     * by hue: evidence is a light wash over the whole square, handwriting is a solid fill
     * with the digit cut out of it, and the key names the evidence after its technique
     * rather than calling it "Why". The palette has no eighth hue left that is legible on
     * a photographed page and not already spoken for.
     */
    val evidence = written

    /**
     * Doubt, which rides on the confidence bar rather than on the square.
     *
     * Amber means this and nothing else, anywhere in the app.
     */
    val uncertain = Color(0xFFFFB300)

    fun colour(role: OverlayRole): Color = when (role) {
        OverlayRole.SOLUTION -> solution
        OverlayRole.CORRECT -> correct
        OverlayRole.INCORRECT -> incorrect
        OverlayRole.HINT -> hint
        OverlayRole.WRITTEN -> written
    }

    fun colour(key: LegendKey): Color = when (key) {
        LegendKey.CORRECT -> correct
        LegendKey.INCORRECT -> incorrect
        LegendKey.SOLUTION -> solution
        LegendKey.HINT -> hint
        LegendKey.EVIDENCE -> evidence
        LegendKey.UNCERTAIN -> uncertain
        LegendKey.PRINTED -> printed
        LegendKey.WRITTEN -> written
        LegendKey.MARKS -> marks
        LegendKey.DEAD_END -> incorrect
    }

    /**
     * One word each, because the key is one line and four of these have to fit across a
     * phone. What a colour means beyond its name belongs in the sentence under it.
     *
     * The evidence swatch is the exception: it carries the name of the technique those
     * squares belong to, which is a real name rather than a category, and is the only
     * place that name is now printed. "Why" is what it falls back to when there is no
     * technique behind the highlight to name.
     */
    fun label(key: LegendKey): String = when (key) {
        LegendKey.CORRECT -> "Right"
        LegendKey.INCORRECT -> "Wrong"
        LegendKey.SOLUTION -> "Answer"
        LegendKey.HINT -> "Answer"
        LegendKey.EVIDENCE -> "Why"
        LegendKey.UNCERTAIN -> "Unsure"
        LegendKey.PRINTED -> "Printed"
        LegendKey.WRITTEN -> "Written"
        LegendKey.MARKS -> "Marks"
        LegendKey.DEAD_END -> "Dead end"
    }

    /** True when this is drawn as an outline on the photograph rather than a fill. */
    fun outlined(key: LegendKey): Boolean = false
}

/**
 * The key to whatever is drawn on the photograph. Absent when nothing is.
 *
 * One line, always. It used to wrap, and a key that grows a second line as the drawing
 * changes moves everything under it. It scrolls sideways rather than wrapping on a screen
 * too narrow to hold it, which no phone in practice is: four one-word entries is the most
 * any layer asks for.
 */
@Composable
fun Legend(
    keys: List<LegendKey>,
    modifier: Modifier = Modifier,
    /** What to call the evidence colour. The technique's name, when one is behind it. */
    evidenceLabel: String? = null,
) {
    if (keys.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (key in keys) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val colour = Overlays.colour(key)
                if (Overlays.outlined(key)) {
                    Box(Modifier.size(11.dp).border(2.dp, colour, RoundedCornerShape(3.dp)))
                } else {
                    Box(Modifier.size(11.dp).background(colour, RoundedCornerShape(3.dp)))
                }
                Text(
                    if (key == LegendKey.EVIDENCE) evidenceLabel ?: Overlays.label(key)
                    else Overlays.label(key),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The bar at the top of every screen.
 *
 * Written by hand rather than with `TopAppBar` because every screen wants the same thing
 * and none of them wants scroll behaviour; this keeps the inset handling in one place,
 * which is what actually went wrong before.
 */
@Composable
fun AppBar(
    title: String,
    subtitle: String? = null,
    onMenu: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            onBack != null -> IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            onMenu != null -> IconButton(onClick = onMenu) {
                Icon(Icons.Filled.Menu, contentDescription = "Your puzzles")
            }

            else -> Box(Modifier.size(12.dp))
        }

        // Three 48dp action buttons and a 48dp menu button leave about seventy dip for
        // this on a small phone, so the title is a size down from where it would like to
        // be. Shrinking the buttons instead would take them under the minimum touch target.
        Column(modifier = Modifier.padding(start = 4.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        actions()
    }
}

/**
 * Strategies, Settings and About, behind one icon.
 *
 * These three are the app talking about itself. They are read once and then rarely, and
 * each was costing a permanent slot: two icons in the bar and a row in the drawer that
 * had nothing to do with puzzles. Behind an overflow they cost one icon between them, the
 * title gets its width back, and the drawer answers only the question it is for - which
 * puzzle.
 */
@Composable
fun OverflowMenu(
    onStrategies: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    /** True over the camera preview, where a plain icon would vanish into the picture. */
    glass: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Counted when the menu opens rather than on every recomposition: it is a directory
    // listing, and it can change while the app is running - a scan refused a moment ago
    // is exactly what the user is about to send.
    val kept = remember(open) { if (open) Diagnostics.refused(context).size else 0 }

    Box {
        if (glass) {
            GlassIconButton({ open = true }, Icons.Filled.MoreVert, "More")
        } else {
            IconButton(onClick = { open = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Strategies") },
                onClick = {
                    open = false
                    onStrategies()
                },
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = {
                    open = false
                    onSettings()
                },
            )
            DropdownMenuItem(
                text = { Text("About") },
                onClick = {
                    open = false
                    onAbout()
                },
            )

            // Here rather than only at the foot of the puzzle list, because this is
            // wanted at the moment a scan has just failed - which is the one moment the
            // user is not looking at their puzzles. The count is on the label so that
            // "nothing was refused" is answered before the menu is even used.
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        if (kept == 0) "Send diagnostics" else "Send diagnostics ($kept)"
                    )
                },
                onClick = {
                    open = false
                    if (!Diagnostics.shareAll(context)) {
                        Toast.makeText(
                            context,
                            "Nothing could be sent from this phone.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }
    }
}

/** A round translucent button, for use over the camera preview. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
) {
    IconButton(onClick = onClick, modifier = Modifier.background(Color(0x66000000), CircleShape)) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White)
    }
}

/**
 * A camera, drawn here rather than pulled from the extended icon set.
 *
 * The core Material icons have no camera, and the extended set is tens of megabytes for
 * one glyph. The lens is a hole punched with the even-odd rule rather than a second
 * shape, so the whole icon tints as one piece.
 */
val CameraIcon: ImageVector = ImageVector.Builder(
    name = "Camera",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
        // The raised part on top.
        moveTo(9f, 3f)
        lineTo(15f, 3f)
        lineTo(16.5f, 6f)
        lineTo(7.5f, 6f)
        close()

        // The body.
        moveTo(4f, 6f)
        lineTo(20f, 6f)
        curveTo(21.1f, 6f, 22f, 6.9f, 22f, 8f)
        lineTo(22f, 19f)
        curveTo(22f, 20.1f, 21.1f, 21f, 20f, 21f)
        lineTo(4f, 21f)
        curveTo(2.9f, 21f, 2f, 20.1f, 2f, 19f)
        lineTo(2f, 8f)
        curveTo(2f, 6.9f, 2.9f, 6f, 4f, 6f)
        close()

        // The lens, punched out.
        moveTo(8.2f, 13.5f)
        arcToRelative(3.8f, 3.8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 7.6f, 0f)
        arcToRelative(3.8f, 3.8f, 0f, isMoreThanHalf = true, isPositiveArc = true, -7.6f, 0f)
        close()
    }
}.build()
