package io.github.tonyxmelon.aisudoku.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The colours the overlay is drawn in, and what each one means.
 *
 * One meaning per colour, defined once, so the key under the photograph and the drawing
 * on it cannot drift apart.
 */
object Overlays {

    val solution = Color(0xFF4FC3F7)
    val correct = Color(0xFF66BB6A)
    val incorrect = Color(0xFFEF5350)
    val hint = Color(0xFFFFCA28)
    val evidence = Color(0xFF7E9BE8)

    /** Doubt is a ring, never a fill, so it can sit over any of the above. */
    val uncertain = Color(0xFFFFB300)

    /** The reading layer: what the app decided each square held. */
    val printed = Color(0xFF4DD0E1)
    val written = Color(0xFFBA68C8)
    val marks = Color(0xFF9E9E9E)

    fun colour(role: OverlayRole): Color = when (role) {
        OverlayRole.SOLUTION -> solution
        OverlayRole.CORRECT -> correct
        OverlayRole.INCORRECT -> incorrect
        OverlayRole.HINT -> hint
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
    }

    fun label(key: LegendKey): String = when (key) {
        LegendKey.CORRECT -> "Right"
        LegendKey.INCORRECT -> "Wrong"
        LegendKey.SOLUTION -> "The solution"
        LegendKey.HINT -> "Try this"
        LegendKey.EVIDENCE -> "The reason"
        LegendKey.UNCERTAIN -> "Not sure"
        LegendKey.PRINTED -> "Printed"
        LegendKey.WRITTEN -> "Handwritten"
        LegendKey.MARKS -> "Pencil marks, ignored"
    }
}

/** The key to whatever is drawn on the photograph. Absent when nothing is. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Legend(keys: List<LegendKey>, modifier: Modifier = Modifier) {
    if (keys.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (key in keys) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val colour = Overlays.colour(key)
                if (key == LegendKey.UNCERTAIN) {
                    Box(
                        Modifier.size(14.dp)
                            .border(2.dp, colour, RoundedCornerShape(3.dp))
                    )
                } else {
                    Box(Modifier.size(14.dp).background(colour, RoundedCornerShape(3.dp)))
                }
                Text(Overlays.label(key), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * The bar at the top of every screen but the camera.
 *
 * Written by hand rather than with `TopAppBar` because all three screens want the same
 * thing and none of them wants scroll behaviour; this keeps the inset handling in one
 * place, which is what actually went wrong before.
 */
@Composable
fun AppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        } else {
            Box(Modifier.size(12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp).weight(1f),
        )
        actions()
    }
}

/** A round translucent button, for use over the camera preview. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
) {
    IconButton(onClick = onClick, modifier = Modifier.background(Color(0x66000000), CircleShape)) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White)
    }
}
