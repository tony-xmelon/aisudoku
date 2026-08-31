package io.github.tonyxmelon.aisudoku.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The two things worth choosing, and a way through to everything else.
 *
 * About, privacy and the licences used to sit at the bottom of this page. They are
 * reading, not settings, and burying three screens of prose under two switches made the
 * switches harder to find and the prose harder to come back to.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    onChange: (Settings) -> Unit,
    onAbout: () -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppBar(title = "Settings", onBack = onClose)
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SettingRow(
                    title = "Explain hints",
                    detail = "Name the technique and show the squares that prove it, instead " +
                        "of just giving the digit.",
                    checked = settings.hintStyle == HintStyle.EXPLAIN,
                    onChange = {
                        onChange(
                            settings.copy(
                                hintStyle = if (it) HintStyle.EXPLAIN else HintStyle.REVEAL
                            )
                        )
                    },
                )
            }

            item {
                SettingRow(
                    title = "Take the photo automatically",
                    detail = "Fire the shutter once the grid is square in frame and steady. " +
                        "The button always works either way.",
                    checked = settings.autoCapture,
                    onChange = { onChange(settings.copy(autoCapture = it)) },
                )
            }

            item { HorizontalDivider() }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAbout)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("About AI Sudoku", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "What it does, what it does with your photos, and the open " +
                                "source it is built on.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
